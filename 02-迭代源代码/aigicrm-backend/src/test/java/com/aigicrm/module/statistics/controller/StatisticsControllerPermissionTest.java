package com.aigicrm.module.statistics.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class StatisticsControllerPermissionTest {

    @Test
    void dashboardAndTrendEndpointsRequireStatisticsViewSingleKey() throws Exception {
        // 【2026-11-12 裁决A】统计单键统一: dashboard:view 退役, 全端点 hasAuthority('statistics:view')
        assertEquals("hasAuthority('statistics:view')",
                authorization("dashboard"));
        assertEquals("hasAuthority('statistics:view')",
                authorization("dailyStats"));
        assertEquals("hasAuthority('statistics:view')",
                authorization("weeklyStats"));
        assertEquals("hasAuthority('statistics:view')",
                authorization("teamActivities"));
    }

    @Test
    void detailedStatisticsRequireStatisticsView() throws Exception {
        assertEquals("hasAuthority('statistics:view')", authorization("indicatorStats"));
        assertEquals("hasAuthority('statistics:view')", authorization("analysis", int.class, Long.class, Long.class));
    }

    private String authorization(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = StatisticsController.class.getMethod(methodName, parameterTypes);
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertNotNull(annotation, methodName + " must declare method-level authorization");
        return annotation.value();
    }
}
