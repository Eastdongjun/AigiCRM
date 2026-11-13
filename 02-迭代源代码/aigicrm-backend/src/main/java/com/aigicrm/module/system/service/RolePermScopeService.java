package com.aigicrm.module.system.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.common.scope.ScopeConfig;
import com.aigicrm.common.scope.ScopeRequest;
import com.aigicrm.security.SecurityUser;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 角色-模块-动作 数据范围(权限体系重构 2026-08-10)。
 * 核心: 每个权限 = 功能(谁能做, RBAC) × 范围(对谁的数据, 本服务)。
 * 未配置某(角色,模块,动作) → 查看沿用角色 data_scope; 编辑/删除/分享等默认 self(仅本人, 兼容现状)。
 */
@Service
@RequiredArgsConstructor
public class RolePermScopeService {

    /** 合法范围级别(与 DataScopeType 主码一致; dept_and_children 为 V7 注释遗留值, 解析器按 dept 语义降级) */
    private static final java.util.Set<String> VALID_SCOPES = java.util.Set.of(
            "all", "custom", "region", "office", "dept", "dept_and_children", "org", "org_and_children", "team", "self");

    /** 短 TTL 本地缓存(30s): effectiveScopes 在 canView/canEdit/canDelete 热路径上, 避免每判定 1 条 JDBC */
    private static final long CACHE_TTL_MS = 30_000L;
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AnchorCacheEntry> anchorCache = new ConcurrentHashMap<>();

    private record CacheEntry(String value, long expireAt) {
        boolean expired() { return System.currentTimeMillis() >= expireAt; }
    }

    private record AnchorCacheEntry(ScopeRequest.AnchorType anchor, long expireAt) {
        boolean expired() { return System.currentTimeMillis() >= expireAt; }
    }

    /**
     * 【T7 2026-11-12】动作轴消费档白名单(与 scripts/check-scope-axis-contract.sh 声明表同源 15 档,
     * 契约④双向对账防两处漂移): 写入面校验——后端永不消费的 (module,action) 档位拒绝入库,
     * 防"勾了不生效"的死配置新增(存量死档清单见 docs/permission/role-perm-scope-dead-configs.md,
     * 属数据治理另走清理迁移)。`*` 通配仅限历史存量行, 配置面禁写。
     */
    private static final java.util.Set<String> CONSUMED_AXIS = java.util.Set.of(
            "customer|view", "customer|edit", "customer|delete", "customer|finance_edit",
            "file|view", "file|manage", "file|delete",
            // 【WP1.5 2026-11-13 锁定决策#7】statistics|view 数据范围档退役: statistics:view 只做入口码,
            // 统计项按来源动作(customer:view 等)过滤——本表移除后写入面拒绝新增该档(存量行走清理迁移)
            "sea|view", "incentive|stats_view",
            "task|view", "score|view", "system|view",
            "approval|view", "reminder|view");

    private final JdbcTemplate jdbc;
    private final com.aigicrm.security.PermissionCacheService permissionCacheService;
    // 【2026-11-03 权限中心 B2】范围配置变更记 sys_rule_version(审计时间线)
    private final com.aigicrm.module.system.mapper.SysRuleVersionMapper ruleVersionMapper;

    /** 【B2 审计】记一条范围变更版本(old 须在写入前读出; 失败不阻断主流程) */
    private void recordScopeVersion(String assigneeType, String assigneeKey, String module, String action,
                                     String oldValue, String newValue) {
        try {
            com.aigicrm.module.system.entity.SysRuleVersion rv = new com.aigicrm.module.system.entity.SysRuleVersion();
            rv.setRuleKey("perm_scope:" + assigneeType + ":" + assigneeKey + ":" + module + ":" + action);
            rv.setOldValue(oldValue);
            rv.setNewValue(newValue);
            rv.setRemark("数据范围/锚点配置");
            try {
                var user = com.aigicrm.security.SecurityUtils.getCurrentUser();
                if (user != null) {
                    rv.setOperatorId(user.getUserId());
                    rv.setOperatorName(user.getUsername());
                }
            } catch (Exception ignore) {
                // 无会话上下文时 operator 为空
            }
            ruleVersionMapper.insert(rv);
        } catch (Exception e) {
            // 版本记录失败不阻断配置变更(旁路降级, 与 SysConfigService.recordRuleVersion 同口径)
        }
    }

