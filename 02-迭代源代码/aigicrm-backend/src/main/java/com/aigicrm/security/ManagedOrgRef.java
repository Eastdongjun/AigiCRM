package com.aigicrm.security;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 【WP1.2 权限升级 2026-11-13】自定义范围授权组织原始行（orgId + includeChildren）。
 *
 * 此前 UserDetailsServiceImpl 在登录装载时把 include_children=1 的行**预展开**为
 * descendantIds 平铺进 managedOrgIds，标志位丢失后所有消费方（resolveCustom 等）
 * 对每个 orgId 无条件再展开子树 ⇒ includeChildren=0 的行也看到子部门（越权放宽）。
 *
 * 现改为装载原始行，展开时机统一收敛到 OrgScopeResolver：includeChildren=true 才
 * 展开子树，false 仅该组织本身。角色级(sys_role_org)与用户级(sys_user_org)行并集装载。
 *
 * 可入 PermissionContext（Redis 多态白名单 com.aigicrm.* 覆盖本包）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ManagedOrgRef implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long orgId;
    /** 1=含子树; 0/ null=仅本组织 */
    private Integer includeChildren;

    public boolean expandChildren() {
        return includeChildren != null && includeChildren == 1;
    }
}
