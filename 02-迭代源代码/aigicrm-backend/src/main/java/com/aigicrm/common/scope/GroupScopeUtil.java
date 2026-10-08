package com.aigicrm.common.scope;

import com.aigicrm.security.SecurityUtils;

import java.util.List;

/**
 * 集团可见性口径工具。
 * 与 SysUserService.applyGroupScope / MybatisPlusConfig.resolveTenantId 保持一致:
 * 平台超管(admin)全集团;其余用户限定所属集团。
 * 【PR-01 P0-02 2026-09-18 fail-closed】取不到集团归属时拒绝/空集，不再回退主集团 1。
 * 历史脏数据通过一次性迁移修复，运行期不再默认放行。
 */
public final class GroupScopeUtil {

    /** 主集团 id: 仅用于新建记录缺省归属等非授权场景。禁止用于授权/过滤回退。 */
    public static final Long DEFAULT_GROUP_ID = 1L;

    private GroupScopeUtil() {
    }

    /**
     * 当前用户可见的集团集合(列表过滤用):
     * <ul>
     *   <li>平台超管(admin) → 返回 null,调用方不过滤(跨集团管理/审计);</li>
     *   <li>其余用户 → SecurityUtils.getCurrentGroupIds() 原样返回(多集团并集);</li>
     *   <li>取不到集团归属(无岗位/无组织) → 返回空集合(调用方按无可见数据处理)。
     *       【PR-01 P0-02 fail-closed】不再回退主集团 1。</li>
     * </ul>
     */
    public static List<Long> visibleGroupIds() {
        if (SecurityUtils.isAdmin()) {
            return null;
        }
        List<Long> groupIds = SecurityUtils.getCurrentGroupIds();
        return (groupIds == null || groupIds.isEmpty()) ? List.of() : groupIds;
    }

    /**
     * 当前会话有效集团(单集团会话口径, 与登录 currentGroupId 一致):
     * <ul>
     *   <li>平台超管(admin) → null(调用方不过滤);</li>
     *   <li>其余用户 → 会话当前集团(无则取所属最小集团);</li>
     *   <li>取不到任何集团归属 → null, 调用方必须按 fail-closed 处理(空结果/拒绝)。</li>
     * </ul>
     */
    public static Long currentGroupOrNull() {
        if (SecurityUtils.isAdmin()) {
            return null;
        }
        var user = SecurityUtils.getCurrentUser();
        if (user != null && user.getCurrentGroupId() != null) {
            return user.getCurrentGroupId();
        }
        List<Long> groupIds = SecurityUtils.getCurrentGroupIds();
        if (groupIds == null || groupIds.isEmpty()) {
            return null;
        }
        return groupIds.stream().sorted().findFirst().orElse(null);
    }

    /**
     * 【审查 C2/C3 2026-10-08】共享缓存键的集团前缀——取值口径与 MybatisPlusConfig 租户拦截器
     * (buildTenantInterceptor → getTenantId/resolveTenantId) 逐字对齐:
     * <ul>
     *   <li>会话有当前集团(currentGroupId) → "g{groupId}:";</li>
     *   <li>无当前集团但有所属集团 → 取排序最小集团(与 resolveTenantId 的多集团 fail-closed 同口径)
     *       → "g{min}:";</li>
     *   <li>取不到任何集团 → 固定前缀 "g-none:"(fail-closed:此时租户插件把查询改写为
     *       tenant_id = NULL 恒空, 该类用户结果同为空集, 共享同一键不会与任何真实集团互串)。</li>
     * </ul>
     * 用途:统计看板/积分榜等"同范围共享缓存"的键前缀,防止跨集团用户互串缓存条目
     * (单集团部署暂不触发, 第二集团上线即生效)。注:admin 的租户拦截器走 ignoreTable
     * 直接放行(跨集团), 该前缀对 admin 只是额外的缓存命名空间, 不改变查询语义。
     */
    public static String tenantCachePrefix() {
        var user = SecurityUtils.getCurrentUser();
        if (user != null && user.getCurrentGroupId() != null) {
            return "g" + user.getCurrentGroupId() + ":";
        }
        List<Long> groupIds = SecurityUtils.getCurrentGroupIds();
        if (groupIds == null || groupIds.isEmpty()) {
            return "g-none:"; // fail-closed 固定前缀(对应租户插件 tenant_id = NULL 恒空)
        }
        // 多集团取排序最小 id, 与 MybatisPlusConfig.resolveTenantId 同口径
        return "g" + groupIds.stream().sorted().findFirst().get() + ":";
    }

    /**
     * 非 admin 且无有效集团归属时为 true。调用方(原生 SQL/列表过滤)遇到此情况
     * 必须返回空集或拒绝访问，禁止回退到任意集团。
     */
    public static boolean isGroupFailClosed() {
        return !SecurityUtils.isAdmin() && currentGroupOrNull() == null;
    }

    /**
     * 【PR-01 2026-09-18】某集团成员用户 id。与登录 AuthService.resolveGroupIds 真正同口径
     * (visibility.md §2):岗位经 position.org_id→dept.group_id 推导(有岗位优先),无岗位者才回退
     * 本人 org_id→dept.group_id——原实现直挂 sys_position.group_id 且无条件 UNION,与登录链是
     * 两套口径(岗位 group_id 与其 org 部门 group_id 不一致时同一用户两处归属不同集团),
     * 【修复 2026-10-08 需求对齐】收口为单一推导。原生 SQL/JdbcTemplate 无法走租户拦截器
     * （sys_user 非租户表），必须显式套本方法做集团边界。admin 调用方自行决定不过滤。
     * 静态方法 + 显式传 JdbcTemplate：避免给热路径 Service 增加构造器依赖。
     */
    public static List<Long> userIdsInGroup(org.springframework.jdbc.core.JdbcTemplate jdbc, Long groupId) {
        if (jdbc == null || groupId == null) return List.of();
        return jdbc.queryForList(
                "SELECT up.user_id FROM sys_user_position up " +
                "JOIN sys_position p ON p.id = up.position_id " +
                "JOIN sys_dept pd ON pd.id = p.org_id AND pd.group_id = ? " +
                "UNION " +
                "SELECT u.id FROM sys_user u " +
                "JOIN sys_dept d ON d.id = u.org_id AND d.group_id = ? " +
                "WHERE NOT EXISTS (SELECT 1 FROM sys_user_position up2 WHERE up2.user_id = u.id)",
                Long.class, groupId, groupId);
    }

    /**
     * 目标记录的集团归属是否对当前用户可操作(单条读取/写操作校验):
     * <ul>
     *   <li>平台超管(admin) → 恒 true(跨集团管理/审计);</li>
     *   <li>其余用户 → groupId 非空且 ∈ 当前用户集团集合;当前用户无任何集团归属时恒 false
     *       【PR-01 P0-02 fail-closed】;null 集团记录视为不可操作。</li>
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
        if (groupIds == null || groupIds.isEmpty()) {
            return false;
        }
        return groupIds.contains(groupId);
    }
}
