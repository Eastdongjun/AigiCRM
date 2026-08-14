package com.ycmn.module.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 角色-组织关联表 sys_role_org
 * 用于 custom 数据权限：一个角色可分配到多个组织，每个组织可独立控制是否含下级
 */
@Data
@TableName("sys_role_org")
public class SysRoleOrg {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long roleId;
    private Long orgId;
    /** 是否包含子组织: 0=仅本组织, 1=含所有下级（递归） */
    private Integer includeChildren;
}
