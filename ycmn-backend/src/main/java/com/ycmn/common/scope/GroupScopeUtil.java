package com.ycmn.common.scope;

import com.ycmn.security.SecurityUtils;

import java.util.List;

/**
 * 集团可见性口径工具。
 * 与 SysUserService.applyGroupScope / MybatisPlusConfig.resolveTenantId 保持一致:
 * 平台超管(admin)全集团;其余用户限定所属集团;取不到集团归属时退主集团 1(fail-closed)。
 */
public final class GroupScopeUtil {

    private GroupScopeUtil() {
    }

    /**
     * 当前用户可见的集团集合(列表过滤用):
     * <ul>
     *   <li>平台超管(admin) → 返回 null,调用方不过滤(跨集团管理/审计);</li>
     *   <li>其余用户 → SecurityUtils.getCurrentGroupIds() 原样返回(多集团并集);</li>
     *   <li>取不到集团归属(无岗位/无组织) → 退主集团 1,与 MybatisPlusConfig.resolveTenantId
     *       的"空集团退回主集团默认值"同口径(fail-closed,不会因取不到集团而不过滤)。</li>
     * </ul>
     */
    public static List<Long> visibleGroupIds() {
        if (SecurityUtils.isAdmin()) {
            return null;
        }
        List<Long> groupIds = SecurityUtils.getCurrentGroupIds();
        return (groupIds == null || groupIds.isEmpty()) ? List.of(1L) : groupIds;
    }
}
