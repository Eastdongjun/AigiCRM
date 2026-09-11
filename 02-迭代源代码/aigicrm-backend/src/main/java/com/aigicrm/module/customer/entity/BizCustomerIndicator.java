package com.aigicrm.module.customer.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("biz_customer_indicator")
public class BizCustomerIndicator {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long customerId;
    private Integer relationshipScore;
    @TableField("indicator_02")
    private Integer indicator02;
    @TableField("indicator_03")
    private Integer indicator03;
    @TableField("indicator_04")
    private Integer indicator04;
    @TableField("indicator_05")
    private Integer indicator05;
    @TableField("indicator_06")
    private Integer indicator06;
    private Integer isCore;
    private Long updatedBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
