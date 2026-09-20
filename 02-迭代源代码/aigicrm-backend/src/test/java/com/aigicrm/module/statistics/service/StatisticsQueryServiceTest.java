package com.aigicrm.module.statistics.service;

import com.aigicrm.module.approval.mapper.BizApprovalMapper;
import com.aigicrm.module.approval.service.ApprovalService;
import com.aigicrm.module.customer.mapper.BizCustomerIndicatorMapper;
import com.aigicrm.module.customer.mapper.BizCustomerLogMapper;
import com.aigicrm.module.customer.mapper.BizCustomerMapper;
import com.aigicrm.module.customer.mapper.BizInvoiceMapper;
import com.aigicrm.module.customer.mapper.BizPaymentMapper;
import com.aigicrm.module.customer.service.CustomerService;
import com.aigicrm.module.customer.entity.BizCustomer;
import com.aigicrm.module.customer.entity.BizCustomerLog;
import com.aigicrm.module.incentive.entity.BizIncentiveRecord;
import com.aigicrm.module.approval.entity.BizApproval;
import com.aigicrm.module.file.entity.BizFile;
import com.aigicrm.module.file.mapper.BizFileMapper;
import com.aigicrm.module.incentive.mapper.BizIncentiveCategoryMapper;
import com.aigicrm.module.incentive.mapper.BizIncentiveRecordMapper;
import com.aigicrm.module.sea.service.SeaService;
import com.aigicrm.module.system.mapper.SysDeptMapper;
import com.aigicrm.module.system.mapper.SysLogMapper;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.security.SecurityUtils;
import com.aigicrm.module.system.entity.SysLog;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatisticsQueryServiceTest {

    @org.junit.jupiter.api.BeforeAll
    static void initMybatisEntityMetadata() {
        initTableInfo(BizFile.class);
        initTableInfo(BizCustomerLog.class);
        initTableInfo(BizIncentiveRecord.class);
        initTableInfo(BizApproval.class);
        initTableInfo(BizCustomer.class);
        initTableInfo(com.aigicrm.module.customer.entity.BizInvoice.class);
        initTableInfo(com.aigicrm.module.customer.entity.BizPayment.class);
        initTableInfo(SysLog.class);
    }

    private static void initTableInfo(Class<?> entityType) {
        if (TableInfoHelper.getTableInfo(entityType) == null) {
            TableInfoHelper.initTableInfo(
                    new MapperBuilderAssistant(new MybatisConfiguration(), ""), entityType);
        }
    }

    @InjectMocks
    private StatisticsQueryService service;

    @Mock private CustomerService customerService;
    @Mock private SeaService seaService;
    @Mock private ApprovalService approvalService;
    @Mock private BizCustomerMapper customerMapper;
    @Mock private BizCustomerLogMapper customerLogMapper;
    @Mock private BizIncentiveRecordMapper incentiveRecordMapper;
    @Mock private BizIncentiveCategoryMapper incentiveCategoryMapper;
    @Mock private BizFileMapper fileMapper;
    @Mock private BizCustomerIndicatorMapper indicatorMapper;
    @Mock private BizInvoiceMapper invoiceMapper;
    @Mock private BizPaymentMapper paymentMapper;
    @Mock private SysUserMapper userMapper;
    @Mock private SysDeptMapper deptMapper;
    @Mock private SysLogMapper sysLogMapper;
    @Mock private BizApprovalMapper approvalMapper;

    @Test
    void dashboardUsesScopeAwareCustomerCountsIncludingTodayExpiring() {
        when(customerService.countByScope()).thenReturn(12L);
        when(customerService.countInProtectionByScope()).thenReturn(8L);
        when(customerService.countExpiringByScope()).thenReturn(3L);
        when(customerService.countExpiredByScope()).thenReturn(1L);
        when(customerService.getExpiringByScope(5)).thenReturn(List.of());
        when(customerService.countExpiringTodayByScope()).thenReturn(2L);
        when(customerService.countNewThisMonth()).thenReturn(4L);
        when(seaService.statistics()).thenReturn(java.util.Map.of());
        when(approvalService.statistics(7L)).thenReturn(java.util.Map.of());

        try (MockedStatic<SecurityUtils> security = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUserId).thenReturn(7L);

            var result = service.dashboard();

            assertEquals(12L, result.get("myCustomers"));
            assertEquals(2L, result.get("todayExpiring"));
            assertEquals(4L, result.get("monthNew"));
            verify(customerService).countExpiringTodayByScope();
        }
    }

    @Test
    void dailyStatsAppliesCurrentUserScopeToFileCounts() {
        when(customerService.dailyNewCustomers()).thenReturn(List.of());
        when(customerService.getCurrentScopeUserIds()).thenReturn(List.of(7L, 8L));
        when(fileMapper.selectCount(org.mockito.ArgumentMatchers.any())).thenReturn(0L);

        service.dailyStats();

        var captured = new AtomicReference<LambdaQueryWrapper<BizFile>>();
        verify(fileMapper, org.mockito.Mockito.atLeastOnce()).selectCount(
                org.mockito.ArgumentMatchers.argThat(wrapper -> {
                    captured.set((LambdaQueryWrapper<BizFile>) wrapper);
                    return true;
                }));
        assertTrue(captured.get().getSqlSegment().contains("uploader_id"));
    }

    @Test
    void weeklyStatsAppliesCurrentUserScopeToFileCounts() {
        when(customerService.weeklyNewCustomers()).thenReturn(List.of());
        when(customerService.getCurrentScopeUserIds()).thenReturn(List.of(7L));
        when(fileMapper.selectCount(org.mockito.ArgumentMatchers.any())).thenReturn(0L);

        service.weeklyStats();

        verify(fileMapper, org.mockito.Mockito.atLeastOnce()).selectCount(
                org.mockito.ArgumentMatchers.argThat(wrapper ->
                        ((LambdaQueryWrapper<BizFile>) wrapper).getSqlSegment().contains("uploader_id")));
    }

    @Test
    void indicatorStatsKeepsCompanyWideVisibilitySeparateFromAccountScope() {
        var customer = new com.aigicrm.module.customer.entity.BizCustomer();
        customer.setId(101L);
        customer.setOwnerId(8L);
        when(customerService.getAllActiveCustomers()).thenReturn(List.of(customer));
        when(indicatorMapper.selectList(null)).thenReturn(List.of());
        when(invoiceMapper.selectList(org.mockito.ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.Wrapper<com.aigicrm.module.customer.entity.BizInvoice>>any())).thenReturn(List.of());
        when(paymentMapper.selectList(org.mockito.ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.Wrapper<com.aigicrm.module.customer.entity.BizPayment>>any())).thenReturn(List.of());

        try (MockedStatic<SecurityUtils> security = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUserId).thenReturn(7L);

            var result = service.indicatorStats();

            assertEquals(0, result.get("coveredCount"));
            assertEquals(0.0, result.get("qualifyRate"));
            verify(customerService).getAllActiveCustomers();
            org.mockito.Mockito.verify(customerService, org.mockito.Mockito.never()).getCurrentScopeUserIds();
        }
    }

    /**
     * 【2026-09-13 回归】四张卡片必须与「客户排行」同为全公司口径。
     * 修复前卡片只统计"当前登录用户负责的客户"（本用例：登录人 7，其名下客户 102 有指标 → coveredCount=1），
     * 而表格是全公司（2 条）→ 首页/该页出现"卡片全 0、表格有数据"的口径不一致。
     */
    @Test
    void indicatorStatsCardsUseCompanyWideScopeSameAsRanking() {
        var mine = new com.aigicrm.module.customer.entity.BizCustomer();
        mine.setId(102L);
        mine.setOwnerId(7L);
        mine.setCustomerName("登录人名下客户");
        var other = new com.aigicrm.module.customer.entity.BizCustomer();
        other.setId(101L);
        other.setOwnerId(8L);
        other.setCustomerName("他人负责客户");
        when(customerService.getAllActiveCustomers()).thenReturn(List.of(mine, other));

        var mineIndicator = new com.aigicrm.module.customer.entity.BizCustomerIndicator();
        mineIndicator.setCustomerId(102L);
        mineIndicator.setRelationshipScore(2);
        mineIndicator.setIndicator02(1);
        var otherIndicator = new com.aigicrm.module.customer.entity.BizCustomerIndicator();
        otherIndicator.setCustomerId(101L);
        otherIndicator.setRelationshipScore(2);
        otherIndicator.setIndicator02(2);
        otherIndicator.setIndicator03(2);
        otherIndicator.setIndicator04(1);
        otherIndicator.setIndicator05(2);
        when(indicatorMapper.selectList(null)).thenReturn(List.of(mineIndicator, otherIndicator));
        when(invoiceMapper.selectList(org.mockito.ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.Wrapper<com.aigicrm.module.customer.entity.BizInvoice>>any())).thenReturn(List.of());
        when(paymentMapper.selectList(org.mockito.ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.Wrapper<com.aigicrm.module.customer.entity.BizPayment>>any())).thenReturn(List.of());

        try (MockedStatic<SecurityUtils> security = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUserId).thenReturn(7L);

            var result = service.indicatorStats();

            // 覆盖客户数 = 全部有指标记录的客户（2），而不是仅登录人名下的 1
            assertEquals(2, result.get("coveredCount"));
            // 平均分 = (3 + 9) / 2 = 6.0
            assertEquals(6.0, result.get("avgScore"));
            assertEquals(0, result.get("coreCount"));
            @SuppressWarnings("unchecked")
            var ranking = (List<java.util.Map<String, Object>>) result.get("customerRanking");
            assertEquals(2, ranking.size());
            assertEquals(102L, ranking.get(1).get("customerId")); // 排序：9 分在前，3 分在后
        }
    }

    @Test
    void teamActivitiesAppliesScopeToEveryActivitySource() {
        when(customerService.getCurrentScopeUserIds()).thenReturn(List.of(7L));
        when(customerLogMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(incentiveRecordMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(fileMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(approvalMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(customerMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(sysLogMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());

        service.teamActivities();

        verify(customerLogMapper).selectList(org.mockito.ArgumentMatchers.argThat(wrapper ->
                ((LambdaQueryWrapper<BizCustomerLog>) wrapper).getSqlSegment().contains("user_id")));
        verify(incentiveRecordMapper).selectList(org.mockito.ArgumentMatchers.argThat(wrapper ->
                ((LambdaQueryWrapper<BizIncentiveRecord>) wrapper).getSqlSegment().contains("user_id")));
        verify(fileMapper).selectList(org.mockito.ArgumentMatchers.argThat(wrapper ->
                ((LambdaQueryWrapper<BizFile>) wrapper).getSqlSegment().contains("uploader_id")));
        verify(approvalMapper).selectList(org.mockito.ArgumentMatchers.argThat(wrapper ->
                ((LambdaQueryWrapper<BizApproval>) wrapper).getSqlSegment().contains("applicant_id")));
        verify(customerMapper).selectList(org.mockito.ArgumentMatchers.argThat(wrapper ->
                ((LambdaQueryWrapper<BizCustomer>) wrapper).getSqlSegment().contains("owner_id")));
        verify(sysLogMapper).selectList(org.mockito.ArgumentMatchers.argThat(wrapper ->
                ((LambdaQueryWrapper<SysLog>) wrapper).getSqlSegment().contains("user_id")));
    }

    @Test
    void coreCustomer_requiresScoreAndBothFinancialThresholds() {
        assertTrue(StatisticsQueryService.isCoreCustomer(12, money("100"), money("100")));
        assertTrue(StatisticsQueryService.isCoreCustomer(15, money("120"), money("150")));
        assertFalse(StatisticsQueryService.isCoreCustomer(11, money("100"), money("100")));
        assertFalse(StatisticsQueryService.isCoreCustomer(12, money("99.99"), money("100")));
        assertFalse(StatisticsQueryService.isCoreCustomer(12, money("100"), money("99.99")));
    }

    @Test
    void indicatorScore_sumsFiveBusinessIndicators_andTreatsMissingValuesAsZero() {
        var indicator = new com.aigicrm.module.customer.entity.BizCustomerIndicator();
        indicator.setRelationshipScore(2);
        indicator.setIndicator02(3);
        indicator.setIndicator03(3);
        indicator.setIndicator04(3);
        indicator.setIndicator05(2);
        indicator.setIndicator06(3);

        org.junit.jupiter.api.Assertions.assertEquals(13, StatisticsQueryService.indicatorScore(indicator));

        indicator.setIndicator04(null);
        org.junit.jupiter.api.Assertions.assertEquals(10, StatisticsQueryService.indicatorScore(indicator));
        org.junit.jupiter.api.Assertions.assertEquals(0, StatisticsQueryService.indicatorScore(null));
    }

    @Test
    void monthDaysFixture_coversOnlyTheCurrentMonthThroughToday() {
        List<LocalDate> days = StatisticsQueryService.monthDaysThrough(LocalDate.of(2026, 2, 3));

        org.junit.jupiter.api.Assertions.assertEquals(3, days.size());
        org.junit.jupiter.api.Assertions.assertEquals(LocalDate.of(2026, 2, 1), days.get(0));
        org.junit.jupiter.api.Assertions.assertEquals(LocalDate.of(2026, 2, 3), days.get(2));
    }

    @Test
    void monthWeekFixture_keepsTheFinalPartialWeek() {
        List<LocalDate> starts = StatisticsQueryService.monthWeekStartsThrough(LocalDate.of(2026, 2, 17));

        org.junit.jupiter.api.Assertions.assertEquals(
                List.of(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 8), LocalDate.of(2026, 2, 15)),
                starts);
    }


    /**
     * 【PR-18 统计口径】指标统计的开票/回款聚合必须排除已作废(voided)台账——
     * 作废记录只留痕不计入金额。断言下发到 Mapper 的查询条件包含 status <> 'voided'
     * (与既有 uploader_id 范围断言同风格, 防口径回退)。
     */
    @Test
    void indicatorStatsExcludesVoidedLedgerFromTotals() {
        when(customerService.getAllActiveCustomers()).thenReturn(List.of());
        when(indicatorMapper.selectList(null)).thenReturn(List.of());
        when(invoiceMapper.selectList(org.mockito.ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.Wrapper<com.aigicrm.module.customer.entity.BizInvoice>>any())).thenReturn(List.of());
        when(paymentMapper.selectList(org.mockito.ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.Wrapper<com.aigicrm.module.customer.entity.BizPayment>>any())).thenReturn(List.of());

        try (MockedStatic<SecurityUtils> security = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUserId).thenReturn(7L);

            service.indicatorStats();

            org.mockito.Mockito.verify(invoiceMapper).selectList(
                    org.mockito.ArgumentMatchers.argThat(w ->
                            w != null && ((com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<?>) w)
                                    .getSqlSegment().contains("status")
                            && ((com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<?>) w)
                                    .getParamNameValuePairs().containsValue("voided")));
            org.mockito.Mockito.verify(paymentMapper).selectList(
                    org.mockito.ArgumentMatchers.argThat(w ->
                            w != null && ((com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<?>) w)
                                    .getSqlSegment().contains("status")
                            && ((com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<?>) w)
                                    .getParamNameValuePairs().containsValue("voided")));
        }
    }

    private BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
