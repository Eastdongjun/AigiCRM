package com.ycmn.module.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 权限规则 — 可配置的数据权限核心
 *
 * rule_type 五种类型：
 *   column_filter   → 字段值过滤（WHERE target_column operator rule_value）
 *   org_filter      → 组织过滤（按组织树递归）
 *   field_visible   → 字段可见性（响应中隐藏/脱敏字段）
 *   action_control  → 操作控制（增删改查导出等按钮权限）
 *   time_range      → 时间范围（WHERE create_time BETWEEN）
 *
 * rule_value 支持变量（运行时替换）：
 *   ${user.userId}  ${user.deptId}  ${user.orgId}  ${user.username}
 *   ${time.today}   ${time.thisMonthStart}  ${time.thisMonthEnd}
 */
@Data
@TableName("sys_permission_rule")
public class SysPermissionRule {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** 规则编码（唯一） */
    private String code;

    /** 目标实体（customer/contact/payment/order） */
    private String targetEntity;

    private String ruleType;

    private String targetColumn;

    /** 操作符（eq/ne/in/between/like/is_null） */
    private String operator;

    /** 规则值（支持变量） */
    private String ruleValue;

    /** 扩展配置JSON */
    private String extraConfig;

    private String description;

    /** 优先级（越小越优先） */
    private Integer priority;

    /** 状态 0=禁用 1=启用 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
