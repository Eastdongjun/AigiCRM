package com.ycmn.module.customer.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisMapperBuilderAssistant;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ycmn.common.BusinessException;
import com.ycmn.common.scope.OrgScopeResolver;
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
import com.ycmn.module.system.entity.SysDept;
import com.ycmn.module.system.entity.SysUser;
import com.ycmn.module.system.mapper.SysDeptMapper;
import com.ycmn.module.system.mapper.SysUserMapper;
import com.ycmn.module.system.service.SysConfigService;
import com.ycmn.security.SecurityUser;
import com.ycmn.security.SecurityUtils;
import org.junit.jupiter.api.BeforeAll;import org.junit.jupiter.api.Test;
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
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
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
        return new CustomerService(
                customerMapper,
                mock(BizCustomerLogMapper.class),
                invoiceMapper,
                mock(BizCustomerShareMapper.class),
                mock(BizCustomerIndicatorMapper.class),
                mock(BizContactMapper.class),
                paymentMapper,
                mock(SysUserMapper.class),
                mock(SysDeptMapper.class),
                mock(BizReminderMapper.class),
                mock(JdbcTemplate.class),
                mock(SysConfigService.class),
                resolver,
                mock(com.ycmn.common.engine.CustomerAccessService.class));
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

    // ===== I1：新六级 code 统一路由 OrgScopeResolver（设计 §4.2） =====

    /** team 码 → resolver 可见 userIds 生成 owner_id IN 条件（修复 default 仅本人的降级） */
    @Test
    void applyDataScope_teamCodeRoutesThroughResolver() {
        SecurityUser su = new SecurityUser();
        su.setUserId(1L);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentDataScope).thenReturn("team");
            m.when(SecurityUtils::getCurrentUser).thenReturn(su);

            OrgScopeResolver resolver = mock(OrgScopeResolver.class);
            when(resolver.resolve(su)).thenReturn(List.of(1L, 2L, 3L));
            CustomerService svc = newService(mock(BizInvoiceMapper.class), mock(BizPaymentMapper.class),
                    mock(BizCustomerMapper.class), resolver);

            LambdaQueryWrapper<BizCustomer> w = new LambdaQueryWrapper<>();
            ReflectionTestUtils.invokeMethod(svc, "applyDataScope", w);

            assertEquals(List.of(1L, 2L, 3L), columnValues(w).get("owner_id"),
                    "team 码必须经 resolver 生成 owner_id IN 条件");
        }
    }

    /** all 码（resolver null=不过滤）→ 不套 owner_id 条件 */
    @Test
    void applyDataScope_allCodeSkipsOwnerFilter() {
        SecurityUser su = new SecurityUser();
        su.setUserId(1L);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentDataScope).thenReturn("all");
            m.when(SecurityUtils::getCurrentUser).thenReturn(su);

            OrgScopeResolver resolver = mock(OrgScopeResolver.class);
            when(resolver.resolve(su)).thenReturn(null);
            CustomerService svc = newService(mock(BizInvoiceMapper.class), mock(BizPaymentMapper.class),
                    mock(BizCustomerMapper.class), resolver);

            LambdaQueryWrapper<BizCustomer> w = new LambdaQueryWrapper<>();
            ReflectionTestUtils.invokeMethod(svc, "applyDataScope", w);

            assertFalse(columnValues(w).containsKey("owner_id"), "all（null）不过滤 owner_id");
        }
    }

    /** 旧码 org 分支保留（现有角色在用，不走 resolver，仅本部门 org 树语义不变） */
    @Test
    void applyDataScope_legacyOrgBranchKept() {
        SecurityUser su = new SecurityUser();
        su.setUserId(1L);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentDataScope).thenReturn("org");
            m.when(SecurityUtils::getCurrentUser).thenReturn(su);
            m.when(SecurityUtils::getCurrentOrgId).thenReturn(5L);

            OrgScopeResolver resolver = mock(OrgScopeResolver.class);
            CustomerService svc = newService(mock(BizInvoiceMapper.class), mock(BizPaymentMapper.class),
                    mock(BizCustomerMapper.class), resolver);

            LambdaQueryWrapper<BizCustomer> w = new LambdaQueryWrapper<>();
            ReflectionTestUtils.invokeMethod(svc, "applyDataScope", w);

            // 旧分支走 org 树查询；断言完全不调用 resolver
            verify(resolver, never()).resolve(any());
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
                mock(com.ycmn.common.engine.CustomerAccessService.class));
    }

    /** 开关默认关的 resolver 桩：resolve 透传 scope；appendSystemLevelScopes 原样返回 base */
    private OrgScopeResolver scopedResolver(org.mockito.stubbing.Answer<List<Long>> resolveAnswer) {
        OrgScopeResolver resolver = mock(OrgScopeResolver.class);
        when(resolver.resolve(any())).thenAnswer(resolveAnswer);
        when(resolver.appendSystemLevelScopes(any(), anyCollection()))
                .thenAnswer(inv -> new ArrayList<>((java.util.Collection<? extends Long>) inv.getArgument(1)));
        return resolver;
    }

    @Test
    void getCurrentScopeUserIds_self_returnsOnlyOwnId() {
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getCurrentDataScope).thenReturn("self");
            mocked.when(SecurityUtils::getCurrentUserId).thenReturn(42L);
            CustomerService svc = newScopedService(mock(SysUserMapper.class), mock(SysDeptMapper.class),
                    scopedResolver(inv -> List.of(42L)));
            assertEquals(List.of(42L), svc.getCurrentScopeUserIds());
        }
    }

    @Test
    void getCurrentScopeUserIds_nullScope_defaultsToSelf() {
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getCurrentDataScope).thenReturn(null);
            mocked.when(SecurityUtils::getCurrentUserId).thenReturn(42L);
            CustomerService svc = newScopedService(mock(SysUserMapper.class), mock(SysDeptMapper.class),
                    scopedResolver(inv -> List.of(42L)));
            assertEquals(List.of(42L), svc.getCurrentScopeUserIds());
        }
    }

    @Test
    void getCurrentScopeUserIds_all_returnsNull() {
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getCurrentDataScope).thenReturn("all");
            CustomerService svc = newScopedService(mock(SysUserMapper.class), mock(SysDeptMapper.class),
                    scopedResolver(inv -> null));
            assertNull(svc.getCurrentScopeUserIds());
        }
    }

    @Test
    void getCurrentScopeUserIds_deptAndChildren_returnsDeptAndDescendantUsers() {
        SysDept dept2 = new SysDept(); dept2.setId(2L); dept2.setParentId(0L); dept2.setStatus(1);
        SysDept dept4 = new SysDept(); dept4.setId(4L); dept4.setParentId(2L); dept4.setStatus(1);
        SysDept dept9 = new SysDept(); dept9.setId(9L); dept9.setParentId(4L); dept9.setStatus(1);
        SysUser user10 = new SysUser(); user10.setId(10L); user10.setDeptId(4L);
        SysUser user11 = new SysUser(); user11.setId(11L); user11.setDeptId(9L);

        SysDeptMapper deptMapper = mock(SysDeptMapper.class);
        when(deptMapper.selectList(any())).thenReturn(List.of(dept2, dept4, dept9));
        SysUserMapper userMapper = mock(SysUserMapper.class);
        when(userMapper.selectList(any())).thenReturn(List.of(user10, user11));

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getCurrentDataScope).thenReturn("dept_and_children");
            mocked.when(SecurityUtils::getCurrentUserId).thenReturn(42L);
            mocked.when(SecurityUtils::getCurrentDeptId).thenReturn(2L);
            CustomerService svc = newScopedService(userMapper, deptMapper,
                    scopedResolver(inv -> List.of(42L)));
            // 部门 2 及子部门 4/9 下的用户 10/11（含自身部门 2 下的用户）
            assertEquals(List.of(10L, 11L), svc.getCurrentScopeUserIds());
        }
    }
}
