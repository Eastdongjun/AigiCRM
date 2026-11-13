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
import com.aigicrm.module.sea.entity.BizSeaPool;
import com.aigicrm.module.sea.mapper.BizSeaPoolMapper;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;import static org.mockito.Mockito.spy;import static org.mockito.Mockito.verify;import static org.mockito.Mockito.when;

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
        TableInfoHelper.initTableInfo(assistant, com.aigicrm.module.sea.entity.BizSeaPool.class);
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
                null,
                mock(BizCustomerLogMapper.class),
                invoiceMapper,
                mock(BizCustomerShareMapper.class),
                mock(BizCustomerIndicatorMapper.class),
                contactMapper,
                paymentMapper,
                mock(com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper.class), mock(com.aigicrm.module.sea.mapper.BizSeaPoolMapper.class),
                mock(SysUserMapper.class),
                mock(SysDeptMapper.class),
                mock(BizReminderMapper.class),
                mock(JdbcTemplate.class),
                mock(SysConfigService.class),
                resolver,
                accessService,
                mock(com.aigicrm.module.system.service.RolePermScopeService.class), mock(com.aigicrm.module.approval.mapper.BizApprovalMapper.class),
                mock(com.aigicrm.module.system.mapper.SysLogMapper.class),
                mock(com.aigicrm.module.customer.service.CustomerEventService.class));
    }

    /** 裁决 2026-09-24: 财务删除留痕钉用——可注入 SysLogMapper 以断言审计快照 */
    private CustomerService newServiceWithAudit(BizInvoiceMapper invoiceMapper, BizPaymentMapper paymentMapper,
                                                BizCustomerMapper customerMapper, OrgScopeResolver resolver,
                                                com.aigicrm.module.system.mapper.SysLogMapper auditMapper) {
        return new CustomerService(
                customerMapper,
                null,
                mock(BizCustomerLogMapper.class),
                invoiceMapper,
                mock(BizCustomerShareMapper.class),
                mock(BizCustomerIndicatorMapper.class),
                mock(BizContactMapper.class),
                paymentMapper,
                mock(com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper.class), mock(com.aigicrm.module.sea.mapper.BizSeaPoolMapper.class),
                mock(SysUserMapper.class),
                mock(SysDeptMapper.class),
                mock(BizReminderMapper.class),
                mock(JdbcTemplate.class),
                mock(SysConfigService.class),
                resolver,
                mock(com.aigicrm.common.engine.CustomerAccessService.class),
                mock(com.aigicrm.module.system.service.RolePermScopeService.class), mock(com.aigicrm.module.approval.mapper.BizApprovalMapper.class),
                auditMapper,
                mock(com.aigicrm.module.customer.service.CustomerEventService.class));
    }

    @Test
    void existsByName_emptyScopeShortCircuits_noEmptyInClause() {
        // 【B1 2026-10-21·整改残余修复】ORG 锚点+可见部门零在职用户时 getCurrentScopeUserIds 返回
        // 非 null 空集, 旧实现 w.in(空集) 生成 IN () 触发 SQL 500(建客查重路径)。守卫后空集直接
        // 判不重名且不触库; 非空集照常走 IN 查询(同时钉住不得借机 fail-open 略过查重)。
        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        when(customerMapper.selectCount(any())).thenReturn(0L);
        CustomerService svc = spy(newService(mock(BizInvoiceMapper.class), mock(BizPaymentMapper.class),
                customerMapper, mock(OrgScopeResolver.class)));
        doReturn(List.of()).when(svc).getCurrentScopeUserIds();
        assertFalse(svc.existsByName("同名客户"));
        verify(customerMapper, never()).selectCount(any());
        doReturn(List.of(5L)).when(svc).getCurrentScopeUserIds();
        assertFalse(svc.existsByName("同名客户"));
        verify(customerMapper, times(1)).selectCount(any());
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
     * 【裁决 2026-11-02 用户名单】最近开票日人工维护: 持 customer:invoice-date:edit(行政/总监/admin,
     * 名单=毛颖娜/肖赟/李艳梅/马吉亮/admin)可直改——覆盖 2026-09-24"纯推导任何角色不可直改"裁决。
     * 人工设定=新基准语义(与开票前移同款): 清零已批延期 + 按 max(注册日,新开票日)+360 重算保护期。
     */
    @Test
    void update_manualLastInvoiceDateChange_withInvoiceDateEditAuthority_setsNewBaseline() {
        BizCustomer existing = new BizCustomer();
        existing.setId(7L);
        existing.setLastInvoiceDate(java.time.LocalDate.of(2026, 1, 1));
        existing.setRegisterDate(java.time.LocalDate.of(2025, 6, 1));
        existing.setApprovedExtendDays(30); // 已批延期必须被清零(新基准360天已覆盖,叠加=双重延长)
        BizCustomer requested = new BizCustomer();
        requested.setId(7L);
        requested.setLastInvoiceDate(java.time.LocalDate.of(2026, 9, 1));
        requested.setRegisterDate(java.time.LocalDate.of(2025, 6, 1));

        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        when(customerMapper.selectById(7L)).thenReturn(existing);
        // 【A1 2026-10-20】update 读现值改悲观锁(selectByIdForUpdate), 测试随之钉同款 stub
        when(customerMapper.selectByIdForUpdate(7L)).thenReturn(existing);
        com.aigicrm.common.engine.CustomerAccessService access =
                mock(com.aigicrm.common.engine.CustomerAccessService.class);
        when(access.canEdit(7L)).thenReturn(true);
        when(access.isInSeaPool(7L)).thenReturn(false);
        CustomerService service = newService(mock(BizInvoiceMapper.class), mock(BizPaymentMapper.class),
                customerMapper, mock(OrgScopeResolver.class), mock(BizContactMapper.class), access);

        try (org.mockito.MockedStatic<SecurityUtils> sec = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::isAdmin).thenReturn(false);
            sec.when(() -> SecurityUtils.hasAuthority("customer:invoice-date:edit")).thenReturn(true);
            service.update(requested);
            org.mockito.ArgumentCaptor<BizCustomer> cap = org.mockito.ArgumentCaptor.forClass(BizCustomer.class);
            verify(customerMapper).updateById(cap.capture());
            assertEquals(java.time.LocalDate.of(2026, 9, 1), cap.getValue().getLastInvoiceDate(), "人工设定的新基准必须落库");
            assertEquals(0, cap.getValue().getApprovedExtendDays(), "新基准上已批延期必须清零");
            assertEquals(java.time.LocalDate.of(2026, 9, 1).plusDays(365), cap.getValue().getExpireDate(),
                    "到期日=新基准+365(未配置时默认保护天数, 2026-11-02 口径A)");
        }
    }

    /** admin 直改同样放行(isAdmin 短路, 无需码)——2026-09-24"含admin一律拒绝"钉翻转 */
    @Test
    void update_manualLastInvoiceDateChange_adminAllowed() {
        BizCustomer existing = new BizCustomer();
        existing.setId(70L);
        existing.setLastInvoiceDate(java.time.LocalDate.of(2026, 1, 1));
        existing.setRegisterDate(java.time.LocalDate.of(2025, 6, 1));
        BizCustomer requested = new BizCustomer();
        requested.setId(70L);
        requested.setLastInvoiceDate(java.time.LocalDate.of(2026, 10, 1));
        requested.setRegisterDate(java.time.LocalDate.of(2025, 6, 1));

        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        when(customerMapper.selectById(70L)).thenReturn(existing);
        when(customerMapper.selectByIdForUpdate(70L)).thenReturn(existing);
        com.aigicrm.common.engine.CustomerAccessService access =
                mock(com.aigicrm.common.engine.CustomerAccessService.class);
        when(access.canEdit(70L)).thenReturn(true);
        when(access.isInSeaPool(70L)).thenReturn(false);
        CustomerService service = newService(mock(BizInvoiceMapper.class), mock(BizPaymentMapper.class),
                customerMapper, mock(OrgScopeResolver.class), mock(BizContactMapper.class), access);

        try (org.mockito.MockedStatic<SecurityUtils> sec = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::isAdmin).thenReturn(true);
            sec.when(() -> SecurityUtils.hasAuthority("customer:invoice-date:edit")).thenReturn(false);
            service.update(requested);
            verify(customerMapper).updateById(org.mockito.ArgumentMatchers.argThat((BizCustomer c) ->
                    java.time.LocalDate.of(2026, 10, 1).equals(c.getLastInvoiceDate())));
        }
    }

    /** 无码非 admin 直改仍拒绝(纯推导口径对名单外人不变), 文案指明授权与财务页两条正路 */
    @Test
    void update_rejectsManualLastInvoiceDateChange_withoutInvoiceDateEditAuthority() {
        BizCustomer existing = new BizCustomer();
        existing.setId(71L);
        existing.setLastInvoiceDate(java.time.LocalDate.of(2026, 1, 1));
        existing.setRegisterDate(java.time.LocalDate.of(2025, 6, 1));
        BizCustomer requested = new BizCustomer();
        requested.setId(71L);
        requested.setLastInvoiceDate(java.time.LocalDate.of(2026, 9, 1));
        requested.setRegisterDate(java.time.LocalDate.of(2025, 6, 1));

        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        when(customerMapper.selectById(71L)).thenReturn(existing);
        when(customerMapper.selectByIdForUpdate(71L)).thenReturn(existing);
        com.aigicrm.common.engine.CustomerAccessService access =
                mock(com.aigicrm.common.engine.CustomerAccessService.class);
        when(access.canEdit(71L)).thenReturn(true);
        when(access.isInSeaPool(71L)).thenReturn(false);
        CustomerService service = newService(mock(BizInvoiceMapper.class), mock(BizPaymentMapper.class),
                customerMapper, mock(OrgScopeResolver.class), mock(BizContactMapper.class), access);

        try (org.mockito.MockedStatic<SecurityUtils> sec = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::isAdmin).thenReturn(false);
            sec.when(() -> SecurityUtils.hasAuthority("customer:invoice-date:edit")).thenReturn(false);
            sec.when(() -> SecurityUtils.hasAuthority("customer:date:edit")).thenReturn(true); // 持注册日码也不放行开票日
            BusinessException ex = assertThrows(BusinessException.class, () -> service.update(requested));
            assertTrue(ex.getMessage().contains("仅授权人员"), "拒绝文案须指明仅授权人员可维护: " + ex.getMessage());
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
        when(customerMapper.selectByIdForUpdate(8L)).thenReturn(existing);
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
                null,
                mock(com.aigicrm.module.customer.mapper.BizCustomerLogMapper.class),
                invoiceMapper,
                mock(com.aigicrm.module.customer.mapper.BizCustomerShareMapper.class),
                indicatorMapper,
                mock(com.aigicrm.module.customer.mapper.BizContactMapper.class),
                paymentMapper,
                mock(com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper.class), mock(com.aigicrm.module.sea.mapper.BizSeaPoolMapper.class),
                mock(com.aigicrm.module.system.mapper.SysUserMapper.class),
                mock(com.aigicrm.module.system.mapper.SysDeptMapper.class),
                mock(com.aigicrm.module.reminder.mapper.BizReminderMapper.class),
                mock(org.springframework.jdbc.core.JdbcTemplate.class),
                mock(com.aigicrm.module.system.service.SysConfigService.class),
                resolver,
                mock(com.aigicrm.common.engine.CustomerAccessService.class),
                mock(com.aigicrm.module.system.service.RolePermScopeService.class), mock(com.aigicrm.module.approval.mapper.BizApprovalMapper.class),
                mock(com.aigicrm.module.system.mapper.SysLogMapper.class),
                mock(com.aigicrm.module.customer.service.CustomerEventService.class));

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
                null,
                mock(BizCustomerLogMapper.class), mock(BizInvoiceMapper.class),
                mock(BizCustomerShareMapper.class), mock(BizCustomerIndicatorMapper.class),
                mock(BizContactMapper.class), mock(BizPaymentMapper.class), mock(com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper.class), mock(com.aigicrm.module.sea.mapper.BizSeaPoolMapper.class),
                userMapper, deptMapper, mock(BizReminderMapper.class), mock(JdbcTemplate.class),
                mock(SysConfigService.class), mock(OrgScopeResolver.class),
                mock(CustomerAccessService.class),
                mock(com.aigicrm.module.system.service.RolePermScopeService.class),
                mock(com.aigicrm.module.approval.mapper.BizApprovalMapper.class),
                mock(com.aigicrm.module.system.mapper.SysLogMapper.class),
                mock(com.aigicrm.module.customer.service.CustomerEventService.class));

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
                null,
                mock(com.aigicrm.module.customer.mapper.BizCustomerLogMapper.class),
                invoiceMapper,
                mock(com.aigicrm.module.customer.mapper.BizCustomerShareMapper.class),
                mock(com.aigicrm.module.customer.mapper.BizCustomerIndicatorMapper.class),
                mock(com.aigicrm.module.customer.mapper.BizContactMapper.class),
                paymentMapper,
                mock(com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper.class), mock(com.aigicrm.module.sea.mapper.BizSeaPoolMapper.class),
                mock(com.aigicrm.module.system.mapper.SysUserMapper.class),
                mock(com.aigicrm.module.system.mapper.SysDeptMapper.class),
                mock(com.aigicrm.module.reminder.mapper.BizReminderMapper.class),
                mock(org.springframework.jdbc.core.JdbcTemplate.class),
                mock(com.aigicrm.module.system.service.SysConfigService.class),
                mock(OrgScopeResolver.class),
                access,
                mock(com.aigicrm.module.system.service.RolePermScopeService.class),
                approvalMapper,
                mock(com.aigicrm.module.system.mapper.SysLogMapper.class),
                mock(com.aigicrm.module.customer.service.CustomerEventService.class));

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
        when(customerMapper.selectByIdForUpdate(1L)).thenReturn(existing);
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
        when(customerMapper.selectByIdForUpdate(1L)).thenReturn(existing);
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
        when(customerMapper.selectByIdForUpdate(2L)).thenReturn(existing);
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
        when(customerMapper.selectByIdForUpdate(3L)).thenReturn(existing);
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
        when(customerMapper.selectByIdForUpdate(4L)).thenReturn(existing);
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
        when(customerMapper.selectByIdForUpdate(5L)).thenReturn(existing);
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
        when(customerMapper.selectByIdForUpdate(1L)).thenReturn(existing);
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
        when(customerMapper.selectByIdForUpdate(1L)).thenReturn(existing);
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

    /** 【D3 2026-10-18】新建/导入缺失注册日：服务端按当天兜底并算出保护期，不再产生空日期新客户。 */
    @Test
    void create_missingRegisterDate_fallsBackToToday_andSetsExpireDate() {
        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        CustomerService svc = newService(mock(BizInvoiceMapper.class), mock(BizPaymentMapper.class),
                customerMapper, mock(OrgScopeResolver.class));
        BizCustomer req = new BizCustomer();
        req.setCustomerName("无注册日客户");
        req.setRegisterDate(null);
        try (MockedStatic<SecurityUtils> ms = mockStatic(SecurityUtils.class)) {
            ms.when(SecurityUtils::isAdmin).thenReturn(false);
            ms.when(SecurityUtils::getCurrentUserId).thenReturn(100L);
            svc.create(req);
        }
        ArgumentCaptor<BizCustomer> captor = ArgumentCaptor.forClass(BizCustomer.class);
        verify(customerMapper).insert(captor.capture());
        BizCustomer saved = captor.getValue();
        assertEquals(java.time.LocalDate.now(), saved.getRegisterDate(), "缺失注册日必须按当天兜底");
        assertNotNull(saved.getExpireDate(), "兜底后必须生成保护期到期日");
        assertEquals(java.time.LocalDate.now().plusDays(365), saved.getExpireDate(),
                "默认保护期 365 天（config mock 返回 null 时走默认值, 2026-11-02 口径A）");
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
        when(customerMapper.selectByIdForUpdate(1L)).thenReturn(existing);
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
        // 【WP1.5】getCurrentScopeUserIds 改委托 CustomerVisibilityService——注入与其共用同一
        // resolver 桩的真实例, 使 4 个场景断言继续打到统一解析链(语义不变: self/all/null/dept_and_children)
        com.aigicrm.module.customer.service.CustomerVisibilityService visibility =
                new com.aigicrm.module.customer.service.CustomerVisibilityService(resolver,
                        mock(com.aigicrm.module.system.service.RolePermScopeService.class),
                        mock(SysUserMapper.class),
                        null);
        return new CustomerService(
                mock(BizCustomerMapper.class),
                visibility,
                mock(BizCustomerLogMapper.class),
                mock(BizInvoiceMapper.class),
                mock(BizCustomerShareMapper.class),
                mock(BizCustomerIndicatorMapper.class),
                mock(BizContactMapper.class),
                mock(BizPaymentMapper.class), mock(com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper.class), mock(com.aigicrm.module.sea.mapper.BizSeaPoolMapper.class),
                userMapper,
                deptMapper,
                mock(BizReminderMapper.class),
                mock(JdbcTemplate.class),
                mock(SysConfigService.class),
                resolver,
                mock(com.aigicrm.common.engine.CustomerAccessService.class),
                mock(com.aigicrm.module.system.service.RolePermScopeService.class), mock(com.aigicrm.module.approval.mapper.BizApprovalMapper.class),
                mock(com.aigicrm.module.system.mapper.SysLogMapper.class),
                mock(com.aigicrm.module.customer.service.CustomerEventService.class));
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
                customerMapper, null, mock(BizCustomerLogMapper.class), mock(BizInvoiceMapper.class),
                mock(BizCustomerShareMapper.class), mock(BizCustomerIndicatorMapper.class),
                mock(BizContactMapper.class), mock(BizPaymentMapper.class), mock(com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper.class), mock(com.aigicrm.module.sea.mapper.BizSeaPoolMapper.class),
                userMapper, mock(SysDeptMapper.class), mock(BizReminderMapper.class),
                mock(JdbcTemplate.class), mock(SysConfigService.class),
                mock(OrgScopeResolver.class), mock(CustomerAccessService.class),
                mock(com.aigicrm.module.system.service.RolePermScopeService.class), mock(com.aigicrm.module.approval.mapper.BizApprovalMapper.class),
                mock(com.aigicrm.module.system.mapper.SysLogMapper.class),
                mock(com.aigicrm.module.customer.service.CustomerEventService.class));
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
                customerMapper, null, mock(BizCustomerLogMapper.class), mock(BizInvoiceMapper.class),
                mock(BizCustomerShareMapper.class), mock(BizCustomerIndicatorMapper.class),
                mock(BizContactMapper.class), mock(BizPaymentMapper.class), mock(com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper.class), mock(com.aigicrm.module.sea.mapper.BizSeaPoolMapper.class),
                userMapper, mock(SysDeptMapper.class), mock(BizReminderMapper.class),
                mock(JdbcTemplate.class), mock(SysConfigService.class),
                mock(OrgScopeResolver.class), mock(CustomerAccessService.class),
                mock(com.aigicrm.module.system.service.RolePermScopeService.class), mock(com.aigicrm.module.approval.mapper.BizApprovalMapper.class),
                mock(com.aigicrm.module.system.mapper.SysLogMapper.class),
                mock(com.aigicrm.module.customer.service.CustomerEventService.class));
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
        when(customerMapper.selectByIdForUpdate(1L)).thenReturn(existing);
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
                cm, null, mock(BizCustomerLogMapper.class), mock(BizInvoiceMapper.class),
                mock(BizCustomerShareMapper.class), mock(BizCustomerIndicatorMapper.class),
                mock(BizContactMapper.class), mock(BizPaymentMapper.class), mock(com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper.class), mock(com.aigicrm.module.sea.mapper.BizSeaPoolMapper.class),
                um, mock(SysDeptMapper.class), mock(BizReminderMapper.class),
                mock(JdbcTemplate.class), mock(SysConfigService.class),
                mock(OrgScopeResolver.class), accessService,
                mock(com.aigicrm.module.system.service.RolePermScopeService.class), mock(com.aigicrm.module.approval.mapper.BizApprovalMapper.class),
                mock(com.aigicrm.module.system.mapper.SysLogMapper.class),
                mock(com.aigicrm.module.customer.service.CustomerEventService.class));

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
        when(customerMapper.selectByIdForUpdate(1L)).thenReturn(existing);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysUser newOwner = new SysUser();
        newOwner.setId(300L); newOwner.setDeptId(null); // 新负责人无部门
        when(userMapper.selectById(300L)).thenReturn(newOwner);

        CustomerAccessService accessService = mock(CustomerAccessService.class);
        when(accessService.canEdit(1L)).thenReturn(true);
        when(accessService.isInSeaPool(1L)).thenReturn(false);

        CustomerService svc = new CustomerService(
                customerMapper, null, mock(BizCustomerLogMapper.class), mock(BizInvoiceMapper.class),
                mock(BizCustomerShareMapper.class), mock(BizCustomerIndicatorMapper.class),
                mock(BizContactMapper.class), mock(BizPaymentMapper.class), mock(com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper.class), mock(com.aigicrm.module.sea.mapper.BizSeaPoolMapper.class),
                userMapper, mock(SysDeptMapper.class), mock(BizReminderMapper.class),
                mock(JdbcTemplate.class), mock(SysConfigService.class),
                mock(OrgScopeResolver.class), accessService,
                mock(com.aigicrm.module.system.service.RolePermScopeService.class), mock(com.aigicrm.module.approval.mapper.BizApprovalMapper.class),
                mock(com.aigicrm.module.system.mapper.SysLogMapper.class),
                mock(com.aigicrm.module.customer.service.CustomerEventService.class));

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
        when(customerMapper.selectByIdForUpdate(1L)).thenReturn(existing);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysUser owner = new SysUser();
        owner.setId(200L); owner.setDeptId(9L); // owner 已调整到部门 9
        when(userMapper.selectById(200L)).thenReturn(owner);

        CustomerAccessService accessService = mock(CustomerAccessService.class);
        when(accessService.canEdit(1L)).thenReturn(true);
        when(accessService.isInSeaPool(1L)).thenReturn(false);

        CustomerService svc = new CustomerService(
                customerMapper, null, mock(BizCustomerLogMapper.class), mock(BizInvoiceMapper.class),
                mock(BizCustomerShareMapper.class), mock(BizCustomerIndicatorMapper.class),
                mock(BizContactMapper.class), mock(BizPaymentMapper.class), mock(com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper.class), mock(com.aigicrm.module.sea.mapper.BizSeaPoolMapper.class),
                userMapper, mock(SysDeptMapper.class), mock(BizReminderMapper.class),
                mock(JdbcTemplate.class), mock(SysConfigService.class),
                mock(OrgScopeResolver.class), accessService,
                mock(com.aigicrm.module.system.service.RolePermScopeService.class), mock(com.aigicrm.module.approval.mapper.BizApprovalMapper.class),
                mock(com.aigicrm.module.system.mapper.SysLogMapper.class),
                mock(com.aigicrm.module.customer.service.CustomerEventService.class));

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

    // 【P0-2 2026-11-12】旧 rankings 集团过滤用例(PR-22)随休眠端点整体退役(端点/三段 SQL 已删)

    /** 【PR-22 批次四】共享不得跨集团:目标用户不在当前集团 → 拒绝,不落共享行/不发育误通知 */
    @Test
    void addShares_crossGroupTarget_rejected() {
        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        when(customerMapper.selectById(501L)).thenReturn(new com.aigicrm.module.customer.entity.BizCustomer());
        org.springframework.jdbc.core.JdbcTemplate jdbc = mock(org.springframework.jdbc.core.JdbcTemplate.class);
        // 集团成员经 GroupScopeUtil 静态 mock 返回(仅 7);目标 66 属别集团
        CustomerService svc = new CustomerService(
                customerMapper, null, mock(com.aigicrm.module.customer.mapper.BizCustomerLogMapper.class),
                mock(BizInvoiceMapper.class), mock(com.aigicrm.module.customer.mapper.BizCustomerShareMapper.class),
                mock(com.aigicrm.module.customer.mapper.BizCustomerIndicatorMapper.class),
                mock(BizContactMapper.class), mock(BizPaymentMapper.class), mock(com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper.class), mock(com.aigicrm.module.sea.mapper.BizSeaPoolMapper.class),
                mock(com.aigicrm.module.system.mapper.SysUserMapper.class),
                mock(com.aigicrm.module.system.mapper.SysDeptMapper.class),
                mock(com.aigicrm.module.reminder.mapper.BizReminderMapper.class),
                jdbc, mock(com.aigicrm.module.system.service.SysConfigService.class),
                mock(com.aigicrm.common.scope.OrgScopeResolver.class),
                mock(CustomerAccessService.class),
                mock(com.aigicrm.module.system.service.RolePermScopeService.class), mock(com.aigicrm.module.approval.mapper.BizApprovalMapper.class),
                mock(com.aigicrm.module.system.mapper.SysLogMapper.class),
                mock(com.aigicrm.module.customer.service.CustomerEventService.class));
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
                customerMapper, null, mock(com.aigicrm.module.customer.mapper.BizCustomerLogMapper.class),
                mock(BizInvoiceMapper.class), mock(com.aigicrm.module.customer.mapper.BizCustomerShareMapper.class),
                indicatorMapper, mock(BizContactMapper.class), mock(BizPaymentMapper.class), mock(com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper.class), mock(com.aigicrm.module.sea.mapper.BizSeaPoolMapper.class),
                mock(com.aigicrm.module.system.mapper.SysUserMapper.class),
                mock(com.aigicrm.module.system.mapper.SysDeptMapper.class),
                mock(com.aigicrm.module.reminder.mapper.BizReminderMapper.class),
                mock(org.springframework.jdbc.core.JdbcTemplate.class),
                mock(com.aigicrm.module.system.service.SysConfigService.class),
                mock(com.aigicrm.common.scope.OrgScopeResolver.class),
                mock(CustomerAccessService.class),
                mock(com.aigicrm.module.system.service.RolePermScopeService.class), mock(com.aigicrm.module.approval.mapper.BizApprovalMapper.class),
                mock(com.aigicrm.module.system.mapper.SysLogMapper.class),
                mock(com.aigicrm.module.customer.service.CustomerEventService.class));
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
                customerMapper, null, mock(com.aigicrm.module.customer.mapper.BizCustomerLogMapper.class),
                mock(BizInvoiceMapper.class), mock(com.aigicrm.module.customer.mapper.BizCustomerShareMapper.class),
                indicatorMapper, mock(BizContactMapper.class), mock(BizPaymentMapper.class), mock(com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper.class), mock(com.aigicrm.module.sea.mapper.BizSeaPoolMapper.class),
                mock(com.aigicrm.module.system.mapper.SysUserMapper.class),
                mock(com.aigicrm.module.system.mapper.SysDeptMapper.class),
                mock(com.aigicrm.module.reminder.mapper.BizReminderMapper.class),
                mock(org.springframework.jdbc.core.JdbcTemplate.class),
                mock(com.aigicrm.module.system.service.SysConfigService.class),
                mock(com.aigicrm.common.scope.OrgScopeResolver.class),
                mock(CustomerAccessService.class),
                mock(com.aigicrm.module.system.service.RolePermScopeService.class), mock(com.aigicrm.module.approval.mapper.BizApprovalMapper.class),
                mock(com.aigicrm.module.system.mapper.SysLogMapper.class),
                mock(com.aigicrm.module.customer.service.CustomerEventService.class));
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
                customerMapper, null, mock(com.aigicrm.module.customer.mapper.BizCustomerLogMapper.class),
                invoiceMapper, mock(com.aigicrm.module.customer.mapper.BizCustomerShareMapper.class),
                indicatorMapper, mock(BizContactMapper.class), paymentMapper, mock(com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper.class), mock(com.aigicrm.module.sea.mapper.BizSeaPoolMapper.class),
                mock(com.aigicrm.module.system.mapper.SysUserMapper.class),
                mock(com.aigicrm.module.system.mapper.SysDeptMapper.class),
                mock(com.aigicrm.module.reminder.mapper.BizReminderMapper.class),
                mock(org.springframework.jdbc.core.JdbcTemplate.class),
                mock(com.aigicrm.module.system.service.SysConfigService.class),
                mock(com.aigicrm.common.scope.OrgScopeResolver.class),
                mock(CustomerAccessService.class),
                mock(com.aigicrm.module.system.service.RolePermScopeService.class), mock(com.aigicrm.module.approval.mapper.BizApprovalMapper.class),
                mock(com.aigicrm.module.system.mapper.SysLogMapper.class),
                mock(com.aigicrm.module.customer.service.CustomerEventService.class));
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
                customerMapper, null, mock(com.aigicrm.module.customer.mapper.BizCustomerLogMapper.class),
                invoiceMapper, mock(com.aigicrm.module.customer.mapper.BizCustomerShareMapper.class),
                mock(com.aigicrm.module.customer.mapper.BizCustomerIndicatorMapper.class),
                mock(BizContactMapper.class), paymentMapper, mock(com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper.class), mock(com.aigicrm.module.sea.mapper.BizSeaPoolMapper.class),
                mock(com.aigicrm.module.system.mapper.SysUserMapper.class),
                mock(com.aigicrm.module.system.mapper.SysDeptMapper.class),
                mock(com.aigicrm.module.reminder.mapper.BizReminderMapper.class),
                mock(org.springframework.jdbc.core.JdbcTemplate.class),
                mock(com.aigicrm.module.system.service.SysConfigService.class),
                mock(com.aigicrm.common.scope.OrgScopeResolver.class),
                accessService,
                mock(com.aigicrm.module.system.service.RolePermScopeService.class), mock(com.aigicrm.module.approval.mapper.BizApprovalMapper.class),
                mock(com.aigicrm.module.system.mapper.SysLogMapper.class),
                mock(com.aigicrm.module.customer.service.CustomerEventService.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> svc.delete(501L));
        assertTrue(ex.getMessage().contains("不允许删除"), "实际: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("3 笔开票"), "提示须含影响范围: " + ex.getMessage());
        // 【文案修复 2026-10-09】拒删指引必须指向真实存在的功能"归档"(系统无客户域"停用")
        assertTrue(ex.getMessage().contains("归档"), "指引须指向归档入口: " + ex.getMessage());
        assertFalse(ex.getMessage().contains("停用"), "不得再指引不存在的'停用': " + ex.getMessage());
        verify(customerMapper, never()).deleteById(any(Long.class));
    }

    // 【P0-1 2026-11-12 外部审计 CRITICAL】现行载体负例: 仅阶段金额(legacy 空)也必须拒删——
    // 2026-10-10 财务载体迁移后新建客户开票/回款只落 stage_amount, 原判定对其完全失效
    @Test
    void delete_withStageAmountsOnly_rejected() {
        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        when(customerMapper.selectById(601L)).thenReturn(new com.aigicrm.module.customer.entity.BizCustomer());
        BizInvoiceMapper invoiceMapper = mock(BizInvoiceMapper.class);
        when(invoiceMapper.selectCount(any())).thenReturn(0L); // legacy 开票恒空(迁移后新建客户)
        BizPaymentMapper paymentMapper = mock(BizPaymentMapper.class);
        when(paymentMapper.selectCount(any())).thenReturn(0L);
        com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper stageAmountMapper =
                mock(com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper.class);
        when(stageAmountMapper.selectCount(any())).thenReturn(2L); // 现行载体有 2 条阶段金额
        CustomerAccessService accessService = mock(CustomerAccessService.class);
        when(accessService.canDelete(601L)).thenReturn(true);

        CustomerService svc = new CustomerService(
                customerMapper, null, mock(com.aigicrm.module.customer.mapper.BizCustomerLogMapper.class),
                invoiceMapper, mock(com.aigicrm.module.customer.mapper.BizCustomerShareMapper.class),
                mock(com.aigicrm.module.customer.mapper.BizCustomerIndicatorMapper.class),
                mock(BizContactMapper.class), paymentMapper, stageAmountMapper, mock(com.aigicrm.module.sea.mapper.BizSeaPoolMapper.class),
                mock(com.aigicrm.module.system.mapper.SysUserMapper.class),
                mock(com.aigicrm.module.system.mapper.SysDeptMapper.class),
                mock(com.aigicrm.module.reminder.mapper.BizReminderMapper.class),
                mock(org.springframework.jdbc.core.JdbcTemplate.class),
                mock(com.aigicrm.module.system.service.SysConfigService.class),
                mock(com.aigicrm.common.scope.OrgScopeResolver.class),
                accessService,
                mock(com.aigicrm.module.system.service.RolePermScopeService.class), mock(com.aigicrm.module.approval.mapper.BizApprovalMapper.class),
                mock(com.aigicrm.module.system.mapper.SysLogMapper.class),
                mock(com.aigicrm.module.customer.service.CustomerEventService.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> svc.delete(601L));
        assertTrue(ex.getMessage().contains("不允许删除"), "仅阶段金额也须拒删: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("2 条阶段金额记录"), "提示须含现行载体影响范围: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("归档"), "指引须指向归档入口: " + ex.getMessage());
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
                customerMapper, null, mock(com.aigicrm.module.customer.mapper.BizCustomerLogMapper.class),
                invoiceMapper, mock(com.aigicrm.module.customer.mapper.BizCustomerShareMapper.class),
                mock(com.aigicrm.module.customer.mapper.BizCustomerIndicatorMapper.class),
                mock(BizContactMapper.class), paymentMapper, mock(com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper.class), mock(com.aigicrm.module.sea.mapper.BizSeaPoolMapper.class),
                mock(com.aigicrm.module.system.mapper.SysUserMapper.class),
                mock(com.aigicrm.module.system.mapper.SysDeptMapper.class),
                mock(com.aigicrm.module.reminder.mapper.BizReminderMapper.class),
                mock(org.springframework.jdbc.core.JdbcTemplate.class),
                mock(com.aigicrm.module.system.service.SysConfigService.class),
                mock(com.aigicrm.common.scope.OrgScopeResolver.class),
                accessService,
                mock(com.aigicrm.module.system.service.RolePermScopeService.class), mock(com.aigicrm.module.approval.mapper.BizApprovalMapper.class),
                mock(com.aigicrm.module.system.mapper.SysLogMapper.class),
                mock(com.aigicrm.module.customer.service.CustomerEventService.class));

        assertDoesNotThrow(() -> svc.delete(501L));
        verify(customerMapper).deleteById(501L);
    }

    /**
     * 【审计 data-2 修复 2026-10-18】客户列表删除不得留下 active 公海池行:
     * 存在 available/pending_approval 池行时拒绝,提示先到公海管理删除(池级权限路径),
     * 且不得静默删除客户或池行。
     */
    @Test
    void delete_customerInActiveSeaPool_rejectedWithSeaManagementHint() {
        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        BizCustomer customer = new BizCustomer();
        customer.setId(501L);
        when(customerMapper.selectById(501L)).thenReturn(customer);
        BizInvoiceMapper invoiceMapper = mock(BizInvoiceMapper.class);
        when(invoiceMapper.selectCount(any())).thenReturn(0L);
        BizPaymentMapper paymentMapper = mock(BizPaymentMapper.class);
        when(paymentMapper.selectCount(any())).thenReturn(0L);
        BizSeaPoolMapper seaPoolMapper = mock(BizSeaPoolMapper.class);
        when(seaPoolMapper.selectCount(any())).thenReturn(1L);
        CustomerAccessService accessService = mock(CustomerAccessService.class);
        when(accessService.canDelete(501L)).thenReturn(true);

        CustomerService svc = new CustomerService(
                customerMapper, null, mock(BizCustomerLogMapper.class),
                invoiceMapper, mock(BizCustomerShareMapper.class),
                mock(BizCustomerIndicatorMapper.class),
                mock(BizContactMapper.class), paymentMapper,
                mock(com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper.class),
                seaPoolMapper,
                mock(SysUserMapper.class), mock(SysDeptMapper.class),
                mock(BizReminderMapper.class), mock(JdbcTemplate.class),
                mock(SysConfigService.class), mock(OrgScopeResolver.class),
                accessService,
                mock(com.aigicrm.module.system.service.RolePermScopeService.class),
                mock(com.aigicrm.module.approval.mapper.BizApprovalMapper.class),
                mock(com.aigicrm.module.system.mapper.SysLogMapper.class),
                mock(com.aigicrm.module.customer.service.CustomerEventService.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> svc.delete(501L));
        assertTrue(ex.getMessage().contains("请先在公海管理删除"), "提示须指向公海管理入口: " + ex.getMessage());
        verify(customerMapper, never()).deleteById(anyLong());
        // 活动池行判据=available/pending_approval(claimed 是历史行, 不阻断)
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<BizSeaPool>> cap =
                ArgumentCaptor.forClass((Class) LambdaQueryWrapper.class);
        verify(seaPoolMapper).selectCount(cap.capture());
        LambdaQueryWrapper<BizSeaPool> w = cap.getValue();
        assertTrue(w.getSqlSegment().contains("customer_id") && w.getSqlSegment().contains("status"),
                "池行判据须按客户+状态定位: " + w.getSqlSegment());
        assertTrue(w.getParamNameValuePairs().containsValue("available"), "available 在阻断范围");
        assertTrue(w.getParamNameValuePairs().containsValue("pending_approval"), "pending_approval 在阻断范围");
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
                customerMapper, null, mock(com.aigicrm.module.customer.mapper.BizCustomerLogMapper.class),
                mock(BizInvoiceMapper.class), mock(com.aigicrm.module.customer.mapper.BizCustomerShareMapper.class),
                mock(com.aigicrm.module.customer.mapper.BizCustomerIndicatorMapper.class),
                mock(BizContactMapper.class), mock(BizPaymentMapper.class), mock(com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper.class), mock(com.aigicrm.module.sea.mapper.BizSeaPoolMapper.class),
                mock(com.aigicrm.module.system.mapper.SysUserMapper.class),
                mock(com.aigicrm.module.system.mapper.SysDeptMapper.class),
                mock(com.aigicrm.module.reminder.mapper.BizReminderMapper.class),
                mock(org.springframework.jdbc.core.JdbcTemplate.class),
                mock(com.aigicrm.module.system.service.SysConfigService.class),
                mock(com.aigicrm.common.scope.OrgScopeResolver.class),
                accessService,
                mock(com.aigicrm.module.system.service.RolePermScopeService.class), mock(com.aigicrm.module.approval.mapper.BizApprovalMapper.class),
                mock(com.aigicrm.module.system.mapper.SysLogMapper.class),
                mock(com.aigicrm.module.customer.service.CustomerEventService.class));

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
                customerMapper, null, mock(BizCustomerLogMapper.class),
                invoiceMapper, shareMapper,
                mock(BizCustomerIndicatorMapper.class),
                mock(BizContactMapper.class), paymentMapper, mock(com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper.class), mock(com.aigicrm.module.sea.mapper.BizSeaPoolMapper.class),
                mock(SysUserMapper.class), mock(SysDeptMapper.class),
                reminderMapper, mock(JdbcTemplate.class), mock(SysConfigService.class),
                mock(OrgScopeResolver.class), accessService,
                mock(com.aigicrm.module.system.service.RolePermScopeService.class),
                mock(com.aigicrm.module.approval.mapper.BizApprovalMapper.class),
                mock(com.aigicrm.module.system.mapper.SysLogMapper.class),
                mock(com.aigicrm.module.customer.service.CustomerEventService.class));

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
                customerMapper, null, mock(BizCustomerLogMapper.class),
                mock(BizInvoiceMapper.class), shareMapper,
                mock(BizCustomerIndicatorMapper.class),
                mock(BizContactMapper.class), mock(BizPaymentMapper.class), mock(com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper.class), mock(com.aigicrm.module.sea.mapper.BizSeaPoolMapper.class),
                mock(SysUserMapper.class), mock(SysDeptMapper.class),
                reminderMapper, mock(JdbcTemplate.class), mock(SysConfigService.class),
                mock(OrgScopeResolver.class), accessService,
                mock(com.aigicrm.module.system.service.RolePermScopeService.class),
                mock(com.aigicrm.module.approval.mapper.BizApprovalMapper.class),
                mock(com.aigicrm.module.system.mapper.SysLogMapper.class),
                mock(com.aigicrm.module.customer.service.CustomerEventService.class));

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
                mock(BizCustomerMapper.class), null, mock(BizCustomerLogMapper.class),
                mock(BizInvoiceMapper.class), shareMapper,
                mock(BizCustomerIndicatorMapper.class), mock(BizContactMapper.class),
                mock(BizPaymentMapper.class), mock(com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper.class), mock(com.aigicrm.module.sea.mapper.BizSeaPoolMapper.class), mock(SysUserMapper.class), mock(SysDeptMapper.class),
                reminderMapper, mock(JdbcTemplate.class), mock(SysConfigService.class),
                mock(OrgScopeResolver.class), mock(CustomerAccessService.class),
                mock(com.aigicrm.module.system.service.RolePermScopeService.class),
                mock(com.aigicrm.module.approval.mapper.BizApprovalMapper.class),
                mock(com.aigicrm.module.system.mapper.SysLogMapper.class),
                mock(com.aigicrm.module.customer.service.CustomerEventService.class));

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
                mock(BizCustomerMapper.class), null, mock(BizCustomerLogMapper.class),
                mock(BizInvoiceMapper.class), shareMapper,
                mock(BizCustomerIndicatorMapper.class), mock(BizContactMapper.class),
                mock(BizPaymentMapper.class), mock(com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper.class), mock(com.aigicrm.module.sea.mapper.BizSeaPoolMapper.class), mock(SysUserMapper.class), mock(SysDeptMapper.class),
                reminderMapper, mock(JdbcTemplate.class), mock(SysConfigService.class),
                mock(OrgScopeResolver.class), mock(CustomerAccessService.class),
                mock(com.aigicrm.module.system.service.RolePermScopeService.class),
                mock(com.aigicrm.module.approval.mapper.BizApprovalMapper.class),
                mock(com.aigicrm.module.system.mapper.SysLogMapper.class),
                mock(com.aigicrm.module.customer.service.CustomerEventService.class));

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
                customerMapper, null, mock(BizCustomerLogMapper.class),
                invoiceMapper, mock(BizCustomerShareMapper.class),
                indicatorMapper, mock(BizContactMapper.class), paymentMapper, mock(com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper.class), mock(com.aigicrm.module.sea.mapper.BizSeaPoolMapper.class),
                mock(SysUserMapper.class), mock(SysDeptMapper.class),
                mock(BizReminderMapper.class), mock(JdbcTemplate.class),
                mock(SysConfigService.class), mock(OrgScopeResolver.class),
                mock(CustomerAccessService.class),
                mock(com.aigicrm.module.system.service.RolePermScopeService.class),
                mock(com.aigicrm.module.approval.mapper.BizApprovalMapper.class),
                mock(com.aigicrm.module.system.mapper.SysLogMapper.class),
                mock(com.aigicrm.module.customer.service.CustomerEventService.class));

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
        java.time.LocalDate expected = java.time.LocalDate.of(2026, 9, 8).plusDays(365).plusDays(360);
        assertEquals(expected, customer.getExpireDate(),
                "expire 应=注册(捞取)日+360+已批延期, 实际: " + customer.getExpireDate());
        assertTrue(customer.getExpireDate().isAfter(java.time.LocalDate.now()), "捞取后保护期必须在未来");
    }

    // ===== 【测试H1修复 2026-10-13】auto_share users 元素类型归一(字符串数字/非法值不再 500) =====

    private CustomerService newAutoShareService(SysConfigService configService) {
        return new CustomerService(
                mock(BizCustomerMapper.class),
                null,
                mock(BizCustomerLogMapper.class),
                mock(BizInvoiceMapper.class),
                mock(BizCustomerShareMapper.class),
                mock(BizCustomerIndicatorMapper.class),
                mock(BizContactMapper.class),
                mock(BizPaymentMapper.class),
                mock(com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper.class), mock(com.aigicrm.module.sea.mapper.BizSeaPoolMapper.class),
                mock(SysUserMapper.class),
                mock(SysDeptMapper.class),
                mock(BizReminderMapper.class),
                mock(JdbcTemplate.class),
                configService,
                mock(OrgScopeResolver.class),
                mock(com.aigicrm.common.engine.CustomerAccessService.class),
                mock(com.aigicrm.module.system.service.RolePermScopeService.class),
                mock(com.aigicrm.module.approval.mapper.BizApprovalMapper.class),
                mock(com.aigicrm.module.system.mapper.SysLogMapper.class),
                mock(com.aigicrm.module.customer.service.CustomerEventService.class));
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

    @Test
    void autoShare_rolesHitButDataScopeAll_notNarrowed() {
        // 【F18 2026-10-13】dataScope=all(销售总监形态): roles 规则命中也不得把列表从全量收窄到组织——
        // 交付前审查实测 NO001 列表仅 16 条而工作台全量 285, 列表与统计口径自相矛盾。
        SysConfigService configService = mock(SysConfigService.class);
        when(configService.getValue("customer.auto_share")).thenReturn(
                "{\"superior\":false,\"admin\":false,\"roles\":[\"0\"],\"users\":[]}");
        SysUserMapper userMapper = mock(SysUserMapper.class);
        com.aigicrm.module.system.entity.SysUser orgMate = new com.aigicrm.module.system.entity.SysUser();
        orgMate.setId(1L);
        when(userMapper.selectList(any())).thenReturn(List.of(orgMate));
        OrgScopeResolver resolver = mock(OrgScopeResolver.class);
        when(resolver.resolveScope(any())).thenReturn(com.aigicrm.common.scope.ScopeDecision.all());
        CustomerService service = new CustomerService(
                mock(BizCustomerMapper.class),
                null,
                mock(BizCustomerLogMapper.class),
                mock(BizInvoiceMapper.class),
                mock(BizCustomerShareMapper.class),
                mock(BizCustomerIndicatorMapper.class),
                mock(BizContactMapper.class),
                mock(BizPaymentMapper.class),
                mock(com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper.class), mock(com.aigicrm.module.sea.mapper.BizSeaPoolMapper.class),
                userMapper,
                mock(SysDeptMapper.class),
                mock(BizReminderMapper.class),
                mock(JdbcTemplate.class),
                configService,
                resolver,
                mock(com.aigicrm.common.engine.CustomerAccessService.class),
                mock(com.aigicrm.module.system.service.RolePermScopeService.class),
                mock(com.aigicrm.module.approval.mapper.BizApprovalMapper.class),
                mock(com.aigicrm.module.system.mapper.SysLogMapper.class),
                mock(com.aigicrm.module.customer.service.CustomerEventService.class));
        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::isAdmin).thenReturn(false);
            sec.when(SecurityUtils::getCurrentUserId).thenReturn(11L);
            sec.when(SecurityUtils::getCurrentUser).thenReturn(mock(SecurityUser.class));
            sec.when(SecurityUtils::getCurrentRoleCodes).thenReturn(List.of("0"));
            sec.when(SecurityUtils::getCurrentOrgId).thenReturn(1L);
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BizCustomer> w =
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
            ReflectionTestUtils.invokeMethod(service, "applyAutoShare", w);
            Map<String, List<Long>> cols = columnValues(w);
            assertFalse(cols.containsKey("owner_id"),
                    "dataScope=all 用户不应被 auto_share roles 收窄到组织, 实际: " + cols);
        }
    }

    @Test
    void autoShare_rolesHitAndScopeNotAll_stillNarrowed() {
        // 【F18 复审钉 2026-10-13】不豁免侧: dataScope 非 all 且 roles 命中 → 仍收窄到组织成员。
        // isDataScopeAll 若被误改宽(非 all 用户也被豁免), auto_share 的"同组织互看"会静默失效。
        SysConfigService configService = mock(SysConfigService.class);
        when(configService.getValue("customer.auto_share")).thenReturn(
                "{\"superior\":false,\"admin\":false,\"roles\":[\"0\"],\"users\":[]}");
        SysUserMapper userMapper = mock(SysUserMapper.class);
        com.aigicrm.module.system.entity.SysUser orgMate = new com.aigicrm.module.system.entity.SysUser();
        orgMate.setId(1L);
        when(userMapper.selectList(any())).thenReturn(List.of(orgMate));
        OrgScopeResolver resolver = mock(OrgScopeResolver.class);
        com.aigicrm.common.scope.ScopeDecision notAll = new com.aigicrm.common.scope.ScopeDecision();
        notAll.setVisibleUserIds(List.of(11L)); // OWNER 锚点可见集合=仅本人 → 非 all
        when(resolver.resolveScope(any())).thenReturn(notAll);
        CustomerService service = new CustomerService(
                mock(BizCustomerMapper.class),
                null,
                mock(BizCustomerLogMapper.class),
                mock(BizInvoiceMapper.class),
                mock(BizCustomerShareMapper.class),
                mock(BizCustomerIndicatorMapper.class),
                mock(BizContactMapper.class),
                mock(BizPaymentMapper.class),
                mock(com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper.class), mock(com.aigicrm.module.sea.mapper.BizSeaPoolMapper.class),
                userMapper,
                mock(SysDeptMapper.class),
                mock(BizReminderMapper.class),
                mock(JdbcTemplate.class),
                configService,
                resolver,
                mock(com.aigicrm.common.engine.CustomerAccessService.class),
                mock(com.aigicrm.module.system.service.RolePermScopeService.class),
                mock(com.aigicrm.module.approval.mapper.BizApprovalMapper.class),
                mock(com.aigicrm.module.system.mapper.SysLogMapper.class),
                mock(com.aigicrm.module.customer.service.CustomerEventService.class));
        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::isAdmin).thenReturn(false);
            sec.when(SecurityUtils::getCurrentUserId).thenReturn(11L);
            sec.when(SecurityUtils::getCurrentUser).thenReturn(mock(SecurityUser.class));
            sec.when(SecurityUtils::getCurrentRoleCodes).thenReturn(List.of("0"));
            sec.when(SecurityUtils::getCurrentOrgId).thenReturn(1L);
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BizCustomer> w =
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
            ReflectionTestUtils.invokeMethod(service, "applyAutoShare", w);
            Map<String, List<Long>> cols = columnValues(w);
            assertEquals(List.of(11L, 1L), cols.get("owner_id"),
                    "非 all 用户 roles 命中应收窄到组织成员(本人+同组织用户), 实际: " + cols);
        }
    }

    // ===== 【L2 2026-10-12 测试报告】五项指标分值域 0-3 服务端校验 =====

    /** saveIndicator: 任一项越界(>3 或 <0)拒绝入库, 不触发任何 mapper 写 */
    @Test
    void saveIndicator_scoreOutOfRange_rejects() {
        com.aigicrm.module.customer.mapper.BizCustomerIndicatorMapper indicatorMapper =
                mock(BizCustomerIndicatorMapper.class);
        CustomerService svc = newIndicatorGuardService(indicatorMapper);
        com.aigicrm.module.customer.entity.BizCustomerIndicator data =
                new com.aigicrm.module.customer.entity.BizCustomerIndicator();
        data.setRelationshipScore(2);
        data.setIndicator03(5); // 越界: 字典四档 0-3

        BusinessException ex = assertThrows(BusinessException.class, () -> svc.saveIndicator(42L, data));
        assertTrue(ex.getMessage().contains("0-3"), "实际: " + ex.getMessage());
        verify(indicatorMapper, never()).insert(any(com.aigicrm.module.customer.entity.BizCustomerIndicator.class));
        verify(indicatorMapper, never()).updateById(any(com.aigicrm.module.customer.entity.BizCustomerIndicator.class));

        com.aigicrm.module.customer.entity.BizCustomerIndicator neg =
                new com.aigicrm.module.customer.entity.BizCustomerIndicator();
        neg.setIndicator05(-1); // 越界: 负值
        BusinessException ex2 = assertThrows(BusinessException.class, () -> svc.saveIndicator(42L, neg));
        assertTrue(ex2.getMessage().contains("0-3"), "实际: " + ex2.getMessage());
    }

    /** saveRelationshipScore: 单字段快捷路径同口径校验(前端直连接口可绕过四选项) */
    @Test
    void saveRelationshipScore_outOfRange_rejects() {
        com.aigicrm.module.customer.mapper.BizCustomerIndicatorMapper indicatorMapper =
                mock(BizCustomerIndicatorMapper.class);
        CustomerService svc = newIndicatorGuardService(indicatorMapper);

        BusinessException ex = assertThrows(BusinessException.class, () -> svc.saveRelationshipScore(42L, 4));
        assertTrue(ex.getMessage().contains("0-3"), "实际: " + ex.getMessage());
        BusinessException ex2 = assertThrows(BusinessException.class, () -> svc.saveRelationshipScore(42L, -1));
        assertTrue(ex2.getMessage().contains("0-3"), "实际: " + ex2.getMessage());
        verify(indicatorMapper, never()).insert(any(com.aigicrm.module.customer.entity.BizCustomerIndicator.class));
        verify(indicatorMapper, never()).updateById(any(com.aigicrm.module.customer.entity.BizCustomerIndicator.class));
    }

    /** 校验用例共用的最小 mock 组装(校验先于 mapper 访问, mapper 桩仅为防御性兜底) */
    private CustomerService newIndicatorGuardService(BizCustomerIndicatorMapper indicatorMapper) {
        return new CustomerService(
                mock(BizCustomerMapper.class), null, mock(BizCustomerLogMapper.class),
                mock(BizInvoiceMapper.class), mock(BizCustomerShareMapper.class),
                indicatorMapper, mock(BizContactMapper.class), mock(BizPaymentMapper.class),
                mock(com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper.class), mock(com.aigicrm.module.sea.mapper.BizSeaPoolMapper.class),
                mock(SysUserMapper.class), mock(SysDeptMapper.class),
                mock(BizReminderMapper.class), mock(JdbcTemplate.class),
                mock(SysConfigService.class), mock(OrgScopeResolver.class),
                mock(CustomerAccessService.class),
                mock(com.aigicrm.module.system.service.RolePermScopeService.class),
                mock(com.aigicrm.module.approval.mapper.BizApprovalMapper.class),
                mock(com.aigicrm.module.system.mapper.SysLogMapper.class),
                mock(com.aigicrm.module.customer.service.CustomerEventService.class));
    }

    /** 【延期规则收敛 2026-10-18】新开票且最近开票日前移 → 清零已批准延期:
     *  新基准上的保护期已覆盖旧延期,不得双重叠加(镜像对照: voidInvoice 删票回算不收回延期) */
    @Test
    void addInvoice_forwardDate_resetsApprovedExtendDays() {
        BizInvoiceMapper invoiceMapper = mock(BizInvoiceMapper.class);
        BizPaymentMapper paymentMapper = mock(BizPaymentMapper.class);
        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        BizCustomerIndicatorMapper indicatorMapper = mock(BizCustomerIndicatorMapper.class);

        CustomerService service = new CustomerService(
                customerMapper, null, mock(BizCustomerLogMapper.class),
                invoiceMapper, mock(BizCustomerShareMapper.class),
                indicatorMapper, mock(BizContactMapper.class), paymentMapper,
                mock(com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper.class), mock(com.aigicrm.module.sea.mapper.BizSeaPoolMapper.class),
                mock(SysUserMapper.class), mock(SysDeptMapper.class),
                mock(BizReminderMapper.class), mock(JdbcTemplate.class),
                mock(SysConfigService.class), mock(OrgScopeResolver.class),
                mock(CustomerAccessService.class),
                mock(com.aigicrm.module.system.service.RolePermScopeService.class),
                mock(com.aigicrm.module.approval.mapper.BizApprovalMapper.class),
                mock(com.aigicrm.module.system.mapper.SysLogMapper.class),
                mock(com.aigicrm.module.customer.service.CustomerEventService.class));

        // 旧票 2025-06-01 基准 + 已批延期 360;再开一张 2026-10-17 的新票
        BizInvoice fresh = new BizInvoice();
        fresh.setId(9L);
        fresh.setInvoiceDate(java.time.LocalDate.of(2026, 10, 17));
        when(invoiceMapper.selectList(any())).thenReturn(List.of(fresh));
        when(invoiceMapper.sumAmountByCustomerId(1L)).thenReturn(java.math.BigDecimal.ZERO);
        when(paymentMapper.sumAmountByCustomerId(1L)).thenReturn(java.math.BigDecimal.ZERO);
        when(indicatorMapper.selectOne(any())).thenReturn(null);

        BizCustomer customer = new BizCustomer();
        customer.setId(1L);
        customer.setRegisterDate(java.time.LocalDate.of(2025, 1, 1));
        customer.setLastInvoiceDate(java.time.LocalDate.of(2025, 6, 1));
        customer.setApprovedExtendDays(360);
        when(customerMapper.selectById(1L)).thenReturn(customer);

        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUserId).thenReturn(7L);
            security.when(SecurityUtils::getCurrentUser).thenReturn(null);
            service.addInvoice(1L, fresh);
        }

        assertEquals(0, customer.getApprovedExtendDays(), "新开票且基准日前移必须清零已批准延期");
        // 新基准=max(新票 2026-10-17, 注册 2025-01-01)=新票日: expire=新票日+365+0(旧延期不再叠加)
        assertEquals(java.time.LocalDate.of(2026, 10, 17), customer.getLastInvoiceDate());
        assertEquals(java.time.LocalDate.of(2026, 10, 17).plusDays(365), customer.getExpireDate(),
                "实际: " + customer.getExpireDate());
        // 前移时清零+重算一次性落库(advanceLastInvoiceDate 单次 updateById);不再有独立清零写
        verify(customerMapper, times(1)).updateById(customer);
    }

    /**
     * 【延期规则收敛 2026-10-18·审计 biz-0】补录早于当前最近开票日的旧票不算前移:
     * 不得清零已批延期、不得回退最近开票日、不得重算到期日——旧实现在 addInvoice 里
     * 无条件清零,补录历史票会误伤已批延期。
     */
    @Test
    void addInvoice_backfilledEarlierInvoice_keepsApprovedExtendDaysAndBaseline() {
        BizInvoiceMapper invoiceMapper = mock(BizInvoiceMapper.class);
        BizPaymentMapper paymentMapper = mock(BizPaymentMapper.class);
        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        BizCustomerIndicatorMapper indicatorMapper = mock(BizCustomerIndicatorMapper.class);

        CustomerService service = new CustomerService(
                customerMapper, null, mock(BizCustomerLogMapper.class),
                invoiceMapper, mock(BizCustomerShareMapper.class),
                indicatorMapper, mock(BizContactMapper.class), paymentMapper,
                mock(com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper.class),
                mock(com.aigicrm.module.sea.mapper.BizSeaPoolMapper.class),
                mock(SysUserMapper.class), mock(SysDeptMapper.class),
                mock(BizReminderMapper.class), mock(JdbcTemplate.class),
                mock(SysConfigService.class), mock(OrgScopeResolver.class),
                mock(CustomerAccessService.class),
                mock(com.aigicrm.module.system.service.RolePermScopeService.class),
                mock(com.aigicrm.module.approval.mapper.BizApprovalMapper.class),
                mock(com.aigicrm.module.system.mapper.SysLogMapper.class),
                mock(com.aigicrm.module.customer.service.CustomerEventService.class));

        java.time.LocalDate newest = java.time.LocalDate.now().minusDays(30);
        BizInvoice existingNewest = new BizInvoice();
        existingNewest.setId(10L);
        existingNewest.setInvoiceDate(newest);
        // 补录旧票后, 最近有效开票仍是库里已有的新票(推导查询口径)
        when(invoiceMapper.selectList(any())).thenReturn(List.of(existingNewest));
        when(indicatorMapper.selectOne(any())).thenReturn(null);

        java.time.LocalDate oldDate = java.time.LocalDate.now().minusYears(2);
        BizInvoice backfilled = new BizInvoice();
        backfilled.setId(11L);
        backfilled.setInvoiceDate(oldDate);
        BizCustomer customer = new BizCustomer();
        customer.setId(1L);
        customer.setRegisterDate(java.time.LocalDate.now().minusYears(3));
        customer.setLastInvoiceDate(newest);
        customer.setApprovedExtendDays(360);
        java.time.LocalDate expireBefore = newest.plusDays(360).plusDays(360);
        customer.setExpireDate(expireBefore);
        when(customerMapper.selectById(1L)).thenReturn(customer);

        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUserId).thenReturn(7L);
            security.when(SecurityUtils::getCurrentUser).thenReturn(null);
            service.addInvoice(1L, backfilled);
        }

        assertEquals(360, customer.getApprovedExtendDays(), "补录旧票不得清零已批延期");
        assertEquals(newest, customer.getLastInvoiceDate(), "补录旧票不得回退最近开票日");
        assertEquals(expireBefore, customer.getExpireDate(), "未前移不得重算到期日");
        verify(invoiceMapper).insert(backfilled);
        verify(customerMapper, never()).updateById(any(BizCustomer.class));
    }

    // ===== 【审查 backend-4 2026-10-18】财务写入口客户存在/未删/同集团自守 =====

    @Test
    void addInvoice_guardRejected_noLedgerInsert() {
        BizInvoiceMapper invoiceMapper = mock(BizInvoiceMapper.class);
        CustomerAccessService accessService = mock(CustomerAccessService.class);
        org.mockito.Mockito.doThrow(new BusinessException("客户不存在或已删除，无法录入财务记录"))
                .when(accessService).assertFinanceCustomerWritable(501L);
        CustomerService svc = newService(invoiceMapper, mock(BizPaymentMapper.class),
                mock(BizCustomerMapper.class), mock(OrgScopeResolver.class),
                mock(BizContactMapper.class), accessService);

        assertThrows(BusinessException.class, () -> svc.addInvoice(501L, new BizInvoice()));
        verify(accessService).assertFinanceCustomerWritable(501L);
        verify(invoiceMapper, never()).insert(any(BizInvoice.class));
    }

    @Test
    void addPayment_guardRejected_noLedgerInsert() {
        BizPaymentMapper paymentMapper = mock(BizPaymentMapper.class);
        CustomerAccessService accessService = mock(CustomerAccessService.class);
        org.mockito.Mockito.doThrow(new BusinessException("无权操作其他集团的客户财务记录"))
                .when(accessService).assertFinanceCustomerWritable(501L);
        CustomerService svc = newService(mock(BizInvoiceMapper.class), paymentMapper,
                mock(BizCustomerMapper.class), mock(OrgScopeResolver.class),
                mock(BizContactMapper.class), accessService);

        assertThrows(BusinessException.class, () -> svc.addPayment(501L, new BizPayment()));
        verify(accessService).assertFinanceCustomerWritable(501L);
        verify(paymentMapper, never()).insert(any(BizPayment.class));
    }

    @Test
    void addInvoice_guardRunsBeforeInsert() {
        BizInvoiceMapper invoiceMapper = mock(BizInvoiceMapper.class);
        CustomerAccessService accessService = mock(CustomerAccessService.class);
        CustomerService svc = newService(invoiceMapper, mock(BizPaymentMapper.class),
                mock(BizCustomerMapper.class), mock(OrgScopeResolver.class),
                mock(BizContactMapper.class), accessService);

        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUserId).thenReturn(7L);
            svc.addInvoice(501L, new BizInvoice());
        }

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(accessService, invoiceMapper);
        order.verify(accessService).assertFinanceCustomerWritable(501L);
        order.verify(invoiceMapper).insert(any(BizInvoice.class));
    }

    // ===== 【审查 biz-3 2026-10-18】公海删除复用客户删除统一内核(分享/提醒/快照) =====

    @Test
    @SuppressWarnings("unchecked")
    void deleteFromSeaPool_softDeletesAndCleansSharesReminders() {
        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        BizCustomer customer = new BizCustomer();
        customer.setId(501L);
        customer.setCustomerName("公海客户");
        when(customerMapper.selectById(501L)).thenReturn(customer);
        BizInvoiceMapper invoiceMapper = mock(BizInvoiceMapper.class);
        when(invoiceMapper.selectCount(any())).thenReturn(0L);
        BizPaymentMapper paymentMapper = mock(BizPaymentMapper.class);
        when(paymentMapper.selectCount(any())).thenReturn(0L);
        BizSeaPoolMapper seaPoolMapper = mock(BizSeaPoolMapper.class);
        when(seaPoolMapper.selectCount(any())).thenReturn(0L); // 池行已由 SeaService 结束
        BizCustomerShareMapper shareMapper = mock(BizCustomerShareMapper.class);
        BizReminderMapper reminderMapper = mock(BizReminderMapper.class);
        com.aigicrm.module.system.mapper.SysLogMapper logMapper =
                mock(com.aigicrm.module.system.mapper.SysLogMapper.class);

        CustomerService svc = new CustomerService(
                customerMapper, null, mock(BizCustomerLogMapper.class),
                invoiceMapper, shareMapper, mock(BizCustomerIndicatorMapper.class),
                mock(BizContactMapper.class), paymentMapper,
                mock(com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper.class),
                seaPoolMapper, mock(SysUserMapper.class), mock(SysDeptMapper.class),
                reminderMapper, mock(JdbcTemplate.class), mock(SysConfigService.class),
                mock(OrgScopeResolver.class), mock(CustomerAccessService.class),
                mock(com.aigicrm.module.system.service.RolePermScopeService.class),
                mock(com.aigicrm.module.approval.mapper.BizApprovalMapper.class), logMapper,
                mock(com.aigicrm.module.customer.service.CustomerEventService.class));

        svc.deleteFromSeaPool(501L);

        verify(customerMapper).deleteById(501L);
        verify(shareMapper).delete(any());
        verify(reminderMapper, times(2)).delete(any());
        // 删除快照必须留痕(公海入口不得绕过 CustomerService.delete 的审计)
        verify(logMapper).insert(any(com.aigicrm.module.system.entity.SysLog.class));
    }

    @Test
    void deleteFromSeaPool_activePoolRowStillRejected() {
        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        when(customerMapper.selectById(501L)).thenReturn(new BizCustomer());
        BizSeaPoolMapper seaPoolMapper = mock(BizSeaPoolMapper.class);
        when(seaPoolMapper.selectCount(any())).thenReturn(1L); // 仍有活跃池行(如并发新入池)

        CustomerService svc = new CustomerService(
                customerMapper, null, mock(BizCustomerLogMapper.class),
                mock(BizInvoiceMapper.class), mock(BizCustomerShareMapper.class),
                mock(BizCustomerIndicatorMapper.class), mock(BizContactMapper.class),
                mock(BizPaymentMapper.class),
                mock(com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper.class),
                seaPoolMapper, mock(SysUserMapper.class), mock(SysDeptMapper.class),
                mock(BizReminderMapper.class), mock(JdbcTemplate.class), mock(SysConfigService.class),
                mock(OrgScopeResolver.class), mock(CustomerAccessService.class),
                mock(com.aigicrm.module.system.service.RolePermScopeService.class),
                mock(com.aigicrm.module.approval.mapper.BizApprovalMapper.class),
                mock(com.aigicrm.module.system.mapper.SysLogMapper.class),
                mock(com.aigicrm.module.customer.service.CustomerEventService.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> svc.deleteFromSeaPool(501L));
        assertTrue(ex.getMessage().contains("请先在公海管理删除"), "实际: " + ex.getMessage());
        verify(customerMapper, never()).deleteById(anyLong());
    }

    // ===== 【A1 2026-10-20】approved_extend_days/expire_date 服务端派生不变量 =====
    // 不变量: 请求体不可写; update 保留库内值; 审批通过累加(见 ExtendApprovalConcurrencyTest
    // .approveExtend_locksCustomerBeforeAccumulating); 入海/开票基准前移可清零(合法写入点, 不在本组钉)。

    /** A1 回归①(默认路径,最常触发): 普通编辑(表单不传 approvedExtendDays)不得把已批延期静默清零 */
    @Test
    void update_normalEdit_keepsApprovedExtendDays_andServerFormulaExpireDate() {
        BizCustomer existing = new BizCustomer();
        existing.setId(7L);
        existing.setRegisterDate(java.time.LocalDate.of(2026, 1, 1));
        existing.setApprovedExtendDays(30); // 库内已批延期 30 天
        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        when(customerMapper.selectByIdForUpdate(7L)).thenReturn(existing);
        CustomerAccessService access = mock(CustomerAccessService.class);
        when(access.canEdit(7L)).thenReturn(true);
        when(access.isInSeaPool(7L)).thenReturn(false);
        CustomerService svc = newService(mock(BizInvoiceMapper.class), mock(BizPaymentMapper.class),
                customerMapper, mock(OrgScopeResolver.class), mock(BizContactMapper.class), access);

        BizCustomer req = new BizCustomer();
        req.setId(7L);
        req.setCustomerName("只改名字"); // PC 表单 payload 无 approvedExtendDays 字段 → 请求侧为 null
        req.setExpireDate(java.time.LocalDate.of(2099, 12, 31)); // 直调接口携带的到期日必须被丢弃
        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::isAdmin).thenReturn(false);
            sec.when(() -> SecurityUtils.hasAuthority("customer:date:edit")).thenReturn(false);
            svc.update(req);
        }
        ArgumentCaptor<BizCustomer> captor = ArgumentCaptor.forClass(BizCustomer.class);
        verify(customerMapper).updateById(captor.capture());
        BizCustomer saved = captor.getValue();
        assertEquals(30, saved.getApprovedExtendDays(), "已批延期必须以库内值保留, 不得按 null=0 清零");
        assertEquals(java.time.LocalDate.of(2026, 1, 1).plusDays(365 + 30), saved.getExpireDate(),
                "到期日=注册日+360+已批延期(服务端公式), 不取请求体 2099 值");
    }

    /** A1 回归②(显式注入): PUT 显式传 approvedExtendDays=5000 不得生效(不可任意拉长保护期) */
    @Test
    void update_explicitApprovedExtendDaysInjection_isIgnored() {
        BizCustomer existing = new BizCustomer();
        existing.setId(7L);
        existing.setRegisterDate(java.time.LocalDate.of(2026, 1, 1));
        existing.setApprovedExtendDays(30);
        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        when(customerMapper.selectByIdForUpdate(7L)).thenReturn(existing);
        CustomerAccessService access = mock(CustomerAccessService.class);
        when(access.canEdit(7L)).thenReturn(true);
        when(access.isInSeaPool(7L)).thenReturn(false);
        CustomerService svc = newService(mock(BizInvoiceMapper.class), mock(BizPaymentMapper.class),
                customerMapper, mock(OrgScopeResolver.class), mock(BizContactMapper.class), access);

        BizCustomer req = new BizCustomer();
        req.setId(7L);
        req.setCustomerName("正常改名");
        req.setApprovedExtendDays(5000); // 越权注入
        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::isAdmin).thenReturn(false);
            sec.when(() -> SecurityUtils.hasAuthority("customer:date:edit")).thenReturn(false);
            svc.update(req);
        }
        ArgumentCaptor<BizCustomer> captor = ArgumentCaptor.forClass(BizCustomer.class);
        verify(customerMapper).updateById(captor.capture());
        assertEquals(30, captor.getValue().getApprovedExtendDays(), "显式注入必须被库内值覆盖");
        assertEquals(java.time.LocalDate.of(2026, 1, 1).plusDays(365 + 30), captor.getValue().getExpireDate(),
                "到期日仍按库内 30 天重算, 不得按 5000 天延长");
    }

    /** A1 回归③(与审批路径的闭环): 审批累加后的库内值(如 100+360=460)在后续普通编辑中不丢失 */
    @Test
    void update_afterApprovalAccumulation_normalEditDoesNotLoseApprovedDays() {
        BizCustomer existing = new BizCustomer();
        existing.setId(7L);
        existing.setRegisterDate(java.time.LocalDate.of(2026, 1, 1));
        existing.setApprovedExtendDays(460); // 审批通过累加后的库内值(写入方: ApprovalService)
        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        when(customerMapper.selectByIdForUpdate(7L)).thenReturn(existing);
        CustomerAccessService access = mock(CustomerAccessService.class);
        when(access.canEdit(7L)).thenReturn(true);
        when(access.isInSeaPool(7L)).thenReturn(false);
        CustomerService svc = newService(mock(BizInvoiceMapper.class), mock(BizPaymentMapper.class),
                customerMapper, mock(OrgScopeResolver.class), mock(BizContactMapper.class), access);

        BizCustomer req = new BizCustomer();
        req.setId(7L);
        req.setCustomerName("审批后又改名字");
        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::isAdmin).thenReturn(false);
            sec.when(() -> SecurityUtils.hasAuthority("customer:date:edit")).thenReturn(false);
            svc.update(req);
        }
        ArgumentCaptor<BizCustomer> captor = ArgumentCaptor.forClass(BizCustomer.class);
        verify(customerMapper).updateById(captor.capture());
        assertEquals(460, captor.getValue().getApprovedExtendDays(), "审批累加成果不得被普通编辑覆盖丢失");
    }

    /** A1 回归④: create 强制置 0, 请求体携带的 approvedExtendDays/expireDate 不进入公式与落库 */
    @Test
    void create_forcesApprovedExtendDaysZero_andServerExpireDate() {
        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        CustomerService svc = newService(mock(BizInvoiceMapper.class), mock(BizPaymentMapper.class),
                customerMapper, mock(OrgScopeResolver.class));
        BizCustomer req = new BizCustomer();
        req.setCustomerName("新客户");
        req.setRegisterDate(java.time.LocalDate.of(2026, 1, 1));
        req.setApprovedExtendDays(999); // 新建即注入延期
        req.setExpireDate(java.time.LocalDate.of(2099, 12, 31));
        try (MockedStatic<SecurityUtils> ms = mockStatic(SecurityUtils.class)) {
            ms.when(SecurityUtils::isAdmin).thenReturn(false);
            ms.when(SecurityUtils::getCurrentUserId).thenReturn(100L);
            svc.create(req);
        }
        ArgumentCaptor<BizCustomer> captor = ArgumentCaptor.forClass(BizCustomer.class);
        verify(customerMapper).insert(captor.capture());
        BizCustomer saved = captor.getValue();
        assertEquals(0, saved.getApprovedExtendDays(), "新建必须强制 approvedExtendDays=0");
        assertEquals(java.time.LocalDate.of(2026, 1, 1).plusDays(365), saved.getExpireDate(),
                "新建到期日=注册日+365+0(服务端公式, 2026-11-02 口径A), 不取请求体 2099 值");
    }

    // ===== 【A3 2026-10-20】客户编号 MAX+1 取号并发兜底(有限重试) =====
    // 真实并发集成测试(仅一方成功或双方不同号)需 Testcontainers/MySQL, 仓库测试栈为纯 mock 单测
    // (审计 F7, P2), 本组以 mock 钉"冲突→退让重试→超限抛原始异常"语义。

    @Test
    void create_codeConflict_retriesWithBumpedSuffix_thenSucceeds() {
        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        when(customerMapper.selectMaxCodeByPrefix(anyString(), anyInt(), any())).thenReturn(0);
        // 并发事务已抢占 max+1 号: 首次 insert 撞唯一键, 第二次(退让 max+2)成功。
        // 重试复用同一实体(编号字段被改写), 用 thenAnswer 在调用时刻快照编号, 避免
        // ArgumentCaptor 存引用导致两次捕获值都是终态。
        java.util.List<String> codesAtInsert = new java.util.ArrayList<>();
        when(customerMapper.insert(any(BizCustomer.class))).thenAnswer(inv -> {
            codesAtInsert.add(((BizCustomer) inv.getArgument(0)).getCustomerCode());
            if (codesAtInsert.size() == 1) {
                throw new org.springframework.dao.DuplicateKeyException("Duplicate entry 'C-202610-001'");
            }
            return 1;
        });
        CustomerService svc = newService(mock(BizInvoiceMapper.class), mock(BizPaymentMapper.class),
                customerMapper, mock(OrgScopeResolver.class));
        BizCustomer req = new BizCustomer();
        try (MockedStatic<SecurityUtils> ms = mockStatic(SecurityUtils.class)) {
            ms.when(SecurityUtils::isAdmin).thenReturn(false);
            ms.when(SecurityUtils::getCurrentUserId).thenReturn(100L);
            assertDoesNotThrow(() -> svc.create(req));
        }
        verify(customerMapper, times(2)).insert(any(BizCustomer.class));
        String ym = java.time.YearMonth.now().toString().replace("-", ""); // region_date_seq 规则的年月段
        assertEquals(java.util.List.of("C-" + ym + "-001", "C-" + ym + "-002"), codesAtInsert,
                "首次按 max+1 取号; 冲突后必须退让一号(max+2), 不得重复使用被抢占的号码");
    }

    @Test
    void create_codeConflict_exhaustsRetries_rethrowsOriginal() {
        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        when(customerMapper.selectMaxCodeByPrefix(anyString(), anyInt(), any())).thenReturn(0);
        org.springframework.dao.DuplicateKeyException boom =
                new org.springframework.dao.DuplicateKeyException("持续冲突");
        when(customerMapper.insert(any(BizCustomer.class))).thenThrow(boom);
        CustomerService svc = newService(mock(BizInvoiceMapper.class), mock(BizPaymentMapper.class),
                customerMapper, mock(OrgScopeResolver.class));
        BizCustomer req = new BizCustomer();
        try (MockedStatic<SecurityUtils> ms = mockStatic(SecurityUtils.class)) {
            ms.when(SecurityUtils::isAdmin).thenReturn(false);
            ms.when(SecurityUtils::getCurrentUserId).thenReturn(100L);
            org.springframework.dao.DuplicateKeyException ex = assertThrows(
                    org.springframework.dao.DuplicateKeyException.class, () -> svc.create(req));
            assertEquals(boom, ex, "超限必须抛原始异常(不吞错、不改语义为业务异常)");
        }
        verify(customerMapper, times(3)).insert(any(BizCustomer.class)); // 上限=首次+2次重试
    }

    // ===== 【A5 2026-10-20】客户删除内核事务 =====

    /**
     * A5 回滚依赖面: 删除内核(软删→删分享→清提醒→留痕)是同一事务, 注解缺失即回归
     * (原缺陷正是 delete 无 @Transactional 而公海入口有)。真实 DB 级回滚验证需
     * Testcontainers/H2(审计 F7, P2, 本批 BLOCKED), 此处钉两个事务边界注解不被误删。
     */
    @Test
    void delete_bothPublicEntriesAreTransactional() throws NoSuchMethodException {
        assertTrue(java.lang.reflect.Modifier.isPublic(
                        CustomerService.class.getMethod("delete", Long.class).getModifiers()),
                "delete 必须保持 public(经代理调用, 注解才生效; 全仓仅 CustomerController 调用, 无自调用)");
        assertNotNull(CustomerService.class.getMethod("delete", Long.class)
                .getAnnotation(org.springframework.transaction.annotation.Transactional.class),
                "客户列表删除入口必须带 @Transactional(A5)");
        assertNotNull(CustomerService.class.getMethod("deleteFromSeaPool", Long.class)
                .getAnnotation(org.springframework.transaction.annotation.Transactional.class),
                "公海删除入口必须保持 @Transactional(A5, 与列表入口同口径)");
    }

    /**
     * A5 异常回滚(mock 级): 提醒清理失败时异常必须向外传播(不被吞), 且客户软删先于失败发生
     * ——即"客户已删、提醒还在"的半删状态只能靠事务回滚消除。DB 级行为本批无法验证(F7)。
     */
    @Test
    void delete_reminderCleanupFailure_propagatesAfterCustomerSoftDelete() {
        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        when(customerMapper.selectById(501L)).thenReturn(new BizCustomer());
        BizSeaPoolMapper seaPoolMapper = mock(BizSeaPoolMapper.class);
        when(seaPoolMapper.selectCount(any())).thenReturn(0L);
        BizInvoiceMapper invoiceMapper = mock(BizInvoiceMapper.class);
        when(invoiceMapper.selectCount(any())).thenReturn(0L);
        BizPaymentMapper paymentMapper = mock(BizPaymentMapper.class);
        when(paymentMapper.selectCount(any())).thenReturn(0L);
        com.aigicrm.module.approval.mapper.BizApprovalMapper approvalMapper =
                mock(com.aigicrm.module.approval.mapper.BizApprovalMapper.class);
        when(approvalMapper.selectCount(any())).thenReturn(0L);
        com.aigicrm.module.customer.mapper.BizCustomerShareMapper shareMapper =
                mock(com.aigicrm.module.customer.mapper.BizCustomerShareMapper.class);
        com.aigicrm.module.reminder.mapper.BizReminderMapper reminderMapper =
                mock(com.aigicrm.module.reminder.mapper.BizReminderMapper.class);
        when(reminderMapper.delete(any())).thenThrow(new RuntimeException("提醒清理失败(模拟 DB 故障)"));
        CustomerAccessService access = mock(CustomerAccessService.class);
        when(access.canDelete(501L)).thenReturn(true);

        CustomerService svc = new CustomerService(
                customerMapper, null, mock(BizCustomerLogMapper.class), invoiceMapper,
                shareMapper, mock(BizCustomerIndicatorMapper.class), mock(BizContactMapper.class),
                paymentMapper, mock(com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper.class),
                seaPoolMapper, mock(SysUserMapper.class), mock(SysDeptMapper.class), reminderMapper,
                mock(JdbcTemplate.class), mock(SysConfigService.class), mock(OrgScopeResolver.class), access,
                mock(com.aigicrm.module.system.service.RolePermScopeService.class), approvalMapper,
                mock(com.aigicrm.module.system.mapper.SysLogMapper.class),
                mock(com.aigicrm.module.customer.service.CustomerEventService.class));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> svc.delete(501L),
                "清理失败必须传播出去交给 @Transactional 回滚, 不得吞掉留下半删状态");
        assertTrue(ex.getMessage().contains("提醒清理失败"));
        // 客户软删发生在失败之前 → 一致性只能由事务回滚保证(与注解测试互为印证)
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(customerMapper, reminderMapper);
        order.verify(customerMapper).deleteById(501L);
        order.verify(reminderMapper).delete(any());
    }

    // ===== 【A5 2026-10-21 复核补验】真实 Spring 事务代理验证(无 DB 环境下的最大可验证档) =====
    // 审计 F7(无 Testcontainers/H2)之下, 用真实 DataSourceTransactionManager + 真实
    // @Transactional 代理织入, 仅 JDBC Connection 用 mock: 验证回滚/提交指令真实下发,
    // 比"注解存在+异常传播"的静态证据高一档; 数据库级物理回滚仍待 DB 测试栈。

    /** 删除内核装配(两用): reminderFails=true 时提醒清理抛错 */
    private CustomerService newDeleteKernelService(BizCustomerMapper customerMapper,
                                                   com.aigicrm.module.reminder.mapper.BizReminderMapper reminderMapper,
                                                   CustomerAccessService access) {
        BizInvoiceMapper invoiceMapper = mock(BizInvoiceMapper.class);
        when(invoiceMapper.selectCount(any())).thenReturn(0L);
        BizPaymentMapper paymentMapper = mock(BizPaymentMapper.class);
        when(paymentMapper.selectCount(any())).thenReturn(0L);
        com.aigicrm.module.approval.mapper.BizApprovalMapper approvalMapper =
                mock(com.aigicrm.module.approval.mapper.BizApprovalMapper.class);
        when(approvalMapper.selectCount(any())).thenReturn(0L);
        com.aigicrm.module.sea.mapper.BizSeaPoolMapper seaPoolMapper =
                mock(com.aigicrm.module.sea.mapper.BizSeaPoolMapper.class);
        when(seaPoolMapper.selectCount(any())).thenReturn(0L);
        return new CustomerService(
                customerMapper, null, mock(BizCustomerLogMapper.class), invoiceMapper,
                mock(com.aigicrm.module.customer.mapper.BizCustomerShareMapper.class),
                mock(BizCustomerIndicatorMapper.class), mock(BizContactMapper.class),
                paymentMapper, mock(com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper.class),
                seaPoolMapper, mock(SysUserMapper.class), mock(SysDeptMapper.class), reminderMapper,
                mock(JdbcTemplate.class), mock(SysConfigService.class), mock(OrgScopeResolver.class), access,
                mock(com.aigicrm.module.system.service.RolePermScopeService.class), approvalMapper,
                mock(com.aigicrm.module.system.mapper.SysLogMapper.class),
                mock(com.aigicrm.module.customer.service.CustomerEventService.class));
    }

    /** 经真实 @Transactional 代理调用 delete 的被测对象(代理由 CGLIB 生成, 事务管理器绑定 mock 数据源) */
    private CustomerService proxiedDeleteService(boolean reminderFails, java.sql.Connection[] connOut) throws Exception {
        javax.sql.DataSource ds = mock(javax.sql.DataSource.class);
        java.sql.Connection conn = mock(java.sql.Connection.class);
        when(ds.getConnection()).thenReturn(conn);
        connOut[0] = conn;
        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        when(customerMapper.selectById(501L)).thenReturn(new BizCustomer());
        com.aigicrm.module.reminder.mapper.BizReminderMapper reminderMapper =
                mock(com.aigicrm.module.reminder.mapper.BizReminderMapper.class);
        if (reminderFails) {
            when(reminderMapper.delete(any())).thenThrow(new RuntimeException("提醒清理失败(模拟 DB 故障)"));
        }
        CustomerAccessService access = mock(CustomerAccessService.class);
        when(access.canDelete(501L)).thenReturn(true);
        CustomerService target = newDeleteKernelService(customerMapper, reminderMapper, access);

        org.springframework.jdbc.datasource.DataSourceTransactionManager tm =
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(ds);
        org.springframework.aop.framework.ProxyFactory pf = new org.springframework.aop.framework.ProxyFactory(target);
        pf.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(
                tm, new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
        return (CustomerService) pf.getProxy();
    }

    @Test
    void delete_reminderCleanupFailure_realTxProxyIssuesRollback() throws Exception {
        java.sql.Connection[] connOut = new java.sql.Connection[1];
        CustomerService proxied = proxiedDeleteService(true, connOut);
        java.sql.Connection conn = connOut[0];

        RuntimeException ex = assertThrows(RuntimeException.class, () -> proxied.delete(501L));
        assertTrue(ex.getMessage().contains("提醒清理失败"));
        verify(conn).rollback(); // 真实事务代理必须对连接下发 rollback——半删状态整体回滚
        verify(conn, never()).commit();
    }

    @Test
    void delete_success_realTxProxyCommits() throws Exception {
        java.sql.Connection[] connOut = new java.sql.Connection[1];
        CustomerService proxied = proxiedDeleteService(false, connOut);
        java.sql.Connection conn = connOut[0];

        assertDoesNotThrow(() -> proxied.delete(501L));
        verify(conn).commit(); // 正常路径走真实提交, 证明代理事务链路本身健全
        verify(conn, never()).rollback();
    }

    // ===== 【A3 2026-10-21 复核补验】取号行锁 + 真实多线程并发 =====

    /** 【A3 取号行锁】有集团上下文时: 先锁集团行(sys_group FOR UPDATE), 取号走 FOR UPDATE 锁定读 */
    @Test
    void create_withGroupContext_locksGroupRowAndUsesForUpdateMaxRead() {
        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(customerMapper.selectMaxCodeByPrefixForUpdate(anyString(), anyInt(), eq(5L))).thenReturn(2);
        CustomerService svc = new CustomerService(
                customerMapper, null, mock(BizCustomerLogMapper.class), mock(BizInvoiceMapper.class),
                mock(BizCustomerShareMapper.class), mock(BizCustomerIndicatorMapper.class),
                mock(BizContactMapper.class), mock(BizPaymentMapper.class),
                mock(com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper.class),
                mock(com.aigicrm.module.sea.mapper.BizSeaPoolMapper.class),
                mock(SysUserMapper.class), mock(SysDeptMapper.class), mock(BizReminderMapper.class),
                jdbc, mock(SysConfigService.class), mock(OrgScopeResolver.class),
                mock(CustomerAccessService.class), mock(com.aigicrm.module.system.service.RolePermScopeService.class),
                mock(com.aigicrm.module.approval.mapper.BizApprovalMapper.class),
                mock(com.aigicrm.module.system.mapper.SysLogMapper.class),
                mock(com.aigicrm.module.customer.service.CustomerEventService.class));

        SecurityUser u = new SecurityUser();
        u.setUserId(100L);
        u.setRoleCodes(List.of("06")); // 非 admin
        u.setCurrentGroupId(5L);       // 集团上下文 → 走取号行锁路径
        org.springframework.security.core.context.SecurityContextHolder.getContext()
                .setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(u, null));
        try {
            svc.create(new BizCustomer());
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
        // 集团行锁先于取号(同租户建客在取号前串行化)
        verify(jdbc).queryForList(contains("sys_group"), eq(Long.class), eq(5L));
        // 取号走 FOR UPDATE 锁定读(读最新已提交值, 不受本事务 RR 旧快照影响), 不再走普通读
        verify(customerMapper).selectMaxCodeByPrefixForUpdate(anyString(), anyInt(), eq(5L));
        verify(customerMapper, never()).selectMaxCodeByPrefix(anyString(), anyInt(), any());
        ArgumentCaptor<BizCustomer> captor = ArgumentCaptor.forClass(BizCustomer.class);
        verify(customerMapper).insert(captor.capture());
        String ym = java.time.YearMonth.now().toString().replace("-", "");
        assertEquals("C-" + ym + "-003", captor.getValue().getCustomerCode(), "锁定读 max=2 → 取号 003");
    }

    /**
     * 【A3 并发验证·线程级】真实多线程并发建客 + 线程安全模拟唯一索引(取号读与插入判重
     * 均看最新"已提交"集合, READ COMMITTED 语义): 验收命题"仅一方成功占用每个号"——
     * 3 方并发必须全部成功且编号互不相同(任一 500/重试超限都会在此暴露)。
     * 非真实 DB(仓库无 Testcontainers/H2, 审计 F7)——真实数据库级并发集成测试仍 BLOCKED;
     * 本测试钉住退让重试机制在线程级交叠下不产生同号/失败。无集团上下文(无锁路径)下验证,
     * 有集团上下文时生产路径由集团行锁串行化(见 create_withGroupContext_locksGroupRowAndUsesForUpdateMaxRead)。
     * 线程数=3 与 CODE_GEN_MAX_ATTEMPTS 对齐: 每次取号失败必然对应一个竞争者成功,
     * 3 方至多失败 2 次, 第 3 次尝试必然得号(确定性论证, 不抖动)。
     */
    @Test
    void create_concurrentThreads_simulatedUniqueIndex_allSucceedWithDistinctCodes() throws Exception {
        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        java.util.Set<String> committedCodes =
                java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
        when(customerMapper.selectMaxCodeByPrefix(anyString(), anyInt(), any())).thenAnswer(inv -> {
            String pfx = inv.getArgument(0);
            return committedCodes.stream()
                    .filter(code -> code.startsWith(pfx))
                    .mapToInt(code -> Integer.parseInt(code.substring(pfx.length())))
                    .max().orElse(0);
        });
        when(customerMapper.insert(any(BizCustomer.class))).thenAnswer(inv -> {
            BizCustomer c = inv.getArgument(0);
            if (!committedCodes.add(c.getCustomerCode())) {
                throw new org.springframework.dao.DuplicateKeyException("dup " + c.getCustomerCode());
            }
            return 1;
        });
        CustomerService svc = newService(mock(BizInvoiceMapper.class), mock(BizPaymentMapper.class),
                customerMapper, mock(OrgScopeResolver.class));

        SecurityUser u = new SecurityUser();
        u.setUserId(100L);
        u.setRoleCodes(List.of("06")); // 非 admin 且无集团上下文 → 无锁路径, 纯退让重试
        org.springframework.security.core.context.SecurityContextHolder.getContext()
                .setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(u, null));
        try {
            java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(3);
            java.util.List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < 3; i++) {
                futures.add(pool.submit(() -> {
                    BizCustomer c = new BizCustomer();
                    c.setCustomerName("并发客户" + Thread.currentThread().getName());
                    svc.create(c);
                }));
            }
            for (java.util.concurrent.Future<?> f : futures) {
                try {
                    f.get();
                } catch (Exception e) {
                    throw new AssertionError("并发建客任一方失败(含重试超限)即验收不通过", e);
                }
            }
            pool.shutdown();
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
        assertEquals(3, committedCodes.size(),
                "3 方并发建客必须全部成功且编号互不相同(集合去重后恰为 3 → 仅一方占用每个号)");
    }

    // ===== 【口径批 2026-11-02 裁决A/B/C】三线公式/捞取重置/捞取守卫 =====

    /** 裁决B: 捞取重置=短保护180天+捞取到期日; 注册日不被覆写; 已批延期清零; 最近开票日显式置空 */
    @Test
    void applyClaimReset_shortProtectionWithoutOverwritingRegister() {
        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        CustomerService service = new CustomerService(
                customerMapper,
                null,
                mock(BizCustomerLogMapper.class), mock(BizInvoiceMapper.class),
                mock(BizCustomerShareMapper.class), mock(BizCustomerIndicatorMapper.class),
                mock(BizContactMapper.class), mock(BizPaymentMapper.class),
                mock(com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper.class),
                mock(com.aigicrm.module.sea.mapper.BizSeaPoolMapper.class),
                mock(SysUserMapper.class), mock(SysDeptMapper.class),
                mock(BizReminderMapper.class), mock(JdbcTemplate.class),
                mock(SysConfigService.class), mock(OrgScopeResolver.class),
                mock(CustomerAccessService.class),
                mock(com.aigicrm.module.system.service.RolePermScopeService.class),
                mock(com.aigicrm.module.approval.mapper.BizApprovalMapper.class),
                mock(com.aigicrm.module.system.mapper.SysLogMapper.class),
                mock(com.aigicrm.module.customer.service.CustomerEventService.class));
        BizCustomer customer = new BizCustomer();
        customer.setId(1L);
        customer.setRegisterDate(java.time.LocalDate.of(2025, 1, 1));
        customer.setLastInvoiceDate(null); // 调用方(SeaService)置空后进入
        customer.setApprovedExtendDays(90);
        java.time.LocalDate claimDay = java.time.LocalDate.of(2026, 11, 2);

        service.applyClaimReset(customer, claimDay);

        assertEquals(claimDay, customer.getClaimDate(), "捞取日期=当天");
        assertEquals(java.time.LocalDate.of(2025, 1, 1), customer.getRegisterDate(), "注册日不得被捞取覆写(裁决B)");
        assertEquals(0, customer.getApprovedExtendDays(), "原负责人任内已批延期清零");
        assertEquals(claimDay.plusDays(180), customer.getExpireDate(), "到期日=捞取日+180(短保护, 裁决B)");
        assertEquals(claimDay.plusDays(180), customer.getAdjustDate(), "捞取到期日=捞取日+180(裁决C)");
        verify(customerMapper).update(isNull(),
                org.mockito.ArgumentMatchers.<LambdaUpdateWrapper<BizCustomer>>any());
    }

    /** 三线: 捞取后开了更晚的新票 → 开票线(365)压捞取线(180), 到期=开票日+365 */
    @Test
    void expireFormula_invoiceAfterClaim_invoiceLineWins() {
        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        CustomerService service = new CustomerService(
                customerMapper, null, mock(BizCustomerLogMapper.class), mock(BizInvoiceMapper.class),
                mock(BizCustomerShareMapper.class), mock(BizCustomerIndicatorMapper.class),
                mock(BizContactMapper.class), mock(BizPaymentMapper.class),
                mock(com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper.class),
                mock(com.aigicrm.module.sea.mapper.BizSeaPoolMapper.class),
                mock(SysUserMapper.class), mock(SysDeptMapper.class),
                mock(BizReminderMapper.class), mock(JdbcTemplate.class),
                mock(SysConfigService.class), mock(OrgScopeResolver.class),
                mock(CustomerAccessService.class),
                mock(com.aigicrm.module.system.service.RolePermScopeService.class),
                mock(com.aigicrm.module.approval.mapper.BizApprovalMapper.class),
                mock(com.aigicrm.module.system.mapper.SysLogMapper.class),
                mock(com.aigicrm.module.customer.service.CustomerEventService.class));
        BizCustomer customer = new BizCustomer();
        customer.setId(1L);
        customer.setRegisterDate(java.time.LocalDate.of(2025, 1, 1));
        customer.setClaimDate(java.time.LocalDate.of(2026, 6, 1));
        customer.setLastInvoiceDate(java.time.LocalDate.of(2026, 11, 10)); // 捞取后的新票

        service.applyExtendApproval(customer, 0);

        assertEquals(java.time.LocalDate.of(2026, 11, 10).plusDays(365), customer.getExpireDate(),
                "开票晚于捞取日 → 开票线主导: 到期=开票日+365(回到正常保护)");
        assertEquals("active", customer.getProtectionStatus());
    }

    /** 三线: 存量捞取户注册日曾被旧逻辑覆写为捞取日 → 建档线不参与, 不得变相拿到 365 */
    @Test
    void expireFormula_claimCustomer_registerLineExcluded() {
        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        CustomerService service = new CustomerService(
                customerMapper, null, mock(BizCustomerLogMapper.class), mock(BizInvoiceMapper.class),
                mock(BizCustomerShareMapper.class), mock(BizCustomerIndicatorMapper.class),
                mock(BizContactMapper.class), mock(BizPaymentMapper.class),
                mock(com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper.class),
                mock(com.aigicrm.module.sea.mapper.BizSeaPoolMapper.class),
                mock(SysUserMapper.class), mock(SysDeptMapper.class),
                mock(BizReminderMapper.class), mock(JdbcTemplate.class),
                mock(SysConfigService.class), mock(OrgScopeResolver.class),
                mock(CustomerAccessService.class),
                mock(com.aigicrm.module.system.service.RolePermScopeService.class),
                mock(com.aigicrm.module.approval.mapper.BizApprovalMapper.class),
                mock(com.aigicrm.module.system.mapper.SysLogMapper.class),
                mock(com.aigicrm.module.customer.service.CustomerEventService.class));
        BizCustomer customer = new BizCustomer();
        customer.setId(1L);
        customer.setRegisterDate(java.time.LocalDate.of(2026, 6, 1)); // 被旧逻辑覆写=捞取日(存量特征)
        customer.setClaimDate(java.time.LocalDate.of(2026, 6, 1));
        customer.setLastInvoiceDate(null);

        service.applyExtendApproval(customer, 30);

        // 建档线若参与: 2026-06-01+365+30; 正确口径(捞取线): 2026-06-01+180+30
        assertEquals(java.time.LocalDate.of(2026, 6, 1).plusDays(180).plusDays(30), customer.getExpireDate(),
                "有捞取线时建档线不参与(存量注册日已被覆写不可信), 到期=捞取日+180+延期");
    }

    /** 裁决B守卫: 捞取后早于捞取日的老票日期不得经 advance 回填最近开票日(防老票复活拉长保护期) */
    @Test
    void advanceGuard_oldInvoiceBeforeClaim_notBackfilled() {
        BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
        CustomerService service = new CustomerService(
                customerMapper, null, mock(BizCustomerLogMapper.class), mock(BizInvoiceMapper.class),
                mock(BizCustomerShareMapper.class), mock(BizCustomerIndicatorMapper.class),
                mock(BizContactMapper.class), mock(BizPaymentMapper.class),
                mock(com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper.class),
                mock(com.aigicrm.module.sea.mapper.BizSeaPoolMapper.class),
                mock(SysUserMapper.class), mock(SysDeptMapper.class),
                mock(BizReminderMapper.class), mock(JdbcTemplate.class),
                mock(SysConfigService.class), mock(OrgScopeResolver.class),
                mock(CustomerAccessService.class),
                mock(com.aigicrm.module.system.service.RolePermScopeService.class),
                mock(com.aigicrm.module.approval.mapper.BizApprovalMapper.class),
                mock(com.aigicrm.module.system.mapper.SysLogMapper.class),
                mock(com.aigicrm.module.customer.service.CustomerEventService.class));
        BizCustomer customer = new BizCustomer();
        customer.setId(1L);
        customer.setRegisterDate(java.time.LocalDate.of(2025, 1, 1));
        customer.setClaimDate(java.time.LocalDate.of(2026, 11, 2));
        customer.setLastInvoiceDate(null); // 捞取置空后

        Object advanced = org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                service, "advanceLastInvoiceDate", customer, java.time.LocalDate.of(2024, 3, 1));

        assertEquals(Boolean.FALSE, advanced, "早于捞取日的老票不得回填");
        assertNull(customer.getLastInvoiceDate(), "最近开票日保持置空");
        verify(customerMapper, never()).updateById(any(BizCustomer.class));
    }
}
