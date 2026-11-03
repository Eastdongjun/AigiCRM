package com.aigicrm.common.engine;

import com.aigicrm.common.BusinessException;
import com.aigicrm.common.scope.OrgScopeResolver;
import com.aigicrm.module.customer.entity.BizCustomer;
import com.aigicrm.module.customer.mapper.BizCustomerMapper;
import com.aigicrm.module.customer.mapper.BizCustomerShareMapper;
import com.aigicrm.module.sea.mapper.BizSeaPoolMapper;
import com.aigicrm.module.sea.service.SeaConfigService;
import com.aigicrm.module.system.service.RolePermScopeService;
import com.aigicrm.security.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 【审查 backend-4 2026-10-18】finance:manage 财务代录的客户对象校验：
 * 代录仍保留权限码放行，但客户必须存在、未删除、且非 admin 不得跨集团。
 */
class CustomerFinanceGuardTest {

    private final BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
    private final CustomerAccessService svc = new CustomerAccessService(
            customerMapper, mock(BizCustomerShareMapper.class), mock(OrgScopeResolver.class),
            mock(BizSeaPoolMapper.class), mock(SeaConfigService.class),
            mock(RolePermScopeService.class));

    private static BizCustomer customer(long id) {
        BizCustomer c = new BizCustomer();
        c.setId(id);
        return c;
    }

    @Test
    void financeManage_sameGroupWritableCustomer_passes() {
        when(customerMapper.selectTenantIdById(1L)).thenReturn(1L);
        when(customerMapper.selectById(1L)).thenReturn(customer(1L));

        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::isAdmin).thenReturn(false);
            sec.when(SecurityUtils::getCurrentGroupIds).thenReturn(List.of(1L));
            sec.when(() -> SecurityUtils.hasAuthority("finance:manage")).thenReturn(true);

            assertDoesNotThrow(() -> svc.assertCanManageFinance(1L));
        }
    }

    @Test
    void financeManage_crossGroupCustomer_rejected403() {
        when(customerMapper.selectTenantIdById(1L)).thenReturn(2L);

        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::isAdmin).thenReturn(false);
            sec.when(SecurityUtils::getCurrentGroupIds).thenReturn(List.of(1L));
            sec.when(() -> SecurityUtils.hasAuthority("finance:manage")).thenReturn(true);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> svc.assertCanManageFinance(1L));
            assertEquals(403, ex.getCode());
            assertTrue(ex.getMessage().contains("其他集团"), "实际: " + ex.getMessage());
        }
        verify(customerMapper, never()).selectById(1L);
    }

    @Test
    void financeManage_deletedOrMissingCustomer_rejected() {
        when(customerMapper.selectTenantIdById(1L)).thenReturn(null);
        when(customerMapper.selectById(1L)).thenReturn(null);

        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::isAdmin).thenReturn(false);
            sec.when(SecurityUtils::getCurrentGroupIds).thenReturn(List.of(1L));
            sec.when(() -> SecurityUtils.hasAuthority("finance:manage")).thenReturn(true);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> svc.assertCanManageFinance(1L));
            assertTrue(ex.getMessage().contains("不存在或已删除"), "实际: " + ex.getMessage());
        }
    }

    @Test
    void admin_canManageAcrossGroups() {
        when(customerMapper.selectById(1L)).thenReturn(customer(1L));

        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::isAdmin).thenReturn(true);
            sec.when(() -> SecurityUtils.hasAuthority("finance:manage")).thenReturn(true);

            assertDoesNotThrow(() -> svc.assertCanManageFinance(1L));
        }
        // admin 跨集团: 不查询 tenant_id
        verify(customerMapper, never()).selectTenantIdById(any());
    }

    @Test
    void noFinanceManage_fallsBackToCanEdit() {
        when(customerMapper.selectTenantIdById(1L)).thenReturn(1L);
        when(customerMapper.selectById(1L)).thenReturn(customer(1L));

        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::isAdmin).thenReturn(false);
            sec.when(SecurityUtils::getCurrentGroupIds).thenReturn(List.of(1L));
            sec.when(() -> SecurityUtils.hasAuthority("finance:manage")).thenReturn(false);
            sec.when(() -> SecurityUtils.hasAuthority("customer:edit")).thenReturn(true);

            // 统一层判定(scopeResolver)默认拒绝
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> svc.assertCanManageFinance(1L));
            assertTrue(ex.getMessage().contains("无权编辑该客户"), "实际: " + ex.getMessage());
        }
    }

    /** 【2026-11-03 拆码】仅持 customer:finance:edit(无 manage/无 customer:edit): 走独立 finance_edit 范围通道 */
    @Test
    void financeEditCode_inScope_passesWithoutCustomerEdit() {
        when(customerMapper.selectTenantIdById(1L)).thenReturn(1L);
        when(customerMapper.selectById(1L)).thenReturn(customer(1L));
        OrgScopeResolver scopeResolver = mock(OrgScopeResolver.class);
        when(scopeResolver.canAccess(any())).thenReturn(true);
        CustomerAccessService svc2 = new CustomerAccessService(customerMapper, mock(BizCustomerShareMapper.class),
                scopeResolver, mock(BizSeaPoolMapper.class), mock(SeaConfigService.class), mock(RolePermScopeService.class));

        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::isAdmin).thenReturn(false);
            sec.when(SecurityUtils::getCurrentGroupIds).thenReturn(List.of(1L));
            com.aigicrm.security.SecurityUser su = new com.aigicrm.security.SecurityUser();
            su.setUserId(7L);
            sec.when(SecurityUtils::getCurrentUser).thenReturn(su);
            sec.when(() -> SecurityUtils.hasAuthority("finance:manage")).thenReturn(false);
            sec.when(() -> SecurityUtils.hasAuthority("customer:finance:edit")).thenReturn(true);
            sec.when(() -> SecurityUtils.hasAuthority("customer:edit")).thenReturn(false);

            assertDoesNotThrow(() -> svc2.assertCanManageFinance(1L));
            // 判定走的是 customer×finance_edit(独立范围 action), 不是 edit
            verify(scopeResolver).canAccess(org.mockito.ArgumentMatchers.argThat(
                    req -> req != null && "finance_edit".equals(req.getAction())));
        }
    }

    /** 【2026-11-03 拆码】持码但目标在 finance_edit 范围外 → 落回旧通道, 无 customer:edit → 拒 */
    @Test
    void financeEditCode_outOfScope_fallsBackAndRejected() {
        when(customerMapper.selectTenantIdById(1L)).thenReturn(1L);
        when(customerMapper.selectById(1L)).thenReturn(customer(1L));

        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::isAdmin).thenReturn(false);
            sec.when(SecurityUtils::getCurrentGroupIds).thenReturn(List.of(1L));
            sec.when(() -> SecurityUtils.hasAuthority("finance:manage")).thenReturn(false);
            sec.when(() -> SecurityUtils.hasAuthority("customer:finance:edit")).thenReturn(true);
            sec.when(() -> SecurityUtils.hasAuthority("customer:edit")).thenReturn(false);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> svc.assertCanManageFinance(1L));
            assertTrue(ex.getMessage().contains("无权编辑该客户"), "实际: " + ex.getMessage());
        }
    }
}
