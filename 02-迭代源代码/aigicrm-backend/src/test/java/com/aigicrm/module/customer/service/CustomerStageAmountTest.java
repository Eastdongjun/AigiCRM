package com.aigicrm.module.customer.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.common.scope.OrgScopeResolver;
import com.aigicrm.module.customer.dto.StageAmountRequest;
import com.aigicrm.module.customer.entity.BizCustomer;
import com.aigicrm.module.customer.entity.BizCustomerIndicator;
import com.aigicrm.module.customer.entity.BizCustomerStageAmount;
import com.aigicrm.module.customer.mapper.*;
import com.aigicrm.module.reminder.mapper.BizReminderMapper;
import com.aigicrm.module.system.mapper.SysDeptMapper;
import com.aigicrm.module.system.mapper.SysLogMapper;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.module.system.service.SysConfigService;
import com.aigicrm.security.SecurityUtils;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 【裁决 2026-10-10】开票回款阶段总额制 + 核心客户三态判定测试。
 * 覆盖: 录入校验/upsert 行锁路径/保护期接力(前移刷新·不回退·未来封顶·零开票不动)/
 * 三态判定矩阵(纯金额·全年行优先·最近有数据年)/is_core 兼容同步/无指标行自动建行/
 * saveIndicator 域外输入屏蔽/判定摘要排序。
 */
class CustomerStageAmountTest {

    private final BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
    private final BizInvoiceMapper invoiceMapper = mock(BizInvoiceMapper.class);
    private final BizPaymentMapper paymentMapper = mock(BizPaymentMapper.class);
    private final BizCustomerIndicatorMapper indicatorMapper = mock(BizCustomerIndicatorMapper.class);
    private final BizCustomerStageAmountMapper stageAmountMapper = mock(BizCustomerStageAmountMapper.class);
    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final BizReminderMapper reminderMapper = mock(BizReminderMapper.class);
    private final SysConfigService configService = mock(SysConfigService.class);
    private final CustomerService service = new CustomerService(
            customerMapper,
            mock(BizCustomerLogMapper.class),
            invoiceMapper,
            mock(BizCustomerShareMapper.class),
            indicatorMapper,
            mock(BizContactMapper.class),
            paymentMapper,
            stageAmountMapper,
            userMapper,
            mock(SysDeptMapper.class),
            reminderMapper,
            mock(JdbcTemplate.class),
            configService,
            mock(OrgScopeResolver.class),
            mock(com.aigicrm.common.engine.CustomerAccessService.class),
            mock(com.aigicrm.module.system.service.RolePermScopeService.class),
            mock(com.aigicrm.module.approval.mapper.BizApprovalMapper.class),
            mock(SysLogMapper.class));

    @BeforeAll
    static void initTableInfo() {
        // LambdaQueryWrapper 依赖实体列缓存(自足 init, 不蹭其他测试类的静态缓存——单跑必挂的老坑)
        for (Class<?> t : List.of(BizCustomer.class, BizCustomerIndicator.class, BizCustomerStageAmount.class,
                com.aigicrm.module.reminder.entity.BizReminder.class)) {
            if (TableInfoHelper.getTableInfo(t) == null) {
                TableInfoHelper.initTableInfo(
                        new com.baomidou.mybatisplus.core.MybatisMapperBuilderAssistant(new MybatisConfiguration(), ""), t);
            }
        }
    }

    private static BizCustomerStageAmount row(int year, String type, String inv, String pay) {
        BizCustomerStageAmount r = new BizCustomerStageAmount();
        r.setCustomerId(501L);
        r.setPeriodYear(year);
        r.setPeriodType(type);
        r.setInvoiceAmount(inv == null ? null : new BigDecimal(inv));
        r.setPaymentAmount(pay == null ? null : new BigDecimal(pay));
        return r;
    }

    private static BizCustomer customerWith(LocalDate lastInvoiceDate) {
        BizCustomer c = new BizCustomer();
        c.setId(501L);
        c.setLastInvoiceDate(lastInvoiceDate);
        c.setRegisterDate(LocalDate.of(2024, 1, 1));
        return c;
    }

    /** 无指标行: recalculateCoreStatus 自动建行(判定与五项解耦, 核心状态不依赖是否评过分) */
    private void stubNoIndicator() {
        when(indicatorMapper.selectOne(any())).thenReturn(null);
    }

    // ====== 录入校验 ======

