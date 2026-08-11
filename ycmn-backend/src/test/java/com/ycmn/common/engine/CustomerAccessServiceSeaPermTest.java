package com.ycmn.common.engine;

import com.ycmn.common.scope.OrgScopeResolver;
import com.ycmn.module.customer.entity.BizCustomer;
import com.ycmn.module.customer.mapper.BizCustomerMapper;
import com.ycmn.module.customer.mapper.BizCustomerShareMapper;
import com.ycmn.module.sea.entity.BizSeaPool;
import com.ycmn.module.sea.mapper.BizSeaPoolMapper;
import com.ycmn.module.sea.service.SeaConfigService;
import com.ycmn.security.SecurityUser;
import com.ycmn.security.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * 【2026-08-10 公海详情可见性 + 池级编辑】CustomerAccessService 权限判定测试。
 * 覆盖两处修复:
 *   A. canView 池分支补 PERM_VIEW —— 池"可见"用户即可读详情(与列表可见一致)
 *   B. canEdit 非池路径并入 customer:edit 能力检查 —— 池编辑者缺该角色码仍可编辑, 普通客户语义不变
 */
class CustomerAccessServiceSeaPermTest {

    private final BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
    private final BizCustomerShareMapper shareMapper = mock(BizCustomerShareMapper.class);
    private final OrgScopeResolver scopeResolver = mock(OrgScopeResolver.class);
    private final BizSeaPoolMapper seaPoolMapper = mock(BizSeaPoolMapper.class);
    private final SeaConfigService seaConfigService = mock(SeaConfigService.class);
    private final com.ycmn.module.system.service.RolePermScopeService rolePermScopeService =
            mock(com.ycmn.module.system.service.RolePermScopeService.class);
    private final CustomerAccessService svc = new CustomerAccessService(
            customerMapper, shareMapper,
            scopeResolver, seaPoolMapper, seaConfigService, rolePermScopeService);

    /** 默认范围桩: 编辑/删除等未配置 → self; resolveScope 空集 → 非本人不在范围内 */
    private void stubDefaultScope() {
        when(rolePermScopeService.effectiveScopes(any(), any(), any())).thenReturn(List.of("self"));
        when(scopeResolver.resolveScope(any(), any())).thenReturn(List.of());
    }

    /** 当前用户: id=100, 无集团/法人归属, 组织可见范围为空(自见范围) */
    private SecurityUser viewer() {
        SecurityUser u = new SecurityUser();
        u.setUserId(100L);
        u.setDeptId(30L);
        u.setDataScope("self");
        return u;
    }

    private BizCustomer customer(Long ownerId) {
        BizCustomer c = new BizCustomer();
        c.setId(1L);
        c.setOwnerId(ownerId);
        c.setGroupId(null);
        c.setLegalEntityId(null);
        return c;
    }

    private BizSeaPool pool() {
        BizSeaPool p = new BizSeaPool();
        p.setId(1L);
        p.setPoolId(10L);
        p.setStatus("available");
        return p;
    }

    // ==================== canView: 池"可见"即可读详情 ====================

    @Test
    void canView_poolCustomerViewerPerm_true() {
        BizCustomer cust = customer(200L); // 原负责人是别人, 释放未清 ownerId
        BizSeaPool pool = pool();
        try (MockedStatic<SecurityUtils> ms = mockStatic(SecurityUtils.class)) {
            ms.when(SecurityUtils::isAdmin).thenReturn(false);
            ms.when(SecurityUtils::getCurrentUserId).thenReturn(100L);
            ms.when(SecurityUtils::getCurrentUser).thenReturn(viewer());
            ms.when(SecurityUtils::getCurrentGroupIds).thenReturn(List.of());
            ms.when(SecurityUtils::getCurrentLegalEntityIds).thenReturn(List.of());
            when(customerMapper.selectById(1L)).thenReturn(cust);
            when(shareMapper.selectCount(any())).thenReturn(0L);
            when(scopeResolver.resolve(any())).thenReturn(List.of()); // 组织范围不含原负责人
            stubDefaultScope();
            when(seaPoolMapper.selectOne(any())).thenReturn(pool);
            when(seaConfigService.hasPoolPerm(10L, SeaConfigService.PERM_VIEW, viewer())).thenReturn(true);

            assertTrue(svc.canView(1L));
        }
    }

