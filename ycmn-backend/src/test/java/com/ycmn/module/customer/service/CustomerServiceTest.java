package com.ycmn.module.customer.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisMapperBuilderAssistant;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ycmn.common.BusinessException;
import com.ycmn.common.engine.CustomerAccessService;
import com.ycmn.common.scope.OrgScopeResolver;
import com.ycmn.module.customer.entity.BizContact;
import com.ycmn.module.customer.entity.BizCustomer;
import com.ycmn.module.customer.entity.BizInvoice;
import com.ycmn.module.customer.entity.BizPayment;
import com.ycmn.module.customer.mapper.BizCustomerMapper;
import com.ycmn.module.customer.mapper.BizCustomerIndicatorMapper;
import com.ycmn.module.customer.mapper.BizCustomerLogMapper;
import com.ycmn.module.customer.mapper.BizCustomerShareMapper;
import com.ycmn.module.customer.mapper.BizContactMapper;
import com.ycmn.module.customer.mapper.BizInvoiceMapper;
import com.ycmn.module.customer.mapper.BizPaymentMapper;
import com.ycmn.module.reminder.mapper.BizReminderMapper;
import com.ycmn.module.system.entity.SysUser;
import com.ycmn.module.system.mapper.SysDeptMapper;
import com.ycmn.module.system.mapper.SysUserMapper;
import com.ycmn.module.system.service.SysConfigService;
import com.ycmn.security.SecurityUser;
import com.ycmn.security.SecurityUtils;
import org.junit.jupiter.api.BeforeAll;import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;import static org.mockito.Mockito.when;

class CustomerServiceTest {

    /** 列表达式：col IN (?,…) 或 col = ? 等（占位符为 #{ew.paramNameValuePairs.MPGENVALn} 命名引用） */
    private static final Pattern COL_EXPR = Pattern.compile(
            "([a-zA-Z_][a-zA-Z0-9_]*)\\s*((?:IN\\s*\\([^)]*\\))|(?:=|<>|!=|>|<|>=|<=)\\s*[^)\\s]+)");
    private static final Pattern REF_EXPR = Pattern.compile("#\\{ew\\.paramNameValuePairs\\.([^}]+)}");