    @Test
    void saveStageAmountValidatesTypeYearAndAmounts() {
        when(customerMapper.selectById(501L)).thenReturn(customerWith(null));
        when(stageAmountMapper.selectByPeriodForUpdate(501L, 2026, "H1")).thenReturn(null);

        assertThrows(BusinessException.class, () ->
                service.saveStageAmount(501L, new StageAmountRequest(2026, "H3", bd("1"), bd("1"))), "非法周期类型必须拒");
        assertThrows(BusinessException.class, () ->
                service.saveStageAmount(501L, new StageAmountRequest(null, "FY", bd("1"), bd("1"))), "缺年份必须拒");
        assertThrows(BusinessException.class, () ->
                service.saveStageAmount(501L, new StageAmountRequest(1999, "FY", bd("1"), bd("1"))), "1999 年越界必须拒");
        assertThrows(BusinessException.class, () ->
                service.saveStageAmount(501L, new StageAmountRequest(LocalDate.now().getYear() + 2, "FY", bd("1"), bd("1"))), "远未来年份必须拒");
        assertThrows(BusinessException.class, () ->
                service.saveStageAmount(501L, new StageAmountRequest(2026, "FY", bd("-1"), bd("1"))), "负金额必须拒");
        assertThrows(BusinessException.class, () ->
                service.saveStageAmount(501L, new StageAmountRequest(2026, "FY", bd("0"), bd("0"))), "两项全 0 必须拒(不算数据)");
        assertThrows(BusinessException.class, () ->
                service.saveStageAmount(501L, new StageAmountRequest(2026, "FY", null, null)), "两项全空必须拒");
        // 客户不存在
        when(customerMapper.selectById(999L)).thenReturn(null);
        assertThrows(BusinessException.class, () ->
                service.saveStageAmount(999L, new StageAmountRequest(2026, "FY", bd("1"), bd("1"))));
        verify(stageAmountMapper, never()).insert(any(BizCustomerStageAmount.class));
    }

    // ====== upsert: 首次 insert / 已存在 update ======

    @Test
    void saveStageAmountUpsertsByUniquePeriod() {
        when(customerMapper.selectById(501L)).thenReturn(customerWith(null));
        when(stageAmountMapper.selectByPeriodForUpdate(501L, 2026, "H1")).thenReturn(null);
        when(stageAmountMapper.selectList(any())).thenReturn(List.of());
        stubNoIndicator();

        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::getCurrentUserId).thenReturn(7L);
            sec.when(SecurityUtils::getCurrentUser).thenReturn(null);
            // 只填回款、开票 null → 开票按 0(该周期无开票也是合法录入), 不触发保护期
            service.saveStageAmount(501L, new StageAmountRequest(2026, "H1", null, bd("80.005")));

            ArgumentCaptor<BizCustomerStageAmount> captor = ArgumentCaptor.forClass(BizCustomerStageAmount.class);
            verify(stageAmountMapper).insert(captor.capture());
            assertEquals(2026, captor.getValue().getPeriodYear());
            assertEquals("H1", captor.getValue().getPeriodType());
            assertEquals(0, bd("0").compareTo(captor.getValue().getInvoiceAmount()), "未填开票额按 0 入库");
            assertEquals(0, bd("80.01").compareTo(captor.getValue().getPaymentAmount()), "金额按两位小数 HALF_UP 归一");
            assertEquals(7L, captor.getValue().getCreatedBy());

