package com.aigicrm.module.approval.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.common.engine.CustomerAccessService;
import com.aigicrm.common.scope.OrgScopeResolver;
import com.aigicrm.module.approval.entity.BizApproval;
import com.aigicrm.module.approval.mapper.BizApprovalConfigMapper;
import com.aigicrm.module.approval.mapper.BizApprovalFlowMapper;
import com.aigicrm.module.approval.mapper.BizApprovalMapper;
import com.aigicrm.module.approval.mapper.BizApprovalNodeMapper;
import com.aigicrm.module.customer.entity.BizCustomer;
import com.aigicrm.module.customer.mapper.BizCustomerMapper;
import com.aigicrm.module.sea.mapper.BizSeaPoolMapper;
import com.aigicrm.module.sea.service.SeaConfigService;
import com.aigicrm.module.sea.service.SeaService;
import com.aigicrm.module.system.entity.SysUser;
import com.aigicrm.module.system.mapper.SysDeptMapper;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.module.system.mapper.SysUserRoleMapper;
import com.aigicrm.module.system.service.SysConfigService;
import com.aigicrm.module.system.service.SysUserService;
import com.aigicrm.security.SecurityAuditService;
import com.aigicrm.security.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 【P0 2026-10-17 体检修复】transfer 提交侧归属校验回归钉。
 * 旧实现只拦公海客户并校验接收人；任何持审批权限的用户可对无查看权限的客户发起转移，
 * 审批人误批即生效。修复：与 extend 同口径，非 admin 发起人必须对客户有查看权限。
 */
class TransferSubmitOwnershipTest {

    private final BizApprovalMapper approvalMapper = mock(BizApprovalMapper.class);
    private final BizApprovalNodeMapper nodeMapper = mock(BizApprovalNodeMapper.class);
    private final BizApprovalConfigMapper configMapper = mock(BizApprovalConfigMapper.class);
    private final BizApprovalFlowMapper flowMapper = mock(BizApprovalFlowMapper.class);
    private final BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
    private final BizSeaPoolMapper seaPoolMapper = mock(BizSeaPoolMapper.class);
    private final CustomerAccessService accessService = mock(CustomerAccessService.class);
    private final ApprovalConfigService approvalConfigService = mock(ApprovalConfigService.class);
    private final SysUserMapper userMapper = mock(SysUserMapper.class);

    private ApprovalService buildService() {
        return new ApprovalService(
                configMapper, approvalMapper, nodeMapper, flowMapper,
                customerMapper, userMapper, mock(SysUserRoleMapper.class), mock(SysDeptMapper.class),
                seaPoolMapper, mock(SeaService.class), mock(OrgScopeResolver.class), mock(SeaConfigService.class),
                accessService, mock(SysUserService.class), approvalConfigService,
                mock(SysConfigService.class), mock(JdbcTemplate.class), mock(SecurityAuditService.class));
    }

    private BizCustomer customer(long id) {
        BizCustomer customer = new BizCustomer();
        customer.setId(id);
        customer.setStatus("normal");
        customer.setOwnerId(999L); // 非发起人
        return customer;
    }

    private SysUser validReceiver(long id) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername("receiver");
        user.setStatus(1);
        user.setDeleted(0);
        return user;
    }

    private void stubApprovable() {
        when(configMapper.selectCount(any())).thenReturn(1L);
        when(approvalConfigService.createNodes(any(), anyString())).thenReturn(java.util.List.of(7L));
    }

    @Test
    void transferSubmit_withoutViewPermission_rejected() {
        when(customerMapper.selectById(601L)).thenReturn(customer(601L));
        when(accessService.canView(601L)).thenReturn(false);
        // 接收人有效, 才能走到归属校验(归属校验在接收人校验之后)
        when(userMapper.selectById(9L)).thenReturn(validReceiver(9L));
        ApprovalService svc = buildService();

        try (var sec = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::getCurrentUserId).thenReturn(7L);
            sec.when(SecurityUtils::isAdmin).thenReturn(false);

            assertThrows(BusinessException.class,
                    () -> svc.submit("transfer", 601L, null, "{\"newOwnerId\":9}"),
                    "无查看权限的发起人不得提交转移审批");
        }
        verify(approvalMapper, never()).insert(any(BizApproval.class));
        verify(accessService).canView(601L);
    }

    @Test
    void transferSubmit_withViewPermission_passes() {
        when(customerMapper.selectById(602L)).thenReturn(customer(602L));
        when(accessService.canView(602L)).thenReturn(true);
        when(userMapper.selectById(9L)).thenReturn(validReceiver(9L));
        stubApprovable();
        ApprovalService svc = buildService();

        try (var sec = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::getCurrentUserId).thenReturn(7L);
            sec.when(SecurityUtils::isAdmin).thenReturn(false);
            sec.when(SecurityUtils::getCurrentDataScope).thenReturn("self");

            svc.submit("transfer", 602L, null, "{\"newOwnerId\":9}");
        }
        verify(accessService).canView(602L);
        verify(approvalMapper).insert(any(BizApproval.class));
    }

    @Test
    void transferSubmit_customerNotFound_rejected() {
        when(customerMapper.selectById(603L)).thenReturn(null);
        ApprovalService svc = buildService();

        try (var sec = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::getCurrentUserId).thenReturn(7L);
            sec.when(SecurityUtils::isAdmin).thenReturn(false);

            assertThrows(BusinessException.class,
                    () -> svc.submit("transfer", 603L, null, "{\"newOwnerId\":9}"),
                    "客户不存在应拒绝");
        }
        verify(accessService, never()).canView(anyLong());
        verify(approvalMapper, never()).insert(any(BizApproval.class));
    }
}
