package com.aigicrm.module.customer.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 【裁决 2026-10-10】开票回款阶段总额(半年/全年录入制): 一客户一周期一行, 阶段内开票额与
 *  阶段性回款额同行(单位万元)。编辑制——无删除/作废状态机, 误录改 0 即无效, 改动留 sys_log 审计。
 *  逐笔发票/回款录入入口同批下线改只读历史, 历史数据保留可查。 */
@Data
@TableName("biz_customer_stage_amount")
public class BizCustomerStageAmount {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long customerId;
    private Integer periodYear;
    /** H1=上半年 H2=下半年 FY=全年(与上半年/下半年独立录入, 汇总值可能不相等, 判定全年行有数据时优先) */
    private String periodType;
    private BigDecimal invoiceAmount;
    private BigDecimal paymentAmount;
    private Long createdBy;
    private Long updatedBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
    /** 展示用派生字段(如"2026年上半年"), 由 Service 填充, 不落库 */
    @TableField(exist = false)
    private String periodLabel;
    /** 更新人姓名(展示用), 由 Service 填充 */
    @TableField(exist = false)
    private String updatedByName;
}
