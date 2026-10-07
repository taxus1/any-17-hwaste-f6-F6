package com.somepro.domain.hwaste.model;

/**
 * 预警状态机（纯领域枚举，落库时存 name() 字符串）。
 *
 * 流转：立起来落 RAISED 已发布；挂出来以后处置（→ HANDLING 处置中，处置过程中把说明补上）；
 * 处置完了关掉（→ CLOSED 已关闭，记下关闭时刻）。已关闭是终态，不再来回动。
 */
public enum AlertStatus {

    /** 已发布：预警刚立起来，还没人处置。 */
    RAISED,
    /** 处置中。 */
    HANDLING,
    /** 已关闭：处置完了，终态。 */
    CLOSED
}
