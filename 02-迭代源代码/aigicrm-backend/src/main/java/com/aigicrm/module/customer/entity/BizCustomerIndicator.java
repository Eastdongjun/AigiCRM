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
    /** 【裁决 2026-10-10】核心三态: core=真核心(年度开票回款双>=100万) quasi=争核心(有数据未双达标) 空=非核心。
     *  纯金额判定, 五项评分不再参与; 只能由 recalculateCoreStatus 写入(saveIndicator 显式置空防域外输入) */
    private String coreType;
    private Long updatedBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
