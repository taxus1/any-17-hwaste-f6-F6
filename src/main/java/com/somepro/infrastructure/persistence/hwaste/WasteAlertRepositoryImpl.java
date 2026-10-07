package com.somepro.infrastructure.persistence.hwaste;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.pagehelper.PageHelper;
import com.somepro.common.exception.BizException;
import com.somepro.domain.hwaste.model.AlertStatus;
import com.somepro.domain.hwaste.model.WasteAlert;
import com.somepro.domain.hwaste.repository.WasteAlertRepository;
import com.somepro.domain.shared.model.PageResult;
import com.somepro.infrastructure.persistence.base.BaseBlockingRepository;
import com.somepro.infrastructure.persistence.hwaste.converter.WasteAlertPoConverter;
import com.somepro.infrastructure.persistence.hwaste.po.WasteAlertPO;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 危废异常预警仓储适配器（基础设施层）。
 *
 * 立预警的「查重 + 取号 + 插入」包在同一把 WA 锁里：同一张联单同一类型几乎同时立，
 * 后到的在锁里重查发现已挂一条（含已关闭的），原样返回不再新建；
 * 同一个预警号也只成一单，库表 uk_alert_no 唯一约束是最后兜底。
 * 处置 / 关闭走条件更新（UPDATE ... WHERE id=? AND status IN <源状态>），
 * 更新 0 行说明已被别人推过状态，后到的请求直接拒掉。
 */
@Repository
public class WasteAlertRepositoryImpl extends BaseBlockingRepository implements WasteAlertRepository {

    private final WasteAlertMapper wasteAlertMapper;
    private final BizNoService bizNoService;

    public WasteAlertRepositoryImpl(WasteAlertMapper wasteAlertMapper, BizNoService bizNoService) {
        this.wasteAlertMapper = wasteAlertMapper;
        this.bizNoService = bizNoService;
    }

    @Override
    public Mono<WasteAlert> createIfAbsent(WasteAlert alert) {
        return blocking(() -> bizNoService.inLock("WA", () -> {
            // 锁内查重：同联单 + 同类型只挂一条（含已关闭的），重复提交不再新建
            WasteAlertPO existing = wasteAlertMapper.selectOne(Wrappers.<WasteAlertPO>lambdaQuery()
                    .eq(WasteAlertPO::getManifestId, alert.getManifestId())
                    .eq(WasteAlertPO::getAlertType, alert.getAlertType().name())
                    .orderByDesc(WasteAlertPO::getId)
                    .last("LIMIT 1"));
            if (existing != null) {
                return WasteAlertPoConverter.toDomain(existing);
            }
            WasteAlertPO po = WasteAlertPoConverter.toPo(alert);
            po.setId(IdUtil.getSnowflakeNextId());
            po.setAlertNo(bizNoService.nextAlertNo());
            wasteAlertMapper.insert(po);
            return WasteAlertPoConverter.toDomain(po);
        }));
    }

    @Override
    public Mono<WasteAlert> findById(Long id) {
        return blocking(() -> {
            WasteAlertPO po = wasteAlertMapper.selectById(id);
            return po == null ? null : WasteAlertPoConverter.toDomain(po);
        });
    }

    @Override
    public Mono<WasteAlert> findByAlertNo(String alertNo) {
        return blocking(() -> {
            WasteAlertPO po = wasteAlertMapper.selectOne(Wrappers.<WasteAlertPO>lambdaQuery()
                    .eq(WasteAlertPO::getAlertNo, alertNo));
            return po == null ? null : WasteAlertPoConverter.toDomain(po);
        });
    }

    @Override
    public Mono<WasteAlert> findByManifestAndType(Long manifestId, String alertType) {
        return blocking(() -> {
            WasteAlertPO po = wasteAlertMapper.selectOne(Wrappers.<WasteAlertPO>lambdaQuery()
                    .eq(WasteAlertPO::getManifestId, manifestId)
                    .eq(WasteAlertPO::getAlertType, alertType)
                    .orderByDesc(WasteAlertPO::getId)
                    .last("LIMIT 1"));
            return po == null ? null : WasteAlertPoConverter.toDomain(po);
        });
    }

    @Override
    public Mono<WasteAlert> handle(WasteAlert alert) {
        return blocking(() -> {
            // 条件更新：RAISED / HANDLING 都能推到 HANDLING（处置中可继续补说明）；已关闭更新 0 行
            WasteAlertPO patch = new WasteAlertPO();
            patch.setStatus(AlertStatus.HANDLING.name());
            patch.setDetail(alert.getDetail());
            int rows = wasteAlertMapper.update(patch, Wrappers.<WasteAlertPO>lambdaUpdate()
                    .eq(WasteAlertPO::getId, alert.getId())
                    .in(WasteAlertPO::getStatus,
                            Arrays.asList(AlertStatus.RAISED.name(), AlertStatus.HANDLING.name())));
            if (rows == 0) {
                throw new BizException("预警已关闭或状态已变更，不能处置");
            }
            return WasteAlertPoConverter.toDomain(wasteAlertMapper.selectById(alert.getId()));
        });
    }

    @Override
    public Mono<WasteAlert> close(WasteAlert alert) {
        return blocking(() -> {
            // 条件更新：只有 HANDLING 推得到 CLOSED；没处置过 / 已关闭的更新 0 行
            WasteAlertPO patch = new WasteAlertPO();
            patch.setStatus(AlertStatus.CLOSED.name());
            patch.setClosedAt(alert.getClosedAt());
            int rows = wasteAlertMapper.update(patch, Wrappers.<WasteAlertPO>lambdaUpdate()
                    .eq(WasteAlertPO::getId, alert.getId())
                    .eq(WasteAlertPO::getStatus, AlertStatus.HANDLING.name()));
            if (rows == 0) {
                throw new BizException("预警不在处置中，不能关闭（须先处置再关闭）");
            }
            return WasteAlertPoConverter.toDomain(wasteAlertMapper.selectById(alert.getId()));
        });
    }

    @Override
    public Mono<PageResult<WasteAlert>> page(int pageNum, int pageSize, Long manifestId, String alertType,
                                             String alertLevel, String status) {
        return this.<PageResult<WasteAlert>>blocking(() -> {
            try {
                PageHelper.startPage(pageNum, pageSize);
                LambdaQueryWrapper<WasteAlertPO> wrapper = Wrappers.<WasteAlertPO>lambdaQuery()
                        .eq(manifestId != null, WasteAlertPO::getManifestId, manifestId)
                        .eq(alertType != null && !alertType.isBlank(),
                                WasteAlertPO::getAlertType, alertType)
                        .eq(alertLevel != null && !alertLevel.isBlank(),
                                WasteAlertPO::getAlertLevel, alertLevel)
                        .eq(status != null && !status.isBlank(), WasteAlertPO::getStatus, status)
                        .orderByDesc(WasteAlertPO::getId);
                List<WasteAlertPO> rows = wasteAlertMapper.selectList(wrapper);
                long total = rows instanceof com.github.pagehelper.Page
                        ? ((com.github.pagehelper.Page<?>) rows).getTotal()
                        : rows.size();
                List<WasteAlert> content = rows.stream()
                        .map(WasteAlertPoConverter::toDomain)
                        .collect(Collectors.toList());
                return new PageResult<>(content, total, pageNum, pageSize);
            } finally {
                PageHelper.clearPage();
            }
        });
    }
}
