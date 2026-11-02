package com.aigicrm.module.approval.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.common.engine.CustomerAccessService;
import com.aigicrm.module.customer.entity.BizCustomer;
import com.aigicrm.module.customer.mapper.BizCustomerMapper;
import com.aigicrm.module.approval.entity.BizApproval;
import com.aigicrm.module.approval.mapper.*;
import com.aigicrm.module.sea.entity.BizSeaPool;
import com.aigicrm.module.sea.mapper.BizSeaPoolMapper;
import com.aigicrm.module.sea.service.SeaConfigService;
import com.aigicrm.module.sea.service.SeaService;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.module.system.mapper.SysUserRoleMapper;
import com.aigicrm.module.system.mapper.SysDeptMapper;
import com.aigicrm.common.scope.OrgScopeResolver;
import com.aigicrm.security.SecurityUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 【P0 2026-10-16 体检修复】延期审批 extend 分支归属校验回归钉。
 * 旧实现: extend 提交只拦"客户在公海", 任何持 incentive:view 的用户可对
 * 无查看权限的客户发起 +360 天保护期延期(审批人误批即生效)。
 * 修复: extend 提交时 fail-fast 校验发起人对该客户的查看权限(canView),
 * 与 transfer 同型的提交侧防线。
 */
class ExtendSubmitOwnershipTest {

    private final BizApprovalMapper approvalMapper = mock(BizApprovalMapper.class);
    private final BizApprovalNodeMapper nodeMapper = mock(BizApprovalNodeMapper.class);
    private final BizApprovalConfigMapper configMapper = mock(BizApprovalConfigMapper.class);
    private final BizApprovalFlowMapper flowMapper = mock(BizApprovalFlowMapper.class);
    private final BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
    private final BizSeaPoolMapper seaPoolMapper = mock(BizSeaPoolMapper.class);
    private final CustomerAccessService accessService = mock(CustomerAccessService.class);
    private final com.aigicrm.module.approval.service.ApprovalConfigService approvalConfigService =
            mock(com.aigicrm.module.approval.service.ApprovalConfigService.class);
    private final SeaConfigService seaConfigService = mock(SeaConfigService.class);

    private ApprovalService svc;

    /** 通过侧夹具: 有配置路径 + 至少一名有效审批人 */
    private void stubApprovable() {
        when(configMapper.selectCount(any())).thenReturn(1L);
        when(approvalConfigService.createNodes(any(), any())).thenReturn(java.util.List.of(7L));
    }

    private void buildService() {
        svc = new ApprovalService(
                configMapper, approvalMapper, nodeMapper, flowMapper,
                customerMapper,
                mock(SysUserMapper.class), mock(SysUserRoleMapper.class), mock(SysDeptMapper.class),
                seaPoolMapper, mock(SeaService.class), mock(OrgScopeResolver.class), seaConfigService,
                accessService,
                mock(com.aigicrm.module.system.service.SysUserService.class),
                mock(com.aigicrm.module.customer.service.CustomerService.class),
                mock(com.aigicrm.module.customer.service.CustomerEventService.class),
                approvalConfigService,
                mock(com.aigicrm.module.system.service.SysConfigService.class),
                mock(org.springframework.jdbc.core.JdbcTemplate.class),
                mock(com.aigicrm.security.SecurityAuditService.class));
    }

    private void asUser(long userId) {
        // SecurityUtils.getCurrentUserId 由 MockedStatic 在各用例内提供
    }

    @Test
    void extendSubmit_withoutViewPermission_rejected() {
        buildService();
        // 客户在私海(非 sea)——旧守卫通过; 发起人对该客户无查看权限
        BizCustomer customer = new BizCustomer();
        customer.setId(501L);
        customer.setStatus("normal");
        customer.setOwnerId(999L); // 非发起人
        when(customerMapper.selectById(501L)).thenReturn(customer);
        when(accessService.canView(501L)).thenReturn(false);

        try (var sec = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::getCurrentUserId).thenReturn(7L);
            sec.when(SecurityUtils::isAdmin).thenReturn(false);

            assertThrows(BusinessException.class,
                    () -> svc.submit("extend", 501L, null, null),
                    "无查看权限的发起人不得提交延期审批");
        }
        verify(approvalMapper, never()).insert(any(BizApproval.class));
    }

    @Test
    void extendSubmit_ownerOrViewAllowed_passes() {
        buildService();
        BizCustomer customer = new BizCustomer();
        customer.setId(502L);
        customer.setStatus("normal");
        customer.setOwnerId(7L); // 发起人 = 负责人
        when(customerMapper.selectById(502L)).thenReturn(customer);
        when(accessService.canView(502L)).thenReturn(true);
        stubApprovable();

        try (var sec = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::getCurrentUserId).thenReturn(7L);
            sec.when(SecurityUtils::isAdmin).thenReturn(false);
            sec.when(SecurityUtils::getCurrentDataScope).thenReturn("self");

            svc.submit("extend", 502L, null, null);
        }
        verify(approvalMapper).insert(any(BizApproval.class));
    }

    @Test
    void extendSubmit_adminBypassesViewCheck() {
        buildService();
        BizCustomer customer = new BizCustomer();
        customer.setId(503L);
        customer.setStatus("normal");
        customer.setOwnerId(888L); // 非发起人
        when(customerMapper.selectById(503L)).thenReturn(customer);
        when(accessService.canView(503L)).thenReturn(false); // admin 不经 canView
        stubApprovable();

        try (var sec = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            sec.when(SecurityUtils::isAdmin).thenReturn(true);
            sec.when(SecurityUtils::getCurrentDataScope).thenReturn("all");

            svc.submit("extend", 503L, null, null);
        }
        verify(approvalMapper).insert(any(BizApproval.class));
        // admin 快速通道: canView 不应被调用(不依赖 accessService 判定)
        verify(accessService, never()).canView(anyLong());
    }

    @Test
    void extendSubmit_customerNotFound_rejected() {
        buildService();
        when(customerMapper.selectById(999L)).thenReturn(null);

        try (var sec = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::getCurrentUserId).thenReturn(7L);
            sec.when(SecurityUtils::isAdmin).thenReturn(false);

            assertThrows(BusinessException.class,
                    () -> svc.submit("extend", 999L, null, null),
                    "客户不存在应拒绝(不创建审批单)");
        }
        verify(approvalMapper, never()).insert(any(BizApproval.class));
    }
}