    /** 【2026-08-14 缓存自愈】全局权限版本递增：范围配置变更影响所有用户，evict 漏网/Redis 写失败时缓存与 DB 版本比对自愈 */
    private void bumpAllPermVersions() {
        jdbc.update("UPDATE sys_user SET perm_version = perm_version + 1");
    }

    /** 【批③ 正交配置层】按用户装载全量范围配置(USER/POSITION/ROLE 三级; 登录时写入 SecurityUser.scopeConfigs)。
     *  装载序 USER→POSITION→ROLE 即优先级编码——OrgScopeResolver.configuredScopeCodes 取首个有匹配的主体
     *  (2026-08-11 P2 已接入解析链路)。注: effectiveScopes/configsFor 当前无生产调用方(运行时解析走 OrgScopeResolver)。 */
    public List<ScopeConfig> loadScopeConfigs(Long userId, List<String> roleCodes, List<Long> positionIds) {
        List<ScopeConfig> all = new ArrayList<>();
        try {
            if (userId != null) {
                all.addAll(queryConfigs("assignee_type='USER' AND assignee_key=?", String.valueOf(userId)));
            }
            if (positionIds != null) {
                for (Long pid : positionIds) {
                    all.addAll(queryConfigs("assignee_type='POSITION' AND assignee_key=?", String.valueOf(pid)));
                }
            }
            if (roleCodes != null) {
                for (String rc : roleCodes) {
                    all.addAll(queryConfigs("assignee_type='ROLE' AND assignee_key=?", rc));
                }
            }
        } catch (Exception e) {
            // 【PR-01 P1-08 2026-09-18 fail-closed】配置表缺失/查询失败不再降级为空
            // （此前返回已加载/空配置，后续回退角色旧 dataScope，可能放宽到更宽范围）。
            // 授权元数据不可用即明确 503，调用方不得用未经证实的默认范围继续放行。
            throw new BusinessException(503, "权限范围配置读取失败，暂不可用");
        }
        return all;
    }

    private List<ScopeConfig> queryConfigs(String where, String key) {
        // 【审查 perm-5 2026-10-18】确定性排序: 同键历史重复行取最早一条(id ASC), 消除读序漂移
        return jdbc.query("SELECT assignee_type, assignee_key, module, action, scope_level, anchor FROM role_perm_scope WHERE " + where + " ORDER BY id ASC",
                (rs, i) -> {
                    ScopeConfig c = new ScopeConfig();
                    c.setAssigneeType(rs.getString("assignee_type"));
                    c.setAssigneeKey(rs.getString("assignee_key"));
                    c.setModule(rs.getString("module"));
                    c.setAction(rs.getString("action"));
                    c.setScopeLevel(rs.getString("scope_level"));
                    c.setAnchor(rs.getString("anchor"));
                    return c;
                }, key);
    }

    /** 从 SecurityUser 内存配置读取 (module,action) 的显式配置(三级按装载序;
     *  运行时优先级判定由 OrgScopeResolver.configuredScopeCodes 承担——此处为并集读取, 供非解析场景/审计用) */
    public List<ScopeConfig> configsFor(SecurityUser user, String module, String action) {
        if (user == null || user.getScopeConfigs() == null) return List.of();
        List<ScopeConfig> out = new ArrayList<>();
        for (ScopeConfig c : user.getScopeConfigs()) {
            if (module.equals(c.getModule()) && action.equals(c.getAction())) out.add(c);
        }
        return out;
    }

