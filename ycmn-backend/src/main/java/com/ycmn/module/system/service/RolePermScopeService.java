package com.ycmn.module.system.service;

import com.ycmn.security.SecurityUser;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 角色-模块-动作 数据范围(权限体系重构 2026-08-10)。
 * 核心: 每个权限 = 功能(谁能做, RBAC) × 范围(对谁的数据, 本服务)。
 * 未配置某(角色,模块,动作) → 查看沿用角色 data_scope; 编辑/删除/分享等默认 self(仅本人, 兼容现状)。
 */
@Service
@RequiredArgsConstructor
public class RolePermScopeService {

    private final JdbcTemplate jdbc;

    /** 有效范围 code 列表(多角色并集; 空=未配置走默认) */
    public List<String> effectiveScopes(SecurityUser user, String module, String action) {
        List<String> codes = new ArrayList<>();
        if (user != null && user.getRoleCodes() != null) {
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

    /** 查询单个 (角色,模块,动作) 的范围; 无配置返回 null */
    public String lookup(String roleCode, String module, String action) {
        try {
            List<String> rows = jdbc.queryForList(
                    "SELECT scope_level FROM role_perm_scope WHERE role_code=? AND module=? AND action=? LIMIT 1",
                    String.class, roleCode, module, action);
            return rows.isEmpty() ? null : rows.get(0);
        } catch (Exception e) {
            return null;
        }
    }

    /** 某角色的全部范围配置 */
    public List<Map<String, Object>> getRoleScopes(String roleCode) {
        return jdbc.queryForList(
                "SELECT id, module, action, scope_level FROM role_perm_scope WHERE role_code=? ORDER BY module, action",
                roleCode);
    }

    /** 保存某角色的范围配置(UPSERT); configs = [{module, action, scopeLevel}] */
    @Transactional
    public void updateRoleScopes(String roleCode, List<Map<String, Object>> configs) {
        if (configs == null) return;
        for (Map<String, Object> cfg : configs) {
            Object module = cfg.get("module");
            Object action = cfg.get("action");
            Object scopeLevel = cfg.get("scopeLevel");
            if (module == null || action == null || scopeLevel == null) continue;
            jdbc.update(
                    "INSERT INTO role_perm_scope (role_code, module, action, scope_level) VALUES (?,?,?,?) " +
                    "ON DUPLICATE KEY UPDATE scope_level=VALUES(scope_level)",
                    roleCode, module.toString(), action.toString(), scopeLevel.toString());
        }
    }

    /** 删除某角色的全部范围配置(角色删除/重置时) */
    @Transactional
    public void deleteRoleScopes(String roleCode) {
        jdbc.update("DELETE FROM role_perm_scope WHERE role_code=?", roleCode);
    }

    /** 是否有任意范围配置(判断"是否已进入新模式") */
    public boolean hasAnyConfig() {
        try {
            Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM role_perm_scope", Integer.class);
            return n != null && n > 0;
        } catch (Exception e) {
            return false;
        }
    }
}
