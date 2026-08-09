package com.ycmn.module.sea.controller;

import com.ycmn.common.BusinessException;
import com.ycmn.module.sea.service.SeaConfigService;
import com.ycmn.module.sea.service.SeaPermissionService;
import com.ycmn.module.sea.service.SeaService;
import com.ycmn.module.approval.mapper.BizApprovalMapper;
import com.ycmn.module.approval.mapper.BizApprovalNodeMapper;
import com.ycmn.module.customer.mapper.BizCustomerMapper;
import com.ycmn.module.system.mapper.SysUserMapper;
import com.ycmn.common.scope.OrgScopeResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 【2026-08-10 公海专项权限】BLOCKER 修复回归——无 view 专项权限时 list/statistics 必须拒绝(403)。
 */
class SeaControllerPermissionTest {

    private SeaPermissionService seaPermissionService;
    private SeaController controller;

    @BeforeEach
    void setUp() {
        seaPermissionService = mock(SeaPermissionService.class);
        controller = new SeaController(
                mock(SeaService.class),
                mock(SeaConfigService.class),
                seaPermissionService,
                mock(BizApprovalMapper.class),
                mock(BizApprovalNodeMapper.class),
                mock(BizCustomerMapper.class),
                mock(SysUserMapper.class),
                mock(OrgScopeResolver.class));
    }

    @Test
    void list_withoutViewPerm_throws403() {
        when(seaPermissionService.currentUserHasPerm(SeaPermissionService.VIEW)).thenReturn(false);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> controller.page(1, 15, null, null, null, null, null, null, null));
        assertTrue(ex.getMessage().contains("没有公海查看权限"));
    }

    @Test
    void statistics_withoutViewPerm_throws403() {
        when(seaPermissionService.currentUserHasPerm(SeaPermissionService.VIEW)).thenReturn(false);
        BusinessException ex = assertThrows(BusinessException.class, () -> controller.statistics());
        assertTrue(ex.getMessage().contains("没有公海查看权限"));
    }
}