    /** 有效范围 code 列表(多主体并集; 空=未配置走默认) */
    public List<String> effectiveScopes(SecurityUser user, String module, String action) {
        List<String> codes = new ArrayList<>();
        // 【批③ 正交配置层】读内存配置(scopeConfigs 三级并集, 无优先级)——热路径零 DB
        if (user != null && user.getScopeConfigs() != null) {
            for (ScopeConfig c : user.getScopeConfigs()) {
                if (module.equals(c.getModule()) && action.equals(c.getAction())) {
                    codes.add(c.getScopeLevel());
                }
            }
        }
        // 内存未命中(如缓存重组前/规则引擎直调)回退查库(30s JDBC 缓存)
        if (codes.isEmpty() && user != null && user.getRoleCodes() != null) {
            for (String roleCode : user.getRoleCodes()) {
                String scope = lookup(roleCode, module, action);
                if (scope != null) codes.add(scope);
            }
        }
        if (codes.isEmpty()) {
            if ("view".equals(action)) {
                if (user != null && user.getDataScopes() != null && !user.getDataScopes().isEmpty()) {
                    return new ArrayList<>(user.getDataScopes());
                }
                return List.of(user != null && user.getDataScope() != null ? user.getDataScope() : "self");
            }
            return List.of("self"); // 编辑/删除/分享等默认仅本人(兼容现状)
        }
        return codes;
    }

    /** 查询单个 (角色,模块,动作) 的范围; 无配置返回 null(30s 本地缓存) */
    public String lookup(String roleCode, String module, String action) {
        String key = roleCode + ":" + module + ":" + action;
        CacheEntry hit = cache.get(key);
        if (hit != null && !hit.expired()) {
            return hit.value();
        }
        try {
            List<String> rows = jdbc.queryForList(
                    "SELECT scope_level FROM role_perm_scope WHERE role_code=? AND module=? AND action=? ORDER BY id ASC LIMIT 1",
                    String.class, roleCode, module, action);
            String value = rows.isEmpty() ? null : rows.get(0);
            cache.put(key, new CacheEntry(value, System.currentTimeMillis() + CACHE_TTL_MS));
            return value;
        } catch (Exception e) {
            // 【PR-01 N-04 2026-09-18 fail-closed】DB 异常不再返 null 伪装"未配置"
            // （调用方会回退默认 dataScope，可能比显式配置更宽）。未知即 503。
            throw new BusinessException(503, "权限范围配置读取失败，暂不可用");
        }
    }

    /** 当前用户任一角色是否显式配置了 (module,action) 范围(用于区分"配置了"与"未配置走默认") */
    public boolean hasConfigured(SecurityUser user, String module, String action) {
        if (user == null || user.getRoleCodes() == null) return false;
        for (String roleCode : user.getRoleCodes()) {
            if (lookup(roleCode, module, action) != null) return true;
        }
        return false;
    }

    /** 有效范围 code 列表(多角色并集; 空=未配置走默认) —— 供统一数据访问层直接获取配置码 */
    public List<String> configuredScopes(SecurityUser user, String module, String action) {
        List<String> codes = new ArrayList<>();
        if (user != null && user.getRoleCodes() != null) {
            for (String roleCode : user.getRoleCodes()) {
                String scope = lookup(roleCode, module, action);
                if (scope != null) codes.add(scope);
            }
        }
        return codes;
    }

    /** 模块级锚点: 配置行 anchor 列(OWNER/ORG), 未配置默认 OWNER(批②: 客户模块可配 ORG) */
    public ScopeRequest.AnchorType anchorOf(SecurityUser user, String module, String action) {
        // 【批③】优先内存配置(登录装载)
        if (user != null && user.getScopeConfigs() != null) {
            for (ScopeConfig c : user.getScopeConfigs()) {
                if (module.equals(c.getModule()) && action.equals(c.getAction())) {
                    return "ORG".equalsIgnoreCase(c.getAnchor()) ? ScopeRequest.AnchorType.ORG : ScopeRequest.AnchorType.OWNER;
                }
            }
        }
        if (user != null && user.getRoleCodes() != null) {
            for (String roleCode : user.getRoleCodes()) {
                AnchorCacheEntry hit = anchorCache.get(roleCode + ":" + module + ":" + action);
                if (hit != null && !hit.expired()) return hit.anchor();
                try {
                    List<String> rows = jdbc.queryForList(
                            "SELECT anchor FROM role_perm_scope WHERE role_code=? AND module=? AND action=? ORDER BY id ASC LIMIT 1",
                            String.class, roleCode, module, action);
                    ScopeRequest.AnchorType a = rows.isEmpty() ? ScopeRequest.AnchorType.OWNER
                            : "ORG".equalsIgnoreCase(rows.get(0)) ? ScopeRequest.AnchorType.ORG : ScopeRequest.AnchorType.OWNER;
                    anchorCache.put(roleCode + ":" + module + ":" + action, new AnchorCacheEntry(a, System.currentTimeMillis() + CACHE_TTL_MS));
                    return a;
                } catch (Exception e) {
                    // 【G3 2026-09-21 fail-closed】锚点读取失败不得静默回退 OWNER(故障期 ORG 配置被悄悄换口径,
                    // 可能更宽)——与全服务 role_perm_scope 读点统一按 503 拒绝
                    throw new BusinessException(503, "权限范围锚点读取失败，暂不可用");
                }
            }
        }
        return ScopeRequest.AnchorType.OWNER;
    }

