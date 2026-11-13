package com.aigicrm.module.system.service;

import com.aigicrm.common.scope.OrgScopeResolver;
import com.aigicrm.common.scope.ScopeDecision;
import com.aigicrm.common.scope.ScopeRequest;
import com.aigicrm.module.customer.service.CustomerService;
import com.aigicrm.module.incentive.service.IncentiveService;
import com.aigicrm.module.sea.entity.BizSeaConfig;
import com.aigicrm.module.sea.service.SeaConfigService;
import com.aigicrm.security.SecurityUser;
import com.aigicrm.security.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 【统一权限配置中心 2026-11-03 用户裁决"门面统一"】生效预览(只读)。
 *
 * 【需求】管理员配完权限立刻看到"某个用户真实拥有什么", 不用猜、不用拿真人账号试
 * 【方法】对目标 userId 用 UserDetailsServiceImpl 重建完整 SecurityUser(与每请求鉴权同链路),
 *         再喂给各引擎现成的解析方法(OrgScopeResolver/SeaConfigService/IncentiveService)
 * 【业务规则】只读不落库; 管理员目标用户全量可见; 数字=范围口径如实呈现
 *             (可见客户数不含运行时规则引擎条件, 预览只表达范围维度)
 * 【注意】不做任何写操作; 范围/功能/公海/激励/文件授权/菜单可见性走真实引擎同源解析;
 *         对象级(本人恒管/上级链)与规则条件不在预览计算(notCovered 如实标注)——
 *         【T8 2026-11-12】原"与真实请求判定零漂移"表述已改为上述诚实口径(未覆盖项显式列出)
 */
@Service
@RequiredArgsConstructor
public class PermCenterService {

    private final UserDetailsServiceImpl userDetailsService;
    private final OrgScopeResolver scopeResolver;
    private final RolePermScopeService rolePermScopeService;
    private final SeaConfigService seaConfigService;
    private final IncentiveService incentiveService;
    private final CustomerService customerService;
    // 【T8 2026-11-12】预览补齐三块: 文件授权/菜单可见性/规则指纹(此前只 5 键, 注释却称"零漂移")
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final com.aigicrm.module.file.mapper.BizFolderTypeDefaultMapper folderTypeDefaultMapper;
    private final com.aigicrm.module.system.mapper.SysRoleMapper roleMapper;
    private final com.aigicrm.module.system.mapper.SysPermissionRuleMapper permissionRuleMapper;

    /** 预览的范围解析目标(与前端权限矩阵 PERMISSION_SCOPE_TARGETS 映射同源; 2026-11-03 对账=摘死配置+补漏配) */
    private static final String[][] SCOPE_TARGETS = {
            {"customer", "view", "客户·查看"},
            {"customer", "edit", "客户·编辑"},
            {"customer", "delete", "客户·删除"},
            {"customer", "finance_edit", "开票回款·编辑"},
            {"reminder", "view", "提醒·查看"},
            {"approval", "view", "审批·查看"},
            {"task", "view", "任务·查看"},
            {"sea", "view", "公海·查看"},
            {"system", "view", "日志·查看(system:log:view, 审计日志共用)"},
            // 【2026-11-03 范围对账】摘 incentive/file 死配置行, 补 score 真消费行。
            // 【WP1.5 2026-11-13 锁定决策#7】(statistics,view) 数据范围档退役: statistics:view
            // 只做入口码, 统计可见人员改按来源动作 (customer,view) 单一源(CustomerVisibilityService),
            // 与 PermScopeManager 前端映射/CONSUMED_AXIS/V2026.11.13.1 存量清理四处同步
            {"score", "view", "学分·查看"},
            // 【2026-11-05 用户拍板 B"四项激励独立数据范围"】incentive:stats:view/rank:view 两码
            // 共用 (incentive, stats_view) 独立档位(同一数据族), 与统计分析档位解耦
            {"incentive", "stats_view", "四项激励·统计/排行数据范围(激励统计查看+激励排行查看共用)"},
    };

