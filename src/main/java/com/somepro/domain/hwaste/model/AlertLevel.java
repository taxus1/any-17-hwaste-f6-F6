package com.somepro.domain.hwaste.model;

/**
 * 预警级别（纯领域枚举）：LOW 低 / MEDIUM 中 / HIGH 高。
 * 级别由立预警时按情况推出来（在途时长 / 重量差幅 / 许可超限一律高），不是手填的。
 */
public enum AlertLevel {

    /** 低。 */
    LOW,
    /** 中。 */
    MEDIUM,
    /** 高。 */
    HIGH
}
