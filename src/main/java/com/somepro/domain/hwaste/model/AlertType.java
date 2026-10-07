package com.somepro.domain.hwaste.model;

/**
 * 危废异常预警类型（纯领域枚举，落库时存 name() 字符串）。
 * OVERDUE 在途超期 / WEIGHT_DIFF 重量差异 / QUOTA 许可超限。
 */
public enum AlertType {

    /** 在途超期：货走在路上拖着不到，超多久从启运那一刻算到现在。 */
    OVERDUE,
    /** 重量差异：实收重量跟联单申报重量对不上。 */
    WEIGHT_DIFF,
    /** 许可超限：处置单位签收后累计接收盖过许可上限。 */
    QUOTA
}
