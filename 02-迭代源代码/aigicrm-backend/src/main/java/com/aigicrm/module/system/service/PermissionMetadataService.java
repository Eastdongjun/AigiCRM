package com.aigicrm.module.system.service;

import com.aigicrm.common.auth.PermissionDependencyRules;
import com.aigicrm.module.system.entity.SysMenu;
import com.aigicrm.module.system.mapper.SysMenuMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 权限点元数据(2026-11-15 权限体系收口·注册表轻量版)。
 *
 * <p>以 sys_menu 为数据源, 叠加运行期语义: 类型(菜单/按钮/字段)、父码、数据范围轴、
 * adminOnly/requiresOrgAdmin、废弃/无端点标记、字段码依赖。前端权限中心据此渲染标签、
 * 置灰依赖与范围控件, 不再各自硬编码。</p>
 *
 * <p>P1 后续可落 sys_permission_meta 表做单一事实源; 本类先保证“码有元数据、前后端同源”。</p>
 */
@Service
@RequiredArgsConstructor
public class PermissionMetadataService {

    private final SysMenuMapper menuMapper;
    private final JdbcTemplate jdbc;

    /** 权限码 → 后端真实消费的 (module, action) 数据范围轴(与 RolePermScopeService.CONSUMED_AXIS 对齐) */
    public static final Map<String, String[]> SCOPE_AXIS = Map.ofEntries(
            Map.entry("customer:view", new String[]{"customer", "view"}),
            Map.entry("customer:edit", new String[]{"customer", "edit"}),
            Map.entry("customer:delete", new String[]{"customer", "delete"}),
            Map.entry("customer:finance:edit", new String[]{"customer", "finance_edit"}),
            Map.entry("reminder:view", new String[]{"reminder", "view"}),
            Map.entry("approval:view", new String[]{"approval", "view"}),
            Map.entry("task:view", new String[]{"task", "view"}),
            Map.entry("sea:view", new String[]{"sea", "view"}),
            Map.entry("system:log:view", new String[]{"system", "view"}),
            Map.entry("score:view", new String[]{"score", "view"}),
            Map.entry("incentive:stats:view", new String[]{"incentive", "stats_view"}),
            Map.entry("incentive:rank:view", new String[]{"incentive", "stats_view"}),
            Map.entry("fabushi:stats:view", new String[]{"fabushi", "stats_view"}),
            Map.entry("fabushi:view", new String[]{"fabushi", "file_view"}),
            Map.entry("fabushi:file:delete", new String[]{"fabushi", "file_delete"}),
            Map.entry("file:version:rollback", new String[]{"file", "version_rollback"}));

    /** 仅平台管理员可用的功能码(授权给非 admin 角色 = 死配置) */
    public static final Set<String> ADMIN_ONLY = Set.of(
            "system:group:add", "system:group:edit", "system:group:delete", "system:task:run",
            "system:recycle:restore", "system:recycle:purge", "file:acl:manage");

    /** 持码仍需组织管理员身份(canManageUser) */
    public static final Set<String> REQUIRES_ORG_ADMIN = Set.of(
            "system:user:add", "system:user:edit", "system:user:delete");

    /** 无独立后端端点/纯前端或旁路码(契约校验白名单) */
    public static final Set<String> NO_ENDPOINT = Set.of(
            "incentive:view-all", "incentive:view-shared",
            "incentive:person-rank:export", "incentive:rank:export",
            "system:role:grant", "system:menu:edit");

    private static final Set<String> FIELD_CODES = Set.of(
            "customer:date:edit", "customer:invoice-date:edit");

    private static final Map<String, String> GROUP_OF_PREFIX = Map.of(
            "finance", "客户管理", "contact", "客户管理", "ranking", "统计分析", "fabushi", "法布施");

    /** 全部权限点元数据(优先读 sys_permission_meta 注册表, 表未就绪时回退 sys_menu 推导) */
    public List<Map<String, Object>> list() {
        List<Map<String, Object>> fromTable = readRegistryTable();
        if (!fromTable.isEmpty()) return fromTable;
        return buildFromMenu();
    }

