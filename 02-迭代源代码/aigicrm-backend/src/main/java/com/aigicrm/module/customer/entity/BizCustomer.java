package com.aigicrm.module.customer.entity;

import com.alibaba.excel.annotation.ExcelIgnore;
import com.alibaba.excel.annotation.ExcelProperty;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("biz_customer")
public class BizCustomer {
    @TableId(type = IdType.AUTO)
    @ExcelIgnore
    private Long id;

    @ExcelProperty("编号")
    private String customerCode;
    @ExcelProperty("客户名称")
    private String customerName;
    /** 【议题五.1 裁决】别名/简称/曾用名(搜索命中) */
    @ExcelProperty("别名")
    private String alias;
    @ExcelProperty("开票名称")
    private String invoiceName;
    @ExcelProperty("行业")
    private String industry;
    @ExcelProperty("开发类型")
    private String devType;
    @ExcelProperty("所属集团")
    private String groupName;

    /** 集团ID外键（废弃：08-02迁移加列但从未回填，全表NULL；集团名直接存 group_name） */
    @ExcelIgnore
    private Long groupId;

    @ExcelProperty("所属分公司")
    private String branchCompany;

    /** 法人公司ID外键（废弃：从未回填；分公司名直接存 branch_company） */
    @ExcelIgnore
    private Long legalEntityId;

    @ExcelProperty("所属单位")
    private String unitName;
    @ExcelProperty("产能")
    private String capacity;
    @ExcelProperty("省")
    private String province;
    @ExcelProperty("市")
    private String city;
    @ExcelProperty("县")
    private String district;
    @ExcelProperty("厂址")
    private String address;
    @ExcelProperty("终端")
    private Integer hasTerminal;
    @ExcelProperty("注册日")
    private LocalDate registerDate;
    @ExcelProperty("最近开票")
    private LocalDate lastInvoiceDate;
    @ExcelProperty("到期日")
    private LocalDate expireDate;
    /** 【2026-10-18→2026-11-02 语义改造C】捞取到期日=捞取日+180,仅捞取客户有值(原"调整日"=基准+180纯展示,无业务消费) */
    @ExcelProperty("捞取到期日")
    private LocalDate adjustDate;
    @ExcelProperty("转移日")
    private LocalDate transferDate;
    /** 【2026-11-02 裁决B】捞取日期=最近一次公海捞取成功日;从未捞取为NULL,多次捞取取最近。
     *  参与到期公式捞取线(claim+180);捞取不再覆写注册日(注册日保留真实建档日语义)。 */
    @ExcelProperty("捞取日期")
    private LocalDate claimDate;
    /** 【议题一裁决 2026-09-19】累计已审批延期天数(只增不减);expireDate=开票推导+本值 */
    @ExcelIgnore
    private Integer approvedExtendDays;

    @ExcelIgnore
    private Long ownerId;
    @ExcelIgnore
    private Long managerId;
    @ExcelIgnore
    private Long officeId;
    @ExcelIgnore
    private Long regionId;
    @ExcelIgnore
    private Long deptId;
    /** 所属部门名（列表展示用，非表字段） */
    @TableField(exist = false)
    @ExcelIgnore
    private String deptName;

    @ExcelProperty("备注")
    private String remark;
    @ExcelProperty("状态")
    private String status;
    @ExcelProperty("保护状态")
    private String protectionStatus;

    @TableField(fill = FieldFill.INSERT)
    @ExcelIgnore
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    @ExcelIgnore
    private LocalDateTime updatedAt;
    @TableLogic
    @ExcelIgnore
    private Integer deleted;

    @TableField(exist = false)
    @ExcelProperty("负责人")
    private String ownerName;
    @TableField(exist = false)
    @ExcelProperty("管理上级")
    private String managerName;
    @TableField(exist = false)
    @ExcelProperty("办事处")
    private String officeName;
    @TableField(exist = false)
    @ExcelProperty("大区")
    private String regionName;
}
