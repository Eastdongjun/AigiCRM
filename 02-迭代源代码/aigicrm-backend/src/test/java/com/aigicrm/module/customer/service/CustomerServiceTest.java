package com.aigicrm.module.customer.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisMapperBuilderAssistant;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aigicrm.common.BusinessException;
import com.aigicrm.common.engine.CustomerAccessService;
import com.aigicrm.common.scope.OrgScopeResolver;
import com.aigicrm.module.customer.entity.BizContact;
import com.aigicrm.module.customer.entity.BizCustomer;
import com.aigicrm.module.customer.entity.BizInvoice;
import com.aigicrm.module.customer.entity.BizPayment;
import com.aigicrm.module.customer.mapper.BizCustomerMapper;
import com.aigicrm.module.customer.mapper.BizCustomerIndicatorMapper;
import com.aigicrm.module.customer.mapper.BizCustomerLogMapper;
import com.aigicrm.module.customer.mapper.BizCustomerShareMapper;
import com.aigicrm.module.customer.mapper.BizContactMapper;
import com.aigicrm.module.customer.mapper.BizInvoiceMapper;
import com.aigicrm.module.customer.mapper.BizPaymentMapper;
import com.aigicrm.module.reminder.mapper.BizReminderMapper;
import com.aigicrm.module.system.entity.SysUser;
import com.aigicrm.module.system.mapper.SysDeptMapper;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.module.system.service.SysConfigService;
import com.aigicrm.security.SecurityUser;
import com.aigicrm.security.SecurityUtils;
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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;import static org.junit.jupiter.api.Assertions.assertNotNull;import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.doNothing;
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
        TableInfoHelper.initTableInfo(assistant, BizInvoice.class);
        TableInfoHelper.initTableInfo(assistant, BizPayment.class);
        TableInfoHelper.initTableInfo(assistant, com.aigicrm.module.approval.entity.BizApproval.class);
        TableInfoHelper.initTableInfo(assistant, com.aigicrm.module.reminder.entity.BizReminder.class);
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
                mock(com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper.class),
                mock(SysUserMapper.class),
                mock(SysDeptMapper.class),
                mock(BizReminderMapper.class),
                mock(JdbcTemplate.class),
                mock(SysConfigService.class),
                resolver,
                accessService,
                mock(com.aigicrm.module.system.service.RolePermScopeService.class), mock(com.aigicrm.module.approval.mapper.BizApprovalMapper.class),
                mock(com.aigicrm.module.system.mapper.SysLogMapper.class));
    }

    /** 裁决 2026-09-24: 财务删除留痕钉用——可注入 SysLogMapper 以断言审计快照 */
    private CustomerService newServiceWithAudit(BizInvoiceMapper invoiceMapper, BizPaymentMapper paymentMapper,
                                                BizCustomerMapper customerMapper, OrgScopeResolver resolver,
                                                com.aigicrm.module.system.mapper.SysLogMapper auditMapper) {
        return new CustomerService(
                customerMapper,
                mock(BizCustomerLogMapper.class),
                invoiceMapper,
                mock(BizCustomerShareMapper.class),
                mock(BizCustomerIndicatorMapper.class),
                mock(BizContactMapper.class),
                paymentMapper,
                mock(com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper.class),
                mock(SysUserMapper.class),
                mock(SysDeptMapper.class),
                mock(BizReminderMapper.class),
                mock(JdbcTemplate.class),
                mock(SysConfigService.class),
                resolver,
                mock(com.aigicrm.common.engine.CustomerAccessService.class),
                mock(com.aigicrm.module.system.service.RolePermScopeService.class), mock(com.aigicrm.module.approval.mapper.BizApprovalMapper.class),
                auditMapper);
    }

    @Test
    void countExpiringTodayByScopeUsesTodayAndExcludesSeaCustomers() {
        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        when(customerMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(3L);
        CustomerService svc = newService(mock(BizInvoiceMapper.class), mock(BizPaymentMapper.class),
                customerMapper, mock(OrgScopeResolver.class));
        SecurityUser admin = new SecurityUser();
        admin.setUserId(1L);

        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUser).thenReturn(admin);
            security.when(SecurityUtils::isAdmin).thenReturn(true);

            assertEquals(3L, svc.countExpiringTodayByScope());
            ArgumentCaptor<LambdaQueryWrapper<BizCustomer>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
            verify(customerMapper).selectCount(captor.capture());
            String sql = captor.getValue().getSqlSegment();
            assertTrue(sql.contains("expire_date"));
            assertTrue(sql.contains("status"));
            assertTrue(sql.contains("deleted"));
        }
    }

    /**
     * 【裁决 2026-09-24】最近开票日=纯推导字段: 任何角色(含 admin/customer:date:edit)经客户编辑接口
     * 直改一律拒绝,纠错走财务页发票补录/作废——替换原"总监或行政可改"双轨(人工值会被重算静默覆盖)。
     */
    @Test
    void update_rejectsManualLastInvoiceDateChange_evenForAdmin() {
        BizCustomer existing = new BizCustomer();
        existing.setId(7L);
        existing.setLastInvoiceDate(java.time.LocalDate.of(2026, 1, 1));
        existing.setRegisterDate(java.time.LocalDate.of(2025, 6, 1));
        BizCustomer requested = new BizCustomer();
        requested.setId(7L);
        requested.setLastInvoiceDate(java.time.LocalDate.of(2026, 9, 1));
        requested.setRegisterDate(java.time.LocalDate.of(2025, 6, 1));

        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        when(customerMapper.selectById(7L)).thenReturn(existing);
        com.aigicrm.common.engine.CustomerAccessService access =
                mock(com.aigicrm.common.engine.CustomerAccessService.class);
        when(access.canEdit(7L)).thenReturn(true);
        when(access.isInSeaPool(7L)).thenReturn(false);
        CustomerService service = newService(mock(BizInvoiceMapper.class), mock(BizPaymentMapper.class),
                customerMapper, mock(OrgScopeResolver.class), mock(BizContactMapper.class), access);

        try (org.mockito.MockedStatic<SecurityUtils> sec = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::isAdmin).thenReturn(true);
            sec.when(() -> SecurityUtils.hasAuthority("customer:date:edit")).thenReturn(true);
            BusinessException ex = assertThrows(BusinessException.class, () -> service.update(requested));
            assertTrue(ex.getMessage().contains("由开票记录自动推导"));
        }
    }

    /** 注册日守卫不变: 无 customer:date:edit 权限改注册日仍按原口径拒绝(开票日未动) */
    @Test
    void update_rejectsRegisterDateChange_withoutDateEditAuthority() {
        BizCustomer existing = new BizCustomer();
        existing.setId(8L);
        existing.setLastInvoiceDate(java.time.LocalDate.of(2026, 1, 1));
        existing.setRegisterDate(java.time.LocalDate.of(2025, 6, 1));
        BizCustomer requested = new BizCustomer();
        requested.setId(8L);
        requested.setLastInvoiceDate(java.time.LocalDate.of(2026, 1, 1));
        requested.setRegisterDate(java.time.LocalDate.of(2025, 9, 1));

        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        when(customerMapper.selectById(8L)).thenReturn(existing);
        com.aigicrm.common.engine.CustomerAccessService access =
                mock(com.aigicrm.common.engine.CustomerAccessService.class);
        when(access.canEdit(8L)).thenReturn(true);
        when(access.isInSeaPool(8L)).thenReturn(false);
        CustomerService service = newService(mock(BizInvoiceMapper.class), mock(BizPaymentMapper.class),
                customerMapper, mock(OrgScopeResolver.class), mock(BizContactMapper.class), access);

        try (org.mockito.MockedStatic<SecurityUtils> sec = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::isAdmin).thenReturn(false);
            sec.when(() -> SecurityUtils.hasAuthority("customer:date:edit")).thenReturn(false);
            BusinessException ex = assertThrows(BusinessException.class, () -> service.update(requested));
            assertTrue(ex.getMessage().contains("注册日"));
        }
    }

    /**
     * 【PR-18 统计口径】作废发票后重算"最近开票日"必须排除已作废发票——
     * 断言下发到 Mapper 的查询含 status <> 'voided' 条件(作废票不得驱动保护期)。
     */
    @Test
    void voidInvoice_recomputesLatestInvoiceDateFromValidOnesOnly() {
        BizInvoiceMapper invoiceMapper = mock(BizInvoiceMapper.class);
        BizPaymentMapper paymentMapper = mock(BizPaymentMapper.class);
        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        OrgScopeResolver resolver = mock(OrgScopeResolver.class);
        var indicatorMapper = mock(com.aigicrm.module.customer.mapper.BizCustomerIndicatorMapper.class);
        CustomerService service = new CustomerService(
                customerMapper,
                mock(com.aigicrm.module.customer.mapper.BizCustomerLogMapper.class),
                invoiceMapper,
                mock(com.aigicrm.module.customer.mapper.BizCustomerShareMapper.class),
                indicatorMapper,
                mock(com.aigicrm.module.customer.mapper.BizContactMapper.class),
                paymentMapper,
                mock(com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper.class),
                mock(com.aigicrm.module.system.mapper.SysUserMapper.class),
                mock(com.aigicrm.module.system.mapper.SysDeptMapper.class),
                mock(com.aigicrm.module.reminder.mapper.BizReminderMapper.class),
                mock(org.springframework.jdbc.core.JdbcTemplate.class),
                mock(com.aigicrm.module.system.service.SysConfigService.class),
                resolver,
                mock(com.aigicrm.common.engine.CustomerAccessService.class),
                mock(com.aigicrm.module.system.service.RolePermScopeService.class), mock(com.aigicrm.module.approval.mapper.BizApprovalMapper.class),
                mock(com.aigicrm.module.system.mapper.SysLogMapper.class));

        BizInvoice invoice = new BizInvoice();
        invoice.setId(5L);
        invoice.setCustomerId(1L);
        invoice.setInvoiceDate(java.time.LocalDate.of(2026, 1, 10));
        when(invoiceMapper.selectById(5L)).thenReturn(invoice);

        // 客户原最近开票日 = 被作废的这张票
        BizCustomer customer = new BizCustomer();
        customer.setId(1L);
        customer.setLastInvoiceDate(java.time.LocalDate.of(2026, 1, 10));
        when(customerMapper.selectById(1L)).thenReturn(customer);
        // 有效发票为空 → 作废后最近开票日应清空
        when(invoiceMapper.selectList(any())).thenReturn(List.of());
        when(invoiceMapper.sumAmountByCustomerId(1L)).thenReturn(java.math.BigDecimal.ZERO);
        when(paymentMapper.sumAmountByCustomerId(1L)).thenReturn(java.math.BigDecimal.ZERO);
        when(indicatorMapper.selectOne(any())).thenReturn(null);

        service.voidInvoice(1L, 5L, "开错票重开");

        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.Wrapper<BizInvoice>> wrapperCaptor =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        verify(invoiceMapper).selectList(wrapperCaptor.capture());
        // MP 3.5 参数命名懒加载: mock 不执行 SQL, 需先构建 SQL 片段触发参数填充再断言
        LambdaQueryWrapper<BizInvoice> latestWrapper = (LambdaQueryWrapper<BizInvoice>) wrapperCaptor.getValue();
        String latestSql = latestWrapper.getSqlSegment();
        assertTrue(latestSql.contains("status"), "查询需含 status 条件");
        assertTrue(latestWrapper.getParamNameValuePairs().containsValue("voided"), "重算最近开票日的查询必须排除 voided 发票");
        assertNull(customer.getLastInvoiceDate(), "作废唯一有效发票后最近开票日应清空");
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
        assertThrows(BusinessException.class, () -> svc.deleteInvoice(200L, 1L, null));
    }

    /**
     * 【PR-18 状态机】已确认(confirmed)发票锁定不可物理删除——更正/取消走"作废"留痕;
     * 仅待核对(pending)暂录可删。回款同口径。
     */
    /**
     * 【议题五.4 回归】status=include_archived 不得被状态等值过滤打成空集——
     * 修复前 eq(status,'include_archived') 恒无命中, "筛选含归档"实际不可用。
     * 断言三种口径: include_archived=排公海+含归档; 默认=排公海+排归档; 显式 'archived'=只看已归档。
     */
    @Test
    void pageStatusFilter_includeArchivedKeepsArchivedRows() {
        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        IPage<BizCustomer> emptyPage = new Page<>(1, 10, 0);
        when(customerMapper.selectPage(any(), any())).thenReturn(emptyPage);
        CustomerService svc = newService(mock(BizInvoiceMapper.class), mock(BizPaymentMapper.class),
                customerMapper, mock(OrgScopeResolver.class));

        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUserId).thenReturn(7L);
            security.when(SecurityUtils::getCurrentUser).thenReturn(null); // applyDataScope 走拒绝分支, 与本断言无关

            ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.Wrapper<BizCustomer>> captor =
                    ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);

            svc.page(1, 10, null, null, null, null, "include_archived", null, null, null, null, null);
            verify(customerMapper, times(1)).selectPage(any(), captor.capture());
            var w1 = (LambdaQueryWrapper<BizCustomer>) captor.getValue();
            w1.getSqlSegment(); // 触发懒加载参数命名
            assertTrue(w1.getParamNameValuePairs().containsValue("sea"), "include_archived 仍应排除公海");
            assertFalse(w1.getParamNameValuePairs().containsValue("include_archived"),
                    "不得按字面 include_archived 等值过滤(恒空集)");
            // 【回归钉 2026-10-09 NM005 反馈】include_archived 不得排除归档——B5 机械替换曾把本分支
            // ne(sea) 改成 notIn(sea,archived),参数多出 archived 但旧断言察觉不到(假绿灯),用户归档后看不到
            assertFalse(w1.getParamNameValuePairs().containsValue("archived"),
                    "include_archived 必须包含归档行(参数含 archived=被排除)");

            svc.page(1, 10, null, null, null, null, null, null, null, null, null, null);
            verify(customerMapper, times(2)).selectPage(any(), captor.capture());
            var w2 = (LambdaQueryWrapper<BizCustomer>) captor.getValue();
            w2.getSqlSegment();
            assertTrue(w2.getParamNameValuePairs().containsValue("archived"), "默认口径应排除归档");

            svc.page(1, 10, null, null, null, null, "archived", null, null, null, null, null);
            verify(customerMapper, times(3)).selectPage(any(), captor.capture());
            var w3 = (LambdaQueryWrapper<BizCustomer>) captor.getValue();
            w3.getSqlSegment();
            assertTrue(w3.getParamNameValuePairs().containsValue("archived"), "显式传 archived=只看已归档");
        }
    }

    /**
     * 【对齐 PC 高级筛选 2026-09-21】保护状态/省/市由 PC 客户端过滤提升为服务端参数 ——
     * protectionStatus 单值=精确 eq(protection_status 域, 与 status 域互不串扰), 逗号多值=in;
     * province/city=LIKE 包含(PC 客户端 contains 同语义); 均不传时不产生对应条件。
     */
    @Test
    void pageFilters_protectionStatusAndProvinceCity() {
        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        IPage<BizCustomer> emptyPage = new Page<>(1, 10, 0);
        when(customerMapper.selectPage(any(), any())).thenReturn(emptyPage);
        CustomerService svc = newService(mock(BizInvoiceMapper.class), mock(BizPaymentMapper.class),
                customerMapper, mock(OrgScopeResolver.class));

        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUserId).thenReturn(7L);
            security.when(SecurityUtils::getCurrentUser).thenReturn(null);

            ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.Wrapper<BizCustomer>> captor =
                    ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);

            svc.page(1, 10, null, null, null, null, null, null, null, null, null, null, "active", null, null);
            verify(customerMapper, times(1)).selectPage(any(), captor.capture());
            var w1 = (LambdaQueryWrapper<BizCustomer>) captor.getValue();
            w1.getSqlSegment();
            assertTrue(w1.getParamNameValuePairs().containsValue("active"), "protectionStatus 单值应精确匹配 protection_status");

            svc.page(1, 10, null, null, null, null, null, null, null, null, null, null, "expiring,expired", "山西", "太原");
            verify(customerMapper, times(2)).selectPage(any(), captor.capture());
            var w2 = (LambdaQueryWrapper<BizCustomer>) captor.getValue();
            w2.getSqlSegment();
            java.util.Collection<Object> vals2 = w2.getParamNameValuePairs().values();
            assertTrue(vals2.contains("expiring") && vals2.contains("expired"), "逗号多值应展开为 in 条件");
            assertTrue(vals2.contains("%山西%") && vals2.contains("%太原%"), "省/市应为 LIKE 包含语义(与 PC contains 对齐)");

            // 【L2 2026-10-08】LIKE 通配符转义:用户输入的 %/_ 按字面量匹配,不得放宽匹配面
            svc.page(1, 10, null, null, null, null, null, null, null, null, null, null, null, "50%部", "太原_市");
            verify(customerMapper, times(3)).selectPage(any(), captor.capture());
            var w3 = (LambdaQueryWrapper<BizCustomer>) captor.getValue();
            w3.getSqlSegment();
            java.util.Collection<Object> vals3 = w3.getParamNameValuePairs().values();
            assertTrue(vals3.contains("%50\\%部%"), "省筛选的 % 应被转义为字面量, 实际: " + vals3);
            assertTrue(vals3.contains("%太原\\_市%"), "市筛选的 _ 应被转义为字面量, 实际: " + vals3);
        }
    }

    /**
     * 【H1 2026-10-08】导出必须回填名称:exportList 改走 selectList 后漏了 pageList 同款
     * fillCustomerNames,ownerName/officeName 为 exist=false 字段 → 导出 xlsx 负责人/办事处列恒空(回归)。
     */
    @Test
    void exportList_fillsOwnerAndOfficeNames() {
        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        BizCustomer row = new BizCustomer();
        row.setId(1L);
        row.setOwnerId(11L);
        row.setOfficeId(3L);
        when(customerMapper.selectList(any())).thenReturn(List.of(row));
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysUser owner = new SysUser();
        owner.setId(11L);
        owner.setRealName("张三");
        when(userMapper.selectByIds(org.mockito.ArgumentMatchers.<java.util.Collection<? extends java.io.Serializable>>any()))
                .thenReturn(List.of(owner));
        SysDeptMapper deptMapper = mock(SysDeptMapper.class);
        com.aigicrm.module.system.entity.SysDept office = new com.aigicrm.module.system.entity.SysDept();
        office.setId(3L);
        office.setDeptName("太原办事处");
        when(deptMapper.selectByIds(org.mockito.ArgumentMatchers.<java.util.Collection<? extends java.io.Serializable>>any()))
                .thenReturn(List.of(office));
        CustomerService svc = new CustomerService(customerMapper,
                mock(BizCustomerLogMapper.class), mock(BizInvoiceMapper.class),
                mock(BizCustomerShareMapper.class), mock(BizCustomerIndicatorMapper.class),
                mock(BizContactMapper.class), mock(BizPaymentMapper.class), mock(com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper.class),
                userMapper, deptMapper, mock(BizReminderMapper.class), mock(JdbcTemplate.class),
                mock(SysConfigService.class), mock(OrgScopeResolver.class),
                mock(CustomerAccessService.class),
                mock(com.aigicrm.module.system.service.RolePermScopeService.class),
                mock(com.aigicrm.module.approval.mapper.BizApprovalMapper.class),
                mock(com.aigicrm.module.system.mapper.SysLogMapper.class));

        // 【门禁修复 2026-10-08】同文件列表路径测试既定模式:mockStatic 钉死 getCurrentUser=null,
        // 否则 surefire 同 JVM 复用线程上,先跑的 ApprovalServiceTest/SeaServiceTest 等类
        // setAuthentication 后未 clearContext,泄漏的非 admin 用户使 applyDataScope 走到
        // 未打桩的 scopeResolver.resolveScope → 返回 null → NPE,与本测试断言无关
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUser).thenReturn(null); // applyDataScope 走拒绝分支, 与本断言无关

            var exported = svc.exportList(new CustomerService.ListFilter(
                    null, null, null, null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null, null, null));

            assertEquals(1, exported.size());
            assertEquals("张三", exported.get(0).getOwnerName(), "导出必须回填负责人名称(H1 回归)");
            assertEquals("太原办事处", exported.get(0).getOfficeName(), "导出必须回填办事处名称(H1 回归)");
        }
    }

    /** 【M1 2026-10-08】导出上限护栏:全量 selectList 超 50_000 行时截断到 50_000(单请求内存护栏) */
    @Test
    void exportList_truncatesAtCap() {
        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        List<BizCustomer> many = new ArrayList<>();
        for (int i = 0; i < 50_001; i++) {
            BizCustomer c = new BizCustomer();
            c.setId((long) i);
            many.add(c);
        }
        when(customerMapper.selectList(any())).thenReturn(many);
        CustomerService svc = newService(mock(BizInvoiceMapper.class), mock(BizPaymentMapper.class),
                customerMapper, mock(OrgScopeResolver.class));

        // 【门禁修复 2026-10-08】同上:钉死 getCurrentUser=null,隔离同 JVM 先行测试类泄漏的
        // SecurityContextHolder,使数据范围分支确定(与本测试的截断护栏断言无关)
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUser).thenReturn(null); // applyDataScope 走拒绝分支, 与本断言无关

            var exported = svc.exportList(new CustomerService.ListFilter(
                    null, null, null, null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null, null, null));

            assertEquals(50_000, exported.size(), "导出超限必须截断到 50_000 行(内存护栏)");
        }
    }

    /**
     * 【议题五.3 裁决补齐】在途审批(reviewing/pending)任一存在 → 拒删;
     * 无票款且无在途审批 → 放行物理删除(挂审批数据被清, canDelete 由 accessService 放行)。
     */
    @Test
    void delete_rejectsWhenActiveApprovalsExist() {
        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        BizCustomer customer = new BizCustomer();
        customer.setId(9L);
        when(customerMapper.selectById(9L)).thenReturn(customer);
        BizInvoiceMapper invoiceMapper = mock(BizInvoiceMapper.class);
        BizPaymentMapper paymentMapper = mock(BizPaymentMapper.class);
        when(invoiceMapper.selectCount(any())).thenReturn(0L);
        when(paymentMapper.selectCount(any())).thenReturn(0L);
        var approvalMapper = mock(com.aigicrm.module.approval.mapper.BizApprovalMapper.class);
        var access = mock(com.aigicrm.common.engine.CustomerAccessService.class);
        doNothing().when(access).assertCanEdit(any());
        when(access.canDelete(anyLong())).thenReturn(true);
        CustomerService svc = new CustomerService(
                customerMapper,
                mock(com.aigicrm.module.customer.mapper.BizCustomerLogMapper.class),
                invoiceMapper,
                mock(com.aigicrm.module.customer.mapper.BizCustomerShareMapper.class),
                mock(com.aigicrm.module.customer.mapper.BizCustomerIndicatorMapper.class),
                mock(com.aigicrm.module.customer.mapper.BizContactMapper.class),
                paymentMapper,
                mock(com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper.class),
                mock(com.aigicrm.module.system.mapper.SysUserMapper.class),
                mock(com.aigicrm.module.system.mapper.SysDeptMapper.class),
                mock(com.aigicrm.module.reminder.mapper.BizReminderMapper.class),
                mock(org.springframework.jdbc.core.JdbcTemplate.class),
                mock(com.aigicrm.module.system.service.SysConfigService.class),
                mock(OrgScopeResolver.class),
                access,
                mock(com.aigicrm.module.system.service.RolePermScopeService.class),
                approvalMapper,
                mock(com.aigicrm.module.system.mapper.SysLogMapper.class));

        // 在途审批 2 条 → 拒删并提示先处理审批
        when(approvalMapper.selectCount(any())).thenReturn(2L);
        BusinessException ex = assertThrows(BusinessException.class, () -> svc.delete(9L));
        assertTrue(String.valueOf(ex.getMessage()).contains("在途审批"), "提示需指向在途审批: " + ex.getMessage());
        verify(customerMapper, never()).deleteById(anyLong());

        // 无在途审批 → 放行删除
        when(approvalMapper.selectCount(any())).thenReturn(0L);
        svc.delete(9L);
        verify(customerMapper).deleteById(9L);
    }

    /**
     * 【裁决 2026-09-24 更新】"发票可以删除,但要有删除记录"——任意状态(含 confirmed/voided)可删:
     * 必填原因 + sys_log 审计快照(票号/金额/状态/原因) + 逻辑删;空白原因拒绝。替换 PR-18 仅暂录可删。
     */
    @Test
    void deleteInvoice_confirmedDeletableWithAuditTrail() {
        BizInvoiceMapper invoiceMapper = mock(BizInvoiceMapper.class);
        BizInvoice confirmed = new BizInvoice();
        confirmed.setId(2L);
        confirmed.setCustomerId(9L);
        confirmed.setStatus("confirmed");
        confirmed.setInvoiceNumber("INV-2026-001");
        confirmed.setInvoiceDate(java.time.LocalDate.of(2026, 8, 1));
        confirmed.setAmount(new java.math.BigDecimal("1200.50"));
        when(invoiceMapper.selectById(2L)).thenReturn(confirmed);
        com.aigicrm.module.system.mapper.SysLogMapper auditMapper =
                mock(com.aigicrm.module.system.mapper.SysLogMapper.class);

        CustomerService svc = newServiceWithAudit(invoiceMapper, mock(BizPaymentMapper.class),
                mock(BizCustomerMapper.class), mock(OrgScopeResolver.class), auditMapper);

        // 空白原因拒绝(留痕强制)
        BusinessException ex = assertThrows(BusinessException.class,
                () -> svc.deleteInvoice(9L, 2L, "  "));
        assertTrue(ex.getMessage().contains("删除原因"));

        // confirmed + 原因 → 可删
        svc.deleteInvoice(9L, 2L, "录错年度");
        verify(invoiceMapper).deleteById(2L);
        // 审计快照落库: detail 必须含票号与原因
        var cap = ArgumentCaptor.forClass(com.aigicrm.module.system.entity.SysLog.class);
        verify(auditMapper).insert(cap.capture());
        String detail = String.valueOf(cap.getValue().getDetail());
        assertTrue(detail.contains("INV-2026-001"));
        assertTrue(detail.contains("录错年度"));
        assertEquals("删除发票", cap.getValue().getAction());
    }

    /** 【裁决 2026-09-24】回款同口径: confirmed 可删+留痕,空白原因拒 */
    @Test
    void deletePayment_confirmedDeletableWithAuditTrail() {
        BizPaymentMapper paymentMapper = mock(BizPaymentMapper.class);
        BizPayment confirmed = new BizPayment();
        confirmed.setId(4L);
        confirmed.setCustomerId(9L);
        confirmed.setStatus("confirmed");
        confirmed.setPaymentAmount(new java.math.BigDecimal("800"));
        when(paymentMapper.selectById(4L)).thenReturn(confirmed);
        com.aigicrm.module.system.mapper.SysLogMapper auditMapper =
                mock(com.aigicrm.module.system.mapper.SysLogMapper.class);

        CustomerService svc = newServiceWithAudit(mock(BizInvoiceMapper.class), paymentMapper,
                mock(BizCustomerMapper.class), mock(OrgScopeResolver.class), auditMapper);

        assertThrows(BusinessException.class, () -> svc.deletePayment(9L, 4L, null));
        svc.deletePayment(9L, 4L, "重复录入");
        verify(paymentMapper).deleteById(4L);
        verify(auditMapper).insert(org.mockito.ArgumentMatchers.any(com.aigicrm.module.system.entity.SysLog.class));
    }

    @Test
    void deleteInvoice_missingInvoice_rejects() {
        BizInvoiceMapper invoiceMapper = mock(BizInvoiceMapper.class);
        when(invoiceMapper.selectById(1L)).thenReturn(null);
        CustomerService svc = newService(invoiceMapper, mock(BizPaymentMapper.class), mock(BizCustomerMapper.class),
                mock(OrgScopeResolver.class));
        assertThrows(BusinessException.class, () -> svc.deleteInvoice(200L, 1L, "probe"));
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
        assertThrows(BusinessException.class, () -> svc.deletePayment(200L, 2L, null));
    }

    // ===== 「所属集团」下拉候选（2026-09-13: 历史集团名去重, 受数据范围约束） =====

    @Test
    void listGroupNames_returnsDistinctTrimmedNamesAndFiltersBlank() {
        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        BizCustomer shenmei = new BizCustomer();
        shenmei.setGroupName("陕煤集团");
        BizCustomer blank = new BizCustomer();
        blank.setGroupName("   ");
        when(customerMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(shenmei, blank));
        CustomerService svc = newService(mock(BizInvoiceMapper.class), mock(BizPaymentMapper.class),
                customerMapper, mock(OrgScopeResolver.class));
        SecurityUser user = new SecurityUser();
        user.setUserId(9L);

        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUser).thenReturn(user);
            security.when(SecurityUtils::isAdmin).thenReturn(true);

            // 空白集团名不得进入下拉候选
            assertEquals(List.of("陕煤集团"), svc.listGroupNames());

            ArgumentCaptor<LambdaQueryWrapper<BizCustomer>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
            verify(customerMapper).selectList(captor.capture());
            String sql = captor.getValue().getSqlSegment();
            assertTrue(sql.contains("group_name"), "候选查询应只按集团名取值并分组: " + sql);
        }
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

    // ===== getById 404 语义（2026-08-14 防探测: 不存在抛 404 BusinessException, 不再返回 null） =====

    @Test
    void getById_missingCustomer_throws404UnifiedMessage() {
        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        when(customerMapper.selectById(9L)).thenReturn(null);
        CustomerService svc = newService(mock(BizInvoiceMapper.class), mock(BizPaymentMapper.class),
                customerMapper, mock(OrgScopeResolver.class));
        BusinessException ex = assertThrows(BusinessException.class, () -> svc.getById(9L));
        assertEquals(404, ex.getCode(), "客户不存在必须抛 code=404(GlobalExceptionHandler 转 HTTP 404)");
        assertEquals("客户不存在或无权访问", ex.getMessage(), "不存在与无权访问必须统一文案, 消除存在性 oracle");
    }

    @Test
    void getById_existingCustomer_returnsFilled() {
        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        BizCustomer c = new BizCustomer();
        c.setId(1L);
        c.setOwnerId(100L);
        when(customerMapper.selectById(1L)).thenReturn(c);
        CustomerService svc = newService(mock(BizInvoiceMapper.class), mock(BizPaymentMapper.class),
                customerMapper, mock(OrgScopeResolver.class));
        assertEquals(c, svc.getById(1L), "存在客户必须正常返回");
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

    // ===== 【P2 2026-09-30 复审修复】非池客户通用 update 流程字段冻结 =====
    // 公海池分支已禁 ownerId/status/protectionStatus, 非池分支此前仅禁非 admin 的 ownerId——
    // 持编辑权限者可直提 status/protectionStatus 绕过 释放/归档/恢复/保护 专门流程。

    private CustomerService newNonPoolSvc(BizCustomerMapper customerMapper, CustomerAccessService accessService) {
        return newService(mock(BizInvoiceMapper.class), mock(BizPaymentMapper.class),
                customerMapper, mock(OrgScopeResolver.class), mock(BizContactMapper.class), accessService);
    }

    @Test
    void update_nonPool_nonAdmin_statusChange_rejected() {
        BizCustomer existing = new BizCustomer();
        existing.setId(2L);
        existing.setOwnerId(200L);
        existing.setStatus("normal");
        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        when(customerMapper.selectById(2L)).thenReturn(existing);
        CustomerAccessService accessService = mock(CustomerAccessService.class);
        when(accessService.canEdit(2L)).thenReturn(true);
        when(accessService.isInSeaPool(2L)).thenReturn(false);
        CustomerService svc = newNonPoolSvc(customerMapper, accessService);
        BizCustomer req = new BizCustomer();
        req.setId(2L);
        req.setStatus("archived"); // 绕过归档流程直改状态
        try (org.mockito.MockedStatic<SecurityUtils> sec = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::isAdmin).thenReturn(false);
            assertThrows(BusinessException.class, () -> svc.update(req),
                    "非 admin 不可经通用编辑改客户状态");
        }
        verify(customerMapper, never()).updateById(any(BizCustomer.class));
    }

    @Test
    void update_nonPool_nonAdmin_protectionStatusChange_rejected() {
        BizCustomer existing = new BizCustomer();
        existing.setId(3L);
        existing.setOwnerId(200L);
        existing.setStatus("normal");
        existing.setProtectionStatus("none");
        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        when(customerMapper.selectById(3L)).thenReturn(existing);
        CustomerAccessService accessService = mock(CustomerAccessService.class);
        when(accessService.canEdit(3L)).thenReturn(true);
        when(accessService.isInSeaPool(3L)).thenReturn(false);
        CustomerService svc = newNonPoolSvc(customerMapper, accessService);
        BizCustomer req = new BizCustomer();
        req.setId(3L);
        req.setProtectionStatus("expiring");
        try (org.mockito.MockedStatic<SecurityUtils> sec = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::isAdmin).thenReturn(false);
            assertThrows(BusinessException.class, () -> svc.update(req),
                    "非 admin 不可经通用编辑改保护状态");
        }
        verify(customerMapper, never()).updateById(any(BizCustomer.class));
    }

    @Test
    void update_nonPool_nonAdmin_sameStatusEcho_allowed() {
        // PC/小程序编辑表单可能原样回传当前状态值: 值未变不算变更, 必须放行(不破坏正常编辑)
        BizCustomer existing = new BizCustomer();
        existing.setId(4L);
        existing.setOwnerId(200L);
        existing.setStatus("normal");
        existing.setProtectionStatus("none");
        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        when(customerMapper.selectById(4L)).thenReturn(existing);
        CustomerAccessService accessService = mock(CustomerAccessService.class);
        when(accessService.canEdit(4L)).thenReturn(true);
        when(accessService.isInSeaPool(4L)).thenReturn(false);
        CustomerService svc = newNonPoolSvc(customerMapper, accessService);
        BizCustomer req = new BizCustomer();
        req.setId(4L);
        req.setCustomerName("正常改名");
        req.setStatus("normal");           // 原值回传
        req.setProtectionStatus("none");   // 原值回传
        try (org.mockito.MockedStatic<SecurityUtils> sec = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::isAdmin).thenReturn(false);
            svc.update(req);
        }
        verify(customerMapper).updateById(any(BizCustomer.class));
    }

    @Test
    void update_nonPool_admin_statusChange_stillAllowed() {
        BizCustomer existing = new BizCustomer();
        existing.setId(5L);
        existing.setOwnerId(200L);
        existing.setStatus("normal");
        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        when(customerMapper.selectById(5L)).thenReturn(existing);
        CustomerAccessService accessService = mock(CustomerAccessService.class);
        when(accessService.canEdit(5L)).thenReturn(true);
        when(accessService.isInSeaPool(5L)).thenReturn(false);
        CustomerService svc = newNonPoolSvc(customerMapper, accessService);
        BizCustomer req = new BizCustomer();
        req.setId(5L);
        req.setStatus("archived");
        try (org.mockito.MockedStatic<SecurityUtils> sec = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::isAdmin).thenReturn(true);
            svc.update(req); // admin 保持既有自由(运维通道)
        }
        ArgumentCaptor<BizCustomer> captor = ArgumentCaptor.forClass(BizCustomer.class);
        verify(customerMapper).updateById(captor.capture());
        assertEquals("archived", captor.getValue().getStatus(), "admin 改状态不受冻结影响");
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
        verify(customerMapper).updateById(any(BizCustomer.class));
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
            com.aigicrm.common.scope.ScopeDecision d = new com.aigicrm.common.scope.ScopeDecision();
            d.setVisibleUserIds(List.of(1L, 2L, 3L));
            d.setVisibleDeptIds(List.of());
            when(resolver.resolveScope(any(com.aigicrm.common.scope.ScopeRequest.class))).thenReturn(d);
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
            when(resolver.resolveScope(any(com.aigicrm.common.scope.ScopeRequest.class)))
                    .thenReturn(com.aigicrm.common.scope.ScopeDecision.all());
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
            com.aigicrm.common.scope.ScopeDecision d = new com.aigicrm.common.scope.ScopeDecision();
            d.setVisibleUserIds(List.of(1L, 9L));
            d.setVisibleDeptIds(List.of());
            when(resolver.resolveScope(any(com.aigicrm.common.scope.ScopeRequest.class))).thenReturn(d);
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
                mock(BizPaymentMapper.class), mock(com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper.class),
                userMapper,
                deptMapper,
                mock(BizReminderMapper.class),
                mock(JdbcTemplate.class),
                mock(SysConfigService.class),
                resolver,
                mock(com.aigicrm.common.engine.CustomerAccessService.class),
                mock(com.aigicrm.module.system.service.RolePermScopeService.class), mock(com.aigicrm.module.approval.mapper.BizApprovalMapper.class),
                mock(com.aigicrm.module.system.mapper.SysLogMapper.class));
    }

    /** 【批② 统一数据访问层】resolver 桩: 统一入口 resolveScope(ScopeRequest) 返回指定 userIds */
    private OrgScopeResolver scopedResolver(java.util.function.Function<com.aigicrm.common.scope.ScopeRequest, com.aigicrm.common.scope.ScopeDecision> fn) {
        OrgScopeResolver resolver = mock(OrgScopeResolver.class);
        when(resolver.resolveScope(any(com.aigicrm.common.scope.ScopeRequest.class)))
                .thenAnswer(inv -> fn.apply(inv.getArgument(0)));
        return resolver;
    }

    private static com.aigicrm.common.scope.ScopeDecision decisionOf(List<Long> userIds) {
        com.aigicrm.common.scope.ScopeDecision d = new com.aigicrm.common.scope.ScopeDecision();
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
                    scopedResolver(req -> com.aigicrm.common.scope.ScopeDecision.all()));
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
                mock(BizContactMapper.class), mock(BizPaymentMapper.class), mock(com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper.class),
                userMapper, mock(SysDeptMapper.class), mock(BizReminderMapper.class),
                mock(JdbcTemplate.class), mock(SysConfigService.class),
                mock(OrgScopeResolver.class), mock(CustomerAccessService.class),
                mock(com.aigicrm.module.system.service.RolePermScopeService.class), mock(com.aigicrm.module.approval.mapper.BizApprovalMapper.class),
                mock(com.aigicrm.module.system.mapper.SysLogMapper.class));
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
                mock(BizContactMapper.class), mock(BizPaymentMapper.class), mock(com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper.class),
                userMapper, mock(SysDeptMapper.class), mock(BizReminderMapper.class),
                mock(JdbcTemplate.class), mock(SysConfigService.class),
                mock(OrgScopeResolver.class), mock(CustomerAccessService.class),
                mock(com.aigicrm.module.system.service.RolePermScopeService.class), mock(com.aigicrm.module.approval.mapper.BizApprovalMapper.class),
                mock(com.aigicrm.module.system.mapper.SysLogMapper.class));
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
                mock(BizContactMapper.class), mock(BizPaymentMapper.class), mock(com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper.class),
                um, mock(SysDeptMapper.class), mock(BizReminderMapper.class),
                mock(JdbcTemplate.class), mock(SysConfigService.class),
                mock(OrgScopeResolver.class), accessService,
                mock(com.aigicrm.module.system.service.RolePermScopeService.class), mock(com.aigicrm.module.approval.mapper.BizApprovalMapper.class),
                mock(com.aigicrm.module.system.mapper.SysLogMapper.class));

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
                mock(BizContactMapper.class), mock(BizPaymentMapper.class), mock(com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper.class),
                userMapper, mock(SysDeptMapper.class), mock(BizReminderMapper.class),
                mock(JdbcTemplate.class), mock(SysConfigService.class),
                mock(OrgScopeResolver.class), accessService,
                mock(com.aigicrm.module.system.service.RolePermScopeService.class), mock(com.aigicrm.module.approval.mapper.BizApprovalMapper.class),
                mock(com.aigicrm.module.system.mapper.SysLogMapper.class));

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
                mock(BizContactMapper.class), mock(BizPaymentMapper.class), mock(com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper.class),
                userMapper, mock(SysDeptMapper.class), mock(BizReminderMapper.class),
                mock(JdbcTemplate.class), mock(SysConfigService.class),
                mock(OrgScopeResolver.class), accessService,
                mock(com.aigicrm.module.system.service.RolePermScopeService.class), mock(com.aigicrm.module.approval.mapper.BizApprovalMapper.class),
                mock(com.aigicrm.module.system.mapper.SysLogMapper.class));

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

    /**
     * 【PR-22 活体验证 2026-09-19 回归】排行三段原生 SQL 的 gc 片段拼接曾拼出 "?GROUP BY"
     * 语法错误(文本块内容从换行符之后开始, gc 与后续关键字之间无空白), 单测因 JdbcTemplate
     * 全 mock 未拦截。此测试捕获真实下发的 SQL, 断言占位符与关键字之间必须有空白分隔。
     */
    @Test
    void customerRankingsSqlKeepsWhitespaceBetweenPlaceholderAndKeywords() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        CustomerService svc = new CustomerService(
                mock(BizCustomerMapper.class), mock(com.aigicrm.module.customer.mapper.BizCustomerLogMapper.class),
                mock(BizInvoiceMapper.class), mock(com.aigicrm.module.customer.mapper.BizCustomerShareMapper.class),
                mock(com.aigicrm.module.customer.mapper.BizCustomerIndicatorMapper.class),
                mock(BizContactMapper.class), mock(BizPaymentMapper.class), mock(com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper.class),
                mock(com.aigicrm.module.system.mapper.SysUserMapper.class),
                mock(com.aigicrm.module.system.mapper.SysDeptMapper.class),
                mock(com.aigicrm.module.reminder.mapper.BizReminderMapper.class),
                jdbc, mock(com.aigicrm.module.system.service.SysConfigService.class),
                mock(com.aigicrm.common.scope.OrgScopeResolver.class),
                mock(CustomerAccessService.class),
                mock(com.aigicrm.module.system.service.RolePermScopeService.class), mock(com.aigicrm.module.approval.mapper.BizApprovalMapper.class),
                mock(com.aigicrm.module.system.mapper.SysLogMapper.class));
        SecurityUser user = new SecurityUser();
        user.setUserId(74L);
        user.setCurrentGroupId(6L);

        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUser).thenReturn(user);
            security.when(SecurityUtils::isAdmin).thenReturn(false);
            security.when(SecurityUtils::getCurrentGroupIds).thenReturn(List.of(6L));

            svc.customerRankings();
        }

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        org.mockito.ArgumentCaptor<Object[]> argCaptor = org.mockito.ArgumentCaptor.forClass(Object[].class);
        verify(jdbc, org.mockito.Mockito.times(3)).queryForList(sqlCaptor.capture(), argCaptor.capture());
        List<String> sqls = sqlCaptor.getAllValues();
        assertEquals(3, sqls.size(), "排行三段 SQL(incentive/relationship/core)均经 JdbcTemplate 下发");
        for (String sql : sqls) {
            assertFalse(sql.matches("(?s).*\\?(GROUP|ORDER|HAVING|LIMIT).*"),
                    "占位符后紧跟 SQL 关键字(缺空白分隔): " + sql);
            assertTrue(sql.contains("tenant_id = ?"), "非 admin 排行 SQL 必须带集团过滤: " + sql);
            assertTrue(sql.contains("LIMIT 100"), "【BS-D】排行聚合查询必须有行数上限(全表聚合防拖垮): " + sql);
        }
        for (Object[] args : argCaptor.getAllValues()) {
            // incentive/relationship 各绑 1 个集团参数;core 段绑 2 个(阶段年度子查询/外层, 2026-10-10 金额源换 stage_amount 后)
            assertTrue(args.length == 1 || args.length == 2,
                    "排行 SQL 集团参数个数应为 1(incentive/relationship)或 2(core),实际 " + args.length);
            for (Object arg : args) {
                assertEquals(6L, arg, "所有集团参数均绑定当前会话集团");
            }
        }
    }

    /** 【PR-22 批次四】共享不得跨集团:目标用户不在当前集团 → 拒绝,不落共享行/不发育误通知 */
    @Test
    void addShares_crossGroupTarget_rejected() {
        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        when(customerMapper.selectById(501L)).thenReturn(new com.aigicrm.module.customer.entity.BizCustomer());
        org.springframework.jdbc.core.JdbcTemplate jdbc = mock(org.springframework.jdbc.core.JdbcTemplate.class);
        // 集团成员经 GroupScopeUtil 静态 mock 返回(仅 7);目标 66 属别集团
        CustomerService svc = new CustomerService(
                customerMapper, mock(com.aigicrm.module.customer.mapper.BizCustomerLogMapper.class),
                mock(BizInvoiceMapper.class), mock(com.aigicrm.module.customer.mapper.BizCustomerShareMapper.class),
                mock(com.aigicrm.module.customer.mapper.BizCustomerIndicatorMapper.class),
                mock(BizContactMapper.class), mock(BizPaymentMapper.class), mock(com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper.class),
                mock(com.aigicrm.module.system.mapper.SysUserMapper.class),
                mock(com.aigicrm.module.system.mapper.SysDeptMapper.class),
                mock(com.aigicrm.module.reminder.mapper.BizReminderMapper.class),
                jdbc, mock(com.aigicrm.module.system.service.SysConfigService.class),
                mock(com.aigicrm.common.scope.OrgScopeResolver.class),
                mock(CustomerAccessService.class),
                mock(com.aigicrm.module.system.service.RolePermScopeService.class), mock(com.aigicrm.module.approval.mapper.BizApprovalMapper.class),
                mock(com.aigicrm.module.system.mapper.SysLogMapper.class));
        SecurityUser user = new SecurityUser();
        user.setUserId(7L);
        user.setCurrentGroupId(1L);
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class);
             MockedStatic<com.aigicrm.common.scope.GroupScopeUtil> group =
                     org.mockito.Mockito.mockStatic(com.aigicrm.common.scope.GroupScopeUtil.class)) {
            security.when(SecurityUtils::getCurrentUserId).thenReturn(7L);
            group.when(com.aigicrm.common.scope.GroupScopeUtil::currentGroupOrNull).thenReturn(1L);
            group.when(() -> com.aigicrm.common.scope.GroupScopeUtil.userIdsInGroup(jdbc, 1L))
                    .thenReturn(java.util.List.of(7L));
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> svc.addShares(501L, java.util.List.of(66L)));
            assertTrue(ex.getMessage().contains("其他集团"), "实际: " + ex.getMessage());
        }
    }

    /**
     * 【PR-05 2026-09-19】组合提交(客户+指标)必须一次性落两边——
     * 建客户成功而指标失败时不得留下"半保存"客户(同事务整体回滚)。
     */
    @Test
    void createWithIndicator_savesCustomerAndIndicatorTogether() {
        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        com.aigicrm.module.customer.mapper.BizCustomerIndicatorMapper indicatorMapper =
                mock(com.aigicrm.module.customer.mapper.BizCustomerIndicatorMapper.class);
        CustomerService svc = new CustomerService(
                customerMapper, mock(com.aigicrm.module.customer.mapper.BizCustomerLogMapper.class),
                mock(BizInvoiceMapper.class), mock(com.aigicrm.module.customer.mapper.BizCustomerShareMapper.class),
                indicatorMapper, mock(BizContactMapper.class), mock(BizPaymentMapper.class), mock(com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper.class),
                mock(com.aigicrm.module.system.mapper.SysUserMapper.class),
                mock(com.aigicrm.module.system.mapper.SysDeptMapper.class),
                mock(com.aigicrm.module.reminder.mapper.BizReminderMapper.class),
                mock(org.springframework.jdbc.core.JdbcTemplate.class),
                mock(com.aigicrm.module.system.service.SysConfigService.class),
                mock(com.aigicrm.common.scope.OrgScopeResolver.class),
                mock(CustomerAccessService.class),
                mock(com.aigicrm.module.system.service.RolePermScopeService.class), mock(com.aigicrm.module.approval.mapper.BizApprovalMapper.class),
                mock(com.aigicrm.module.system.mapper.SysLogMapper.class));
        doAnswer(inv -> {
            inv.getArgument(0, BizCustomer.class).setId(501L);
            return 1;
        }).when(customerMapper).insert(any(BizCustomer.class));
        SecurityUser user = new SecurityUser();
        user.setUserId(9L);

        com.aigicrm.module.customer.entity.BizCustomerIndicator indicator =
                new com.aigicrm.module.customer.entity.BizCustomerIndicator();
        indicator.setRelationshipScore(2);
        indicator.setIndicator02(1);
        // saveIndicator 首查无行→insert; 随后触发的 recalculateCoreStatus 在同事务内回读到刚插的行
        // (生产语义)→update 落核心三态。第二次起返回该行, 防 mock 恒 null 造成二次 insert 假象
        when(indicatorMapper.selectOne(any())).thenReturn(null).thenReturn(indicator);
        Long id;
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUser).thenReturn(user);
            security.when(SecurityUtils::isAdmin).thenReturn(true);
            security.when(SecurityUtils::getCurrentUserId).thenReturn(9L);
            BizCustomer req = new BizCustomer();
            req.setCustomerName("PR05组合提交测试客户");
            id = svc.createWithIndicator(req, indicator);
        }

        assertEquals(501L, id, "组合提交返回新建客户 id");
        verify(customerMapper).insert(any(BizCustomer.class));
        ArgumentCaptor<com.aigicrm.module.customer.entity.BizCustomerIndicator> indCaptor =
                ArgumentCaptor.forClass(com.aigicrm.module.customer.entity.BizCustomerIndicator.class);
        verify(indicatorMapper).insert(indCaptor.capture());
        assertEquals(501L, indCaptor.getValue().getCustomerId(), "指标必须挂到新建客户上");
        assertEquals(9L, indCaptor.getValue().getUpdatedBy());
    }

    /** 【PR-05】指标落库失败必须向上抛(由 @Transactional 整体回滚),不得吞掉留下半保存客户 */
    @Test
    void createWithIndicator_indicatorFailurePropagates() {
        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        com.aigicrm.module.customer.mapper.BizCustomerIndicatorMapper indicatorMapper =
                mock(com.aigicrm.module.customer.mapper.BizCustomerIndicatorMapper.class);
        when(customerMapper.insert(any(BizCustomer.class))).thenReturn(1);
        when(indicatorMapper.insert(any(com.aigicrm.module.customer.entity.BizCustomerIndicator.class)))
                .thenThrow(new RuntimeException("DB down"));
        CustomerService svc = new CustomerService(
                customerMapper, mock(com.aigicrm.module.customer.mapper.BizCustomerLogMapper.class),
                mock(BizInvoiceMapper.class), mock(com.aigicrm.module.customer.mapper.BizCustomerShareMapper.class),
                indicatorMapper, mock(BizContactMapper.class), mock(BizPaymentMapper.class), mock(com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper.class),
                mock(com.aigicrm.module.system.mapper.SysUserMapper.class),
                mock(com.aigicrm.module.system.mapper.SysDeptMapper.class),
                mock(com.aigicrm.module.reminder.mapper.BizReminderMapper.class),
                mock(org.springframework.jdbc.core.JdbcTemplate.class),
                mock(com.aigicrm.module.system.service.SysConfigService.class),
                mock(com.aigicrm.common.scope.OrgScopeResolver.class),
                mock(CustomerAccessService.class),
                mock(com.aigicrm.module.system.service.RolePermScopeService.class), mock(com.aigicrm.module.approval.mapper.BizApprovalMapper.class),
                mock(com.aigicrm.module.system.mapper.SysLogMapper.class));
        SecurityUser user = new SecurityUser();
        user.setUserId(9L);

        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUser).thenReturn(user);
            security.when(SecurityUtils::isAdmin).thenReturn(true);
            security.when(SecurityUtils::getCurrentUserId).thenReturn(9L);
            BizCustomer req = new BizCustomer();
            req.setCustomerName("PR05组合提交失败测试客户");
            com.aigicrm.module.customer.entity.BizCustomerIndicator indicator =
                    new com.aigicrm.module.customer.entity.BizCustomerIndicator();
            assertThrows(RuntimeException.class, () -> svc.createWithIndicator(req, indicator),
                    "指标失败必须传播(同事务回滚),不得吞异常留下已建客户");
        }
    }

    /**
     * 【PR-17 议题三裁决"即时计算"】saveRelationshipScore 也必须触发核心客户重算——
     * 此前只有 saveIndicator 触发,只改客情分跨过 12 分门槛时核心标记不刷新(口径双轨)。
     */
    @Test
    void saveRelationshipScore_triggersCoreRecalc() {
        BizCustomerIndicatorMapper indicatorMapper = mock(BizCustomerIndicatorMapper.class);
        BizInvoiceMapper invoiceMapper = mock(BizInvoiceMapper.class);
        BizPaymentMapper paymentMapper = mock(BizPaymentMapper.class);
        // 已有指标行(走 update 分支)
        com.aigicrm.module.customer.entity.BizCustomerIndicator existing =
                new com.aigicrm.module.customer.entity.BizCustomerIndicator();
        existing.setId(9L);
        existing.setCustomerId(501L);
        existing.setRelationshipScore(1);
        when(indicatorMapper.selectOne(any())).thenReturn(existing);  // getIndicator 走 selectOne
        // 重算依赖:开票/回款总额(门槛 100/100)
        when(invoiceMapper.sumAmountByCustomerId(501L)).thenReturn(new java.math.BigDecimal("150"));
        when(paymentMapper.sumAmountByCustomerId(501L)).thenReturn(new java.math.BigDecimal("120"));
        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        when(customerMapper.selectById(501L)).thenReturn(new com.aigicrm.module.customer.entity.BizCustomer());

        CustomerService svc = new CustomerService(
                customerMapper, mock(com.aigicrm.module.customer.mapper.BizCustomerLogMapper.class),
                invoiceMapper, mock(com.aigicrm.module.customer.mapper.BizCustomerShareMapper.class),
                indicatorMapper, mock(BizContactMapper.class), paymentMapper, mock(com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper.class),
                mock(com.aigicrm.module.system.mapper.SysUserMapper.class),
                mock(com.aigicrm.module.system.mapper.SysDeptMapper.class),
                mock(com.aigicrm.module.reminder.mapper.BizReminderMapper.class),
                mock(org.springframework.jdbc.core.JdbcTemplate.class),
                mock(com.aigicrm.module.system.service.SysConfigService.class),
                mock(com.aigicrm.common.scope.OrgScopeResolver.class),
                mock(CustomerAccessService.class),
                mock(com.aigicrm.module.system.service.RolePermScopeService.class), mock(com.aigicrm.module.approval.mapper.BizApprovalMapper.class),
                mock(com.aigicrm.module.system.mapper.SysLogMapper.class));
        SecurityUser user = new SecurityUser();
        user.setUserId(7L);

        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUserId).thenReturn(7L);
            svc.saveRelationshipScore(501L, 3);
        }

        // 客情分更新 + 重算回写指标(is_core 联动):共 2 次 indicatorMapper.updateById
        verify(indicatorMapper, times(2)).updateById(any(com.aigicrm.module.customer.entity.BizCustomerIndicator.class));
        ArgumentCaptor<com.aigicrm.module.customer.entity.BizCustomerIndicator> cap =
                ArgumentCaptor.forClass(com.aigicrm.module.customer.entity.BizCustomerIndicator.class);
        verify(indicatorMapper, times(2)).updateById(cap.capture());
        // 重算那次带回写后的核心状态:指标和=3(仅客情)+0+0+0+0=3 < 12 → isCore=0
        // (即使开票/回款达标,五项不齐也不算核心——与"即时计算"口径一致)
        boolean hasRecalc = cap.getAllValues().stream().anyMatch(i -> i.getIsCore() != null);
        assertTrue(hasRecalc, "重算必须回写 is_core 字段");
    }

    // ===== 【议题五.3/.4 裁决】删除守卫 + 归档/恢复 =====

    @Test
    void delete_withInvoices_rejected() {
        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        when(customerMapper.selectById(501L)).thenReturn(new com.aigicrm.module.customer.entity.BizCustomer());
        BizInvoiceMapper invoiceMapper = mock(BizInvoiceMapper.class);
        when(invoiceMapper.selectCount(any())).thenReturn(3L); // 有 3 笔开票
        BizPaymentMapper paymentMapper = mock(BizPaymentMapper.class);
        when(paymentMapper.selectCount(any())).thenReturn(0L);
        CustomerAccessService accessService = mock(CustomerAccessService.class);
        when(accessService.canDelete(501L)).thenReturn(true);

        CustomerService svc = new CustomerService(
                customerMapper, mock(com.aigicrm.module.customer.mapper.BizCustomerLogMapper.class),
                invoiceMapper, mock(com.aigicrm.module.customer.mapper.BizCustomerShareMapper.class),
                mock(com.aigicrm.module.customer.mapper.BizCustomerIndicatorMapper.class),
                mock(BizContactMapper.class), paymentMapper, mock(com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper.class),
                mock(com.aigicrm.module.system.mapper.SysUserMapper.class),
                mock(com.aigicrm.module.system.mapper.SysDeptMapper.class),
                mock(com.aigicrm.module.reminder.mapper.BizReminderMapper.class),
                mock(org.springframework.jdbc.core.JdbcTemplate.class),
                mock(com.aigicrm.module.system.service.SysConfigService.class),
                mock(com.aigicrm.common.scope.OrgScopeResolver.class),
                accessService,
                mock(com.aigicrm.module.system.service.RolePermScopeService.class), mock(com.aigicrm.module.approval.mapper.BizApprovalMapper.class),
                mock(com.aigicrm.module.system.mapper.SysLogMapper.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> svc.delete(501L));
        assertTrue(ex.getMessage().contains("不允许删除"), "实际: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("3 笔开票"), "提示须含影响范围: " + ex.getMessage());
        // 【文案修复 2026-10-09】拒删指引必须指向真实存在的功能"归档"(系统无客户域"停用")
        assertTrue(ex.getMessage().contains("归档"), "指引须指向归档入口: " + ex.getMessage());
        assertFalse(ex.getMessage().contains("停用"), "不得再指引不存在的'停用': " + ex.getMessage());
        verify(customerMapper, never()).deleteById(any(Long.class));
    }

    @Test
    void delete_noBusinessData_succeeds() {
        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        when(customerMapper.selectById(501L)).thenReturn(new com.aigicrm.module.customer.entity.BizCustomer());
        BizInvoiceMapper invoiceMapper = mock(BizInvoiceMapper.class);
        when(invoiceMapper.selectCount(any())).thenReturn(0L);
        BizPaymentMapper paymentMapper = mock(BizPaymentMapper.class);
        when(paymentMapper.selectCount(any())).thenReturn(0L);
        CustomerAccessService accessService = mock(CustomerAccessService.class);
        when(accessService.canDelete(501L)).thenReturn(true);

        CustomerService svc = new CustomerService(
                customerMapper, mock(com.aigicrm.module.customer.mapper.BizCustomerLogMapper.class),
                invoiceMapper, mock(com.aigicrm.module.customer.mapper.BizCustomerShareMapper.class),
                mock(com.aigicrm.module.customer.mapper.BizCustomerIndicatorMapper.class),
                mock(BizContactMapper.class), paymentMapper, mock(com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper.class),
                mock(com.aigicrm.module.system.mapper.SysUserMapper.class),
                mock(com.aigicrm.module.system.mapper.SysDeptMapper.class),
                mock(com.aigicrm.module.reminder.mapper.BizReminderMapper.class),
                mock(org.springframework.jdbc.core.JdbcTemplate.class),
                mock(com.aigicrm.module.system.service.SysConfigService.class),
                mock(com.aigicrm.common.scope.OrgScopeResolver.class),
                accessService,
                mock(com.aigicrm.module.system.service.RolePermScopeService.class), mock(com.aigicrm.module.approval.mapper.BizApprovalMapper.class),
                mock(com.aigicrm.module.system.mapper.SysLogMapper.class));

        assertDoesNotThrow(() -> svc.delete(501L));
        verify(customerMapper).deleteById(501L);
    }

    @Test
    void archive_andRestore_statusTransitions() {
        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        com.aigicrm.module.customer.entity.BizCustomer cust =
                new com.aigicrm.module.customer.entity.BizCustomer();
        cust.setId(501L);
        cust.setStatus("normal");
        when(customerMapper.selectById(501L)).thenReturn(cust);
        CustomerAccessService accessService = mock(CustomerAccessService.class);
        when(accessService.canEdit(501L)).thenReturn(true);

        CustomerService svc = new CustomerService(
                customerMapper, mock(com.aigicrm.module.customer.mapper.BizCustomerLogMapper.class),
                mock(BizInvoiceMapper.class), mock(com.aigicrm.module.customer.mapper.BizCustomerShareMapper.class),
                mock(com.aigicrm.module.customer.mapper.BizCustomerIndicatorMapper.class),
                mock(BizContactMapper.class), mock(BizPaymentMapper.class), mock(com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper.class),
                mock(com.aigicrm.module.system.mapper.SysUserMapper.class),
                mock(com.aigicrm.module.system.mapper.SysDeptMapper.class),
                mock(com.aigicrm.module.reminder.mapper.BizReminderMapper.class),
                mock(org.springframework.jdbc.core.JdbcTemplate.class),
                mock(com.aigicrm.module.system.service.SysConfigService.class),
                mock(com.aigicrm.common.scope.OrgScopeResolver.class),
                accessService,
                mock(com.aigicrm.module.system.service.RolePermScopeService.class), mock(com.aigicrm.module.approval.mapper.BizApprovalMapper.class),
                mock(com.aigicrm.module.system.mapper.SysLogMapper.class));

        svc.archive(501L);
        assertEquals("archived", cust.getStatus());
        svc.restore(501L);
        assertEquals("normal", cust.getStatus());
        // 非归档状态不可恢复
        BusinessException ex = assertThrows(BusinessException.class, () -> svc.restore(501L));
        assertTrue(ex.getMessage().contains("不在归档"));
    }

    // ===== 【2026-10-09】死分享提醒清理(分享撤销/客户终态时 pending 一并清, 根治列表 customerName 整列「—」) =====

    @SuppressWarnings("unchecked")
    @Test
    void delete_cleansSharesAndPendingShareReminders() {
        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        when(customerMapper.selectById(501L)).thenReturn(new BizCustomer());
        BizInvoiceMapper invoiceMapper = mock(BizInvoiceMapper.class);
        when(invoiceMapper.selectCount(any())).thenReturn(0L);
        BizPaymentMapper paymentMapper = mock(BizPaymentMapper.class);
        when(paymentMapper.selectCount(any())).thenReturn(0L);
        BizCustomerShareMapper shareMapper = mock(BizCustomerShareMapper.class);
        BizReminderMapper reminderMapper = mock(BizReminderMapper.class);
        CustomerAccessService accessService = mock(CustomerAccessService.class);
        when(accessService.canDelete(501L)).thenReturn(true);

        CustomerService svc = new CustomerService(
                customerMapper, mock(BizCustomerLogMapper.class),
                invoiceMapper, shareMapper,
                mock(BizCustomerIndicatorMapper.class),
                mock(BizContactMapper.class), paymentMapper, mock(com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper.class),
                mock(SysUserMapper.class), mock(SysDeptMapper.class),
                reminderMapper, mock(JdbcTemplate.class), mock(SysConfigService.class),
                mock(OrgScopeResolver.class), accessService,
                mock(com.aigicrm.module.system.service.RolePermScopeService.class),
                mock(com.aigicrm.module.approval.mapper.BizApprovalMapper.class),
                mock(com.aigicrm.module.system.mapper.SysLogMapper.class));

        svc.delete(501L);
        // 删除是终态: 分享行与 pending 分享提醒一并清理(悬挂提醒回填不到客户名);
        // 【体检 P1 2026-10-12】INV-05 全量口径: 再补一次全类型 pending 清理(expiring/invoice_* 死行供给源)
        verify(shareMapper).delete(any());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<com.aigicrm.module.reminder.entity.BizReminder>> cap =
                ArgumentCaptor.forClass((Class) LambdaQueryWrapper.class);
        verify(reminderMapper, times(2)).delete(cap.capture());
        LambdaQueryWrapper<com.aigicrm.module.reminder.entity.BizReminder> shareScoped = cap.getAllValues().get(0);
        assertTrue(shareScoped.getSqlSegment().contains("customer_id"), "按客户定位: " + shareScoped.getSqlSegment());
        assertTrue(shareScoped.getSqlSegment().contains("reminder_type"), "限分享三类: " + shareScoped.getSqlSegment());
        assertTrue(shareScoped.getParamNameValuePairs().containsValue("pending"), "仅清 pending");
        assertTrue(shareScoped.getParamNameValuePairs().containsValue("share_to"), "share_to 在清理范围");
        LambdaQueryWrapper<com.aigicrm.module.reminder.entity.BizReminder> allTypes = cap.getAllValues().get(1);
        assertTrue(allTypes.getSqlSegment().contains("customer_id"), "按客户定位: " + allTypes.getSqlSegment());
        assertFalse(allTypes.getSqlSegment().contains("reminder_type"), "全类型清理不限提醒类型: " + allTypes.getSqlSegment());
        assertTrue(allTypes.getParamNameValuePairs().containsValue("pending"), "仅清 pending");
    }

    @Test
    void archive_cleansPendingShareRemindersButKeepsShareRows() {
        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        BizCustomer cust = new BizCustomer();
        cust.setId(501L);
        cust.setStatus("normal");
        when(customerMapper.selectById(501L)).thenReturn(cust);
        BizCustomerShareMapper shareMapper = mock(BizCustomerShareMapper.class);
        BizReminderMapper reminderMapper = mock(BizReminderMapper.class);
        CustomerAccessService accessService = mock(CustomerAccessService.class);
        when(accessService.canEdit(501L)).thenReturn(true);

        CustomerService svc = new CustomerService(
                customerMapper, mock(BizCustomerLogMapper.class),
                mock(BizInvoiceMapper.class), shareMapper,
                mock(BizCustomerIndicatorMapper.class),
                mock(BizContactMapper.class), mock(BizPaymentMapper.class), mock(com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper.class),
                mock(SysUserMapper.class), mock(SysDeptMapper.class),
                reminderMapper, mock(JdbcTemplate.class), mock(SysConfigService.class),
                mock(OrgScopeResolver.class), accessService,
                mock(com.aigicrm.module.system.service.RolePermScopeService.class),
                mock(com.aigicrm.module.approval.mapper.BizApprovalMapper.class),
                mock(com.aigicrm.module.system.mapper.SysLogMapper.class));

        svc.archive(501L);
        assertEquals("archived", cust.getStatus());
        // 归档可恢复: 分享行保留; 【体检 P1 2026-10-12】INV-05 全量口径——清全部类型 pending 提醒
        verify(shareMapper, never()).delete(any());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<com.aigicrm.module.reminder.entity.BizReminder>> archiveCap =
                ArgumentCaptor.forClass((Class) LambdaQueryWrapper.class);
        verify(reminderMapper, times(1)).delete(archiveCap.capture());
        assertFalse(archiveCap.getValue().getSqlSegment().contains("reminder_type"),
                "归档清理不限提醒类型(全类型): " + archiveCap.getValue().getSqlSegment());
        assertTrue(archiveCap.getValue().getParamNameValuePairs().containsValue("pending"), "仅清 pending");
    }

    @Test
    void removeAllShares_cleansPendingShareReminders() {
        BizCustomerShareMapper shareMapper = mock(BizCustomerShareMapper.class);
        BizReminderMapper reminderMapper = mock(BizReminderMapper.class);
        CustomerService svc = new CustomerService(
                mock(BizCustomerMapper.class), mock(BizCustomerLogMapper.class),
                mock(BizInvoiceMapper.class), shareMapper,
                mock(BizCustomerIndicatorMapper.class), mock(BizContactMapper.class),
                mock(BizPaymentMapper.class), mock(com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper.class), mock(SysUserMapper.class), mock(SysDeptMapper.class),
                reminderMapper, mock(JdbcTemplate.class), mock(SysConfigService.class),
                mock(OrgScopeResolver.class), mock(CustomerAccessService.class),
                mock(com.aigicrm.module.system.service.RolePermScopeService.class),
                mock(com.aigicrm.module.approval.mapper.BizApprovalMapper.class),
                mock(com.aigicrm.module.system.mapper.SysLogMapper.class));

        // 入海/捞取/管理端清空三路径均经此口,分享撤销后三类 pending 提醒随分享失效
        svc.removeAllShares(501L);
        verify(shareMapper).delete(any());
        verify(reminderMapper, times(1)).delete(any());
    }

    @SuppressWarnings("unchecked")
    @Test
    void removeShare_cleansOnlyTargetUserPendingShareTo() {
        BizCustomerShareMapper shareMapper = mock(BizCustomerShareMapper.class);
        BizReminderMapper reminderMapper = mock(BizReminderMapper.class);
        CustomerService svc = new CustomerService(
                mock(BizCustomerMapper.class), mock(BizCustomerLogMapper.class),
                mock(BizInvoiceMapper.class), shareMapper,
                mock(BizCustomerIndicatorMapper.class), mock(BizContactMapper.class),
                mock(BizPaymentMapper.class), mock(com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper.class), mock(SysUserMapper.class), mock(SysDeptMapper.class),
                reminderMapper, mock(JdbcTemplate.class), mock(SysConfigService.class),
                mock(OrgScopeResolver.class), mock(CustomerAccessService.class),
                mock(com.aigicrm.module.system.service.RolePermScopeService.class),
                mock(com.aigicrm.module.approval.mapper.BizApprovalMapper.class),
                mock(com.aigicrm.module.system.mapper.SysLogMapper.class));

        svc.removeShare(501L, 66L);
        verify(shareMapper).delete(any());
        ArgumentCaptor<LambdaQueryWrapper<com.aigicrm.module.reminder.entity.BizReminder>> cap =
                ArgumentCaptor.forClass((Class) LambdaQueryWrapper.class);
        verify(reminderMapper, times(1)).delete(cap.capture());
        LambdaQueryWrapper<com.aigicrm.module.reminder.entity.BizReminder> w = cap.getValue();
        // 单个移除只清被移除者的 share_to,不误伤分享人/负责人侧的成组通知
        assertTrue(w.getSqlSegment().contains("owner_id"), "按被移除人定位: " + w.getSqlSegment());
        assertTrue(w.getParamNameValuePairs().containsValue(66L), "目标用户 66");
        assertTrue(w.getParamNameValuePairs().containsValue("share_to"), "仅 share_to");
        assertTrue(w.getParamNameValuePairs().containsValue("pending"), "仅 pending");
    }

    // ===== 【口径统一 2026-10-09 用户裁决】已超期=expireDate<今日 且排除公海/归档,三处(工作台/统计页/列表钻取)同判据 =====

    @Test
    void countExpiredByScope_dateCriteria_excludesSeaArchived() {
        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        when(customerMapper.selectCount(any())).thenReturn(0L);
        CustomerService svc = newService(mock(BizInvoiceMapper.class), mock(BizPaymentMapper.class),
                customerMapper, mock(OrgScopeResolver.class));

        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUserId).thenReturn(7L);
            security.when(SecurityUtils::getCurrentUser).thenReturn(null);
            svc.countExpiredByScope();
            ArgumentCaptor<LambdaQueryWrapper<BizCustomer>> cap =
                    ArgumentCaptor.forClass((Class) LambdaQueryWrapper.class);
            verify(customerMapper).selectCount(cap.capture());
            LambdaQueryWrapper<BizCustomer> w = cap.getValue();
            String sql = w.getSqlSegment();
            // 判据=expire_date < 今日 + status NOT IN (sea, archived);不再 protection_status='expired'(不排公海旧口径)
            assertTrue(sql.contains("expire_date"), "按到期日期判定: " + sql);
            assertTrue(sql.contains("NOT IN"), "排除公海/归档: " + sql);
            assertTrue(w.getParamNameValuePairs().containsValue("sea"), "排除 sea");
            assertTrue(w.getParamNameValuePairs().containsValue("archived"), "排除 archived");
            assertTrue(w.getParamNameValuePairs().containsValue(java.time.LocalDate.now()), "上界=今日");
            assertFalse(w.getParamNameValuePairs().containsValue("expired"), "不再等值 protection_status=expired");
        }
    }

    @Test
    void page_statusExpired_translatesToDateCriteria() {
        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        when(customerMapper.selectPage(any(), any())).thenReturn(new Page<>(1, 10, 0));
        CustomerService svc = newService(mock(BizInvoiceMapper.class), mock(BizPaymentMapper.class),
                customerMapper, mock(OrgScopeResolver.class));

        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUserId).thenReturn(7L);
            security.when(SecurityUtils::getCurrentUser).thenReturn(null);
            // 列表/统计卡 status=expired(工作台钻取/快速筛选同参): 与统计页同判据
            svc.page(1, 10, null, null, null, null, "expired", null);
            ArgumentCaptor<LambdaQueryWrapper<BizCustomer>> cap =
                    ArgumentCaptor.forClass((Class) LambdaQueryWrapper.class);
            verify(customerMapper).selectPage(any(), cap.capture());
            LambdaQueryWrapper<BizCustomer> w = cap.getValue();
            String sql = w.getSqlSegment();
            assertTrue(sql.contains("expire_date"), "已超期=日期判据: " + sql);
            assertTrue(sql.contains("NOT IN"), "排除公海/归档: " + sql);
            assertFalse(w.getParamNameValuePairs().containsValue("expired"),
                    "status 列等值 'expired' 只命中无池办事处的到期客户(入池即转 sea),不再使用");
        }
    }

    // ===== 【回归修复 2026-10-09 公海死循环】legacy 老票不得把保护期基准拖到注册(捞取)日之前 =====

    @Test
    void recalc_legacyInvoiceBeforeRegister_keepsExpireAnchoredToRegister() {
        BizInvoiceMapper invoiceMapper = mock(BizInvoiceMapper.class);
        BizPaymentMapper paymentMapper = mock(BizPaymentMapper.class);
        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        BizCustomerIndicatorMapper indicatorMapper = mock(BizCustomerIndicatorMapper.class);

        CustomerService service = new CustomerService(
                customerMapper, mock(BizCustomerLogMapper.class),
                invoiceMapper, mock(BizCustomerShareMapper.class),
                indicatorMapper, mock(BizContactMapper.class), paymentMapper, mock(com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper.class),
                mock(SysUserMapper.class), mock(SysDeptMapper.class),
                mock(BizReminderMapper.class), mock(JdbcTemplate.class),
                mock(SysConfigService.class), mock(OrgScopeResolver.class),
                mock(CustomerAccessService.class),
                mock(com.aigicrm.module.system.service.RolePermScopeService.class),
                mock(com.aigicrm.module.approval.mapper.BizApprovalMapper.class),
                mock(com.aigicrm.module.system.mapper.SysLogMapper.class));

        // 生产 302 号客户复刻: legacy 票 2024-09-11, 最近捞取(注册)日 2026-09-08, 已批延期 360 天。
        // COALESCE(开票日优先)旧口径算出 expire=2026-09-01(过去)→凌晨任务踢回公海→死循环
        BizInvoice legacy = new BizInvoice();
        legacy.setId(5L);
        legacy.setCustomerId(1L);
        legacy.setInvoiceDate(java.time.LocalDate.of(2024, 9, 11));
        when(invoiceMapper.selectList(any())).thenReturn(List.of(legacy));
        when(invoiceMapper.selectById(5L)).thenReturn(legacy);

        BizCustomer customer = new BizCustomer();
        customer.setId(1L);
        customer.setRegisterDate(java.time.LocalDate.of(2026, 9, 8));
        customer.setLastInvoiceDate(java.time.LocalDate.of(2024, 9, 11));
        customer.setApprovedExtendDays(360);
        when(customerMapper.selectById(1L)).thenReturn(customer);
        when(invoiceMapper.sumAmountByCustomerId(1L)).thenReturn(java.math.BigDecimal.ZERO);
        when(paymentMapper.sumAmountByCustomerId(1L)).thenReturn(java.math.BigDecimal.ZERO);
        when(indicatorMapper.selectOne(any())).thenReturn(null);

        service.voidInvoice(1L, 5L, "回算保护期(测试)");

        // 基准=max(开票日,注册日)=注册日(捞取承诺的保护期起点), 不得被 2024 年老票拖回过去
        java.time.LocalDate expected = java.time.LocalDate.of(2026, 9, 8).plusDays(360).plusDays(360);
        assertEquals(expected, customer.getExpireDate(),
                "expire 应=注册(捞取)日+360+已批延期, 实际: " + customer.getExpireDate());
        assertTrue(customer.getExpireDate().isAfter(java.time.LocalDate.now()), "捞取后保护期必须在未来");
    }

    // ===== 【测试H1修复 2026-10-13】auto_share users 元素类型归一(字符串数字/非法值不再 500) =====

    private CustomerService newAutoShareService(SysConfigService configService) {
        return new CustomerService(
                mock(BizCustomerMapper.class),
                mock(BizCustomerLogMapper.class),
                mock(BizInvoiceMapper.class),
                mock(BizCustomerShareMapper.class),
                mock(BizCustomerIndicatorMapper.class),
                mock(BizContactMapper.class),
                mock(BizPaymentMapper.class),
                mock(com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper.class),
                mock(SysUserMapper.class),
                mock(SysDeptMapper.class),
                mock(BizReminderMapper.class),
                mock(JdbcTemplate.class),
                configService,
                mock(OrgScopeResolver.class),
                mock(com.aigicrm.common.engine.CustomerAccessService.class),
                mock(com.aigicrm.module.system.service.RolePermScopeService.class),
                mock(com.aigicrm.module.approval.mapper.BizApprovalMapper.class),
                mock(com.aigicrm.module.system.mapper.SysLogMapper.class));
    }

    @Test
    void autoShareUsers_stringNumericElement_normalizedAndNotCrash() {
        // 生产踩雷形态: users 混入字符串数字 ["136"] → 旧实现 ClassCastException, 全体非admin列表500
        SysConfigService configService = mock(SysConfigService.class);
        when(configService.getValue("customer.auto_share")).thenReturn(
                "{\"superior\":false,\"admin\":false,\"roles\":[],\"users\":[\"136\", 11, \"abc\"]}");
        CustomerService service = newAutoShareService(configService);
        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::getCurrentUserId).thenReturn(11L);
            List<Long> ids = assertDoesNotThrow(() ->
                    ReflectionTestUtils.invokeMethod(service, "getAutoShareOwnerIds"));
            // 本人(11)命中豁免列表 → 空=不过滤
            assertTrue(ids == null || ids.isEmpty(), "本人命中 users 豁免应返回空(不加过滤), 实际: " + ids);
        }
    }

    @Test
    void autoShareUsers_stringElementOfOthers_ignoredWithoutCrash() {
        // 字符串形态但非本人: 不炸、不豁免, 回落到默认 ownerIds(仅本人)
        SysConfigService configService = mock(SysConfigService.class);
        when(configService.getValue("customer.auto_share")).thenReturn(
                "{\"superior\":false,\"admin\":false,\"roles\":[],\"users\":[\"136\", \"abc\"]}");
        CustomerService service = newAutoShareService(configService);
        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::getCurrentUserId).thenReturn(11L);
            List<Long> ids = assertDoesNotThrow(() ->
                    ReflectionTestUtils.invokeMethod(service, "getAutoShareOwnerIds"));
            assertNotNull(ids);
            assertEquals(List.of(11L), ids, "非豁免用户应回落到仅本人, 字符串元素被归一忽略而非抛 ClassCastException");
        }
    }
}
