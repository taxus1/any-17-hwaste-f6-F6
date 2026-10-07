package com.somepro.domain.hwaste.model;

/**
 * 危废异常预警状态机（纯领域枚举，落库时存 name() 字符串）。
 *
 * 流转：立预警落 RAISED 已发布；开始处置补说明 → HANDLING 处置中；
 * 处置完关掉 → CLOSED 已关闭（终态，关闭时刻落下）。
 */
public enum AlertStatus {

    /** 已发布。 */
    RAISED,
    /** 处置中。 */
    HANDLING,
    /** 已关闭（终态）。 */
    CLOSED
}
