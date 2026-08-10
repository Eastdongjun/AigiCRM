package com.ycmn.module.system.service;

import com.ycmn.security.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/** M5 公司级统计可见授权: canViewCompany 三态(admin恒真/未配置默认全员/已配置仅勾选) */
class StatsPermissionServiceTest {

    @Test
    void canViewCompany_adminAlwaysTrue() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        StatsPermissionService svc = new StatsPermissionService(jdbc);
        try (MockedStatic<SecurityUtils> ms = mockStatic(SecurityUtils.class)) {
            ms.when(SecurityUtils::isAdmin).thenReturn(true);
            assertTrue(svc.canViewCompany(), "admin 恒可见");
        }
    }

    @Test
    void canViewCompany_emptyConfig_defaultEveryone() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(eq("SELECT COUNT(*) FROM biz_stats_permission"), eq(Integer.class))).thenReturn(0);
        StatsPermissionService svc = new StatsPermissionService(jdbc);
        try (MockedStatic<SecurityUtils> ms = mockStatic(SecurityUtils.class)) {
            ms.when(SecurityUtils::isAdmin).thenReturn(false);
            ms.when(SecurityUtils::getCurrentUserId).thenReturn(100L);
            assertTrue(svc.canViewCompany(), "未配置授权 → 默认全员可见(兼容现状)");
        }
    }

    @Test
    void canViewCompany_configuredNotGranted_false() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(eq("SELECT COUNT(*) FROM biz_stats_permission"), eq(Integer.class))).thenReturn(3);
        when(jdbc.queryForObject(contains("can_view_company=1"), eq(Integer.class), eq(100L))).thenReturn(0);
        StatsPermissionService svc = new StatsPermissionService(jdbc);
        try (MockedStatic<SecurityUtils> ms = mockStatic(SecurityUtils.class)) {
            ms.when(SecurityUtils::isAdmin).thenReturn(false);
            ms.when(SecurityUtils::getCurrentUserId).thenReturn(100L);
            assertFalse(svc.canViewCompany(), "已配置授权且该用户未勾选 → 不可见");
        }
    }

    @Test
    void canViewCompany_configuredGranted_true() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(eq("SELECT COUNT(*) FROM biz_stats_permission"), eq(Integer.class))).thenReturn(3);
        when(jdbc.queryForObject(contains("can_view_company=1"), eq(Integer.class), eq(100L))).thenReturn(1);
        StatsPermissionService svc = new StatsPermissionService(jdbc);
        try (MockedStatic<SecurityUtils> ms = mockStatic(SecurityUtils.class)) {
            ms.when(SecurityUtils::isAdmin).thenReturn(false);
            ms.when(SecurityUtils::getCurrentUserId).thenReturn(100L);
            assertTrue(svc.canViewCompany(), "已配置授权且该用户已勾选 → 可见");
        }
    }
}
