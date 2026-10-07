package com.somepro.domain.hwaste.model;

import com.somepro.common.exception.BizException;
import com.somepro.domain.shared.model.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 危废异常预警（聚合根，纯领域，无框架注解）。
 *
 * 一条预警占一条记录，挂在一张联单下：记类型、级别、说明、发布时刻和关闭时刻。
 * 预警编号形如 WA-2026-0001，由仓储层分配，全局唯一。
 *
 * 不变量集中在这里：
 * - 立预警得对得上号：在途超期只给运输中的联单立（超多久从启运那一刻算到现在）；
 *   重量差异只给已签收的单子立（签收实收跟申报比，一分不差的不立）；
 *   许可超限只给签收后处置单位累计接收盖过许可上限的单子立。
 * - 级别是推出来的，不是手填的：在途超期看在途时长（不足三天 LOW，三天到七天含七天
 *   MEDIUM，七天往上 HIGH）；重量差异看差幅（不到一成 LOW，一成到两成含两成 MEDIUM，
 *   两成往上 HIGH）；许可超限一律 HIGH。
 * - 状态机：立起来落 RAISED；处置（→ HANDLING，处置过程中把说明补上）；处置完了关掉
 *   （→ CLOSED，记下关闭时刻）。已关闭是终态，不再处置也不再关。
 */
@Getter
@Setter
public class WasteAlert extends BaseEntity {

    /** 在途超期分档：不足三天落低。 */
    private static final long OVERDUE_MEDIUM_DAYS = 3L;
    /** 在途超期分档：三天到七天（含）落中，七天往上落高。 */
    private static final long OVERDUE_HIGH_DAYS = 7L;
    /** 重量差异分档：差幅不到一成落低。 */
    private static final BigDecimal DIFF_MEDIUM_RATIO = new BigDecimal("0.1");
    /** 重量差异分档：一成到两成（含）落中，两成往上落高。 */
    private static final BigDecimal DIFF_HIGH_RATIO = new BigDecimal("0.2");

    private Long id;

    /** 预警编号，全局唯一，形如 WA-2026-0001（由仓储层分配）。 */
    private String alertNo;

    /** 挂在哪张联单下（t_transfer_manifest.id）。 */
    private Long manifestId;

    private AlertType alertType;

    private AlertLevel alertLevel;

    private AlertStatus status;

    /** 预警说明：立单时可带，处置过程中再补上。 */
    private String detail;

    /** 发布时刻（立单那一刻落）。 */
    private LocalDateTime raisedAt;

    /** 关闭时刻（关掉那一刻落）。 */
    private LocalDateTime closedAt;

    /**
     * 立在途超期预警：只给正走在路上的联单立；货都没启运或者已经签收的谈不上超期。
     * 超多久从启运那一刻算到现在：不足三天 LOW，三天到七天（含七天）MEDIUM，七天往上 HIGH。
     */
    public static WasteAlert raiseOverdue(TransferManifest manifest, String detail) {
        require(manifest.getStatus() == ManifestStatus.IN_TRANSIT && manifest.getTransportBegin() != null,
                "只有在途运输中的联单才能立在途超期预警");
        WasteAlert alert = base(manifest, AlertType.OVERDUE, detail);
        Duration onRoad = Duration.between(manifest.getTransportBegin(), alert.getRaisedAt());
        alert.setAlertLevel(levelByOnRoad(onRoad));
        return alert;
    }

    /**
     * 立重量差异预警：只给已经签收的单子立，拿签收实收跟联单申报比，差了才立，
     * 一分不差的不用立。差幅不到一成 LOW，一成到两成（含两成）MEDIUM，两成往上 HIGH。
     */
    public static WasteAlert raiseWeightDiff(TransferManifest manifest, BigDecimal receivedWeight, String detail) {
        requireSigned(manifest, "重量差异");
        if (receivedWeight == null) {
            throw new BizException("该联单还没有签收记录，立不了重量差异预警");
        }
        BigDecimal declared = manifest.getTransferWeight();
        if (declared == null || declared.signum() <= 0) {
            throw new BizException("联单申报重量异常，立不了重量差异预警");
        }
        BigDecimal diff = receivedWeight.subtract(declared).abs();
        if (diff.signum() == 0) {
            throw new BizException("签收重量与申报重量一致，无需立重量差异预警");
        }
        BigDecimal ratio = diff.divide(declared, 4, RoundingMode.HALF_UP);
        WasteAlert alert = base(manifest, AlertType.WEIGHT_DIFF, detail);
        alert.setAlertLevel(levelByDiffRatio(ratio));
        return alert;
    }

