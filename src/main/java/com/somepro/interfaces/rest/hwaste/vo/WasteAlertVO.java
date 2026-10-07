package com.somepro.interfaces.rest.hwaste.vo;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 危废异常预警对外返回对象（VO，用户接口层）—— 不可变 record。
 * 列表与详情共用；delFlag / createBy / updateBy / updateTime 及内部派期字段不进 API 契约。
 */
public record WasteAlertVO(
        Long id,
        String alertNo,
        Long manifestId,
        String alertType,
        String alertLevel,
        String status,
        String detail,
        LocalDateTime raisedAt,
        LocalDateTime closedAt,
        LocalDateTime createTime) implements Serializable {
}
