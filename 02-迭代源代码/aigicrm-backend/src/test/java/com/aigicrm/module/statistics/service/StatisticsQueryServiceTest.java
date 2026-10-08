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
    // 【G2 2026-09-21】visibleCustomers 依赖; 未 stub 时 resolveModuleUsers 返回 null(=全量),
    // 既有"全公司口径"聚合用例语义不变; 新政策行为由下方过滤用例单独钉住
    @Mock private com.aigicrm.common.scope.OrgScopeResolver orgScopeResolver;
    @Mock private SeaService seaService;
    @Mock private ApprovalService approvalService;
    @Mock private BizCustomerMapper customerMapper;
    @Mock private BizCustomerLogMapper customerLogMapper;
    @Mock private BizIncentiveRecordMapper incentiveRecordMapper;
    @Mock private BizIncentiveCategoryMapper incentiveCategoryMapper;
    // 【2026-09-23】客户排行四项激励聚合改走激励模块分享制(applyShareVisibility 复用)
    @Mock private com.aigicrm.module.incentive.service.IncentiveService incentiveService;
    @Mock private BizFileMapper fileMapper;
    @Mock private BizCustomerIndicatorMapper indicatorMapper;
    @Mock private BizInvoiceMapper invoiceMapper;
    @Mock private BizPaymentMapper paymentMapper;
    @Mock private SysUserMapper userMapper;
    @Mock private SysDeptMapper deptMapper;
    @Mock private SysLogMapper sysLogMapper;
    @Mock private BizApprovalMapper approvalMapper;
    // 【C1/C2 2026-10-08】dashboardScopeKey 非 admin 分支依赖锚点解析(service 构造注入,此前无该分支直达测试)
    @Mock private com.aigicrm.module.system.service.RolePermScopeService rolePermScopeService;

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

    /**
     * 【2026-09-23 回归】客户排行的四项激励聚合=激励模块分享制(本人+他人shared=1)，
     * 不再按客户数据可见性收口。修复前：记录被 visibleCustomers 过滤，登录人名下之外的客户
     * 激励行被整体剔除——SELF/team 销售打开客户排行"只看得到自己的，看不到别人的"。
     */
    @Test
    void customerRankingsIncentiveAggregationUsesShareVisibilityNotCustomerScope() {
        var othersCustomer = new com.aigicrm.module.customer.entity.BizCustomer();
        othersCustomer.setId(999L);
        othersCustomer.setOwnerId(8L);
        othersCustomer.setCustomerName("他人负责客户");
        othersCustomer.setIndustry("煤矿");
        var sharedRecord = new com.aigicrm.module.incentive.entity.BizIncentiveRecord();
        sharedRecord.setCustomerId(999L);
        sharedRecord.setUserId(8L);
        sharedRecord.setShared(1);
        sharedRecord.setScore(5);

        when(incentiveCategoryMapper.selectList(null)).thenReturn(List.of());
        when(incentiveRecordMapper.selectList(org.mockito.ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.Wrapper<com.aigicrm.module.incentive.entity.BizIncentiveRecord>>any()))
                .thenReturn(List.of(sharedRecord));
        when(customerMapper.selectBatchIds(org.mockito.ArgumentMatchers.<java.util.Collection<Long>>any()))
                .thenReturn(List.of(othersCustomer));
        var owner = new com.aigicrm.module.system.entity.SysUser();
        owner.setId(8L);
        owner.setRealName("张三");
        when(userMapper.selectBatchIds(org.mockito.ArgumentMatchers.<java.util.Collection<Long>>any()))
                .thenReturn(List.of(owner));
        // indicatorStats(客情/指标排行)内部依赖: 全空,聚焦激励排行断言
        when(customerService.getAllActiveCustomers()).thenReturn(List.of());
        when(indicatorMapper.selectList(null)).thenReturn(List.of());
        when(invoiceMapper.selectList(org.mockito.ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.Wrapper<com.aigicrm.module.customer.entity.BizInvoice>>any())).thenReturn(List.of());
        when(paymentMapper.selectList(org.mockito.ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.Wrapper<com.aigicrm.module.customer.entity.BizPayment>>any())).thenReturn(List.of());

        var result = service.customerRankings(null);

        // 他人客户(登录人不可见范围)的激励行必须出现——口径=激励分享制,非客户可见性
        @SuppressWarnings("unchecked")
        var incentiveRanking = (List<java.util.Map<String, Object>>) result.get("incentiveRanking");
        assertEquals(1, incentiveRanking.size());
        assertEquals(999L, incentiveRanking.get(0).get("customerId"));
        assertEquals(5, incentiveRanking.get(0).get("totalScore"));
        // 过滤确实委托给激励模块单源(applyShareVisibility),统计侧不再自造口径
        verify(incentiveService).applyShareVisibility(org.mockito.ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.aigicrm.module.incentive.entity.BizIncentiveRecord>>any());
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

    /**
     * 【G2 2026-09-21 权限审计批次2】非 admin 统计口径收口:
     * 指标与客户必须按 statistics 模块可见人员集合过滤(修复"全员口径"在租户插件关闭时跨集团/跨范围泄露)。
     */
    @org.junit.jupiter.api.Test
    void indicatorStatsFiltersByModuleVisibilityForNonAdmin() {
        var mine = new com.aigicrm.module.customer.entity.BizCustomer();
        mine.setId(102L);
        mine.setOwnerId(7L);
        var other = new com.aigicrm.module.customer.entity.BizCustomer();
        other.setId(101L);
        other.setOwnerId(8L);
        when(customerService.getAllActiveCustomers()).thenReturn(java.util.List.of(mine, other));
        var mineIndicator = new com.aigicrm.module.customer.entity.BizCustomerIndicator();
        mineIndicator.setCustomerId(102L);
        mineIndicator.setRelationshipScore(3);
        var otherIndicator = new com.aigicrm.module.customer.entity.BizCustomerIndicator();
        otherIndicator.setCustomerId(101L);
        otherIndicator.setRelationshipScore(3);
        when(indicatorMapper.selectList(null)).thenReturn(java.util.List.of(mineIndicator, otherIndicator));
        when(invoiceMapper.selectList(org.mockito.ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.Wrapper<com.aigicrm.module.customer.entity.BizInvoice>>any())).thenReturn(java.util.List.of());
        when(paymentMapper.selectList(org.mockito.ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.Wrapper<com.aigicrm.module.customer.entity.BizPayment>>any())).thenReturn(java.util.List.of());

        try (org.mockito.MockedStatic<com.aigicrm.security.SecurityUtils> security = org.mockito.Mockito.mockStatic(com.aigicrm.security.SecurityUtils.class)) {
            security.when(com.aigicrm.security.SecurityUtils::getCurrentUserId).thenReturn(7L);
            security.when(com.aigicrm.security.SecurityUtils::getCurrentUser).thenReturn(org.mockito.Mockito.mock(com.aigicrm.security.SecurityUser.class));
            security.when(com.aigicrm.security.SecurityUtils::isAdmin).thenReturn(false);
            when(orgScopeResolver.resolveModuleUsers(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("statistics")))
                    .thenReturn(java.util.List.of(7L));

            var result = service.indicatorStats();

            assertEquals(1, result.get("coveredCount"), "范围外客户(他人负责)的指标必须被剔除");
        }
    }

    private BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    /**
     * 【C1/C2 2026-10-08】看板范围签名必须并入集团前缀与规则引擎指纹:
     * C1——applyDataScope 对非 admin 同样追加 applyRuleEngineFilter,同可见集合(all)不同规则
     *      的用户键必须不同(无规则=固定值 all);
     * C2——键前缀含当前集团(currentGroupId),不同集团同 "all" 范围不得共享键(取不到集团=fail-closed 固定前缀)。
     */
    @Test
    void dashboardScopeKey_includesGroupPrefixAndRuleFingerprint() {
        com.aigicrm.security.SecurityUser user = new com.aigicrm.security.SecurityUser();
        user.setUserId(7L);
        user.setCurrentGroupId(2L);
        com.aigicrm.module.system.entity.SysPermissionRule rule =
                new com.aigicrm.module.system.entity.SysPermissionRule();
        rule.setRuleType("column_filter");
        rule.setTargetEntity("customer");
        rule.setTargetColumn("industry");
        rule.setOperator("eq");
        rule.setRuleValue("冶金");
        rule.setStatus(1);
        user.setPermissionRules(List.of(rule));
        when(orgScopeResolver.resolveScope(org.mockito.ArgumentMatchers.any()))
                .thenReturn(com.aigicrm.common.scope.ScopeDecision.all());

        try (MockedStatic<SecurityUtils> security = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUser).thenReturn(user);
            security.when(SecurityUtils::isAdmin).thenReturn(false);
            security.when(SecurityUtils::getCurrentGroupIds).thenReturn(List.of(2L));

            String withRule = service.dashboardScopeKey();
            assertTrue(withRule.startsWith("g2:all|"), "键应为 集团前缀g2 + 范围all + 规则指纹, 实际: " + withRule);
            org.junit.jupiter.api.Assertions.assertNotEquals("all",
                    withRule.substring(withRule.lastIndexOf('|') + 1),
                    "有 customer 规则时指纹不得是固定值 all(同集合不同规则会互串, C1)");

            // 无规则 → 指纹固定值 all
            user.setPermissionRules(List.of());
            String noRule = service.dashboardScopeKey();
            assertTrue(noRule.startsWith("g2:all|all"), "无规则时指纹应为固定值 all, 实际: " + noRule);

            // C2: 集团前缀随 currentGroupId 变化;取不到集团时=fail-closed 固定前缀
            user.setCurrentGroupId(3L);
            assertTrue(service.dashboardScopeKey().startsWith("g3:"), "不同集团键前缀必须不同");
            user.setCurrentGroupId(null);
            security.when(SecurityUtils::getCurrentGroupIds).thenReturn(List.of());
            assertTrue(service.dashboardScopeKey().startsWith("g-none:"),
                    "取不到集团时必须用 fail-closed 固定前缀, 实际: " + service.dashboardScopeKey());
        }
    }
}