    /** 某角色的全部范围配置 */
    public List<Map<String, Object>> getRoleScopes(String roleCode) {
        return jdbc.queryForList(
                "SELECT id, module, action, scope_level, anchor FROM role_perm_scope WHERE role_code=? ORDER BY module, action, id ASC",
                roleCode);
    }

    /** 【批③】按主体(岗位/用户)查范围配置(含锚点) */
    public List<Map<String, Object>> getAssigneeScopes(String assigneeType, String assigneeKey) {
        return jdbc.queryForList(
                "SELECT id, module, action, scope_level, anchor FROM role_perm_scope WHERE assignee_type=? AND assignee_key=? ORDER BY module, action, id ASC",
                assigneeType, assigneeKey);
    }

    /**
     * 保存某角色的范围配置(UPSERT, 角色级兼容入口); configs = [{module, action, scopeLevel, anchor?}]。
     * 【修复 2026-08-10 P2-07】configs 为空列表 = 删除该角色全部配置(恢复"未配置"默认语义);
     * 【修复 P2-05】scopeLevel 不在合法集合内直接拒绝。
     */
    @Transactional
    public void updateRoleScopes(String roleCode, List<Map<String, Object>> configs) {
        updateScopes(ScopeConfig.ASSIGNEE_ROLE, roleCode, configs);
    }