    @Test
    void canView_poolCustomerNoPoolPerm_false() {
        BizCustomer cust = customer(200L);
        BizSeaPool pool = pool();
        try (MockedStatic<SecurityUtils> ms = mockStatic(SecurityUtils.class)) {
            ms.when(SecurityUtils::isAdmin).thenReturn(false);
            ms.when(SecurityUtils::getCurrentUserId).thenReturn(100L);
            ms.when(SecurityUtils::getCurrentUser).thenReturn(viewer());
            ms.when(SecurityUtils::getCurrentGroupIds).thenReturn(List.of());
            ms.when(SecurityUtils::getCurrentLegalEntityIds).thenReturn(List.of());
            when(customerMapper.selectById(1L)).thenReturn(cust);
            when(shareMapper.selectCount(any())).thenReturn(0L);
            when(scopeResolver.resolve(any())).thenReturn(List.of());
            stubDefaultScope();
            when(seaPoolMapper.selectOne(any())).thenReturn(pool);
            // 四种池权限均未命中

            assertFalse(svc.canView(1L));
        }
    }

    // ==================== canEdit: 池编辑者不受 customer:edit 角色码限制 ====================

    @Test
    void canEdit_poolEditorWithoutCustomerEditRole_true() {
        BizCustomer cust = customer(200L);
        BizSeaPool pool = pool();
        try (MockedStatic<SecurityUtils> ms = mockStatic(SecurityUtils.class)) {
            ms.when(SecurityUtils::isAdmin).thenReturn(false);
            ms.when(SecurityUtils::getCurrentUserId).thenReturn(100L);
            ms.when(SecurityUtils::getCurrentUser).thenReturn(viewer());
            when(customerMapper.selectById(1L)).thenReturn(cust);
            when(seaPoolMapper.selectOne(any())).thenReturn(pool);
            // 池编辑权限命中, 即使没有 customer:edit 角色码
            ms.when(() -> SecurityUtils.hasAuthority("customer:edit")).thenReturn(false);
            when(seaConfigService.hasPoolPerm(10L, SeaConfigService.PERM_EDIT, viewer())).thenReturn(true);

            assertTrue(svc.canEdit(1L));
        }
    }

    // ==================== canEdit: 非池客户语义不变 ====================

    @Test
    void canEdit_regularOwnerWithRole_true() {
        BizCustomer cust = customer(100L); // 自己是负责人
        try (MockedStatic<SecurityUtils> ms = mockStatic(SecurityUtils.class)) {
            ms.when(SecurityUtils::isAdmin).thenReturn(false);
            ms.when(SecurityUtils::getCurrentUserId).thenReturn(100L);
            ms.when(SecurityUtils::getCurrentUser).thenAnswer(inv -> { var u = new com.ycmn.security.SecurityUser(); u.setUserId(100L); return u; });
            ms.when(() -> SecurityUtils.hasAuthority("customer:edit")).thenReturn(true);
            when(customerMapper.selectById(1L)).thenReturn(cust);
            when(seaPoolMapper.selectOne(any())).thenReturn(null); // 不在公海池
            when(scopeResolver.canAccess(any(com.ycmn.common.scope.ScopeRequest.class))).thenReturn(true);

            assertTrue(svc.canEdit(1L));
        }
    }

    @Test
    void canEdit_regularOwnerWithoutRole_false() {
        BizCustomer cust = customer(100L);
        try (MockedStatic<SecurityUtils> ms = mockStatic(SecurityUtils.class)) {
            ms.when(SecurityUtils::isAdmin).thenReturn(false);
            ms.when(SecurityUtils::getCurrentUserId).thenReturn(100L);
            ms.when(() -> SecurityUtils.hasAuthority("customer:edit")).thenReturn(false);
            when(customerMapper.selectById(1L)).thenReturn(cust);
            when(seaPoolMapper.selectOne(any())).thenReturn(null);

            assertFalse(svc.canEdit(1L));
        }
    }

