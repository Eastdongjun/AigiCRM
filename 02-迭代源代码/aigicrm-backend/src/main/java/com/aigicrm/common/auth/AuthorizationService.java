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

    /** 单条判定(OWNER/ORG 维度按主导锚点; 本人恒可=兼容现状) */
    public boolean assertData(String actionCode, Long targetOwnerId, Long targetDeptId) {
        ScopeDecision d = scope(actionCode);
        if (d == null) return false;
        if (d.isAll()) return true;
        PermActionRegistry.ActionDef def = registry.of(actionCode);
        ScopeRequest.AnchorType anchor = dominantAnchor(actionCode, def);
        if (anchor == ScopeRequest.AnchorType.ORG) {
            return d.canAccessDept(targetDeptId);
        }
        Long self = SecurityUtils.getCurrentUserId();
        return d.canAccessOwner(self) || d.canAccessOwner(targetOwnerId);
    }

    // ==================== 新轨(授予表)解析 ====================

    private ScopeDecision newTrackScope(SecurityUser user, PermActionRegistry.ActionDef def) {
        List<GrantReader.GrantRow> grants = grantReader.grantsOf(user, def.getCode());
        if (grants.isEmpty()) {
            return ScopeDecision.none(); // 无授权=无范围(fail-closed, 规格 §3.2 empty→NONE)
        }
        List<ScopeSpec> parts = new ArrayList<>();
        for (GrantReader.GrantRow g : grants) {
            parts.add(specResolver.resolve(user, g));
        }
        ScopeSpec spec = ScopeSpec.union(parts);
        // 上级/负责人开关: 仅 view_like 动作追加(锁定决策#8; 写动作绝不放宽)
        if (def.isViewLike()) {
            boolean[] sw = legacyResolver.switchFlags();
            if (sw[0]) spec.merge(ScopeSpec.ofOwners(specResolver.teamOf(user.getUserId())));
            if (sw[1]) spec.merge(ledOrgSpec(user.getUserId()));
        }
        if (spec.isEmpty()) return ScopeDecision.none();
        ScopeDecision d = new ScopeDecision();
        if (spec.isAll()) {
            d.setVisibleUserIds(null);
            d.setVisibleDeptIds(null);
        } else {
            d.setVisibleUserIds(spec.visibleOwners());
            d.setVisibleDeptIds(spec.visibleDepts());
        }
        return d;
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
                    .anchor(rolePermScopeService.anchorOf(user, module, action))
                    .build();
            return legacyResolver.resolveScope(req);
        } catch (Exception e) {
            log.warn("旧轨范围解析失败 code={}: {}", actionCode, e.getMessage());
            return ScopeDecision.none();
        }
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

    /** 主导锚点: 授权行首个 anchor → 注册表 default_anchor → OWNER */
    private ScopeRequest.AnchorType dominantAnchor(String actionCode, PermActionRegistry.ActionDef def) {
        SecurityUser user = SecurityUtils.getCurrentUser();
        if (user != null && "new".equals(readMode(def != null ? def.getModule() : ""))) {
            for (GrantReader.GrantRow g : grantReader.grantsOf(user, actionCode)) {
                if (g.getAnchor() != null) {
                    return "ORG".equalsIgnoreCase(g.getAnchor())
                            ? ScopeRequest.AnchorType.ORG : ScopeRequest.AnchorType.OWNER;
                }
            }
        }
        return rolePermScopeService.anchorOf(SecurityUtils.getCurrentUser(),
                def != null ? def.getModule() : "", legacySlotAction(actionCode));
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
