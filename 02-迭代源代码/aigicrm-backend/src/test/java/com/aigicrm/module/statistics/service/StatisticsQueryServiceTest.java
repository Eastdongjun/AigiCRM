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
    // 【2026-09-23】客户排行四项服务聚合改走激励模块分享制(applyShareVisibility 复用)
    @Mock private com.aigicrm.module.incentive.service.IncentiveService incentiveService;
    @Mock private BizFileMapper fileMapper;
    @Mock private BizCustomerIndicatorMapper indicatorMapper;
    // 【裁决 2026-10-10】判定金额源换阶段总额(invoice/payment mapper 已从 service 移除)
    @Mock private com.aigicrm.module.customer.mapper.BizCustomerStageAmountMapper stageAmountMapper;
    @Mock private SysUserMapper userMapper;
    @Mock private SysDeptMapper deptMapper;
    @Mock private SysLogMapper sysLogMapper;
    @Mock private BizApprovalMapper approvalMapper;
    // 【C1/C2 2026-10-08】dashboardScopeKey 非 admin 分支依赖锚点解析(service 构造注入,此前无该分支直达测试)
    @Mock private com.aigicrm.module.system.service.RolePermScopeService rolePermScopeService;
    // 【2026-11-03 门槛配置化】核心三态双门槛读 sys_config(未 stub 时 getValue 返回 null → 回退默认 50/100)
    @Mock private com.aigicrm.module.system.service.SysConfigService configService;

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
        when(stageAmountMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());

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
        when(stageAmountMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());

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
     * 【口径统一 2026-11-02 用户裁决选项一】客户排行三类(四项服务/客情/六项指标)=客户可见范围同基线:
     * 你能看到哪些客户, 排行就按哪些客户算。曾于 2026-09-23 由 G2 改分享制(销售反馈"只看到自己客户"),
     * 2026-11-02 用户拍板三类排行统一客户可见范围——分享记录引用的范围外客户不再进入排行。
     * (激励个人排行维持公司级=2026-09-24"允许暴露"裁决, 不属客户维度)
     */
    @Test
    void customerRankingsIncentiveAggregationUsesCustomerVisibilityBaseline() {
        var visibleCustomer = new com.aigicrm.module.customer.entity.BizCustomer();
        visibleCustomer.setId(500L);
        visibleCustomer.setOwnerId(7L);
        visibleCustomer.setCustomerName("可见客户");
        visibleCustomer.setIndustry("煤矿");
        var visibleRecord = new com.aigicrm.module.incentive.entity.BizIncentiveRecord();
        visibleRecord.setCustomerId(500L);
        visibleRecord.setUserId(7L);
        visibleRecord.setShared(1);
        visibleRecord.setScore(5);
        // 范围外客户的"已分享"记录: 旧分享制口径会进排行, 新基线必须剔除
        var sharedRecord = new com.aigicrm.module.incentive.entity.BizIncentiveRecord();
        sharedRecord.setCustomerId(999L);
        sharedRecord.setUserId(8L);
        sharedRecord.setShared(1);
        sharedRecord.setScore(9);

        when(incentiveCategoryMapper.selectList(null)).thenReturn(List.of());
        when(incentiveRecordMapper.selectList(org.mockito.ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.Wrapper<com.aigicrm.module.incentive.entity.BizIncentiveRecord>>any()))
                .thenReturn(List.of(visibleRecord, sharedRecord));
        var owner = new com.aigicrm.module.system.entity.SysUser();
        owner.setId(7L);
        owner.setRealName("本人");
        when(userMapper.selectBatchIds(org.mockito.ArgumentMatchers.<java.util.Collection<Long>>any()))
                .thenReturn(List.of(owner));
        // 客户可见范围=仅 500(999 在范围外, 即使记录 shared=1 也不进排行)
        when(customerService.getAllActiveCustomers()).thenReturn(List.of(visibleCustomer));
        when(indicatorMapper.selectList(null)).thenReturn(List.of());
        when(stageAmountMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());

        var result = service.customerRankings(null);

        @SuppressWarnings("unchecked")
        var incentiveRanking = (List<java.util.Map<String, Object>>) result.get("incentiveRanking");
        assertEquals(1, incentiveRanking.size(), "范围外客户的分享记录不得进入排行(2026-11-02 统一基线)");
        assertEquals(500L, incentiveRanking.get(0).get("customerId"));
        assertEquals(5, incentiveRanking.get(0).get("totalScore"));
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

    /** 【裁决 2026-10-10 + 2026-11-03 双门槛配置化】核心三态=纯金额门槛:
     *  双>=真核心门槛(默认100万)=core; 双>=争核心门槛(默认50万)未双达真核心=quasi; 任一低于争核心门槛(含无数据)=''。
     *  五项评分不再参与判定(旧 isCoreCustomer 的 score>=12 条件废除)。 */
    @Test
    void coreType_pureAmountThresholds() {
        java.math.BigDecimal minor = money("50");
        java.math.BigDecimal major = money("100");
        assertEquals("core", StatisticsQueryService.coreTypeOf(money("100"), money("100"), minor, major));
        assertEquals("core", StatisticsQueryService.coreTypeOf(money("120"), money("150"), minor, major));
        assertEquals("quasi", StatisticsQueryService.coreTypeOf(money("99.99"), money("100"), minor, major));
        assertEquals("quasi", StatisticsQueryService.coreTypeOf(money("100"), money("99.99"), minor, major));
        // 争核心门槛边界: 双>=50 即 quasi(2026-11-03 起"有数据"不再自动 quasi)
        assertEquals("quasi", StatisticsQueryService.coreTypeOf(money("50"), money("50"), minor, major));
        // 任一低于争核心门槛即非核心(旧口径此两例为 quasi, 2026-11-03 起收紧)
        assertEquals("", StatisticsQueryService.coreTypeOf(money("5"), money("0"), minor, major));
        assertEquals("", StatisticsQueryService.coreTypeOf(money("120"), money("30"), minor, major));
        assertEquals("", StatisticsQueryService.coreTypeOf(money("40"), money("60"), minor, major));
        // 门槛为参数: 管理员改小后原非核心可回升 quasi/自定义档位
        assertEquals("", StatisticsQueryService.coreTypeOf(money("55"), money("55"), money("60"), money("80")));
        assertEquals("quasi", StatisticsQueryService.coreTypeOf(money("60"), money("60"), money("60"), money("80")));
        assertEquals("core", StatisticsQueryService.coreTypeOf(money("80"), money("80"), money("60"), money("80")));
    }

    /** 年度口径: 最近有数据年 + 全年行有数据优先, 否则上半年+下半年合计; 两值均 0 的行不算数据 */
    @Test
    void annualAmounts_latestDataYear_fullYearRowPreferred() {
        var row = new com.aigicrm.module.customer.entity.BizCustomerStageAmount();
        java.util.function.BiFunction<Integer, String, com.aigicrm.module.customer.entity.BizCustomerStageAmount> mk =
                (year, type) -> {
                    var r = new com.aigicrm.module.customer.entity.BizCustomerStageAmount();
                    r.setCustomerId(101L);
                    r.setPeriodYear(year);
                    r.setPeriodType(type);
                    r.setInvoiceAmount(BigDecimal.ZERO);
                    r.setPaymentAmount(BigDecimal.ZERO);
                    return r;
                };
        var h1 = mk.apply(2026, "H1"); h1.setInvoiceAmount(money("60")); h1.setPaymentAmount(money("50"));
        var h2 = mk.apply(2026, "H2"); h2.setInvoiceAmount(money("80")); h2.setPaymentAmount(money("70"));
        var oldFy = mk.apply(2025, "FY"); oldFy.setInvoiceAmount(money("999")); oldFy.setPaymentAmount(money("999"));
        var zeroRow = mk.apply(2027, "H1"); // 两值均 0: 不算数据, 不得抬基准年
        var ann = StatisticsQueryService.annualAmountsByCustomer(java.util.List.of(h1, h2, oldFy, zeroRow));
        // 基准年=2026(有数据), 无 FY 行 → H1+H2 = 开票140/回款120
        assertEquals(money("140"), ann.get(101L)[0]);
        assertEquals(money("120"), ann.get(101L)[1]);

        var fy = mk.apply(2026, "FY"); fy.setInvoiceAmount(money("130")); fy.setPaymentAmount(money("90"));
        var ann2 = StatisticsQueryService.annualAmountsByCustomer(java.util.List.of(h1, h2, fy));
        // FY 行有数据 → 优先于 H1+H2(全年行是年末权威汇总, 可与半年合计不等)
        assertEquals(money("130"), ann2.get(101L)[0]);
        assertEquals(money("90"), ann2.get(101L)[1]);

        // 全年行两值全 0 → 不算数据, 回落 H1+H2
        var fyZero = mk.apply(2026, "FY");
        var ann3 = StatisticsQueryService.annualAmountsByCustomer(java.util.List.of(h1, h2, fyZero));
        assertEquals(money("140"), ann3.get(101L)[0]);
        // 只有全 0 行的客户不进结果(无数据=非核心, 调用方以 null 判非核心)
        org.junit.jupiter.api.Assertions.assertFalse(
                StatisticsQueryService.annualAmountsByCustomer(java.util.List.of(zeroRow)).containsKey(101L));
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
     * 【裁决 2026-10-10】判定金额源必须是阶段总额表(biz_customer_stage_amount), 不再逐笔聚合发票/回款
     * (逐笔录入入口已下线改只读历史; 旧"排除 voided"口径随之作废——阶段表无作废概念)。
     * 断言 indicatorStats 下发 stageAmountMapper.selectList(防金额源回退到逐笔)。
     */
    @Test
    void indicatorStatsUsesStageAmountTableAsAmountSource() {
        when(customerService.getAllActiveCustomers()).thenReturn(List.of());
        when(indicatorMapper.selectList(null)).thenReturn(List.of());
        when(stageAmountMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());

        try (MockedStatic<SecurityUtils> security = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getCurrentUserId).thenReturn(7L);

            service.indicatorStats();

            org.mockito.Mockito.verify(stageAmountMapper).selectList(org.mockito.ArgumentMatchers.any());
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
        when(stageAmountMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(java.util.List.of());

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

    /**
     * 【A6 2026-10-20 审计】空集守卫: getCurrentScopeUserIds 返回非 null 空集合
     * (如 ORG 锚点部门无在职用户)时, applyCustomerScope/applyFileScope 必须显式 1=0,
     * 不得生成 IN () 语法错误(500), 也不得放行全量。
     */
    @Test
    void analysis_emptyScopeSet_appliesFalsePredicateInsteadOfEmptyIn() {
        when(customerService.getCurrentScopeUserIds()).thenReturn(List.of());
        when(customerMapper.selectCount(org.mockito.ArgumentMatchers.any())).thenReturn(0L);
        when(fileMapper.selectCount(org.mockito.ArgumentMatchers.any())).thenReturn(0L);
        when(deptMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(customerMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());

        var result = service.analysis(2026, null, null);

        // 全部统计口径在空集下都是 0(而非报错/全量)
        assertEquals(0L, result.get("totalCustomers"));
        var captured = new java.util.ArrayList<LambdaQueryWrapper<com.aigicrm.module.customer.entity.BizCustomer>>();
        verify(customerMapper, org.mockito.Mockito.atLeastOnce()).selectCount(
                org.mockito.ArgumentMatchers.argThat(w -> {
                    captured.add((LambdaQueryWrapper<com.aigicrm.module.customer.entity.BizCustomer>) w);
                    return true;
                }));
        assertFalse(captured.isEmpty(), "应发生客户统计查询");
        for (var w : captured) {
            String segment = w.getSqlSegment();
            assertTrue(segment.contains("1=0"), "空集应显式 1=0, 实际: " + segment);
            assertFalse(segment.contains("IN ()"), "不得生成 IN () 空集合语法(500 根因): " + segment);
        }
        var fileCaptured = new java.util.ArrayList<LambdaQueryWrapper<com.aigicrm.module.file.entity.BizFile>>();
        verify(fileMapper, org.mockito.Mockito.atLeastOnce()).selectCount(
                org.mockito.ArgumentMatchers.argThat(w -> {
                    fileCaptured.add((LambdaQueryWrapper<com.aigicrm.module.file.entity.BizFile>) w);
                    return true;
                }));
        for (var w : fileCaptured) {
            assertTrue(w.getSqlSegment().contains("1=0"), "applyFileScope 空集同样 1=0: " + w.getSqlSegment());
        }
    }

    /**
     * 【A6】teamActivities 空集: 直接返回空列表, 六路来源不各发恒空查询
     * (语义等同 1=0, 且杜绝 IN () 空集合 SQL)。
     */
    @Test
    void teamActivities_emptyScopeSet_returnsEmptyWithoutQueryingSources() {
        when(customerService.getCurrentScopeUserIds()).thenReturn(List.of());

        var result = service.teamActivities();

        assertTrue(result.isEmpty(), "空集范围 → 空动态");
        verify(customerLogMapper, org.mockito.Mockito.never()).selectList(org.mockito.ArgumentMatchers.any());
        verify(incentiveRecordMapper, org.mockito.Mockito.never()).selectList(org.mockito.ArgumentMatchers.any());
        verify(fileMapper, org.mockito.Mockito.never()).selectList(org.mockito.ArgumentMatchers.any());
        verify(approvalMapper, org.mockito.Mockito.never()).selectList(org.mockito.ArgumentMatchers.any());
        verify(customerMapper, org.mockito.Mockito.never()).selectList(org.mockito.ArgumentMatchers.any());
        verify(sysLogMapper, org.mockito.Mockito.never()).selectList(org.mockito.ArgumentMatchers.any());
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
