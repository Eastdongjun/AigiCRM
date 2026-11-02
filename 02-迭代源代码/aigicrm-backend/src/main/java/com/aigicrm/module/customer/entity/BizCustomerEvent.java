package com.aigicrm.module.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 客户生命线事件(2026-11-02 裁决D: "客户日期需要记录追踪,后期能看到完整生命线")。
 * 只增不改的追加日志;租户列由 MyBatis-Plus 插件维护,实体不映射(与白名单表同范式)。
 * 事件类型: created/invoice_adv/invoice_manual/extend/released/claimed/transferred。
 */
@Data
@TableName("biz_customer_event")
public class BizCustomerEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long customerId;
    private String eventType;
    private LocalDate eventDate;
    private String detail;
    private String oldValue;
    private String newValue;
    private Long createdBy;
    private LocalDateTime createdAt;

    @TableField(exist = false)
    private String createdByName;
}