    /** 【批③ 正交配置层】通用保存(主体三级 ROLE/POSITION/USER + 锚点) */
    @Transactional
    public void updateScopes(String assigneeType, String assigneeKey, List<Map<String, Object>> configs) {
        if (configs == null) return;
        if (configs.isEmpty()) {
            // 【B2 审计】清空=恢复默认, 逐 (module,action) 记旧值→默认
            for (var row : jdbc.queryForList(
                    "SELECT module, action, scope_level, anchor FROM role_perm_scope WHERE assignee_type=? AND assignee_key=?",
                    assigneeType, assigneeKey)) {
                recordScopeVersion(assigneeType, assigneeKey, String.valueOf(row.get("module")), String.valueOf(row.get("action")),
                        row.get("scope_level") + "/" + row.get("anchor"), "默认(未配置)");
            }
            jdbc.update("DELETE FROM role_perm_scope WHERE assignee_type=? AND assignee_key=?", assigneeType, assigneeKey);
            dualDeleteGrants(assigneeType, assigneeKey); // 【WP8 双写】授予表同步删除
            invalidate(assigneeKey);
            permissionCacheService.evictAll();
            // 【2026-08-14 缓存自愈】版本递增兜底：evict 漏网/Redis 写失败时缓存与 DB 版本比对自愈
            bumpAllPermVersions();
            return;
        }
        for (Map<String, Object> cfg : configs) {
            Object module = cfg.get("module");
            Object action = cfg.get("action");
            Object scopeLevel = cfg.get("scopeLevel");
            if (module == null || action == null || scopeLevel == null) continue;
            String scope = scopeLevel.toString();
            if (!VALID_SCOPES.contains(scope)) {
                throw new com.aigicrm.common.BusinessException("非法的数据范围取值: " + scope);
            }
            // 【T7 2026-11-12】写入面档位校验: 后端未消费的 (module,action) 拒绝配置(防新增死配置)
            String axisKey = module + "|" + action;
            if (!CONSUMED_AXIS.contains(axisKey)) {
                throw new com.aigicrm.common.BusinessException(400,
                        "该档位(" + axisKey + ")后端未消费，禁止配置——请选择有效的功能×范围档");
            }
            // 【WP1.3 2026-11-13 fail-closed】USER 级 region/office 档写入面拦截:
            // 目标用户任职组织无对应 dept_type 祖先(大区=2/办事处=3)时拒绝配置(400)——
            // 否则运行时解析 fail-closed 返回空集, 勾了等于没勾("配置成功但不生效"体验)
            if (ScopeConfig.ASSIGNEE_USER.equals(assigneeType)
                    && ("region".equals(scope) || "office".equals(scope))) {
                int wantType = "region".equals(scope) ? 2 : 3;
                if (!userHasAncestorOfType(assigneeKey, wantType)) {
                    throw new com.aigicrm.common.BusinessException(400, wantType == 2
                            ? "该用户不在任何大区下，无法配置“本大区”范围"
                            : "该用户不在任何办事处下，无法配置“本办事处”范围");
                }
            }
            String anchor = cfg.get("anchor") != null ? cfg.get("anchor").toString() : "OWNER";
            if (!"OWNER".equalsIgnoreCase(anchor) && !"ORG".equalsIgnoreCase(anchor)) {
                throw new com.aigicrm.common.BusinessException("非法的范围锚点取值: " + anchor);
            }
            // role_code 列同步写(兼容 V7 查询/回滚窗口)
            String roleCode = ScopeConfig.ASSIGNEE_ROLE.equals(assigneeType) ? assigneeKey : "";
            // 【审查 perm-5 2026-10-18】同一 (assignee,module,action) 只允许一个锚点:
            // 旧唯一键含 anchor, 切换 OWNER/ORG 时旧行不会被 upsert 命中, 会遗留两条锚点
            // (anchorOf 取第一条无排序 → 口径漂移)。写入前先清掉同键其它锚点, 保留本锚点行
            // 走下方 upsert(保留 id/created_at); 迁移 V2026.10.18.4 同键去重并加四列唯一索引。
            // 【B2 审计】旧值在清锚点/upsert 前读出
            String oldScope = null;
            for (var row : jdbc.queryForList(
                    "SELECT scope_level, anchor FROM role_perm_scope WHERE assignee_type=? AND assignee_key=? AND module=? AND action=?",
                    assigneeType, assigneeKey, module.toString(), action.toString())) {
                oldScope = row.get("scope_level") + "/" + row.get("anchor");
            }
            jdbc.update("DELETE FROM role_perm_scope WHERE assignee_type=? AND assignee_key=? "
                            + "AND module=? AND action=? AND anchor<>?",
                    assigneeType, assigneeKey, module.toString(), action.toString(), anchor.toUpperCase());
            jdbc.update(
                    "INSERT INTO role_perm_scope (role_code, assignee_type, assignee_key, module, action, scope_level, anchor) VALUES (?,?,?,?,?,?,?) " +
                    "ON DUPLICATE KEY UPDATE scope_level=VALUES(scope_level), anchor=VALUES(anchor), role_code=VALUES(role_code)",
                    roleCode, assigneeType, assigneeKey, module.toString(), action.toString(), scope, anchor.toUpperCase());
            recordScopeVersion(assigneeType, assigneeKey, module.toString(), action.toString(), oldScope, scope + "/" + anchor.toUpperCase());
            dualUpsertGrants(assigneeType, assigneeKey, module.toString(), action.toString(), scope, anchor.toUpperCase()); // 【WP8 双写】
            invalidate(assigneeKey);
        }
        // 【2026-08-14 评审 P2】evictAll/bump 提升到循环外：批量 N 条配置只做一次全量失效+全表版本递增
        // （循环内会 N 次 UPDATE sys_user 全表行锁）
        permissionCacheService.evictAll();
        bumpAllPermVersions();
    }

    // ==================== 【WP8 双写 2026-11-13】旧表保存同步写 sys_perm_grant ====================
    // 切流(WP8)前 sys_perm_grant 非权威——双写保证新轨数据新鲜, 读开关翻转时零回填等待。
    // 锚点保真: ROLE 级授权与 role_perm_scope 行一一对应; scope_type 映射与 V2026.11.13.3 同表。

    private final com.aigicrm.common.auth.PermActionRegistry permActionRegistry;

