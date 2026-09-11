package com.aigicrm.common.scope;

import com.aigicrm.security.SecurityUtils;

import java.util.List;

/**
 * 集团可见性口径工具。
 * 与 SysUserService.applyGroupScope / MybatisPlusConfig.resolveTenantId 保持一致:
 * 平台超管(admin)全集团;其余用户限定所属集团;取不到集团归属时回退主集团 1
 * (注意: 这是向主集团放行的默认作用域, 非安全拒绝——多集团上线前须改为显式成员校验/拒绝)。
 */
public final class GroupScopeUtil {

    /** 主集团 id: 历史未回填集团归属时的默认回退值(放行到主集团, 非安全拒绝)。
     *  与 SysRoleService.validateRoleOrgsGroup / MybatisPlusConfig 同口径, 禁止散落裸 1L。 */
    public static final Long DEFAULT_GROUP_ID = 1L;

    private GroupScopeUtil() {
    }

    /**
     * 当前用户可见的集团集合(列表过滤用):
     * <ul>
     *   <li>平台超管(admin) → 返回 null,调用方不过滤(跨集团管理/审计);</li>
     *   <li>其余用户 → SecurityUtils.getCurrentGroupIds() 原样返回(多集团并集);</li>
     *   <li>取不到集团归属(无岗位/无组织) → 回退主集团 1(默认作用域)。⚠️ 注意: 这是向主集团 1
     *       放行(fail-open 到主集团), 非安全拒绝——多集团部署下无归属账号默认可读主集团数据,
     *       上线前需改为显式成员校验/拒绝。</li>
     * </ul>
     */
    public static List<Long> visibleGroupIds() {
        if (SecurityUtils.isAdmin()) {
            return null;
        }
        List<Long> groupIds = SecurityUtils.getCurrentGroupIds();
        return (groupIds == null || groupIds.isEmpty()) ? List.of(DEFAULT_GROUP_ID) : groupIds;
    }

    /**
     * 目标记录的集团归属是否对当前用户可操作(单条读取/写操作校验):
     * <ul>
     *   <li>平台超管(admin) → 恒 true(跨集团管理/审计);</li>
     *   <li>其余用户 → groupId 非空且 ∈ 当前用户集团集合(空集合回退主集团 1, 与
     *       {@link #visibleGroupIds()} 同口径——注意为放行非拒绝);null 集团记录视为不可操作。</li>
     * </ul>
     */
    public static boolean canAccessGroup(Long groupId) {
        if (SecurityUtils.isAdmin()) {
            return true;
        }
        if (groupId == null) {
            return false;
        }
        List<Long> groupIds = SecurityUtils.getCurrentGroupIds();
        List<Long> allowed = (groupIds == null || groupIds.isEmpty()) ? List.of(DEFAULT_GROUP_ID) : groupIds;
        return allowed.contains(groupId);
    }
}
