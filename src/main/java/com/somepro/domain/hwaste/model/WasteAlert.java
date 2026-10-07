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
 * 一条预警占一条记录，挂在一张联单下，记类型、级别、说明、发布时刻、关闭时刻。
 * 预警编号形如 WA-2026-0001，由仓储层分配，全局唯一；同一张联单 + 同一类型只准挂一条，
 * 重复提交不再新建（查重在仓储侧锁内兜底）。
 *
 * 立预警得对得上号（前置门槛在应用层编排时先验，这里收级别与落账）：
 * - OVERDUE 在途超期：只给正走在路上（IN_TRANSIT）的联单立；级别看上路时长，
 *   从启运那一刻算到现在：不足三天 LOW，三天到七天（含七天）MEDIUM，七天往上 HIGH。
 * - WEIGHT_DIFF 重量差异：只给已签收的联单立，实收跟申报一分不差不立；级别看差幅，
 *   |实收 - 申报| / 申报：不到一成 LOW，一成到两成（含两成）MEDIUM，两成往上 HIGH。
 * - QUOTA 许可超限：只给签收后累计接收盖过许可上限的单子立；一律 HIGH。
 *
 * 状态机：RAISED 已发布 →（处置补说明）HANDLING 处置中 →（关）CLOSED 已关闭（终态）。
 */
@Getter
@Setter
public class WasteAlert extends BaseEntity {

    /** 在途时长阈值：上路不足三天落 LOW。 */
    public static final long OVERDUE_MEDIUM_DAYS = 3L;
    /** 在途时长阈值：三天到七天（含七天）落 MEDIUM，七天往上落 HIGH。 */
    public static final long OVERDUE_HIGH_DAYS = 7L;

    /** 重量差幅阈值：差不到一成落 LOW。 */
    public static final BigDecimal DIFF_MEDIUM_RATIO = new BigDecimal("0.10");
    /** 重量差幅阈值：一成到两成（含两成）落 MEDIUM，两成往上落 HIGH。 */
    public static final BigDecimal DIFF_HIGH_RATIO = new BigDecimal("0.20");

    private Long id;

    /** 预警编号，全局唯一，形如 WA-2026-0001（由仓储层分配）。 */
    private String alertNo;

    /** 挂在哪张联单下（t_transfer_manifest.id）。 */
    private Long manifestId;

    private AlertType alertType;

    private AlertLevel alertLevel;

    private AlertStatus status;

    /** 预警说明。 */
    private String detail;

    /** 发布时刻。 */
    private LocalDateTime raisedAt;

    /** 关闭时刻。 */
    private LocalDateTime closedAt;

    /** 下次处置到点时刻（按级别时限推算）：本题不启用，留库列空。 */
    private LocalDateTime nextDueAt;

    /** 已升级次数：本题不启用，留库列 0。 */
    private Integer escalateCount;

    /**
     * 在途超期预警：只给正走在路上的联单立，级别看上路时长（启运 → 现在）。
     *
     * @param transportBegin 启运时刻（不能为空）
     */
    public static WasteAlert raiseOverdue(Long manifestId, LocalDateTime transportBegin) {
        if (manifestId == null) {
            throw new BizException("联单不能为空");
        }
        if (transportBegin == null) {
            throw new BizException("联单尚未启运，谈不上在途超期");
        }
        long days = Duration.between(transportBegin, LocalDateTime.now()).toDays();
        AlertLevel level = classifyOverdue(days);
        return base(manifestId, AlertType.OVERDUE, level,
                "在途超期：货自启运已在路上约 " + days + " 天");
    }

