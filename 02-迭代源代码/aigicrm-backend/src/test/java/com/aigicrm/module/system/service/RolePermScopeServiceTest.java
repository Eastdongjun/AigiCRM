package com.aigicrm.module.system.service;

import com.aigicrm.security.SecurityUser;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 角色-模块-动作 数据范围: effectiveScopes 默认/配置逻辑 */
class RolePermScopeServiceTest {

    private SecurityUser user(String... roleCodes) {
        SecurityUser u = new SecurityUser();
        u.setUserId(1L);
        u.setRoleCodes(new java.util.ArrayList<>(List.of(roleCodes)));
        u.setDataScopes(List.of("dept"));
        u.setDataScope("dept");
        return u;
    }

    @Test
    void effectiveScopes_noConfig_view_fallsBackToRoleDataScope() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(contains("scope_level"), eq(String.class), eq("admin"), eq("customer"), eq("view")))
                .thenReturn(List.of());
        RolePermScopeService svc = new RolePermScopeService(jdbc, mock(com.aigicrm.security.PermissionCacheService.class));
        assertEquals(List.of("dept"), svc.effectiveScopes(user("admin"), "customer", "view"),
                "查看未配置范围 → 沿用角色 data_scope");
    }

    @Test
    void effectiveScopes_noConfig_edit_defaultsSelf() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(contains("scope_level"), eq(String.class), eq("admin"), eq("customer"), eq("edit")))
                .thenReturn(List.of());
        RolePermScopeService svc = new RolePermScopeService(jdbc, mock(com.aigicrm.security.PermissionCacheService.class));
        assertEquals(List.of("self"), svc.effectiveScopes(user("admin"), "customer", "edit"),
                "编辑未配置范围 → 默认仅本人(兼容现状)");
    }

    @Test
    void effectiveScopes_configured_usesRoleScope() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(contains("scope_level"), eq(String.class), eq("xingzheng"), eq("customer"), eq("delete")))
                .thenReturn(List.of("dept"));
        RolePermScopeService svc = new RolePermScopeService(jdbc, mock(com.aigicrm.security.PermissionCacheService.class));
        assertEquals(List.of("dept"), svc.effectiveScopes(user("xingzheng"), "customer", "delete"),
                "已配置范围 → 用配置值");
    }

    @Test
    void updateRoleScopes_upsertsConfig() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RolePermScopeService svc = new RolePermScopeService(jdbc, mock(com.aigicrm.security.PermissionCacheService.class));
        svc.updateRoleScopes("xingzheng", List.of(
                Map.of("module", "customer", "action", "delete", "scopeLevel", "dept")));
        verify(jdbc).update(contains("INSERT INTO role_perm_scope"),
                eq("xingzheng"), eq("ROLE"), eq("xingzheng"), eq("customer"), eq("delete"), eq("dept"), eq("OWNER"));
    }

}