    public Map<String, Object> preview(Long userId) {
        SecurityUser target = (SecurityUser) userDetailsService.loadUserByUsername(String.valueOf(userId));

        Map<String, Object> data = new LinkedHashMap<>();
        Map<String, Object> userInfo = new LinkedHashMap<>();
        userInfo.put("userId", target.getUserId());
        userInfo.put("username", target.getUsername());
        userInfo.put("realName", target.getRealName());
        userInfo.put("roleCodes", target.getRoleCodes());
        userInfo.put("dataScope", target.getDataScope());
        userInfo.put("admin", target.hasRole("admin"));
        data.put("user", userInfo);
        data.put("permissions", new ArrayList<>(target.getPermissions()));

        // 范围解析表: 每个真实消费范围的权限码 → 生效范围/锚点/可见人数(可见客户数仅客户查看行)
        List<Map<String, Object>> scopes = new ArrayList<>();
        for (String[] t : SCOPE_TARGETS) {
            String module = t[0];
            String action = t[1];
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("module", module);
            row.put("action", action);
            row.put("label", t[2]);
            List<String> effective = rolePermScopeService.effectiveScopes(target, module, action);
            row.put("scope", effective == null || effective.isEmpty() ? "默认" : String.join(",", effective));
            ScopeRequest.AnchorType anchor = rolePermScopeService.anchorOf(target, module, action);
            row.put("anchor", anchor.name());
            ScopeRequest req = ScopeRequest.builder().user(target).module(module).action(action).anchor(anchor).build();
            ScopeDecision d = scopeResolver.resolveScope(req);
            if (d.isAll()) {
                row.put("visibleUserCount", -1); // -1 = 全部(前端显示"全部")
            } else {
                List<Long> uids = d.getVisibleUserIds();
                row.put("visibleUserCount", uids == null ? 0 : uids.size());
                List<Long> deptIds = d.getVisibleDeptIds();
                row.put("visibleDeptCount", deptIds == null ? 0 : deptIds.size());
            }
            if ("customer".equals(module) && "view".equals(action)) {
                row.put("visibleCustomerCount", customerService.countByScopeFor(target));
            }
            scopes.add(row);
        }
        data.put("scopes", scopes);

        // 公海各池判定(引擎方法本就接受任意 SecurityUser)
        List<Map<String, Object>> pools = new ArrayList<>();
        for (BizSeaConfig pool : seaConfigService.list()) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("poolId", pool.getId());
            p.put("poolName", pool.getPoolName());
            p.put("status", pool.getStatus());
            p.put("view", seaConfigService.hasPoolPerm(pool.getId(), SeaConfigService.PERM_VIEW, target));
            p.put("edit", seaConfigService.hasPoolPerm(pool.getId(), SeaConfigService.PERM_EDIT, target));
            p.put("delete", seaConfigService.hasPoolPerm(pool.getId(), SeaConfigService.PERM_DELETE, target));
            p.put("claim", seaConfigService.hasPoolPerm(pool.getId(), SeaConfigService.PERM_CLAIM, target));
            pools.add(p);
        }
        data.put("seaPools", pools);

        // 激励专项授权四开关(无行=默认: 分享可见开, 其余关)
        Map<String, Object> grant = new LinkedHashMap<>();
        Map<String, Object> row = incentiveService.grantRow(target.getUserId());
        grant.put("canViewShared", row == null ? Boolean.TRUE : flag(row, "can_view_shared", true));
        grant.put("canViewAll", row == null ? Boolean.FALSE : flag(row, "can_view_all", false));
        grant.put("canEdit", row == null ? Boolean.FALSE : flag(row, "can_edit", false));
        grant.put("canDelete", row == null ? Boolean.FALSE : flag(row, "can_delete", false));
        data.put("incentive", grant);