    /**
     * 重量差异预警：只给已签收的联单立，拿实收重量跟申报重量比，差了才立。
     * 级别看差幅 |实收 - 申报| / 申报。
     */
    public static WasteAlert raiseWeightDiff(Long manifestId, BigDecimal declaredWeight, BigDecimal receivedWeight) {
        if (manifestId == null) {
            throw new BizException("联单不能为空");
        }
        if (declaredWeight == null || declaredWeight.signum() <= 0) {
            throw new BizException("联单申报重量缺失，无法比对重量差异");
        }
        if (receivedWeight == null) {
            throw new BizException("缺少实收重量，无法比对重量差异");
        }
        BigDecimal diff = receivedWeight.subtract(declaredWeight);
        if (diff.signum() == 0) {
            throw new BizException("实收重量与申报重量一分不差，无需立重量差异预警");
        }
        BigDecimal ratio = diff.abs().divide(declaredWeight, 4, RoundingMode.HALF_UP);
        AlertLevel level = classifyWeightDiff(ratio);
        return base(manifestId, AlertType.WEIGHT_DIFF, level,
                "重量差异：申报 " + declaredWeight + " 千克，实收 " + receivedWeight
                        + " 千克，差幅 " + ratio.multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString()
                        + "%");
    }

    /** 许可超限预警：签收后累计接收盖过许可上限才立，一律落 HIGH。 */
    public static WasteAlert raiseQuota(Long manifestId, BigDecimal licensedWeight, BigDecimal receivedWeight) {
        if (manifestId == null) {
            throw new BizException("联单不能为空");
        }
        BigDecimal licensed = licensedWeight == null ? BigDecimal.ZERO : licensedWeight;
        BigDecimal received = receivedWeight == null ? BigDecimal.ZERO : receivedWeight;
        if (received.compareTo(licensed) <= 0) {
            throw new BizException("累计接收未盖过许可上限，无需立许可超限预警");
        }
        return base(manifestId, AlertType.QUOTA, AlertLevel.HIGH,
                "许可超限：累计接收 " + received + " 千克，已盖过许可上限 " + licensed + " 千克");
    }

    /** 处置：已发布 / 处置中可办，把处置说明补上；已关闭的不再动。 */
    public void handle(String handlingNote) {
        if (status == AlertStatus.CLOSED) {
            throw new BizException("预警已关闭，不能再处置");
        }
        if (handlingNote == null || handlingNote.isBlank()) {
            throw new BizException("处置必须补上说明");
        }
        this.status = AlertStatus.HANDLING;
        this.detail = handlingNote.trim();
    }

    /** 关闭：处置完才关，落下关闭时刻；已关闭的不重复关。 */
    public void close() {
        if (status == AlertStatus.CLOSED) {
            throw new BizException("预警已关闭，不能重复关闭");
        }
        if (status != AlertStatus.HANDLING) {
            throw new BizException("预警尚未处置，不能直接关闭");
        }
        this.status = AlertStatus.CLOSED;
        this.closedAt = LocalDateTime.now();
    }

    /** 在途时长定级：不足三天 LOW，三天到七天（含七天）MEDIUM，七天往上 HIGH。 */
    private static AlertLevel classifyOverdue(long days) {
        if (days < OVERDUE_MEDIUM_DAYS) {
            return AlertLevel.LOW;
        }
        if (days <= OVERDUE_HIGH_DAYS) {
            return AlertLevel.MEDIUM;
        }
        return AlertLevel.HIGH;
    }

    /** 重量差幅定级：不到一成 LOW，一成到两成（含两成）MEDIUM，两成往上 HIGH。 */
    private static AlertLevel classifyWeightDiff(BigDecimal ratio) {
        if (ratio.compareTo(DIFF_MEDIUM_RATIO) < 0) {
            return AlertLevel.LOW;
        }
        if (ratio.compareTo(DIFF_HIGH_RATIO) <= 0) {
            return AlertLevel.MEDIUM;
        }
        return AlertLevel.HIGH;
    }

    /** 立预警公共落账：状态落已发布，记发布时刻。 */
    private static WasteAlert base(Long manifestId, AlertType type, AlertLevel level, String detail) {
        WasteAlert alert = new WasteAlert();
        alert.setManifestId(manifestId);
        alert.setAlertType(type);
        alert.setAlertLevel(level);
        alert.setStatus(AlertStatus.RAISED);
        alert.setDetail(detail);
        alert.setRaisedAt(LocalDateTime.now());
        alert.setEscalateCount(0);
        return alert;
    }
}