    /** 读 sys_permission_meta(表未建/查询失败 → 空, 调用方回退) */
    private List<Map<String, Object>> readRegistryTable() {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT code, name, type, module, parent_code, scope_module, scope_action, scope_mode, "
                            + "admin_only, requires_org_admin, deprecated, no_endpoint, depends_on "
                            + "FROM sys_permission_meta ORDER BY code");
            List<Map<String, Object>> out = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                String code = str(row.get("code"));
                if (code == null || code.isBlank()) continue;
                String scopeModule = str(row.get("scope_module"));
                String scopeAction = str(row.get("scope_action"));
                Map<String, Object> meta = new LinkedHashMap<>();
                meta.put("code", code);
                meta.put("name", str(row.get("name")));
                meta.put("type", str(row.get("type")));
                meta.put("module", str(row.get("module")));
                meta.put("group", GROUP_OF_PREFIX.get(str(row.get("module"))));
                meta.put("parentCode", str(row.get("parent_code")));
                meta.put("scopeAxis", scopeModule == null ? null
                        : Map.of("module", scopeModule, "action", scopeAction == null ? "view" : scopeAction));
                meta.put("scopeMode", str(row.get("scope_mode")));
                meta.put("adminOnly", bool(row.get("admin_only")));
                meta.put("requiresOrgAdmin", bool(row.get("requires_org_admin")));
                meta.put("deprecated", bool(row.get("deprecated")));
                meta.put("noEndpoint", bool(row.get("no_endpoint")));
                meta.put("dependsOn", str(row.get("depends_on")));
                out.add(meta);
            }
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static boolean bool(Object v) {
        if (v == null) return false;
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.intValue() != 0;
        return "1".equals(String.valueOf(v)) || "true".equalsIgnoreCase(String.valueOf(v));
    }

    /** 注册表未就绪时的回退: 从 sys_menu + 静态语义推导 */
    private List<Map<String, Object>> buildFromMenu() {
        List<SysMenu> all = menuMapper.selectList(null);
        Map<Long, SysMenu> byId = new LinkedHashMap<>();
        for (SysMenu m : all) {
            if (m.getId() != null) byId.put(m.getId(), m);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (SysMenu m : all) {
            String code = m.getPermission();
            if (code == null || code.isBlank()) continue;
            String prefix = code.contains(":") ? code.substring(0, code.indexOf(':')) : code;
            String parentCode = null;
            if (m.getParentId() != null && byId.get(m.getParentId()) != null) {
                String p = byId.get(m.getParentId()).getPermission();
                if (p != null && !p.isBlank()) parentCode = p;
            }
            String type = resolveType(m, code);
            String[] axis = SCOPE_AXIS.get(code);
            String dependsOn = PermissionDependencyRules.REQUIRES.get(code);
            String scopeMode = axis != null ? "DIRECT" : (dependsOn != null ? "INHERIT" : "NONE");

            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("code", code);
            meta.put("name", m.getMenuName());
            meta.put("type", type);
            meta.put("module", prefix);
            meta.put("group", GROUP_OF_PREFIX.getOrDefault(prefix, null));
            meta.put("parentCode", parentCode);
            meta.put("scopeAxis", axis == null ? null : Map.of("module", axis[0], "action", axis[1]));
            meta.put("scopeMode", scopeMode);
            meta.put("adminOnly", ADMIN_ONLY.contains(code));
            meta.put("requiresOrgAdmin", REQUIRES_ORG_ADMIN.contains(code));
            meta.put("deprecated", m.getStatus() != null && m.getStatus() == 0);
            meta.put("noEndpoint", NO_ENDPOINT.contains(code));
            meta.put("dependsOn", dependsOn);
            out.add(meta);
        }
        return out;
    }

    private String resolveType(SysMenu m, String code) {
        if (FIELD_CODES.contains(code)) return "FIELD";
        Integer t = m.getMenuType();
        if (t != null && t == 1) return "MENU";
        return "BUTTON";
    }
}
