package com.aigicrm.module.system.controller;

import com.aigicrm.common.BusinessException;
import com.aigicrm.module.system.service.SysPositionService;
import com.aigicrm.security.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 【P1 2026-09-29 复审修复】岗位权限规则 admin-only 守卫(控制器层)。
 * 旁路口径: 持 system:position:edit 的非 admin 经 PUT /{positionId}/rules
 * 绑定/解绑规则 = 改写岗位下用户数据/字段权限, 绕过 assignUserPositions 提权防护。
 */
class SysPositionRuleGuardControllerTest {

    private final SysPositionService positionService = mock(SysPositionService.class);
    private final SysPositionController controller = new SysPositionController(positionService);

    private static Map<String, List<Long>> body(List<Long> ruleIds) {
        Map<String, List<Long>> m = new HashMap<>();
        m.put("ruleIds", ruleIds);
        return m;
    }

    @Test
    void updateRules_nonAdmin_rejected() {
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::isAdmin).thenReturn(false);
            assertThrows(BusinessException.class, () -> controller.updateRules(9L, body(List.of(1L, 2L))),
                    "非 admin 修改岗位权限规则必须被拒");
        }
        verify(positionService, never()).saveRules(anyLong(), any());
    }

    @Test
    void updateRules_admin_passesThrough() {
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::isAdmin).thenReturn(true);
            controller.updateRules(9L, body(List.of(1L)));
        }
        verify(positionService).saveRules(9L, List.of(1L));
    }

    @Test
    void updateRules_admin_clearingRules_alsoAllowed() {
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::isAdmin).thenReturn(true);
            controller.updateRules(9L, body(List.of()));
        }
        verify(positionService).saveRules(9L, List.of());
    }
}