    private static String scopeTypeOfLegacy(String legacyScope) {
        if (legacyScope == null) return "SELF";
        switch (legacyScope) {
            case "all": return "ALL";
            case "custom": return "CUSTOM";
            case "region": return "MY_REGION_SUB";
            case "office": return "MY_OFFICE_SUB";
            case "dept":
            case "dept_and_children":
            case "org_and_children": return "MY_DEPT_SUB";
            case "org": return "MY_DEPT";
            case "team": return "MY_TEAM";
            default: return "SELF";
        }
    }

    /** 槽保存 → 授予表 upsert(槽的全部码; effect=ALLOW; tenant=1) */
    private void dualUpsertGrants(String assigneeType, String assigneeKey, String module, String action,
                                   String scopeLevel, String anchor) {
        try {
            List<String> codes = permActionRegistry.codesOf(module, action);
            for (String code : codes) {
                jdbc.update(
                        "INSERT INTO sys_perm_grant (tenant_id, subject_type, subject_id, action_code, scope_type, anchor, effect) " +
                        "VALUES (1, ?, ?, ?, ?, ?, 'ALLOW') " +
                        "ON DUPLICATE KEY UPDATE scope_type = VALUES(scope_type), anchor = VALUES(anchor)",
                        assigneeType, assigneeKey, code, scopeTypeOfLegacy(scopeLevel), anchor);
            }
        } catch (Exception e) {
            // 授予表未就绪/写失败不阻断旧表主流程(旧轨权威; 对账工具兜底差异)
            logDualWriteFailure("upsert", assigneeType, assigneeKey, e);
        }
    }

    /** 槽清空 → 授予表删除(该主体该槽全部码) */
    private void dualDeleteGrants(String assigneeType, String assigneeKey) {
        try {
            jdbc.update("DELETE FROM sys_perm_grant WHERE tenant_id = 1 AND subject_type = ? AND subject_id = ?",
                    assigneeType, assigneeKey);
        } catch (Exception e) {
            logDualWriteFailure("delete", assigneeType, assigneeKey, e);
        }
    }

    private void logDualWriteFailure(String op, String assigneeType, String assigneeKey, Exception e) {
        org.slf4j.LoggerFactory.getLogger(RolePermScopeService.class)
                .warn("双写失败({}) {}/{}: {}", op, assigneeType, assigneeKey, e.getMessage());
    }

    /**
     * 【P0-5 修复 2026-11-13 方案二】角色菜单绑定的槽码授权 upsert(供 SysRoleService.saveRoleMenus 调用)。
     * 动作轴权威仍在 sys_role_menu(本批不迁移); 但新轨 perm.read.*=new 时范围解析按 sys_perm_grant——
     * 新绑槽码的角色若无授权行 → 范围恒空(fail-closed 误伤)。本方法保证: 菜单绑码即有槽授权,
     * scope 取 匹配 role_perm_scope 行 → 角色 data_scope 映射 → SELF(与 V2026.11.13.3 回填同优先级)。
     */
    public void dualUpsertRoleSlotGrants(String roleCode, String dataScope, java.util.Collection<String> codes) {
        if (codes == null || codes.isEmpty()) return;
        try {
            for (String code : codes) {
                String[] slot = permActionRegistry.slotOf(code);
                if (slot == null) continue; // 非槽码(入口/路由)无范围语义, 无需授权行
                String scopeLevel = null;
                String anchor = "OWNER";
                for (var row : jdbc.queryForList(
                        "SELECT scope_level, anchor FROM role_perm_scope WHERE assignee_type='ROLE' AND assignee_key=? AND module=? AND action=?",
                        roleCode, slot[0], slot[1])) {
                    scopeLevel = String.valueOf(row.get("scope_level"));
                    anchor = String.valueOf(row.get("anchor"));
                }
                String mapped = scopeLevel != null ? scopeTypeOfLegacy(scopeLevel)
                        : scopeTypeOfLegacy(dataScope != null ? dataScope : "self");
                jdbc.update(
                        "INSERT INTO sys_perm_grant (tenant_id, subject_type, subject_id, action_code, scope_type, anchor, effect) " +
                        "VALUES (1, 'ROLE', ?, ?, ?, ?, 'ALLOW') " +
                        "ON DUPLICATE KEY UPDATE scope_type = VALUES(scope_type), anchor = VALUES(anchor)",
                        roleCode, code, mapped, anchor);
            }
        } catch (Exception e) {
            logDualWriteFailure("slot-grants", "ROLE", roleCode, e);
        }
    }

