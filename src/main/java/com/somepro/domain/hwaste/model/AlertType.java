package com.somepro.domain.hwaste.model;

import com.somepro.common.exception.BizException;

import java.util.Locale;

/**
 * 预警类型（纯领域枚举）：OVERDUE 在途超期 / WEIGHT_DIFF 重量差异 / QUOTA 许可超限。
 */
public enum AlertType {

    /** 在途超期：货启运后拖着不走。 */
    OVERDUE,
    /** 重量差异：签收实收跟联单申报对不上。 */
    WEIGHT_DIFF,
    /** 许可超限：处置单位累计接收盖过许可上限。 */
    QUOTA;

    /**
     * 归一化并校验预警类型：去空白、转大写；不在三种之内的挡回。
     * 返回归一化后的枚举。
     */
    public static AlertType of(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BizException("预警类型不能为空");
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        try {
            return AlertType.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new BizException("预警类型不合法：" + raw + "（仅支持 OVERDUE/WEIGHT_DIFF/QUOTA）");
        }
    }
}
