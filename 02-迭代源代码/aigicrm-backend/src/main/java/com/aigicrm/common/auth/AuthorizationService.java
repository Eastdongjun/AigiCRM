package com.aigicrm.common.auth;

import com.aigicrm.common.scope.OrgScopeResolver;
import com.aigicrm.common.scope.ScopeDecision;
import com.aigicrm.common.scope.ScopeRequest;
import com.aigicrm.module.system.service.RolePermScopeService;
import com.aigicrm.security.SecurityUser;
import com.aigicrm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 【WP3 鉴权内核 2026-11-13】统一鉴权门面(规格 §3.1 "一个鉴权门面")。
 *
 * can(action) / assertAction(action) / scope(action) / assertData(action, ownerId, deptId) / isAdmin()
 *
 * 双轨读开关(规格 WP8 perm.read.<module>=old|new, 默认 old):
 *   old → 委托旧链 OrgScopeResolver.resolveScope(现有 role_perm_scope+dataScopes 并集权威, 行为零变化)
 *   new → sys_perm_grant 授权逐行经 ScopeSpecResolver 12 档解析后并集(锁定决策#1) + 开关追加(仅 view_like)
 * 模块灰度切流在 WP8; WP3-WP7 期间默认 old=零行为变化(禁止大爆炸, 规格 §7)。
 *
 * 错误语义(规格 §3.4): 401 未登录(框架层); assertAction 无动作 → 403; assertData 无权 → 403。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthorizationService {

    /** 读开关键前缀: perm.read.customer=old|new (默认 old) */
    public static final String READ_SWITCH_PREFIX = "perm.read.";
    private static final long SWITCH_TTL_MS = 30_000L;

    private final PermActionRegistry registry;
    private final GrantReader grantReader;
    private final ScopeSpecResolver specResolver;
    private final OrgScopeResolver legacyResolver;
    private final RolePermScopeService rolePermScopeService;
    private final com.aigicrm.module.system.service.SysConfigService configService;
    private final JdbcTemplate jdbc;

    private volatile long switchCacheExpire = 0L;
    private final java.util.concurrent.ConcurrentHashMap<String, String> readModeCache = new java.util.concurrent.ConcurrentHashMap<>();

    // ==================== 动作判定 ====================

    public boolean isAdmin() {
        return SecurityUtils.isAdmin();
    }

    /** 动作可判(与 @PreAuthorize hasAuthority 同语义: admin 恒真; 授权由 sys_user_role→菜单码链路) */
    public boolean can(String actionCode) {
        if (isAdmin()) return true;
        SecurityUser user = SecurityUtils.getCurrentUser();
        return user != null && user.getPermissions() != null && user.getPermissions().contains(actionCode);
    }

    /** 无动作即 403(规格 §3.4) */
    public void assertAction(String actionCode) {
        if (!can(actionCode)) {
            throw new com.aigicrm.common.BusinessException(403, "无权执行该操作");
        }
    }

    // ==================== 双轨包装器(WP7 模块接入统一入口) ====================

    /**
     * 【WP7 模块接入】可见用户集双轨: 读开关=old(默认) → 执行 legacyFallback(现有调用原样闭包,
     * 锚点/集团语义由调用方保留, 测试桩不迁移); =new → 内核授予表解析(scope(actionCode))。
     * 语义契约: 返回 null=全量不过滤; 空集=范围无人(fail-closed); 非空=可见负责人集合。
     */
    public List<Long> visibleUsersDualTrack(String actionCode, java.util.function.Supplier<List<Long>> legacyFallback) {
        PermActionRegistry.ActionDef def = registry.of(actionCode);
        if (def != null && "new".equals(readMode(def.getModule()))) {
            ScopeDecision d = scope(actionCode);
            return d.isAll() ? null : d.getVisibleUserIds();
        }
        return legacyFallback.get();
    }

    /** 【WP7】WithinGroup 变体: 同上, 集团收口语义由 legacyFallback 闭包保留 */
    public List<Long> visibleUsersWithinGroupDualTrack(String actionCode, java.util.function.Supplier<List<Long>> legacyFallback) {
        PermActionRegistry.ActionDef def = registry.of(actionCode);
        if (def != null && "new".equals(readMode(def.getModule()))) {
            ScopeDecision d = scopeWithinGroup(actionCode);
            return d.isAll() ? null : d.getVisibleUserIds();
        }
        return legacyFallback.get();
    }

    /** 【WP7】范围判定双轨(供"加了范围条件没有?"的模块本地分支): true=走了内核新轨(调用方跳过旧逻辑) */
    public boolean isNewTrack(String module) {
        return "new".equals(readMode(module));
    }

    // ==================== 槽→码映射(双轨守卫用) ====================

    /** 【P2-12】视图别名(导出/排行跟随列表授权; 锁定决策#9): 自身无授权按别名码解析 */
    private static final java.util.Map<String, String> VIEW_ALIASES = java.util.Map.of(
            "customer:export", "customer:view");

    /**
     * 【WP7】(module, action) 槽 → 动作码(注册表首个命中); 无槽码返回 null(双轨恒旧轨)。
     * 覆盖表: (file,manage) 的两个门码(码 file:manage 已退役, 槽仍由 V2026.11.13.3 回填挂两码)。
     */
    public String actionCodeOf(String module, String action) {
        return registry.codeOf(module, action);
    }

    // ==================== 范围解析 ====================

    /**
     * 按动作解析数据范围(仅 scope_required=1 的动作有范围语义, 其余返回 all=no-op)。
     * 码未注册(注册表空/灰度前) → fail-closed 空集(不放大不报错); 旧轨默认不受影响(old 优先)。
     */
    public ScopeDecision scope(String actionCode) {
        SecurityUser user = SecurityUtils.getCurrentUser();
        if (user == null) return ScopeDecision.none();
        PermActionRegistry.ActionDef def = registry.of(actionCode);
        if (def == null) {
            // 注册表未就绪: 走旧轨(与现状一致), 不产生新行为
            return legacyScope(user, actionCode);
        }
        if (!def.isScopeRequired()) {
            return ScopeDecision.all(); // 入口/路由码: 无范围语义(规格 §3.3 no-op)
        }
        if (!"new".equals(readMode(def.getModule()))) {
            return legacyScope(user, actionCode);
        }
        return newTrackScope(user, def);
    }

    /** 单条判定(OWNER/ORG 维度按主导锚点; 仅本人自己的记录恒可) */
    public boolean assertData(String actionCode, Long targetOwnerId, Long targetDeptId) {
        ScopeDecision d = scope(actionCode);
        if (d == null) return false;
        if (d.isAll()) return true;
        // 【P0-2 修复 2026-11-13】"本人恒可"仅指目标记录是本人的——旧写法
        // canAccessOwner(self) || canAccessOwner(target) 中 self 恒在可见集(SELF/部门/团队都含本人),
        // || 短路导致对任意目标放行(越权漏洞)。现只放行"目标=本人"。
        Long self = SecurityUtils.getCurrentUserId();
        if (self != null && self.equals(targetOwnerId)) return true;
        PermActionRegistry.ActionDef def = registry.of(actionCode);
        ScopeRequest.AnchorType anchor = dominantAnchor(actionCode, def);
        if (anchor == ScopeRequest.AnchorType.ORG) {
            return d.canAccessDept(targetDeptId);
        }
        return d.canAccessOwner(targetOwnerId);
    }

    /**
     * 组织集合 → 在职用户集(维度补齐; 【P1-4 三轮修复】改走 sys_employment——决策#2 多任职:
     * 按 sys_user.dept_id 会漏"兼职在 B 部门"的用户; 任职表由 KernelOrgSyncService 保鲜)。
     * 【四轮 P2 修复】employment 与 sys_user.dept_id **恒并集**(不再"employment 空才回退")——
     * 部分同步失败的用户由 dept_id 权威列兜底, 不漏人; dept_id 本是主职权威列, 并集不放大。
     */
    private List<Long> usersInDepts(Set<Long> deptIds) {
        if (deptIds == null || deptIds.isEmpty()) return List.of();
        try {
            String ph = String.join(",", deptIds.stream().map(x -> "?").toList());
            java.util.LinkedHashSet<Long> out = new java.util.LinkedHashSet<>();
            try {
                out.addAll(jdbc.queryForList(
                        "SELECT DISTINCT e.user_id FROM sys_employment e " +
                        "WHERE e.org_id IN (" + ph + ") AND e.status = 1 " +
                        "AND (e.valid_from IS NULL OR e.valid_from <= CURDATE()) " +
                        "AND (e.valid_to IS NULL OR e.valid_to >= CURDATE())",
                        Long.class, deptIds.toArray()));
            } catch (Exception inner) {
                log.warn("任职表用户展开失败(表未就绪?): {}", inner.getMessage());
            }
            out.addAll(jdbc.queryForList(
                    "SELECT id FROM sys_user WHERE dept_id IN (" + ph + ") AND deleted = 0 AND status = 1",
                    Long.class, deptIds.toArray()));
            return new ArrayList<>(out);
        } catch (Exception e) {
            log.warn("部门用户展开失败: {}", e.getMessage());
            return List.of();
        }
    }

    /** 用户集合 → 其所属组织集(维度补齐; 【P1-4】employment ∪ sys_user.dept_id) */
    private Set<Long> deptsOfUsers(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) return Set.of();
        try {
            String ph = String.join(",", userIds.stream().map(x -> "?").toList());
            java.util.LinkedHashSet<Long> out = new java.util.LinkedHashSet<>();
            out.addAll(jdbc.queryForList(
                    "SELECT DISTINCT e.org_id FROM sys_employment e " +
                    "WHERE e.user_id IN (" + ph + ") AND e.status = 1",
                    Long.class, userIds.toArray()));
            out.addAll(jdbc.queryForList(
                    "SELECT DISTINCT dept_id FROM sys_user WHERE id IN (" + ph + ") AND deleted = 0 AND dept_id IS NOT NULL",
                    Long.class, userIds.toArray()));
            return out;
        } catch (Exception e) {
            log.warn("用户部门回查失败: {}", e.getMessage());
            return Set.of();
        }
    }

    // ==================== 新轨(授予表)解析 ====================

    /** 【P0-6 修复】按指定用户的新轨解析(resolveScope 双轨守卫用——预览/对账等非会话用户) */
    public ScopeDecision scopeForUser(SecurityUser user, String actionCode) {
        PermActionRegistry.ActionDef def = registry.of(actionCode);
        if (def == null) return ScopeDecision.none();
        if (!def.isScopeRequired()) return ScopeDecision.all();
        return newTrackScope(user, def);
    }

    /** 【P1-10 修复】新轨+集团收口(对账 WithinGroup 槽用; 与 scopeWithinGroup 同口径, 显式绕过读开关) */
    public ScopeDecision scopeWithinGroupForUser(SecurityUser user, String actionCode) {
        ScopeDecision d = scopeForUser(user, actionCode);
        if (d == null || !d.isAll()) return d;
        if (user != null && user.hasRole("admin")) return d;
        Long g = com.aigicrm.common.scope.GroupScopeUtil.currentGroupOrNull();
        if (g == null) return ScopeDecision.none();
        List<Long> members = com.aigicrm.common.scope.GroupScopeUtil.userIdsInGroup(jdbc, g);
        ScopeDecision out = new ScopeDecision();
        out.setVisibleUserIds(members.isEmpty() ? List.of() : members);
        out.setVisibleDeptIds(List.of());
        return out;
    }

    /** 【WP8 对账】显式新轨解析(绕过读开关, 供对账工具/测试对比) */
    public ScopeDecision scopeNewTrack(SecurityUser user, String actionCode) {
        return scopeForUser(user, actionCode);
    }

    private ScopeDecision newTrackScope(SecurityUser user, PermActionRegistry.ActionDef def) {
        // 【P1-3 三轮修复 2026-11-13】视图别名无条件生效: 导出跟随列表(锁定决策#9 卡片=列表=导出)。
        // 旧写法"自身无授权才走别名"被回填/双写的 SELF grant 架空——导出范围=仅本人 ≠ 列表。
        // 视图别名的码直接按别名码的授权解析(自身行不参与)。
        String resolveCode = def.getCode();
        String alias = VIEW_ALIASES.get(resolveCode);
        if (alias != null) {
            resolveCode = alias;
        }
        // 【文件专项 P0 修复】槽级聚合: (file,manage) 槽五个动作码共享同一槽配置——
        // 角色只勾其中任一码(如仅 file:upload)也有槽 grant, 不因"单码无 grant"变空集。
        List<GrantReader.GrantRow> grants = grantsForSlot(user, resolveCode);
        if (grants.isEmpty()) {
            return ScopeDecision.none(); // 无授权=无范围(fail-closed, 规格 §3.2 empty→NONE)
        }
        // 【P0 三轮二修复 2026-11-13】按 grant **维度来源**分组解析, 废弃"合并后全局两遍补齐"——
        // 旧两遍会把 SELF/MY_TEAM(OWNER 维授权) 经 deptsOfUsers→usersInDepts 两跳放大成"部门同事"
        // (越权放宽; SELF 语义=仅本人, 决策#2 只定义 MY_DEPT 与 SELF 无关)。
        // 分组后维度补齐**单向**:
        //   DEPT 型授权(组织档) → usersInDepts 展开(OWNER 锚消费, 与旧轨 resolveDeptTree 同口径)
        //   OWNER 型授权(本人/团队档) → deptsOfUsers 回填(ORG 锚消费, 与旧轨 self/team 的 dept 口径一致)
        //   两条链**互不回灌**: DEPT 型展开出的用户不再转回部门, OWNER 型派生的部门不再展开用户。
        List<ScopeSpec> deptBased = new ArrayList<>();  // MY_DEPT*/MY_PRIMARY*/MY_OFFICE*/MY_REGION*/CUSTOM
        List<ScopeSpec> ownerBased = new ArrayList<>(); // SELF / MY_TEAM / ALL(短路于下方)
        boolean anyAll = false;
        for (GrantReader.GrantRow g : grants) {
            // 【P2 四轮修复 fail-closed】未知 scope_type 直接 none(旧行为=仅本人, 会把脏数据静默放行本人)
            if (!isKnownScopeType(g.getScopeType())) {
                log.warn("[PermEpochKernel] 未知 scope_type={} action={} → fail-closed none", g.getScopeType(), g.getActionCode());
                return ScopeDecision.none();
            }
            ScopeSpec s = specResolver.resolve(user, g);
            if (s.isAll()) { anyAll = true; break; }
            if (isDeptBasedScope(g.getScopeType())) deptBased.add(s);
            else ownerBased.add(s);
        }
        if (anyAll) return ScopeDecision.all(); // 任一 all → 本集团不过滤(锁定决策#6)

        // 【P1-1 四轮修复 2026-11-13】deptsOfUsers 只对 **nativeOwners**(OWNER 型授权+本人)反查——
        // 若对"DEPT 型展开出来的部门用户"再反查部门, 同事的兼职部门会并入 ORG 锚可见集(二次放大:
        // MY_DEPT+ORG 锚 会看到同事兼职部门的客户)。决策#2 的"全部任职组织"是**查看者**的属性,
        // 不是"我部门里每个人的全部任职组织"。
        Set<Long> nativeOwners = new HashSet<>();
        for (ScopeSpec s : ownerBased) nativeOwners.addAll(s.getOwnerIds());
        nativeOwners.add(user.getUserId());             // 本人恒含(旧轨 resolveUnion 同款)
        Set<Long> owners = new HashSet<>(nativeOwners);
        Set<Long> depts = new HashSet<>();
        for (ScopeSpec s : deptBased) depts.addAll(s.getDeptIds());
        // 单向补齐(各一次, 无回灌):
        owners.addAll(usersInDepts(depts));             // DEPT 型 → OWNER 锚可见用户
        depts.addAll(deptsOfUsers(nativeOwners));       // OWNER 型+本人 → ORG 锚可见部门(仅 native 反查)

        if (owners.isEmpty() && depts.isEmpty()) return ScopeDecision.none();

        ScopeRequest.AnchorType anchor = dominantAnchor(resolveCode, def);
        ScopeDecision d = new ScopeDecision();
        if (anchor == ScopeRequest.AnchorType.ORG) {
            // ORG 锚: 部门维=DEPT 型部门 ∪ OWNER 型人员所属部门; 用户维=部门在职用户(A6 口径);
            // 上级/负责人开关**不追加**(旧轨 ORG 分支同款)
            d.setVisibleDeptIds(new ArrayList<>(depts));
            d.setVisibleUserIds(new ArrayList<>(usersInDepts(depts)));
        } else {
            // OWNER 锚: 用户维=OWNER 型人员 ∪ DEPT 型部门用户 ∪ 本人; 上级/负责人开关仅此处追加
            if (def.isViewLike()) {
                boolean[] sw = legacyResolver.switchFlags();
                boolean superiorOn = sw != null && sw.length > 0 && sw[0];
                boolean leaderOn = sw != null && sw.length > 1 && sw[1];
                if (superiorOn) owners.addAll(specResolver.teamOf(user.getUserId()));
                if (leaderOn) {
                    for (Long uid : ledOrgSpec(user.getUserId()).getOwnerIds()) owners.add(uid);
                }
            }
            owners.add(user.getUserId()); // 开关合并后重保本人
            d.setVisibleUserIds(new ArrayList<>(owners));
            d.setVisibleDeptIds(List.of()); // 镜像旧轨 OWNER 分支(deptIds 恒空 list)
        }
        if (d.getVisibleUserIds() != null && d.getVisibleUserIds().isEmpty()
                && (d.getVisibleDeptIds() == null || d.getVisibleDeptIds().isEmpty())) {
            return ScopeDecision.none();
        }
        return d;
    }

    /**
     * 【文件专项 P0 修复 2026-11-13】槽级授权聚合: 一槽多码(如 (file,manage) 五码)时,
     * 合并槽内全部码的 grant 行(去重)——任一码有 grant 即代表槽配置生效;
     * 单码槽退化为 grantsOf(code) 原语义。
     */
    private List<GrantReader.GrantRow> grantsForSlot(SecurityUser user, String actionCode) {
        String[] slot = registry.slotOf(actionCode);
        if (slot == null) return grantReader.grantsOf(user, actionCode);
        List<GrantReader.GrantRow> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String c : registry.codesOf(slot[0], slot[1])) {
            for (GrantReader.GrantRow g : grantReader.grantsOf(user, c)) {
                String key = g.getSubjectType() + ":" + g.getSubjectId() + ":" + g.getScopeType() + ":" + g.getAnchor();
                if (seen.add(key)) out.add(g);
            }
        }
        return out;
    }

    /** DEPT 维授权(组织档): 解析产出部门集合, OWNER 锚消费时展开为部门用户 */
    private static boolean isDeptBasedScope(String scopeType) {
        return switch (scopeType == null ? "" : scopeType) {
            case "MY_DEPT", "MY_DEPT_SUB", "MY_PRIMARY_DEPT", "MY_PRIMARY_DEPT_SUB",
                 "MY_OFFICE", "MY_OFFICE_SUB", "MY_REGION", "MY_REGION_SUB", "CUSTOM" -> true;
            default -> false; // SELF / MY_TEAM / ALL / 未知
        };
    }

    /** 【P2 四轮修复】已知 scope_type 全集(12 档; 未知 → fail-closed none) */
    private static boolean isKnownScopeType(String scopeType) {
        return switch (scopeType == null ? "" : scopeType) {
            case "SELF", "MY_TEAM", "ALL",
                 "MY_DEPT", "MY_DEPT_SUB", "MY_PRIMARY_DEPT", "MY_PRIMARY_DEPT_SUB",
                 "MY_OFFICE", "MY_OFFICE_SUB", "MY_REGION", "MY_REGION_SUB", "CUSTOM" -> true;
            default -> false;
        };
    }

    /** 负责人开关: leader_id=本人 的组织子树(ORG 维=部门集; OWNER 维=部门内在职用户集) */
    private ScopeSpec ledOrgSpec(Long userId) {
        Set<Long> roots = new HashSet<>();
        try {
            roots.addAll(jdbc.queryForList(
                    "SELECT id FROM sys_dept WHERE leader_id = ? AND status = 1", Long.class, userId));
        } catch (Exception e) {
            return ScopeSpec.none();
        }
        Set<Long> depts = specResolver.expandSubtrees(roots);
        if (depts.isEmpty()) return ScopeSpec.none();
        ScopeSpec s = ScopeSpec.ofDepts(depts);
        try {
            String ph = String.join(",", depts.stream().map(x -> "?").toArray(String[]::new));
            List<Long> users = jdbc.queryForList(
                    "SELECT id FROM sys_user WHERE dept_id IN (" + ph + ") AND deleted = 0 AND status = 1",
                    Long.class, depts.toArray());
            s.merge(ScopeSpec.ofOwners(users));
        } catch (Exception ignore) {
            // OWNER 维失败不回滚 ORG 维(两维独立并集)
        }
        return s;
    }

    // ==================== 旧轨(现有链路, WP8 前权威) ====================

    private ScopeDecision legacyScope(SecurityUser user, String actionCode) {
        PermActionRegistry.ActionDef def = registry.of(actionCode);
        String module = def != null ? def.getModule() : legacyModuleOf(actionCode);
        String action = legacySlotAction(actionCode);
        try {
            ScopeRequest req = ScopeRequest.builder()
                    .user(user)
                    .module(module)
                    .action(action)
                    // 【WP7 锚点保真】旧轨逐模块复刻现有锚点语义: 仅 customer 域读 anchorOf
                    // (P2-3 起列表/统计同源); 其余模块现有消费=resolveModuleUsers 2参 OWNER 硬编码,
                    // 切轨不得改变默认行为, 否则"默认轨=零行为变化"承诺即破。
                    .anchor(legacyAnchor(module, action))
                    .build();
            return legacyResolver.resolveScope(req);
        } catch (Exception e) {
            log.warn("旧轨范围解析失败 code={}: {}", actionCode, e.getMessage());
            return ScopeDecision.none();
        }
    }

    /** 旧轨锚点保真: customer=anchorOf 配置; 其余模块=OWNER(与 resolveModuleUsers 2参现状一致) */
    private ScopeRequest.AnchorType legacyAnchor(String module, String action) {
        if ("customer".equals(module)) {
            SecurityUser u = SecurityUtils.getCurrentUser();
            return rolePermScopeService.anchorOf(u, module, action);
        }
        return ScopeRequest.AnchorType.OWNER;
    }

    // ==================== 集团收口(复刻 resolveModuleUsersWithinGroup) ====================

    /**
     * scope() + WithinGroup 语义: 非 admin 的 all 收口到本集团成员集(无集团 fail-closed 空集);
     * admin 的 all 保持 null(平台全量)。供 task/score/system 等现有 WithinGroup 消费方切轨。
     */
    public ScopeDecision scopeWithinGroup(String actionCode) {
        ScopeDecision d = scope(actionCode);
        if (d == null || !d.isAll()) return d;
        SecurityUser user = SecurityUtils.getCurrentUser();
        if (user != null && user.hasRole("admin")) return d; // admin 全量保持
        Long g = com.aigicrm.common.scope.GroupScopeUtil.currentGroupOrNull();
        if (g == null) return ScopeDecision.none();
        ScopeDecision out = new ScopeDecision();
        List<Long> members = com.aigicrm.common.scope.GroupScopeUtil.userIdsInGroup(jdbc, g);
        out.setVisibleUserIds(members.isEmpty() ? List.of() : members);
        out.setVisibleDeptIds(List.of());
        return out;
    }

    /**
     * 码→旧轨 (module, action) 槽动作映射(消费面=role_perm_scope 14 轴; 与 V2026.11.13.3 回填同表)。
     * 未列出的码走注册表 action_code 兜底(其 scope_required=0 时本方法不会被消费)。
     */
    private String legacySlotAction(String code) {
        switch (code) {
            case "customer:view": return "view";
            case "customer:edit": return "edit";
            case "customer:delete": return "delete";
            case "customer:finance:edit": return "finance_edit";
            case "file:view": return "view";
            // 【文件专项 P0 修复】manage 组五码(matrixActionOf 同表): upload/edit/share 旧表漏列,
            // 槽锚(dominantAnchor/legacyScope)须与 FolderPermissionService 消费一致
            case "file:upload":
            case "file:edit":
            case "file:share":
            case "file:acl:manage":
            case "file:folder:manage": return "manage";
            case "file:delete": return "delete";
            case "incentive:stats:view":
            case "incentive:rank:view": return "stats_view";
            case "approval:view":
            case "reminder:view":
            case "sea:view":
            case "score:view":
            case "task:view":
            case "system:log:view": return "view";
            default:
                // 未列出的码: 走注册表 action_code 兜底(其 scope_required=0 时不会被旧轨消费)
                return actionCodeTail(code);
        }
    }

    private String actionCodeTail(String code) {
        int i = code.lastIndexOf(':');
        return i < 0 ? code : code.substring(i + 1);
    }

    private String legacyModuleOf(String code) {
        PermActionRegistry.ActionDef def = registry.of(code);
        if (def != null) return def.getModule();
        int i = code.indexOf(':');
        return i < 0 ? code : code.substring(0, i);
    }

    // ==================== 主导锚点 ====================

    /** 【ScopeQuery 消费】主导锚点判定单一入口(防两处实现漂移) */
    public ScopeRequest.AnchorType dominantAnchorForQuery(String actionCode, PermActionRegistry.ActionDef def) {
        return dominantAnchor(actionCode, def);
    }

    /** 主导锚点: 镜像旧轨 P2-3 口径——锚点以 role_perm_scope.anchorOf 配置为准(grant 行 anchor 仅双写参考) */
    private ScopeRequest.AnchorType dominantAnchor(String actionCode, PermActionRegistry.ActionDef def) {
        SecurityUser user = SecurityUtils.getCurrentUser();
        String module = def != null ? def.getModule() : legacyModuleOf(actionCode);
        return rolePermScopeService.anchorOf(user, module, legacySlotAction(actionCode));
    }

    // ==================== 读开关 ====================

    /** perm.read.<module>: 'new' 走新轨, 其余(缺失/old/脏值)一律旧轨 */
    public String readMode(String module) {
        if (module == null || module.isBlank()) return "old";
        long now = System.currentTimeMillis();
        if (now >= switchCacheExpire) {
            synchronized (this) {
                if (now >= switchCacheExpire) {
                    readModeCache.clear();
                    switchCacheExpire = now + SWITCH_TTL_MS;
                }
            }
        }
        return readModeCache.computeIfAbsent(module, m -> {
            try {
                String v = configService.getValue(READ_SWITCH_PREFIX + m);
                return "new".equalsIgnoreCase(v == null ? "" : v.trim()) ? "new" : "old";
            } catch (Exception e) {
                return "old";
            }
        });
    }

    /** 模块读开关切换(管理端, WP8); 变更即失效缓存 */
    public void evictReadMode() {
        synchronized (this) {
            switchCacheExpire = 0;
            readModeCache.clear();
        }
    }
}
