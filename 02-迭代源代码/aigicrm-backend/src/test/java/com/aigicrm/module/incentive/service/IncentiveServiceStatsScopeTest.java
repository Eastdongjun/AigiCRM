package com.aigicrm.module.incentive.service;

import com.aigicrm.common.scope.OrgScopeResolver;
import com.aigicrm.module.customer.entity.BizCustomer;
import com.aigicrm.module.customer.mapper.BizCustomerMapper;
import com.aigicrm.module.incentive.mapper.BizIncentiveCategoryMapper;
import com.aigicrm.module.incentive.mapper.BizIncentiveRecordMapper;
import com.aigicrm.module.file.mapper.BizFileMapper;
import com.aigicrm.module.incentive.entity.BizIncentiveRecord;
import com.aigicrm.module.system.mapper.SysDeptMapper;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.module.system.entity.SysUser;
import com.aigicrm.security.SecurityUser;
import com.aigicrm.security.SecurityUtils;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 激励统计家族数据范围收口·单元测试（2026-11-04 用户令"四项服务/四项激励也设置上对应的数据的权限"）。
 * 被测行为: categoryStats(客户维度)=可见客户集过滤; scoreboard/部门排行(人员维度)=可见人员集过滤;
 * admin 与系统任务上下文(null 用户)不过滤(与 StatisticsQueryService.visibleCustomers 同 fail-open 模式)。
 */
@ExtendWith(MockitoExtension.class)
class IncentiveServiceStatsScopeTest {

    @org.junit.jupiter.api.BeforeAll
    static void initMybatisEntityMetadata() {
        initTableInfo(BizIncentiveRecord.class);
        initTableInfo(BizCustomer.class);
    }

    private static void initTableInfo(Class<?> entityType) {
        if (TableInfoHelper.getTableInfo(entityType) == null) {
            TableInfoHelper.initTableInfo(
                    new MapperBuilderAssistant(new MybatisConfiguration(), ""), entityType);
        }
    }

    @InjectMocks
    private IncentiveService service;

    @Mock private BizIncentiveCategoryMapper categoryMapper;
    @Mock private BizIncentiveRecordMapper recordMapper;
    @Mock private SysUserMapper userMapper;
    @Mock private BizFileMapper fileMapper;
    @Mock private BizCustomerMapper customerMapper;
    @Mock private SysDeptMapper deptMapper;
    @Mock private com.aigicrm.common.scope.OrgScopeResolver scopeResolver;
    @Mock private org.springframework.jdbc.core.JdbcTemplate jdbc;

    private static final Long ME = 72L;
    private static final Long OTHER = 39L;

    private SecurityUser me() {
        SecurityUser user = new SecurityUser();
        user.setUserId(ME);
        user.setUsername("smoke_sales");
        return user;
    }

    private BizCustomer cust(Long id, Long ownerId) {
        BizCustomer c = new BizCustomer();
        c.setId(id);
        c.setOwnerId(ownerId);
        c.setDeleted(0);
        c.setStatus("正常");
        return c;
    }

    private BizIncentiveRecord rec(Long id, Long userId, Long customerId) {
        BizIncentiveRecord r = new BizIncentiveRecord();
        r.setId(id);
        r.setUserId(userId);
        r.setCustomerId(customerId);
        r.setFilePreIds(List.of(id)); // 佐证(JSON 数组) → fileCount>=1 → 计入统计
        r.setScore(10);
        r.setCategoryId(11L);
        return r;
    }

    @Test
    void categoryStatsFiltersRecordsToVisibleCustomers() {
        // 可见人员=[ME] → 可见客户=[1(我的), 3(未分配负责人保持全员可见)]; 客户2=范围外
        when(customerMapper.selectList(any())).thenReturn(List.of(cust(1L, ME), cust(2L, OTHER), cust(3L, null)));
        when(recordMapper.selectList(any())).thenReturn(List.of(
                rec(101L, ME, 1L), rec(102L, OTHER, 2L), rec(103L, ME, 3L)));
        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::getCurrentUser).thenReturn(me());
            sec.when(SecurityUtils::isAdmin).thenReturn(false);
            when(scopeResolver.resolveModuleUsers(any(), anyString())).thenReturn(List.of(ME));

            var result = service.categoryStats(null);

            // 范围外客户2的记录被剔除: totalFiles=2(客户1+客户3), totalScore=20
            assertEquals(2, ((Number) result.get("totalFiles")).intValue());
            assertEquals(20, ((Number) result.get("totalScore")).intValue());
        }
        // 数据范围解析只按 statistics 模块基线(与 customerRankings 同源), 调用一次
        verify(scopeResolver).resolveModuleUsers(any(), anyString());
    }

    @Test
    void scoreboardFiltersRowsToVisibleUsers() {
        // 可见人员=[ME] → 个人积分榜只出 ME 行, 他人(39)行不出现
        when(recordMapper.selectList(any())).thenReturn(List.of(
                rec(201L, ME, 1L), rec(202L, OTHER, 1L)));
        when(userMapper.selectById(ME)).thenReturn(new SysUser());
        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::getCurrentUser).thenReturn(me());
            sec.when(SecurityUtils::isAdmin).thenReturn(false);
            when(scopeResolver.resolveModuleUsers(any(), anyString())).thenReturn(List.of(ME));

            var rows = service.scoreboard(null);

            assertEquals(1, rows.size());
            assertEquals(ME, ((Number) rows.get(0).get("userId")).longValue());
        }
    }

    @Test
    void deptRankingInheritsUserScope() {
        when(recordMapper.selectList(any())).thenReturn(List.of(
                rec(301L, ME, 1L), rec(302L, OTHER, 1L)));
        when(userMapper.selectById(ME)).thenReturn(new SysUser());
        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::getCurrentUser).thenReturn(me());
            sec.when(SecurityUtils::isAdmin).thenReturn(false);
            when(scopeResolver.resolveModuleUsers(any(), anyString())).thenReturn(List.of(ME));

            var rows = service.deptRanking(null);

            // 部门排行派生自个人榜(范围内), 范围外人员不出现在任何部门聚合
            assertEquals(1, rows.size());
        }
    }

    @Test
    void adminAndSystemContextStayUnfiltered() {
        // admin: 全量(不触发范围解析, 故客户集桩用 lenient)
        lenient().when(customerMapper.selectList(any())).thenReturn(List.of(cust(1L, ME), cust(2L, OTHER)));
        when(recordMapper.selectList(any())).thenReturn(List.of(rec(101L, ME, 1L), rec(102L, OTHER, 2L)));
        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::getCurrentUser).thenReturn(me());
            sec.when(SecurityUtils::isAdmin).thenReturn(true);
            var result = service.categoryStats(null);
            assertEquals(2, ((Number) result.get("totalFiles")).intValue());
            verify(scopeResolver, never()).resolveModuleUsers(any(), anyString());
        }
    }
}
