package com.aigicrm.module.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

/** 角色-规则关联（兼容直接给角色配规则） */
@Data
@TableName("sys_role_rule")
public class SysRoleRule {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long roleId;
    private Long ruleId;
}
