package com.somepro.application.hwaste;

import com.somepro.common.exception.BizException;
import com.somepro.domain.hwaste.model.AlertType;
import com.somepro.domain.hwaste.model.TransferManifest;
import com.somepro.domain.hwaste.model.WasteAlert;
import com.somepro.domain.hwaste.repository.ManifestSignoffRepository;
import com.somepro.domain.hwaste.repository.TransferManifestRepository;
import com.somepro.domain.hwaste.repository.TreatmentUnitRepository;
import com.somepro.domain.hwaste.repository.WasteAlertRepository;
import com.somepro.domain.shared.model.PageResult;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 危废异常预警用例编排（应用层）：立预警 → 处置 → 关闭，以及多条件翻页。
 *
 * 立预警不是随手写，得对得上号（规则落在领域对象 {@link WasteAlert}，这里只做编排取数）：
 * - 在途超期 OVERDUE：只给运输中的联单立，超多久从启运那一刻算到现在；
 * - 重量差异 WEIGHT_DIFF：只给已签收的单子立，签收实收取自签收单，跟联单申报比，差了才立；
 * - 许可超限 QUOTA：只给签收后处置单位累计接收盖过许可上限的单子立。
 * 级别由领域按情况推出来（在途时长 / 重量差幅 / 许可超限一律高），不是手填的。
 * 同一张联单配同一种类型只挂一条，重复提交不再新建（仓储侧锁内查重兜底）。
 *
 * 挂出来以后可以处置（RAISED → HANDLING，处置过程中把说明补上）；处置完了能关掉
 * （HANDLING → CLOSED，落下关闭时刻）。已关闭是终态。领域状态机先挡一道，
 * 仓储条件更新（只认源状态）兜底，手快点两下第二下没什么可动。
 */
@Service
public class WasteAlertAppService {

    private final WasteAlertRepository wasteAlertRepository;
    private final TransferManifestRepository transferManifestRepository;
    private final ManifestSignoffRepository manifestSignoffRepository;
    private final TreatmentUnitRepository treatmentUnitRepository;

    public WasteAlertAppService(WasteAlertRepository wasteAlertRepository,
                                TransferManifestRepository transferManifestRepository,
                                ManifestSignoffRepository manifestSignoffRepository,
                                TreatmentUnitRepository treatmentUnitRepository) {
        this.wasteAlertRepository = wasteAlertRepository;
        this.transferManifestRepository = transferManifestRepository;
        this.manifestSignoffRepository = manifestSignoffRepository;
        this.treatmentUnitRepository = treatmentUnitRepository;
    }

    /**
     * 立预警：manifestId 或 manifestNo 传其一；alertType 限 OVERDUE / WEIGHT_DIFF / QUOTA。
     * 前置条件与级别推算都在领域工厂里；同一张联单配同一种类型已挂过的，直接返回已挂的那条。
     */
    public Mono<WasteAlert> raise(Long manifestId, String manifestNo, String alertType, String detail) {
        return Mono.defer(() -> {
            AlertType type = AlertType.of(alertType);
            return loadManifest(manifestId, manifestNo)
                    .flatMap(manifest -> raiseByType(manifest, type, detail))
                    .flatMap(wasteAlertRepository::createIfAbsent);
        });
    }

    /** 处置：已发布 / 处置中 → 处置中，处置过程中把说明补上（传了说明才覆盖）；已关闭的办不了。 */
    public Mono<WasteAlert> handle(Long alertId, String alertNo, String detail) {
        return load(alertId, alertNo).flatMap(alert -> {
            alert.handle(detail);
            return wasteAlertRepository.handle(alert);
        });
    }

    /** 关闭：处置完了才能关，落下关闭时刻；已关闭是终态。 */
    public Mono<WasteAlert> close(Long alertId, String alertNo) {
        return load(alertId, alertNo).flatMap(alert -> {
            alert.close();
            return wasteAlertRepository.close(alert);
        });
    }

    /** 翻预警：联单 / 类型 / 级别 / 状态随意拼，一个都不填分页列全；每行带预警号。 */
    public Mono<PageResult<WasteAlert>> page(int pageNum, int pageSize, Long manifestId, String alertType,
                                             String alertLevel, String status) {
        return wasteAlertRepository.page(pageNum, pageSize, manifestId, alertType, alertLevel, status);
    }

    /** 按类型取数立单：规则与级别推算都在领域工厂里，这里只负责把要用的数取回来。 */
    private Mono<WasteAlert> raiseByType(TransferManifest manifest, AlertType type, String detail) {
        switch (type) {
            case OVERDUE:
                return Mono.fromCallable(() -> WasteAlert.raiseOverdue(manifest, detail));
            case WEIGHT_DIFF:
                return manifestSignoffRepository.findByManifestId(manifest.getId())
                        .switchIfEmpty(Mono.error(new BizException("该联单还没有签收记录，立不了重量差异预警")))
                        .map(signoff -> WasteAlert.raiseWeightDiff(manifest, signoff.getReceivedWeight(), detail));
            case QUOTA:
                return treatmentUnitRepository.findById(manifest.getUnitId())
                        .switchIfEmpty(Mono.error(new BizException("处置单位不存在")))
                        .map(unit -> WasteAlert.raiseQuota(manifest, unit, detail));
            default:
                return Mono.error(new BizException("不支持的预警类型：" + type));
        }
    }

    /** 按 id 或编号加载联单；两个都不传或查不到都视为业务失败。 */
    private Mono<TransferManifest> loadManifest(Long manifestId, String manifestNo) {
        Mono<TransferManifest> found;
        if (manifestId != null) {
            found = transferManifestRepository.findById(manifestId);
        } else if (manifestNo != null && !manifestNo.isBlank()) {
            found = transferManifestRepository.findByManifestNo(manifestNo.trim());
        } else {
            return Mono.error(new BizException("manifestId 或 manifestNo 必传其一"));
        }
        return found.switchIfEmpty(Mono.error(new BizException("转移联单不存在")));
    }

    /** 按 id 或编号加载预警；两个都不传或查不到都视为业务失败。 */
    private Mono<WasteAlert> load(Long alertId, String alertNo) {
        Mono<WasteAlert> found;
        if (alertId != null) {
            found = wasteAlertRepository.findById(alertId);
        } else if (alertNo != null && !alertNo.isBlank()) {
            found = wasteAlertRepository.findByAlertNo(alertNo.trim());
        } else {
            return Mono.error(new BizException("alertId 或 alertNo 必传其一"));
        }
        return found.switchIfEmpty(Mono.error(new BizException("预警不存在")));
    }
}
