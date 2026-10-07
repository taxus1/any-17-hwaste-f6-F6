package com.somepro.application.hwaste;

import com.somepro.common.exception.BizException;
import com.somepro.domain.hwaste.model.AlertType;
import com.somepro.domain.hwaste.model.ManifestStatus;
import com.somepro.domain.hwaste.model.ManifestSignoff;
import com.somepro.domain.hwaste.model.TransferManifest;
import com.somepro.domain.hwaste.model.TreatmentUnit;
import com.somepro.domain.hwaste.model.WasteAlert;
import com.somepro.domain.hwaste.repository.ManifestSignoffRepository;
import com.somepro.domain.hwaste.repository.TransferManifestRepository;
import com.somepro.domain.hwaste.repository.TreatmentUnitRepository;
import com.somepro.domain.hwaste.repository.WasteAlertRepository;
import com.somepro.domain.shared.model.PageResult;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 危废异常预警用例编排（应用层）：立预警 → 处置（补说明）→ 关闭，以及多条件分页查询。
 *
 * 立预警不是随手写，得对得上号，前置门槛在这里先验，级别在领域对象里按情况推出：
 * - OVERDUE 在途超期：只给正走在路上（IN_TRANSIT）的联单立；没启运或已签收的谈不上超期。
 * - WEIGHT_DIFF 重量差异：只给已签收（RECEIVED / DISPOSED）的联单立，拿实收跟申报比，
 *   一分不差不立。
 * - QUOTA 许可超限：只给已签收且接收厂累计接收盖过许可上限的单子立，一律高 HIGH。
 *
 * 同一张联单 + 同一类型只挂一条，重复提交不新建（仓储侧锁内查重，已挂则原样返回）。
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
     * 立预警：alertType 限 OVERDUE / WEIGHT_DIFF / QUOTA；联单按 manifestId 或 manifestNo 定位。
     * 级别由领域按情况推出，同联单同类型重复提交返回已挂的那一条，不再新建。
     */
    public Mono<WasteAlert> raise(Long manifestId, String manifestNo, String alertType) {
        AlertType type = parseType(alertType);
        return loadManifest(manifestId, manifestNo).flatMap(manifest -> {
            switch (type) {
                case OVERDUE:
                    return raiseOverdue(manifest);
                case WEIGHT_DIFF:
                    return raiseWeightDiff(manifest);
                case QUOTA:
                    return raiseQuota(manifest);
                default:
                    return Mono.error(new BizException("不支持的预警类型：" + alertType));
            }
        });
    }

    /** 处置：已发布 / 处置中可办，把处置说明补上；已关闭的不再动。 */
    public Mono<WasteAlert> handle(Long alertId, String alertNo, String handlingNote) {
        return loadAlert(alertId, alertNo).flatMap(alert -> {
            // 状态门槛与说明必填在领域行为里；仓储侧条件更新兜底并发
            alert.handle(handlingNote);
            return wasteAlertRepository.handle(alert);
        });
    }

    /** 关闭：处置完才关，落下关闭时刻；没处置过 / 已关闭的关不掉。 */
    public Mono<WasteAlert> close(Long alertId, String alertNo) {
        return loadAlert(alertId, alertNo).flatMap(alert -> {
            alert.close();
            return wasteAlertRepository.close(alert);
        });
    }

    public Mono<WasteAlert> detail(Long alertId, String alertNo) {
        return loadAlert(alertId, alertNo);
    }

    /** 翻预警：联单 / 类型 / 级别 / 状态随意拼，一个都不传则分页列全；每行带预警号。 */
    public Mono<PageResult<WasteAlert>> page(int pageNum, int pageSize, Long manifestId, String alertType,
                                             String alertLevel, String status) {
        return wasteAlertRepository.page(pageNum, pageSize, manifestId, alertType, alertLevel, status);
    }

    /** 在途超期：只给正走在路上的联单立；超多久从启运算到现在，级别在领域里按时长定。 */
    private Mono<WasteAlert> raiseOverdue(TransferManifest manifest) {
        if (manifest.getStatus() != ManifestStatus.IN_TRANSIT) {
            return Mono.error(new BizException("只有正走在路上（运输中）的联单才谈得上在途超期"));
        }
        if (manifest.getTransportBegin() == null) {
            return Mono.error(new BizException("联单尚未启运，谈不上在途超期"));
        }
        WasteAlert alert = WasteAlert.raiseOverdue(manifest.getId(), manifest.getTransportBegin());
        return wasteAlertRepository.createIfAbsent(alert);
    }

    /** 重量差异：只给已签收的联单立，拿签收实收重量跟联单申报重量比，差了才立。 */
    private Mono<WasteAlert> raiseWeightDiff(TransferManifest manifest) {
        if (manifest.getStatus() != ManifestStatus.RECEIVED && manifest.getStatus() != ManifestStatus.DISPOSED) {
            return Mono.error(new BizException("只有已签收的联单才能立重量差异预警"));
        }
        return manifestSignoffRepository.findByManifestId(manifest.getId())
                .switchIfEmpty(Mono.error(new BizException("找不到该联单的签收记录，无法比对实收重量")))
                .flatMap(this::checkConfirmed)
                .flatMap(signoff -> wasteAlertRepository.createIfAbsent(WasteAlert.raiseWeightDiff(
                        manifest.getId(), manifest.getTransferWeight(), signoff.getReceivedWeight())));
    }

    /** 许可超限：只给已签收的联单立，接收厂累计接收盖过许可上限才立，级别一律 HIGH。 */
    private Mono<WasteAlert> raiseQuota(TransferManifest manifest) {
        if (manifest.getStatus() != ManifestStatus.RECEIVED && manifest.getStatus() != ManifestStatus.DISPOSED) {
            return Mono.error(new BizException("只有签收后的联单才能立许可超限预警"));
        }
        Long manifestId = manifest.getId();
        return treatmentUnitRepository.findById(manifest.getUnitId())
                .switchIfEmpty(Mono.error(new BizException("联单对应的处置单位不存在")))
                // 盖过许可上限才立，门槛与级别（一律 HIGH）都在领域工厂里
                .flatMap(unit -> wasteAlertRepository.createIfAbsent(WasteAlert.raiseQuota(
                        manifestId, unit.getLicensedWeight(), unit.getReceivedWeight())));
    }

    /** 签收单取到后核对实收重量（findByManifestId 已按联单查，这里仅防御性兜底空重量）。 */
    private Mono<ManifestSignoff> checkConfirmed(ManifestSignoff signoff) {
        if (signoff.getReceivedWeight() == null) {
            return Mono.error(new BizException("签收记录缺少实收重量，无法比对重量差异"));
        }
        return Mono.just(signoff);
    }

    private AlertType parseType(String alertType) {
        if (alertType == null || alertType.isBlank()) {
            throw new BizException("预警类型不能为空：OVERDUE / WEIGHT_DIFF / QUOTA");
        }
        try {
            return AlertType.valueOf(alertType.trim());
        } catch (IllegalArgumentException e) {
            throw new BizException("不支持的预警类型：" + alertType
                    + "，只支持 OVERDUE / WEIGHT_DIFF / QUOTA");
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
    private Mono<WasteAlert> loadAlert(Long alertId, String alertNo) {
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
