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
        RolePermScopeService svc = new RolePermScopeService(jdbc, mock(com.aigicrm.security.PermissionCacheService.class),
                mock(com.aigicrm.module.system.mapper.SysRuleVersionMapper.class));
        assertEquals(List.of("dept"), svc.effectiveScopes(user("admin"), "customer", "view"),
                "查看未配置范围 → 沿用角色 data_scope");
    }

    @Test
    void effectiveScopes_noConfig_edit_defaultsSelf() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(contains("scope_level"), eq(String.class), eq("admin"), eq("customer"), eq("edit")))
                .thenReturn(List.of());
        RolePermScopeService svc = new RolePermScopeService(jdbc, mock(com.aigicrm.security.PermissionCacheService.class),
                mock(com.aigicrm.module.system.mapper.SysRuleVersionMapper.class));
        assertEquals(List.of("self"), svc.effectiveScopes(user("admin"), "customer", "edit"),
                "编辑未配置范围 → 默认仅本人(兼容现状)");
    }

    @Test
    void effectiveScopes_configured_usesRoleScope() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(contains("scope_level"), eq(String.class), eq("xingzheng"), eq("customer"), eq("delete")))
                .thenReturn(List.of("dept"));
        RolePermScopeService svc = new RolePermScopeService(jdbc, mock(com.aigicrm.security.PermissionCacheService.class),
                mock(com.aigicrm.module.system.mapper.SysRuleVersionMapper.class));
        assertEquals(List.of("dept"), svc.effectiveScopes(user("xingzheng"), "customer", "delete"),
                "已配置范围 → 用配置值");
    }

    @Test
    void updateRoleScopes_upsertsConfig() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RolePermScopeService svc = new RolePermScopeService(jdbc, mock(com.aigicrm.security.PermissionCacheService.class),
                mock(com.aigicrm.module.system.mapper.SysRuleVersionMapper.class));
        svc.updateRoleScopes("xingzheng", List.of(
                Map.of("module", "customer", "action", "delete", "scopeLevel", "dept")));
        verify(jdbc).update(contains("INSERT INTO role_perm_scope"),
                eq("xingzheng"), eq("ROLE"), eq("xingzheng"), eq("customer"), eq("delete"), eq("dept"), eq("OWNER"));
    }

    /**
     * 【审查 perm-5 2026-10-18】同键切换锚点: 写入前必须清理其它锚点旧行,
     * 否则旧唯一键(含 anchor)不会命中, 两条锚点并存 → anchorOf 取第一条无排序。
     */
    @Test
    void updateScopes_switchingAnchor_removesConflictingAnchorRows() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RolePermScopeService svc = new RolePermScopeService(jdbc, mock(com.aigicrm.security.PermissionCacheService.class),
                mock(com.aigicrm.module.system.mapper.SysRuleVersionMapper.class));

        svc.updateScopes(com.aigicrm.common.scope.ScopeConfig.ASSIGNEE_ROLE, "xingzheng",
                List.of(Map.of("module", "customer", "action", "view",
                        "scopeLevel", "dept", "anchor", "ORG")));

        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(jdbc);
        inOrder.verify(jdbc).update(contains("DELETE FROM role_perm_scope"),
                eq("ROLE"), eq("xingzheng"), eq("customer"), eq("view"), eq("ORG"));
        inOrder.verify(jdbc).update(contains("INSERT INTO role_perm_scope"),
                eq("xingzheng"), eq("ROLE"), eq("xingzheng"), eq("customer"), eq("view"), eq("dept"), eq("ORG"));
    }

    /** 读取锚点带确定性排序(id ASC), 历史重复行取最早一条 */
    @Test
    void anchorOf_readsEarliestDeterministically() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RolePermScopeService svc = new RolePermScopeService(jdbc, mock(com.aigicrm.security.PermissionCacheService.class),
                mock(com.aigicrm.module.system.mapper.SysRuleVersionMapper.class));
        when(jdbc.queryForList(contains("SELECT anchor"), eq(String.class), eq("xingzheng"), eq("customer"), eq("view")))
                .thenReturn(List.of("ORG"));

        com.aigicrm.common.scope.ScopeRequest.AnchorType anchor =
                svc.anchorOf(user("xingzheng"), "customer", "view");

        assertEquals(com.aigicrm.common.scope.ScopeRequest.AnchorType.ORG, anchor);
        org.mockito.ArgumentCaptor<String> sql = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(sql.capture(), eq(String.class), eq("xingzheng"), eq("customer"), eq("view"));
        org.junit.jupiter.api.Assertions.assertTrue(sql.getValue().contains("ORDER BY id ASC"),
                "锚点读取必须确定性排序: " + sql.getValue());
    }

    /** 范围读取同样带确定性排序(id ASC) */
    @Test
    void lookup_readsEarliestDeterministically() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RolePermScopeService svc = new RolePermScopeService(jdbc, mock(com.aigicrm.security.PermissionCacheService.class),
                mock(com.aigicrm.module.system.mapper.SysRuleVersionMapper.class));
        when(jdbc.queryForList(contains("SELECT scope_level"), eq(String.class), eq("xingzheng"), eq("customer"), eq("view")))
                .thenReturn(List.of("dept"));

        assertEquals("dept", svc.lookup("xingzheng", "customer", "view"));
        org.mockito.ArgumentCaptor<String> sql = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(sql.capture(), eq(String.class), eq("xingzheng"), eq("customer"), eq("view"));
        org.junit.jupiter.api.Assertions.assertTrue(sql.getValue().contains("ORDER BY id ASC"),
                "范围读取必须确定性排序: " + sql.getValue());
    }

    // ===== 【T7 2026-11-12】写入面档位校验: 死档拒配 400, 活档(15)全放行 =====

    @Test
    void updateScopes_deadAxisRejectedWith400() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RolePermScopeService svc = new RolePermScopeService(jdbc, mock(com.aigicrm.security.PermissionCacheService.class),
                mock(com.aigicrm.module.system.mapper.SysRuleVersionMapper.class));
        // incentive|view 是生产死档(激励可见性走 grant 表)——写入面必须 400 拒绝, 防新增死配置
        List<Map<String, Object>> dead = List.of(Map.of("module", "incentive", "action", "view", "scopeLevel", "dept"));
        com.aigicrm.common.BusinessException ex = org.junit.jupiter.api.Assertions.assertThrows(
                com.aigicrm.common.BusinessException.class, () -> svc.updateScopes("ROLE", "06", dead));
        org.junit.jupiter.api.Assertions.assertTrue(String.valueOf(ex.getMessage()).contains("后端未消费"),
                "死档拒绝文案应说明原因: " + ex.getMessage());
    }

    @Test
    void updateScopes_consumedAxisAccepted() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RolePermScopeService svc = new RolePermScopeService(jdbc, mock(com.aigicrm.security.PermissionCacheService.class),
                mock(com.aigicrm.module.system.mapper.SysRuleVersionMapper.class));
        // 活档抽样(incentive|stats_view 拍板B档 + approval|view/reminder|view 两个易漏档)不抛且落库
        svc.updateScopes("ROLE", "06", List.of(
                Map.of("module", "incentive", "action", "stats_view", "scopeLevel", "dept"),
                Map.of("module", "approval", "action", "view", "scopeLevel", "self"),
                Map.of("module", "reminder", "action", "view", "scopeLevel", "all")));
        org.mockito.Mockito.verify(jdbc, org.mockito.Mockito.atLeast(3)).update(
                org.mockito.ArgumentMatchers.contains("INSERT INTO role_perm_scope"),
                org.mockito.ArgumentMatchers.<Object>any(), org.mockito.ArgumentMatchers.<Object>any(),
                org.mockito.ArgumentMatchers.<Object>any(), org.mockito.ArgumentMatchers.<Object>any(),
                org.mockito.ArgumentMatchers.<Object>any(), org.mockito.ArgumentMatchers.<Object>any(),
                org.mockito.ArgumentMatchers.<Object>any());
    }

}
