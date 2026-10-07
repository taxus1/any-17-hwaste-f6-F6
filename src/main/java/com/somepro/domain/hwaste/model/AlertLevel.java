package com.somepro.domain.hwaste.model;

/**
 * 危废异常预警级别（纯领域枚举，落库时存 name() 字符串）。
 * LOW 低 / MEDIUM 中 / HIGH 高。级别按情况推出，不许手填。
 */
public enum AlertLevel {

    LOW,
    MEDIUM,
    HIGH
}
