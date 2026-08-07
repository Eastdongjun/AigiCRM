package com.ycmn.module.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 用户-组织关联表 sys_user_org
 * 【2026-08-08】用户级 custom 数据权限：每个用户可单独配置可见组织（如多个行政各看不同部门）。
 * 优先级高于角色级 sys_role_org：用户 data_scope_override=custom 时使用本表组织。
 */
@Data
@TableName("sys_user_org")
public class SysUserOrg {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long orgId;
    /** 是否包含子组织: 0=仅本组织, 1=含所有下级（递归） */
    private Integer includeChildren;
}