    /**
     * 立许可超限预警：只给签收后处置单位累计接收盖过许可上限的单子立，一律落 HIGH。
     */
    public static WasteAlert raiseQuota(TransferManifest manifest, TreatmentUnit unit, String detail) {
        requireSigned(manifest, "许可超限");
        if (unit == null) {
            throw new BizException("处置单位不存在");
        }
        BigDecimal received = unit.getReceivedWeight() == null ? BigDecimal.ZERO : unit.getReceivedWeight();
        BigDecimal licensed = unit.getLicensedWeight() == null ? BigDecimal.ZERO : unit.getLicensedWeight();
        if (received.compareTo(licensed) <= 0) {
            throw new BizException("处置单位累计接收未盖过许可上限，无需立许可超限预警");
        }
        WasteAlert alert = base(manifest, AlertType.QUOTA, detail);
        alert.setAlertLevel(AlertLevel.HIGH);
        return alert;
    }

    /**
     * 处置：已发布 / 处置中 → 处置中，处置过程中把说明补上（传了说明才覆盖）。
     * 已关闭是终态，不再处置。
     */
    public void handle(String detail) {
        require(this.status != AlertStatus.CLOSED, "已关闭的预警不能再处置");
        this.status = AlertStatus.HANDLING;
        if (detail != null && !detail.isBlank()) {
            this.detail = detail.trim();
        }
    }

    /** 关闭：处置完了才能关，落下关闭时刻；已关闭是终态。 */
    public void close() {
        require(this.status == AlertStatus.HANDLING, "只有处置中的预警才能关闭");
        this.status = AlertStatus.CLOSED;
        this.closedAt = LocalDateTime.now();
    }

    /** 立单公共部分：挂联单、落类型、记说明，状态落已发布并记下发布时刻。 */
    private static WasteAlert base(TransferManifest manifest, AlertType type, String detail) {
        WasteAlert alert = new WasteAlert();
        alert.setManifestId(manifest.getId());
        alert.setAlertType(type);
        alert.setStatus(AlertStatus.RAISED);
        alert.setDetail(detail == null || detail.isBlank() ? null : detail.trim());
        alert.setRaisedAt(LocalDateTime.now());
        return alert;
    }

    /** 已签收（含已处置）才谈得上签收后的事；还在路上、没启运、退回、作废的都立不了。 */
    private static void requireSigned(TransferManifest manifest, String typeLabel) {
        require(manifest.getStatus() == ManifestStatus.RECEIVED
                        || manifest.getStatus() == ManifestStatus.DISPOSED,
                "只有已签收的联单才能立" + typeLabel + "预警");
    }

    private static AlertLevel levelByOnRoad(Duration onRoad) {
        if (onRoad.compareTo(Duration.ofDays(OVERDUE_MEDIUM_DAYS)) < 0) {
            return AlertLevel.LOW;
        }
        if (onRoad.compareTo(Duration.ofDays(OVERDUE_HIGH_DAYS)) <= 0) {
            return AlertLevel.MEDIUM;
        }
        return AlertLevel.HIGH;
    }

    private static AlertLevel levelByDiffRatio(BigDecimal ratio) {
        if (ratio.compareTo(DIFF_MEDIUM_RATIO) < 0) {
            return AlertLevel.LOW;
        }
        if (ratio.compareTo(DIFF_HIGH_RATIO) <= 0) {
            return AlertLevel.MEDIUM;
        }
        return AlertLevel.HIGH;
    }

    private static void require(boolean ok, String message) {
        if (!ok) {
            throw new BizException(message);
        }
    }
}
