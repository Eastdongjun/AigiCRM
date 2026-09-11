package com.aigicrm.module.statistics.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class StatisticsControllerPermissionTest {

    @Test
    void dashboardAndTrendEndpointsAcceptDashboardOrStatisticsView() throws Exception {
        assertEquals("hasAnyAuthority('statistics:view','dashboard:view')",
                authorization("dashboard"));
        assertEquals("hasAnyAuthority('statistics:view','dashboard:view')",
                authorization("dailyStats"));
        assertEquals("hasAnyAuthority('statistics:view','dashboard:view')",
                authorization("weeklyStats"));
        assertEquals("hasAnyAuthority('statistics:view','dashboard:view')",
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