    @Test
    void canEdit_regularNonOwner_false() {
        BizCustomer cust = customer(200L); // 非负责人
        try (MockedStatic<SecurityUtils> ms = mockStatic(SecurityUtils.class)) {
            ms.when(SecurityUtils::isAdmin).thenReturn(false);
            ms.when(SecurityUtils::getCurrentUserId).thenReturn(100L);
            ms.when(SecurityUtils::getCurrentUser).thenAnswer(inv -> { var u = new com.ycmn.security.SecurityUser(); u.setUserId(100L); return u; });
            ms.when(() -> SecurityUtils.hasAuthority("customer:edit")).thenReturn(true);
            when(customerMapper.selectById(1L)).thenReturn(cust);
            when(seaPoolMapper.selectOne(any())).thenReturn(null);
            stubDefaultScope();

            assertFalse(svc.canEdit(1L));
        }
    }

    // ==================== canDelete: 池内客户按池删除权限判定 ====================

    @Test
    void canDelete_poolCustomerWithDeletePerm_true() {
        BizCustomer cust = customer(200L);
        BizSeaPool pool = pool();
        try (MockedStatic<SecurityUtils> ms = mockStatic(SecurityUtils.class)) {
            ms.when(SecurityUtils::isAdmin).thenReturn(false);
            ms.when(SecurityUtils::getCurrentUser).thenReturn(viewer());
            ms.when(SecurityUtils::getCurrentUserId).thenReturn(100L);
            when(customerMapper.selectById(1L)).thenReturn(cust);
            when(seaPoolMapper.selectOne(any())).thenReturn(pool);
            when(seaConfigService.hasPoolPerm(10L, SeaConfigService.PERM_DELETE, viewer())).thenReturn(true);

            assertTrue(svc.canDelete(1L));
        }
    }

    @Test
    void canDelete_poolCustomerOriginalOwnerNoDeletePerm_false() {
        // 关键修复: 原负责人(释放未清 ownerId, 是客户 owner 但无池删除权限)不得经客户模块删除公海客户
        BizCustomer cust = customer(200L);
        BizSeaPool pool = pool();
        try (MockedStatic<SecurityUtils> ms = mockStatic(SecurityUtils.class)) {
            ms.when(SecurityUtils::isAdmin).thenReturn(false);
            ms.when(SecurityUtils::getCurrentUser).thenReturn(viewer());
            ms.when(SecurityUtils::getCurrentUserId).thenReturn(200L); // 当前用户=原负责人
            when(customerMapper.selectById(1L)).thenReturn(cust);
            when(seaPoolMapper.selectOne(any())).thenReturn(pool);
            // 原负责人对池无 PERM_DELETE

            assertFalse(svc.canDelete(1L), "池内客户删除必须按池删除权限, 原负责人 owner 判断不得放行");
        }
    }

    @Test
    void canDelete_nonPoolOwner_true() {
        BizCustomer cust = customer(100L); // 自己是负责人
        try (MockedStatic<SecurityUtils> ms = mockStatic(SecurityUtils.class)) {
            ms.when(SecurityUtils::isAdmin).thenReturn(false);
            ms.when(SecurityUtils::getCurrentUserId).thenReturn(100L);
            ms.when(SecurityUtils::getCurrentUser).thenAnswer(inv -> { var u = new com.ycmn.security.SecurityUser(); u.setUserId(100L); return u; });
            when(customerMapper.selectById(1L)).thenReturn(cust);
            when(seaPoolMapper.selectOne(any())).thenReturn(null);
            when(scopeResolver.canAccess(any(com.ycmn.common.scope.ScopeRequest.class))).thenReturn(true);

            assertTrue(svc.canDelete(1L));
        }
    }

    @Test
    void canDelete_nonPoolNonOwner_false() {
        BizCustomer cust = customer(200L);
        try (MockedStatic<SecurityUtils> ms = mockStatic(SecurityUtils.class)) {
            ms.when(SecurityUtils::isAdmin).thenReturn(false);
            ms.when(SecurityUtils::getCurrentUserId).thenReturn(100L);
            when(customerMapper.selectById(1L)).thenReturn(cust);
            when(seaPoolMapper.selectOne(any())).thenReturn(null);
            stubDefaultScope();

            assertFalse(svc.canDelete(1L));
        }
    }
}