    /** 删除某角色的全部范围配置(角色删除/重置时) */
    @Transactional
    public void deleteRoleScopes(String roleCode) {
        jdbc.update("DELETE FROM role_perm_scope WHERE assignee_type='ROLE' AND assignee_key=?", roleCode);
        dualDeleteGrants("ROLE", roleCode); // 【WP8 双写】
        invalidate(roleCode);
        permissionCacheService.evictAll();
        // 【2026-08-14 缓存自愈】版本递增兜底：evict 漏网/Redis 写失败时缓存与 DB 版本比对自愈
        bumpAllPermVersions();
    }

    /**
     * 【WP1.3 2026-11-13】用户任职组织链上是否存在指定 dept_type 祖先(大区=2/办事处=3)。
     * 与 OrgScopeResolver.findAncestorOfType 同算法(parent_id 上行, 20 层防环), 独立 JDBC
     * 实现避免反向依赖(Resolver 不依赖本服务, 本服务不注入 Mapper 树)。
     */
    private boolean userHasAncestorOfType(String userId, int deptType) {
        try {
            List<Long> deptIds = jdbc.queryForList(
                    "SELECT dept_id FROM sys_user WHERE id=? AND deleted=0", Long.class, Long.valueOf(userId));
            if (deptIds.isEmpty() || deptIds.get(0) == null) return false;
            Long cur = deptIds.get(0);
            for (int i = 0; i < 20 && cur != null; i++) {
                List<Map<String, Object>> rows = jdbc.queryForList(
                        "SELECT id, parent_id, dept_type FROM sys_dept WHERE id=?", cur);
                if (rows.isEmpty()) return false;
                Object t = rows.get(0).get("dept_type");
                if (t != null && ((Number) t).intValue() == deptType) return true;
                Object p = rows.get(0).get("parent_id");
                cur = p != null ? ((Number) p).longValue() : null;
            }
            return false;
        } catch (NumberFormatException e) {
            return false; // assigneeKey 非法 → 视为无锚(fail-closed)
        }
    }

    /** role_code 改名时迁移其全部范围配置(防止旧 code 孤儿化, 新 code 意外收紧) */
    @Transactional
    public void renameRoleCode(String oldCode, String newCode) {
        jdbc.update("UPDATE role_perm_scope SET role_code=?, assignee_key=? WHERE assignee_type='ROLE' AND assignee_key=?",
                newCode, newCode, oldCode);
        try { // 【WP8 双写】授予表主体改名
            jdbc.update("UPDATE sys_perm_grant SET subject_id=? WHERE tenant_id=1 AND subject_type='ROLE' AND subject_id=?",
                    newCode, oldCode);
        } catch (Exception e) {
            logDualWriteFailure("rename", oldCode, newCode, e);
        }
        invalidate(oldCode);
        invalidate(newCode);
        permissionCacheService.evictAll();
        // 【2026-08-14 缓存自愈】版本递增兜底：evict 漏网/Redis 写失败时缓存与 DB 版本比对自愈
        bumpAllPermVersions();
    }

    /** 是否有任意范围配置(判断"是否已进入新模式")。
     * 【PR-01 N-04】DB 异常不再返 false 伪装"无配置"（会退回旧 dataScope 路径），未知即 503。 */
    public boolean hasAnyConfig() {
        try {
            Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM role_perm_scope", Integer.class);
            return n != null && n > 0;
        } catch (Exception e) {
            throw new BusinessException(503, "权限范围配置读取失败，暂不可用");
        }
    }

    /** 角色级缓存失效(该角色所有 (module,action) 条目) */
    private void invalidate(String roleCode) {
        cache.entrySet().removeIf(e -> e.getKey().startsWith(roleCode + ":"));
        anchorCache.entrySet().removeIf(e -> e.getKey().startsWith(roleCode + ":"));
    }
}
