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
}