            // 已存在 → 走 update 不再 insert
            BizCustomerStageAmount existing = row(2026, "H1", "60", "50");
            existing.setId(9L);
            when(stageAmountMapper.selectByPeriodForUpdate(501L, 2026, "H1")).thenReturn(existing);
            service.saveStageAmount(501L, new StageAmountRequest(2026, "H1", bd("70"), bd("55")));
            verify(stageAmountMapper, times(1)).insert(any(BizCustomerStageAmount.class));
            ArgumentCaptor<BizCustomerStageAmount> upd = ArgumentCaptor.forClass(BizCustomerStageAmount.class);
            verify(stageAmountMapper, atLeastOnce()).updateById(upd.capture());
            assertEquals(0, bd("70").compareTo(upd.getValue().getInvoiceAmount()));
            assertEquals(0, bd("55").compareTo(upd.getValue().getPaymentAmount()));
        }
    }

    // ====== 保护期接力 ======

    @Test
    void saveStageAmountRelaysProtectionPeriodForwardOnly() {
        when(stageAmountMapper.selectByPeriodForUpdate(501L, 2026, "H1")).thenReturn(null);
        when(stageAmountMapper.selectList(any())).thenReturn(List.of());
        stubNoIndicator();
        when(configService.getValue(anyString())).thenReturn(null); // 默认 360/180

        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::getCurrentUserId).thenReturn(7L);
            sec.when(SecurityUtils::getCurrentUser).thenReturn(null);

            // ① 阶段末晚于现有开票日 → 前移到阶段末(2026-06-30), 到期日=阶段末+360
            when(customerMapper.selectById(501L)).thenReturn(customerWith(LocalDate.of(2026, 1, 1)));
            service.saveStageAmount(501L, new StageAmountRequest(2026, "H1", bd("120"), bd("0")));
            ArgumentCaptor<BizCustomer> c1 = ArgumentCaptor.forClass(BizCustomer.class);
            verify(customerMapper, times(1)).updateById(c1.capture());
            assertEquals(LocalDate.of(2026, 6, 30), c1.getValue().getLastInvoiceDate());
            assertEquals(LocalDate.of(2026, 6, 30).plusDays(360), c1.getValue().getExpireDate());

            // ② 现有开票日已晚于阶段末 → 不回退(不动保护期)
            reset(customerMapper);
            when(customerMapper.selectById(501L)).thenReturn(customerWith(LocalDate.of(2026, 11, 1)));
            service.saveStageAmount(501L, new StageAmountRequest(2026, "H2", bd("10"), bd("0")));
            verify(customerMapper, never()).updateById(any(BizCustomer.class));

            // ③ 零开票(只录回款) → 不驱动保护期
            when(customerMapper.selectById(501L)).thenReturn(customerWith(null));
            reset(customerMapper);
            when(customerMapper.selectById(501L)).thenReturn(customerWith(null));
            service.saveStageAmount(501L, new StageAmountRequest(2026, "H2", null, bd("88")));
            verify(customerMapper, never()).updateById(any(BizCustomer.class));

            // ④ 提前录入进行中周期(阶段末在未来) → 按今天封顶, 不得把保护期推到未来
            reset(customerMapper);
            when(customerMapper.selectById(501L)).thenReturn(customerWith(LocalDate.of(2025, 1, 1)));
            service.saveStageAmount(501L, new StageAmountRequest(LocalDate.now().getYear(), "FY", bd("200"), bd("150")));
            ArgumentCaptor<BizCustomer> c4 = ArgumentCaptor.forClass(BizCustomer.class);
            verify(customerMapper, times(1)).updateById(c4.capture());
            assertEquals(LocalDate.now(), c4.getValue().getLastInvoiceDate(), "未来阶段末按今天封顶");
        }
    }

    // ====== 【体检 P1 2026-10-12】治愈回收: 重新开票(开票日前移)即清 invoice 类 pending 提醒 ======

    @SuppressWarnings("unchecked")
    @Test
    void saveStageAmountInvoiceHealedCleansInvoicePendingReminders() {
        when(stageAmountMapper.selectByPeriodForUpdate(501L, 2026, "H1")).thenReturn(null);
        when(stageAmountMapper.selectList(any())).thenReturn(List.of());
        stubNoIndicator();
        when(configService.getValue(anyString())).thenReturn(null);

        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::getCurrentUserId).thenReturn(7L);
            sec.when(SecurityUtils::getCurrentUser).thenReturn(null);
            // ① 从未开票客户首次录开票额 → 治愈 invoice_never/invoice_overdue: pending 的 invoice 提醒即清
            when(customerMapper.selectById(501L)).thenReturn(customerWith(null));
            service.saveStageAmount(501L, new StageAmountRequest(2026, "H1", bd("120"), bd("0")));
            ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.aigicrm.module.reminder.entity.BizReminder>> cap =
                    ArgumentCaptor.forClass((Class) com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class);
            verify(reminderMapper, times(1)).delete(cap.capture());
            // 先取 sqlSegment 触发 in() 参数的懒绑定, 再读参数值(MP 3.5.6 行为)
            String healSeg = cap.getValue().getSqlSegment();
            assertTrue(healSeg.contains("customer_id") && healSeg.contains("reminder_type") && healSeg.contains("status"),
                    "治愈清理按客户+类型+状态定位, 实际段: " + healSeg);
            assertTrue(cap.getValue().getParamNameValuePairs().containsValue("invoice_never"), "治愈清理须含 invoice_never");
            assertTrue(cap.getValue().getParamNameValuePairs().containsValue("invoice_overdue"), "治愈清理须含 invoice_overdue");
            assertTrue(cap.getValue().getParamNameValuePairs().containsValue("pending"), "仅清 pending");

            // ② 零开票(只录回款)不驱动治愈清理
            reset(reminderMapper);
            reset(customerMapper);
            when(customerMapper.selectById(501L)).thenReturn(customerWith(null));
            service.saveStageAmount(501L, new StageAmountRequest(2026, "H2", null, bd("88")));
            verify(reminderMapper, never()).delete(any());
        }
    }

    // ====== 三态判定矩阵(纯金额, 五项评分不参与) ======

    @Test
    void coreTypePureAmountMatrix() {
        // 无任何有数据行 → ''(非核心)
        when(stageAmountMapper.selectList(any())).thenReturn(List.of());
        assertEquals("", ReflectionTestUtils.invokeMethod(service, "computeCoreType", 501L));

        // H1+H2 合计双达标 → core
        when(stageAmountMapper.selectList(any())).thenReturn(List.of(
                row(2026, "H1", "60", "50"), row(2026, "H2", "80", "70")));
        assertEquals("core", ReflectionTestUtils.invokeMethod(service, "computeCoreType", 501L));

        // 有数据未双达标 → quasi(哪怕单项很高)
        when(stageAmountMapper.selectList(any())).thenReturn(List.of(row(2026, "H1", "500", "80")));
        assertEquals("quasi", ReflectionTestUtils.invokeMethod(service, "computeCoreType", 501L));

        // 全年行有数据 → 优先于 H1+H2(H1+H2 双达标但全年行汇总未达标 → quasi)
        when(stageAmountMapper.selectList(any())).thenReturn(List.of(
                row(2026, "H1", "60", "50"), row(2026, "H2", "80", "70"),
                row(2026, "FY", "90", "120")));
        assertEquals("quasi", ReflectionTestUtils.invokeMethod(service, "computeCoreType", 501L));

        // 基准年=最近有数据年: 2025 双达标, 2026 只有少量数据 → 基准年前移按 2026 判(quasi)
        when(stageAmountMapper.selectList(any())).thenReturn(List.of(
                row(2025, "FY", "500", "500"), row(2026, "H1", "30", "20")));
        assertEquals("quasi", ReflectionTestUtils.invokeMethod(service, "computeCoreType", 501L));

        // 两值均 0 的行不算数据, 不得抬基准年(2026 全 0 行不遮蔽 2025 真数据)
        when(stageAmountMapper.selectList(any())).thenReturn(List.of(
                row(2025, "FY", "500", "500"), row(2026, "H1", "0", "0")));
        assertEquals("core", ReflectionTestUtils.invokeMethod(service, "computeCoreType", 501L));

        // 边界: 恰好 100/100 → core(≥ 门槛)
        when(stageAmountMapper.selectList(any())).thenReturn(List.of(row(2026, "FY", "100", "100")));
        assertEquals("core", ReflectionTestUtils.invokeMethod(service, "computeCoreType", 501L));
    }

    @Test
    void recalculateSyncsIsCoreAndAutoCreatesIndicatorRow() {
        when(stageAmountMapper.selectList(any())).thenReturn(List.of(row(2026, "FY", "150", "120")));

        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::getCurrentUserId).thenReturn(7L);
            sec.when(SecurityUtils::getCurrentUser).thenReturn(null);

            // 无指标行 → 自动建行落 core_type, is_core 兼容同步
            stubNoIndicator();
            ReflectionTestUtils.invokeMethod(service, "recalculateCoreStatus", 501L);
            ArgumentCaptor<BizCustomerIndicator> ins = ArgumentCaptor.forClass(BizCustomerIndicator.class);
            verify(indicatorMapper).insert(ins.capture());
            assertEquals("core", ins.getValue().getCoreType());
            assertEquals(1, ins.getValue().getIsCore());

            // 有指标行 → 原位更新(core→quasi 时 is_core 归 0)
            reset(indicatorMapper);
            BizCustomerIndicator existing = new BizCustomerIndicator();
            existing.setId(3L);
            existing.setCustomerId(501L);
            existing.setRelationshipScore(3);
            existing.setIsCore(1);
            when(indicatorMapper.selectOne(any())).thenReturn(existing);
            when(stageAmountMapper.selectList(any())).thenReturn(List.of(row(2026, "FY", "150", "20")));
            ReflectionTestUtils.invokeMethod(service, "recalculateCoreStatus", 501L);
            ArgumentCaptor<BizCustomerIndicator> upd = ArgumentCaptor.forClass(BizCustomerIndicator.class);
            verify(indicatorMapper).updateById(upd.capture());
            assertEquals("quasi", upd.getValue().getCoreType());
            assertEquals(0, upd.getValue().getIsCore());
        }
    }

    /** saveIndicator 是五项评分入口, 请求体携带 core_type/is_core 不得覆盖判定落库值(域外输入屏蔽) */
    @Test
    void saveIndicatorShieldsCoreFieldsFromRequestPayload() {
        BizCustomerIndicator existing = new BizCustomerIndicator();
        existing.setId(3L);
        existing.setCustomerId(501L);
        existing.setCoreType("core");
        existing.setIsCore(1);
        when(indicatorMapper.selectOne(any())).thenReturn(existing);

        BizCustomerIndicator attack = new BizCustomerIndicator();
        attack.setRelationshipScore(3);
        attack.setCoreType("quasi");
        attack.setIsCore(0);

        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::getCurrentUserId).thenReturn(7L);
            sec.when(SecurityUtils::getCurrentUser).thenReturn(null);
            when(stageAmountMapper.selectList(any())).thenReturn(List.of());

            service.saveIndicator(501L, attack);

            // saveIndicator 内部还会触发 recalculateCoreStatus 的 updateById(第二次, coreType 为判定值);
            // 本断言只针对第一次(请求透传落库的那次): core_type/is_core 须置空交由 updateById 跳过
            ArgumentCaptor<BizCustomerIndicator> captor = ArgumentCaptor.forClass(BizCustomerIndicator.class);
            verify(indicatorMapper, atLeastOnce()).updateById(captor.capture());
            assertNull(captor.getAllValues().get(0).getCoreType(), "core_type 须置空交由 updateById 跳过, 不被请求覆盖");
            assertNull(captor.getAllValues().get(0).getIsCore());
        }
    }

    // ====== 判定摘要: 排序 + verdict ======

    @Test
    void stageAmountSummaryOrdersRowsAndComputesVerdict() {
        when(userMapper.selectByIds(any())).thenReturn(List.of());
        // Service 对返回列表原地排序, 须喂可变列表(List.of 不可变会抛 UnsupportedOperationException)
        when(stageAmountMapper.selectList(any())).thenReturn(new java.util.ArrayList<>(List.of(
                row(2025, "FY", "300", "300"),
                row(2026, "H1", "60", "50"),
                row(2026, "FY", "130", "90"),
                row(2026, "H2", "80", "70"))));

        var summary = service.getStageAmountSummary(501L);
        @SuppressWarnings("unchecked")
        List<BizCustomerStageAmount> rows = (List<BizCustomerStageAmount>) summary.get("rows");
        assertEquals(4, rows.size());
        assertEquals("2026全年", rows.get(0).getPeriodLabel(), "年内 全年 > 下半年 > 上半年");
        assertEquals("2026下半年", rows.get(1).getPeriodLabel());
        assertEquals("2026上半年", rows.get(2).getPeriodLabel());
        assertEquals("2025全年", rows.get(3).getPeriodLabel(), "年倒序");
        assertEquals(2026, summary.get("baseYear"));
        assertEquals(0, bd("130").compareTo((BigDecimal) summary.get("annualInvoiceAmount")), "全年行优先, 不取 H1+H2");
        assertEquals(0, bd("90").compareTo((BigDecimal) summary.get("annualPaymentAmount")));
        assertEquals("quasi", summary.get("coreType"));

        // 无 FY 行时回落 H1+H2
        when(stageAmountMapper.selectList(any())).thenReturn(new java.util.ArrayList<>(List.of(
                row(2026, "H1", "60", "55"), row(2026, "H2", "80", "75"))));
        var s2 = service.getStageAmountSummary(501L);
        assertEquals(0, bd("140").compareTo((BigDecimal) s2.get("annualInvoiceAmount")));
        assertEquals("core", s2.get("coreType"));

        // 无数据 → 非核心, baseYear=0
        when(stageAmountMapper.selectList(any())).thenReturn(new java.util.ArrayList<>(List.of(row(2026, "H1", "0", "0"))));
        var s3 = service.getStageAmountSummary(501L);
        assertEquals("", s3.get("coreType"));
        assertEquals(0, s3.get("baseYear"));
    }

    private static BigDecimal bd(String v) { return new BigDecimal(v); }
}