        // 【T8 2026-11-12】文件授权清单: 该用户命中的类型/夹例外授权行(ROLE 按角色码匹配/USER 按 id,
        // 含已过期行如实展示 expired=true——与文件权限页红标同口径)
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.util.Set<Long> roleIds = new java.util.HashSet<>();
        java.util.Map<Long, String> roleNames = new java.util.HashMap<>();
        for (com.aigicrm.module.system.entity.SysRole r : roleMapper.selectList(null)) {
            roleNames.put(r.getId(), r.getRoleName());
            if (r.getRoleCode() != null && target.getRoleCodes().contains(r.getRoleCode())) roleIds.add(r.getId());
        }
        List<Map<String, Object>> fileGrants = new ArrayList<>();
        for (com.aigicrm.module.file.entity.BizFolderTypeDefault g : folderTypeDefaultMapper.selectList(null)) {
            boolean hit = "USER".equals(g.getSubjectType())
                    ? userId.equals(g.getSubjectId())
                    : ("ROLE".equals(g.getSubjectType()) && roleIds.contains(g.getSubjectId()));
            if (!hit) continue;
            Map<String, Object> fg = new LinkedHashMap<>();
            fg.put("objectType", g.getObjectType());
            fg.put("folderType", g.getFolderType());
            fg.put("folderId", g.getFolderId());
            fg.put("scopeLevel", g.getScopeLevel());
            fg.put("perms", g.getPerms());
            fg.put("subjectType", g.getSubjectType());
            fg.put("subjectName", "USER".equals(g.getSubjectType()) ? target.getRealName() : roleNames.get(g.getSubjectId()));
            fg.put("expiresAt", g.getExpiresAt());
            fg.put("expired", g.getExpiresAt() != null && g.getExpiresAt().isBefore(now));
            fileGrants.add(fg);
        }
        data.put("fileGrants", fileGrants);

        // 【T8】菜单可见性: 该用户 role_menu 可见菜单行(名称/路径/类型), 与侧栏渲染同源(status=1)
        List<Map<String, Object>> menus = jdbc.queryForList(
                "SELECT DISTINCT m.menu_name, m.path, m.menu_type FROM sys_menu m " +
                "JOIN sys_role_menu rm ON rm.menu_id = m.id " +
                "JOIN sys_user_role ur ON ur.role_id = rm.role_id " +
                "WHERE ur.user_id = ? AND m.status = 1 ORDER BY m.sort_order, m.id", userId);
        Map<String, Object> menuVisibility = new LinkedHashMap<>();
        menuVisibility.put("menuCount", menus.size());
        menuVisibility.put("menus", menus);
        data.put("menuVisibility", menuVisibility);

        // 【T8】规则引擎指纹: 生产实况=零绑定(权限维度全面核查 2026-11-09 实证), 指纹恒空属正常;
        // 明示"规则条件不参与上方范围数字"(此前注释宣称与真实判定零漂移, 未覆盖文件/菜单/规则三面)
        List<com.aigicrm.module.system.entity.SysPermissionRule> rules = permissionRuleMapper.selectList(null);
        Map<String, Object> ruleEngine = new LinkedHashMap<>();
        ruleEngine.put("boundRules", rules == null ? 0 : rules.size());
        ruleEngine.put("fingerprint", com.aigicrm.common.engine.PermissionRuleEngine
                .evaluateForEntity(rules == null ? List.of() : rules, target, "customer"));
        ruleEngine.put("note", "规则条件不参与上方范围数字；当前规则绑定数为 0 时指纹恒空属正常(生产实况)");
        data.put("ruleEngine", ruleEngine);

        // 【T8】诚实标注: 对象级(本人恒管/上级链)不在本预览计算——实际以文件页行级判定为准
        data.put("notCovered", List.of(
                "对象级授权(上传者本人恒管/上级链可管)：未在预览计算，实际以文件页行级判定(row.perms)为准",
                "逐夹 ACL 历史转换行：已由类型默认授权体系取代，存量行只读展示于文件权限页"));
        return data;
    }

    private boolean flag(Map<String, Object> row, String column, boolean defaultValue) {
        Object v = row.get(column);
        if (v == null) return defaultValue;
        return Integer.valueOf(1).equals(((Number) v).intValue());
    }
}