    @BeforeAll
    static void initTableInfo() {
        // LambdaQueryWrapper.getSqlSegment() 需要实体 TableInfo（纯单测无 MyBatis 容器）
        MybatisMapperBuilderAssistant assistant =
                new MybatisMapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, BizCustomer.class);
    }

    /** 解析 wrapper 条件为 列名→值列表（IN 展开为多值，eq 为单值） */
    private Map<String, List<Long>> columnValues(LambdaQueryWrapper<?> w) {
        Map<String, List<Long>> out = new HashMap<>();
        String sql = w.getSqlSegment();
        if (sql == null || sql.isBlank()) return out;
        Map<String, Object> params = w.getParamNameValuePairs();
        Matcher colM = COL_EXPR.matcher(sql);
        while (colM.find()) {
            String col = colM.group(1).toLowerCase(Locale.ROOT);
            List<Long> colVals = new ArrayList<>();
            Matcher refM = REF_EXPR.matcher(colM.group(2));
            while (refM.find()) {
                Object v = params.get(refM.group(1));
                if (v instanceof Number num) colVals.add(num.longValue());
            }
            out.put(col, colVals);
        }
        return out;
    }

    private CustomerService newService(BizInvoiceMapper invoiceMapper, BizPaymentMapper paymentMapper,
                                       BizCustomerMapper customerMapper, OrgScopeResolver resolver) {
        return newService(invoiceMapper, paymentMapper, customerMapper, resolver,
                mock(BizContactMapper.class), mock(CustomerAccessService.class));
    }

    private CustomerService newService(BizInvoiceMapper invoiceMapper, BizPaymentMapper paymentMapper,
                                       BizCustomerMapper customerMapper, OrgScopeResolver resolver,
                                       BizContactMapper contactMapper, CustomerAccessService accessService) {
        return new CustomerService(
                customerMapper,
                mock(BizCustomerLogMapper.class),
                invoiceMapper,
                mock(BizCustomerShareMapper.class),
                mock(BizCustomerIndicatorMapper.class),
                contactMapper,
                paymentMapper,
                mock(SysUserMapper.class),
                mock(SysDeptMapper.class),
                mock(BizReminderMapper.class),
                mock(JdbcTemplate.class),
                mock(SysConfigService.class),
                resolver,
                accessService,
                mock(com.ycmn.module.system.service.RolePermScopeService.class));
    }

    @Test
    void deleteInvoice_rejectsInvoiceOfAnotherCustomer() {
        BizInvoiceMapper invoiceMapper = mock(BizInvoiceMapper.class);
        BizInvoice invoice = new BizInvoice();
        invoice.setId(1L);
        invoice.setCustomerId(100L);
        when(invoiceMapper.selectById(1L)).thenReturn(invoice);

        CustomerService svc = newService(invoiceMapper, mock(BizPaymentMapper.class), mock(BizCustomerMapper.class),
                mock(OrgScopeResolver.class));
        // 路径客户 200 ≠ 记录客户 100 → 拒绝，防跨客户删除（IDOR）
        assertThrows(BusinessException.class, () -> svc.deleteInvoice(200L, 1L));
    }

    @Test
    void deleteInvoice_missingInvoice_rejects() {
        BizInvoiceMapper invoiceMapper = mock(BizInvoiceMapper.class);
        when(invoiceMapper.selectById(1L)).thenReturn(null);
        CustomerService svc = newService(invoiceMapper, mock(BizPaymentMapper.class), mock(BizCustomerMapper.class),
                mock(OrgScopeResolver.class));
        assertThrows(BusinessException.class, () -> svc.deleteInvoice(200L, 1L));
    }

    @Test
    void deletePayment_rejectsPaymentOfAnotherCustomer() {
        BizPaymentMapper paymentMapper = mock(BizPaymentMapper.class);
        BizPayment payment = new BizPayment();
        payment.setId(2L);
        payment.setCustomerId(100L);
        when(paymentMapper.selectById(2L)).thenReturn(payment);

        CustomerService svc = newService(mock(BizInvoiceMapper.class), paymentMapper, mock(BizCustomerMapper.class),
                mock(OrgScopeResolver.class));
        assertThrows(BusinessException.class, () -> svc.deletePayment(200L, 2L));
    }

    // ===== 联系人 IDOR（2026-08-10 安全修复: 与 deleteInvoice 同款归属校验） =====

    @Test
    void deleteContact_contactOfAnotherCustomer_rejects() {
        BizContactMapper contactMapper = mock(BizContactMapper.class);
        BizContact contact = new BizContact();
        contact.setId(1L);
        contact.setCustomerId(100L);
        when(contactMapper.selectById(1L)).thenReturn(contact);

        CustomerService svc = newService(mock(BizInvoiceMapper.class), mock(BizPaymentMapper.class),
                mock(BizCustomerMapper.class), mock(OrgScopeResolver.class), contactMapper, mock(CustomerAccessService.class));
        // 路径客户 200 ≠ 联系人客户 100 → 拒绝，防跨客户删除（IDOR）
        assertThrows(BusinessException.class, () -> svc.deleteContact(200L, 1L));
    }

    @Test
    void deleteContact_missingContact_rejects() {
        BizContactMapper contactMapper = mock(BizContactMapper.class);
        when(contactMapper.selectById(1L)).thenReturn(null);
        CustomerService svc = newService(mock(BizInvoiceMapper.class), mock(BizPaymentMapper.class),
                mock(BizCustomerMapper.class), mock(OrgScopeResolver.class), contactMapper, mock(CustomerAccessService.class));
        assertThrows(BusinessException.class, () -> svc.deleteContact(200L, 1L));
    }

    @Test
    void updateContact_contactOfAnotherCustomer_rejects() {
        BizContactMapper contactMapper = mock(BizContactMapper.class);
        BizContact contact = new BizContact();
        contact.setId(1L);
        contact.setCustomerId(100L);
        when(contactMapper.selectById(1L)).thenReturn(contact);

        CustomerService svc = newService(mock(BizInvoiceMapper.class), mock(BizPaymentMapper.class),
                mock(BizCustomerMapper.class), mock(OrgScopeResolver.class), contactMapper, mock(CustomerAccessService.class));
        BizContact req = new BizContact();
        // 路径客户 200 ≠ 联系人客户 100 → 拒绝，防跨客户改挂（IDOR）
        assertThrows(BusinessException.class, () -> svc.updateContact(200L, 1L, req));
    }

    // ===== 公海池内 update 字段保护（2026-08-10 安全修复: 禁止经编辑接口改属主/状态） =====

    @Test
    void update_poolCustomer_ownerIdChange_rejected() {
        BizCustomer existing = new BizCustomer();
        existing.setId(1L);
        existing.setOwnerId(200L);
        existing.setStatus("normal");
        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        when(customerMapper.selectById(1L)).thenReturn(existing);
        CustomerAccessService accessService = mock(CustomerAccessService.class);
        when(accessService.canEdit(1L)).thenReturn(true);
        when(accessService.isInSeaPool(1L)).thenReturn(true);

        CustomerService svc = newService(mock(BizInvoiceMapper.class), mock(BizPaymentMapper.class),
                customerMapper, mock(OrgScopeResolver.class), mock(BizContactMapper.class), accessService);
        BizCustomer req = new BizCustomer();
        req.setId(1L);
        req.setOwnerId(100L); // 尝试自领客户
        assertThrows(BusinessException.class, () -> svc.update(req),
                "池内客户改 ownerId 必须被拒, 防绕过捞取审批");
    }

    @Test
    void update_poolCustomer_statusChange_rejected() {
        BizCustomer existing = new BizCustomer();
        existing.setId(1L);
        existing.setOwnerId(200L);
        existing.setStatus("normal");
        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        when(customerMapper.selectById(1L)).thenReturn(existing);
        CustomerAccessService accessService = mock(CustomerAccessService.class);
        when(accessService.canEdit(1L)).thenReturn(true);
        when(accessService.isInSeaPool(1L)).thenReturn(true);

        CustomerService svc = newService(mock(BizInvoiceMapper.class), mock(BizPaymentMapper.class),
                customerMapper, mock(OrgScopeResolver.class), mock(BizContactMapper.class), accessService);
        BizCustomer req = new BizCustomer();
        req.setId(1L);
        req.setStatus("sea"); // 尝试改状态
        assertThrows(BusinessException.class, () -> svc.update(req));
    }

    @Test
    void update_poolCustomer_businessFieldChange_allowed_ownerKept() {
        BizCustomer existing = new BizCustomer();
        existing.setId(1L);
        existing.setOwnerId(200L);
        existing.setStatus("normal");
        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        when(customerMapper.selectById(1L)).thenReturn(existing);
        CustomerAccessService accessService = mock(CustomerAccessService.class);
        when(accessService.canEdit(1L)).thenReturn(true);
        when(accessService.isInSeaPool(1L)).thenReturn(true);

        CustomerService svc = newService(mock(BizInvoiceMapper.class), mock(BizPaymentMapper.class),
                customerMapper, mock(OrgScopeResolver.class), mock(BizContactMapper.class), accessService);
        BizCustomer req = new BizCustomer();
        req.setId(1L);
        req.setCustomerName("新名字"); // 仅改业务字段, ownerId/status 未显式传(null)
        svc.update(req);

        ArgumentCaptor<BizCustomer> captor = ArgumentCaptor.forClass(BizCustomer.class);
        verify(customerMapper).updateById(captor.capture());
        assertEquals(200L, captor.getValue().getOwnerId(), "池内客户 ownerId 必须以库内值为准");
        assertEquals("normal", captor.getValue().getStatus());
    }

    @Test
    void update_nonPool_ownerChange_allowed() {
        BizCustomer existing = new BizCustomer();
        existing.setId(1L);
        existing.setOwnerId(200L);
        existing.setStatus("normal");
        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        when(customerMapper.selectById(1L)).thenReturn(existing);
        CustomerAccessService accessService = mock(CustomerAccessService.class);
        when(accessService.canEdit(1L)).thenReturn(true);
        when(accessService.isInSeaPool(1L)).thenReturn(false);

        CustomerService svc = newService(mock(BizInvoiceMapper.class), mock(BizPaymentMapper.class),
                customerMapper, mock(OrgScopeResolver.class), mock(BizContactMapper.class), accessService);
        BizCustomer req = new BizCustomer();
        req.setId(1L);
        req.setOwnerId(100L); // admin 可改负责人(代建/转交)
        try (MockedStatic<SecurityUtils> ms = mockStatic(SecurityUtils.class)) {
            ms.when(SecurityUtils::isAdmin).thenReturn(true);
            svc.update(req);
        }
        verify(customerMapper).updateById(any());
    }

    // ===== M1 代建规则(2026-08-10): 非admin强制ownerId=自己, admin可指定 =====

    @Test
    void create_nonAdmin_forcesOwnerToSelf() {
        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        CustomerService svc = newService(mock(BizInvoiceMapper.class), mock(BizPaymentMapper.class),
                customerMapper, mock(OrgScopeResolver.class));
        BizCustomer req = new BizCustomer();
        req.setOwnerId(999L); // 尝试把客户挂到他人名下
        try (MockedStatic<SecurityUtils> ms = mockStatic(SecurityUtils.class)) {
            ms.when(SecurityUtils::isAdmin).thenReturn(false);
            ms.when(SecurityUtils::getCurrentUserId).thenReturn(100L);
            svc.create(req);
        }
        ArgumentCaptor<BizCustomer> captor = ArgumentCaptor.forClass(BizCustomer.class);
        verify(customerMapper).insert(captor.capture());
        assertEquals(100L, captor.getValue().getOwnerId(), "非admin创建必须强制 ownerId=自己");
    }

    @Test
    void create_admin_canSpecifyOwnerForDeputyCreation() {
        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        CustomerService svc = newService(mock(BizInvoiceMapper.class), mock(BizPaymentMapper.class),
                customerMapper, mock(OrgScopeResolver.class));
        BizCustomer req = new BizCustomer();
        req.setOwnerId(999L); // admin 代建: 指定负责人
        try (MockedStatic<SecurityUtils> ms = mockStatic(SecurityUtils.class)) {
            ms.when(SecurityUtils::isAdmin).thenReturn(true);
            svc.create(req);
        }
        ArgumentCaptor<BizCustomer> captor = ArgumentCaptor.forClass(BizCustomer.class);
        verify(customerMapper).insert(captor.capture());
        assertEquals(999L, captor.getValue().getOwnerId(), "admin 代建可指定负责人");
    }

    @Test
    void update_nonPool_nonAdminOwnerChange_rejected() {
        BizCustomer existing = new BizCustomer();
        existing.setId(1L);
        existing.setOwnerId(200L);
        existing.setStatus("normal");
        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        when(customerMapper.selectById(1L)).thenReturn(existing);
        CustomerAccessService accessService = mock(CustomerAccessService.class);
        when(accessService.canEdit(1L)).thenReturn(true);
        when(accessService.isInSeaPool(1L)).thenReturn(false);

        CustomerService svc = newService(mock(BizInvoiceMapper.class), mock(BizPaymentMapper.class),
                customerMapper, mock(OrgScopeResolver.class), mock(BizContactMapper.class), accessService);
        BizCustomer req = new BizCustomer();
        req.setId(1L);
        req.setOwnerId(100L); // 非admin 试图改负责人
        try (MockedStatic<SecurityUtils> ms = mockStatic(SecurityUtils.class)) {
            ms.when(SecurityUtils::isAdmin).thenReturn(false);
            assertThrows(BusinessException.class, () -> svc.update(req),
                    "非admin 改负责人必须走转移/审批流程");
        }
    }

    // ===== I1：新六级 code 统一路由 OrgScopeResolver（设计 §4.2） =====

    /** team 码 → resolver 可见 userIds 生成 owner_id IN 条件（修复 default 仅本人的降级） */
    /** 【批② 统一数据访问层】team 码 → 统一层 OWNER 解析生成 owner_id IN 条件 */
    @Test
    void applyDataScope_teamCodeRoutesThroughResolver() {
        SecurityUser su = new SecurityUser();
        su.setUserId(1L);
        su.setDataScope("team");
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUser).thenReturn(su);
            m.when(SecurityUtils::isAdmin).thenReturn(false);

            OrgScopeResolver resolver = mock(OrgScopeResolver.class);
            com.ycmn.common.scope.ScopeDecision d = new com.ycmn.common.scope.ScopeDecision();
            d.setVisibleUserIds(List.of(1L, 2L, 3L));
            d.setVisibleDeptIds(List.of());
            when(resolver.resolveScope(any(com.ycmn.common.scope.ScopeRequest.class))).thenReturn(d);
            CustomerService svc = newService(mock(BizInvoiceMapper.class), mock(BizPaymentMapper.class),
                    mock(BizCustomerMapper.class), resolver);

            LambdaQueryWrapper<BizCustomer> w = new LambdaQueryWrapper<>();
            ReflectionTestUtils.invokeMethod(svc, "applyDataScope", w);

            assertEquals(List.of(1L, 2L, 3L), columnValues(w).get("owner_id"),
                    "team 码必须经统一层生成 owner_id IN 条件");
        }
    }

    /** all 码（resolver null=不过滤）→ 不套 owner_id 条件 */
    @Test
    void applyDataScope_allCodeSkipsOwnerFilter() {
        SecurityUser su = new SecurityUser();
        su.setUserId(1L);
        su.setDataScope("all");
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUser).thenReturn(su);
            m.when(SecurityUtils::isAdmin).thenReturn(false);

            OrgScopeResolver resolver = mock(OrgScopeResolver.class);
            when(resolver.resolveScope(any(com.ycmn.common.scope.ScopeRequest.class)))
                    .thenReturn(com.ycmn.common.scope.ScopeDecision.all());
            CustomerService svc = newService(mock(BizInvoiceMapper.class), mock(BizPaymentMapper.class),
                    mock(BizCustomerMapper.class), resolver);

            LambdaQueryWrapper<BizCustomer> w = new LambdaQueryWrapper<>();
            ReflectionTestUtils.invokeMethod(svc, "applyDataScope", w);

            assertFalse(columnValues(w).containsKey("owner_id"), "all 不过滤 owner_id");
        }
    }

    /** 【批② 统一数据访问层】旧码 org 语义由统一层覆盖(仅本部门用户), 不再有第二套分支 */
    @Test
    void applyDataScope_legacyOrgBranchKept() {
        SecurityUser su = new SecurityUser();
        su.setUserId(1L);
        su.setDataScope("org");
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUser).thenReturn(su);
            m.when(SecurityUtils::isAdmin).thenReturn(false);

            OrgScopeResolver resolver = mock(OrgScopeResolver.class);
            com.ycmn.common.scope.ScopeDecision d = new com.ycmn.common.scope.ScopeDecision();
            d.setVisibleUserIds(List.of(1L, 9L));
            d.setVisibleDeptIds(List.of());
            when(resolver.resolveScope(any(com.ycmn.common.scope.ScopeRequest.class))).thenReturn(d);
            CustomerService svc = newService(mock(BizInvoiceMapper.class), mock(BizPaymentMapper.class),
                    mock(BizCustomerMapper.class), resolver);

            LambdaQueryWrapper<BizCustomer> w = new LambdaQueryWrapper<>();
            ReflectionTestUtils.invokeMethod(svc, "applyDataScope", w);

            assertEquals(List.of(1L, 9L), columnValues(w).get("owner_id"),
                    "org 语义(仅本部门用户)由统一层 OWNER 解析覆盖");
        }
    }

    // ===== 数据范围解析（FEAT-20260804-DICT-DEPT-READ 统计复用） =====

    private CustomerService newScopedService(SysUserMapper userMapper, SysDeptMapper deptMapper,
                                             OrgScopeResolver resolver) {
        return new CustomerService(
                mock(BizCustomerMapper.class),
                mock(BizCustomerLogMapper.class),
                mock(BizInvoiceMapper.class),
                mock(BizCustomerShareMapper.class),
                mock(BizCustomerIndicatorMapper.class),
                mock(BizContactMapper.class),
                mock(BizPaymentMapper.class),
                userMapper,
                deptMapper,
                mock(BizReminderMapper.class),
                mock(JdbcTemplate.class),
                mock(SysConfigService.class),
                resolver,
                mock(com.ycmn.common.engine.CustomerAccessService.class),
                mock(com.ycmn.module.system.service.RolePermScopeService.class));
    }

    /** 【批② 统一数据访问层】resolver 桩: 统一入口 resolveScope(ScopeRequest) 返回指定 userIds */
    private OrgScopeResolver scopedResolver(java.util.function.Function<com.ycmn.common.scope.ScopeRequest, com.ycmn.common.scope.ScopeDecision> fn) {
        OrgScopeResolver resolver = mock(OrgScopeResolver.class);
        when(resolver.resolveScope(any(com.ycmn.common.scope.ScopeRequest.class)))
                .thenAnswer(inv -> fn.apply(inv.getArgument(0)));
        return resolver;
    }

    private static com.ycmn.common.scope.ScopeDecision decisionOf(List<Long> userIds) {
        com.ycmn.common.scope.ScopeDecision d = new com.ycmn.common.scope.ScopeDecision();
        d.setVisibleUserIds(userIds);
        d.setVisibleDeptIds(List.of());
        return d;
    }

    @Test
    void getCurrentScopeUserIds_self_returnsOnlyOwnId() {
        SecurityUser su = new SecurityUser();
        su.setUserId(42L);
        su.setDataScope("self");
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getCurrentUser).thenReturn(su);
            mocked.when(SecurityUtils::isAdmin).thenReturn(false);
            CustomerService svc = newScopedService(mock(SysUserMapper.class), mock(SysDeptMapper.class),
                    scopedResolver(req -> decisionOf(List.of(42L))));
            assertEquals(List.of(42L), svc.getCurrentScopeUserIds());
        }
    }

    @Test
    void getCurrentScopeUserIds_nullScope_defaultsToSelf() {
        SecurityUser su = new SecurityUser();
        su.setUserId(42L);
        su.setDataScope(null); // 未配置 → 统一层回退 self
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getCurrentUser).thenReturn(su);
            mocked.when(SecurityUtils::isAdmin).thenReturn(false);
            CustomerService svc = newScopedService(mock(SysUserMapper.class), mock(SysDeptMapper.class),
                    scopedResolver(req -> decisionOf(List.of(42L))));
            assertEquals(List.of(42L), svc.getCurrentScopeUserIds());
        }
    }

    @Test
    void getCurrentScopeUserIds_all_returnsNull() {
        SecurityUser su = new SecurityUser();
        su.setUserId(1L);
        su.setDataScope("all");
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getCurrentUser).thenReturn(su);
            mocked.when(SecurityUtils::isAdmin).thenReturn(false);
            CustomerService svc = newScopedService(mock(SysUserMapper.class), mock(SysDeptMapper.class),
                    scopedResolver(req -> com.ycmn.common.scope.ScopeDecision.all()));
            assertNull(svc.getCurrentScopeUserIds());
        }
    }

    /** 【批② 统一数据访问层】dept_and_children 由统一层解析(本部门及子部门用户集合) */
    @Test
    void getCurrentScopeUserIds_deptAndChildren_returnsDeptAndDescendantUsers() {
        SecurityUser su = new SecurityUser();
        su.setUserId(42L);
        su.setDeptId(2L);
        su.setDataScope("dept_and_children");
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getCurrentUser).thenReturn(su);
            mocked.when(SecurityUtils::isAdmin).thenReturn(false);
            CustomerService svc = newScopedService(mock(SysUserMapper.class), mock(SysDeptMapper.class),
                    scopedResolver(req -> decisionOf(List.of(10L, 11L))));
            // 部门 2 及子部门 4/9 下的用户 10/11
            assertEquals(List.of(10L, 11L), svc.getCurrentScopeUserIds());
        }
    }

    // ===== P2-2: 客户 dept_id 落库同步（2026-08-11） =====

    /** create 时按负责人(owner)部门同步 dept_id——ORG 锚点数据范围可用 */
    @Test
    void create_syncsDeptIdFromOwner() {
        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysUser owner = new SysUser();
        owner.setId(100L); owner.setDeptId(7L);
        when(userMapper.selectById(100L)).thenReturn(owner);

        CustomerService svc = new CustomerService(
                customerMapper, mock(BizCustomerLogMapper.class), mock(BizInvoiceMapper.class),
                mock(BizCustomerShareMapper.class), mock(BizCustomerIndicatorMapper.class),
                mock(BizContactMapper.class), mock(BizPaymentMapper.class),
                userMapper, mock(SysDeptMapper.class), mock(BizReminderMapper.class),
                mock(JdbcTemplate.class), mock(SysConfigService.class),
                mock(OrgScopeResolver.class), mock(CustomerAccessService.class),
                mock(com.ycmn.module.system.service.RolePermScopeService.class));
        BizCustomer req = new BizCustomer();
        try (MockedStatic<SecurityUtils> ms = mockStatic(SecurityUtils.class)) {
            ms.when(SecurityUtils::isAdmin).thenReturn(false);
            ms.when(SecurityUtils::getCurrentUserId).thenReturn(100L);
            svc.create(req);
        }
        ArgumentCaptor<BizCustomer> captor = ArgumentCaptor.forClass(BizCustomer.class);
        verify(customerMapper).insert(captor.capture());
        assertEquals(100L, captor.getValue().getOwnerId());
        assertEquals(7L, captor.getValue().getDeptId(), "create 必须同步负责人部门到 dept_id");
    }

    /** 负责人部门缺失时不阻断创建(ORG 分支降级, 安全性不劣化) */
    @Test
    void create_ownerWithoutDept_keepsNullDept() {
        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysUser owner = new SysUser();
        owner.setId(100L); owner.setDeptId(null);
        when(userMapper.selectById(100L)).thenReturn(owner);

        CustomerService svc = new CustomerService(
                customerMapper, mock(BizCustomerLogMapper.class), mock(BizInvoiceMapper.class),
                mock(BizCustomerShareMapper.class), mock(BizCustomerIndicatorMapper.class),
                mock(BizContactMapper.class), mock(BizPaymentMapper.class),
                userMapper, mock(SysDeptMapper.class), mock(BizReminderMapper.class),
                mock(JdbcTemplate.class), mock(SysConfigService.class),
                mock(OrgScopeResolver.class), mock(CustomerAccessService.class),
                mock(com.ycmn.module.system.service.RolePermScopeService.class));
        BizCustomer req = new BizCustomer();
        try (MockedStatic<SecurityUtils> ms = mockStatic(SecurityUtils.class)) {
            ms.when(SecurityUtils::isAdmin).thenReturn(false);
            ms.when(SecurityUtils::getCurrentUserId).thenReturn(100L);
            svc.create(req);
        }
        ArgumentCaptor<BizCustomer> captor = ArgumentCaptor.forClass(BizCustomer.class);
        verify(customerMapper).insert(captor.capture());
        assertEquals(null, captor.getValue().getDeptId(), "owner 无部门时 dept_id 保持 null");
    }

    /** admin 转交负责人时同步 dept_id 到新负责人部门 */
    @Test
    void update_adminTransferOwner_syncsDeptId() {
        BizCustomer existing = new BizCustomer();
        existing.setId(1L); existing.setOwnerId(200L); existing.setStatus("normal");
        existing.setDeptId(5L);
        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        when(customerMapper.selectById(1L)).thenReturn(existing);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysUser newOwner = new SysUser();
        newOwner.setId(300L); newOwner.setDeptId(9L);
        when(userMapper.selectById(300L)).thenReturn(newOwner);

        CustomerAccessService accessService = mock(CustomerAccessService.class);
        when(accessService.canEdit(1L)).thenReturn(true);
        when(accessService.isInSeaPool(1L)).thenReturn(false);

        BizCustomerMapper cm = customerMapper;
        SysUserMapper um = userMapper;
        CustomerService svc = new CustomerService(
                cm, mock(BizCustomerLogMapper.class), mock(BizInvoiceMapper.class),
                mock(BizCustomerShareMapper.class), mock(BizCustomerIndicatorMapper.class),
                mock(BizContactMapper.class), mock(BizPaymentMapper.class),
                um, mock(SysDeptMapper.class), mock(BizReminderMapper.class),
                mock(JdbcTemplate.class), mock(SysConfigService.class),
                mock(OrgScopeResolver.class), accessService,
                mock(com.ycmn.module.system.service.RolePermScopeService.class));

        BizCustomer req = new BizCustomer();
        req.setId(1L); req.setOwnerId(300L);
        try (MockedStatic<SecurityUtils> ms = mockStatic(SecurityUtils.class)) {
            ms.when(SecurityUtils::isAdmin).thenReturn(true);
            svc.update(req);
        }
        ArgumentCaptor<BizCustomer> captor = ArgumentCaptor.forClass(BizCustomer.class);
        verify(customerMapper).updateById(captor.capture());
        assertEquals(300L, captor.getValue().getOwnerId());
        assertEquals(9L, captor.getValue().getDeptId(), "admin 转交负责人必须同步 dept_id");
    }

    /** 【A3 2026-08-12】admin 转交且新 owner 无部门时, 必须显式清空 dept_id(不再遗留旧负责人部门) */
    @Test
    void update_adminTransferToOwnerWithoutDept_clearsDeptId() {
        BizCustomer existing = new BizCustomer();
        existing.setId(1L); existing.setOwnerId(200L); existing.setStatus("normal");
        existing.setDeptId(5L); // 旧负责人部门(应被清空)
        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        when(customerMapper.selectById(1L)).thenReturn(existing);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysUser newOwner = new SysUser();
        newOwner.setId(300L); newOwner.setDeptId(null); // 新负责人无部门
        when(userMapper.selectById(300L)).thenReturn(newOwner);

        CustomerAccessService accessService = mock(CustomerAccessService.class);
        when(accessService.canEdit(1L)).thenReturn(true);
        when(accessService.isInSeaPool(1L)).thenReturn(false);

        CustomerService svc = new CustomerService(
                customerMapper, mock(BizCustomerLogMapper.class), mock(BizInvoiceMapper.class),
                mock(BizCustomerShareMapper.class), mock(BizCustomerIndicatorMapper.class),
                mock(BizContactMapper.class), mock(BizPaymentMapper.class),
                userMapper, mock(SysDeptMapper.class), mock(BizReminderMapper.class),
                mock(JdbcTemplate.class), mock(SysConfigService.class),
                mock(OrgScopeResolver.class), accessService,
                mock(com.ycmn.module.system.service.RolePermScopeService.class));

        BizCustomer req = new BizCustomer();
        req.setId(1L); req.setOwnerId(300L);
        try (MockedStatic<SecurityUtils> ms = mockStatic(SecurityUtils.class)) {
            ms.when(SecurityUtils::isAdmin).thenReturn(true);
            svc.update(req);
        }
        // updateById 收到新 owner(300)且 dept_id 已在实体上置 null(updateById 只写非 null, 故不写 dept)
        ArgumentCaptor<BizCustomer> captor = ArgumentCaptor.forClass(BizCustomer.class);
        verify(customerMapper).updateById(captor.capture());
        assertEquals(300L, captor.getValue().getOwnerId());
        assertNull(captor.getValue().getDeptId(), "owner 无部门时实体 dept_id 应为 null");
        // 【A3】旧 dept_id=5 须经 LambdaUpdateWrapper.set(null) 显式清空(updateById 无法写 null)
        verify(customerMapper).update(isNull(), org.mockito.ArgumentMatchers.<LambdaUpdateWrapper<BizCustomer>>any());
    }

    /** 【A3 2026-08-12】非 admin 编辑自身客户(owner 不变)但负责人部门已调整 → dept_id 跟随当前 owner 部门 */
    @Test
    void update_ownerUnchanged_stillFollowsOwnerDept() {
        BizCustomer existing = new BizCustomer();
        existing.setId(1L); existing.setOwnerId(200L); existing.setStatus("normal");
        existing.setDeptId(5L); // 库内旧值
        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        when(customerMapper.selectById(1L)).thenReturn(existing);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysUser owner = new SysUser();
        owner.setId(200L); owner.setDeptId(9L); // owner 已调整到部门 9
        when(userMapper.selectById(200L)).thenReturn(owner);

        CustomerAccessService accessService = mock(CustomerAccessService.class);
        when(accessService.canEdit(1L)).thenReturn(true);
        when(accessService.isInSeaPool(1L)).thenReturn(false);

        CustomerService svc = new CustomerService(
                customerMapper, mock(BizCustomerLogMapper.class), mock(BizInvoiceMapper.class),
                mock(BizCustomerShareMapper.class), mock(BizCustomerIndicatorMapper.class),
                mock(BizContactMapper.class), mock(BizPaymentMapper.class),
                userMapper, mock(SysDeptMapper.class), mock(BizReminderMapper.class),
                mock(JdbcTemplate.class), mock(SysConfigService.class),
                mock(OrgScopeResolver.class), accessService,
                mock(com.ycmn.module.system.service.RolePermScopeService.class));

        BizCustomer req = new BizCustomer();
        req.setId(1L); // 不传 ownerId = 视为不修改负责人, 用库内 owner
        try (MockedStatic<SecurityUtils> ms = mockStatic(SecurityUtils.class)) {
            ms.when(SecurityUtils::isAdmin).thenReturn(false);
            svc.update(req);
        }
        ArgumentCaptor<BizCustomer> captor = ArgumentCaptor.forClass(BizCustomer.class);
        verify(customerMapper).updateById(captor.capture());
        assertNull(captor.getValue().getOwnerId(), "未传 ownerId = 不修改负责人(updateById 只写非 null, 库内 owner 保持 200)");
        assertEquals(9L, captor.getValue().getDeptId(), "owner 部门调整后 dept_id 跟随当前 owner(库内 200)部门");
    }
}
