package com.somepro.infrastructure.persistence.hwaste.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.somepro.infrastructure.persistence.base.BasePO;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * t_waste_alert 表的持久化对象（PO，基础设施层）。只描述表形状，不放业务规则。
 */
@Getter
@Setter
@TableName("t_waste_alert")
public class WasteAlertPO extends BasePO {

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    @TableField("alert_no")
    private String alertNo;

    @TableField("manifest_id")
    private Long manifestId;

    @TableField("alert_type")
    private String alertType;

    @TableField("alert_level")
    private String alertLevel;

    @TableField("status")
    private String status;

    @TableField("detail")
    private String detail;

    @TableField("raised_at")
    private LocalDateTime raisedAt;

    @TableField("closed_at")
    private LocalDateTime closedAt;

    @TableField("next_due_at")
    private LocalDateTime nextDueAt;

    @TableField("escalate_count")
    private Integer escalateCount;
}
