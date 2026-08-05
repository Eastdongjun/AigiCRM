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
import com.ycmn.module.system.mapper.SysDeptMapper;
import com.ycmn.module.system.mapper.SysOrgMapper;
import com.ycmn.module.system.mapper.SysUserMapper;
import com.ycmn.module.system.service.SysConfigService;
import com.ycmn.security.SecurityUser;
import com.ycmn.security.SecurityUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
                mock(SysOrgMapper.class),
                mock(SysDeptMapper.class),
                mock(BizReminderMapper.class),
                mock(JdbcTemplate.class),
                mock(SysConfigService.class),
                resolver);
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
}
