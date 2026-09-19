package com.aigicrm.module.customer.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("biz_invoice")
public class BizInvoice {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long customerId;
    private LocalDate invoiceDate;
    private String invoiceNumber;
    private String invoiceType;
    private String invoiceCompany;
    private BigDecimal amount;
    private Long createdBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;

    @TableField(exist = false)
    private String createdByName;

    /** 【PR-18 2026-09-19】台账状态机 */
    private String status;
    private String source;
    private Long confirmedBy;
    private java.time.LocalDateTime confirmedAt;
    private String voidedReason;
}
