package com.aigicrm.module.reminder.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("biz_reminder")
public class BizReminder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long customerId;
    private String reminderType;
    private Long ownerId;
    private Long officeId;
    private Integer overdueDays;
    private String status;
    private LocalDateTime processedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    /** 【2026-11-17 阶段三】统一回收站：软删 + 审计 */
    @TableLogic
    private Integer deleted;
    private LocalDateTime deletedAt;
    private Long deletedBy;
    private String deletedReason;

    @TableField(exist = false)
    private String customerName;
    @TableField(exist = false)
    private String ownerName;
    @TableField(exist = false)
    private String officeName;
    @TableField(exist = false)
    private java.time.LocalDate lastInvoiceDate;
}
