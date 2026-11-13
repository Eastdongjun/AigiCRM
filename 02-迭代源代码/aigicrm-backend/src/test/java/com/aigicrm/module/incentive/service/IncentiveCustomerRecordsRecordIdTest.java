package com.aigicrm.module.incentive.service;

import com.aigicrm.module.incentive.entity.BizIncentiveRecord;
import com.aigicrm.module.incentive.mapper.BizIncentiveCategoryMapper;
import com.aigicrm.module.incentive.mapper.BizIncentiveRecordMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 【性能 2026-10-27】customer-records 的 recordId 定位口径：
 * 小程序拨盘详情页此前全量拉取(生产 544KB/516 条)后客户端线性查找一条；
 * 修复=recordId 命中时查询收窄到该记录所属客户，只返回一组。
 * 可见性与全量同口径：种子记录也过分享制(不可见/不存在 → 空结果，不泄露客户归属)。
 */
class IncentiveCustomerRecordsRecordIdTest {

    private IncentiveService newService(BizIncentiveRecordMapper recMapper) {
        return new IncentiveService(
                mock(BizIncentiveCategoryMapper.class), recMapper,
                mock(com.aigicrm.module.system.mapper.SysUserMapper.class),
                mock(com.aigicrm.module.file.mapper.BizFileMapper.class),
                mock(com.aigicrm.module.customer.mapper.BizCustomerMapper.class),
                mock(com.aigicrm.module.system.mapper.SysDeptMapper.class),
                mock(com.aigicrm.common.scope.OrgScopeResolver.class),
                null,
                mock(org.springframework.jdbc.core.JdbcTemplate.class));
    }

    @org.junit.jupiter.api.BeforeAll
    static void initTableInfo() {
        // 【2026-11-11】lambda 列名解析需要实体 TableInfo(纯单测无 MyBatis 容器);
        // 原先蹭其他测试类的静态注册, 类名字母序本类先跑时缓存未建即炸——显式自注册消除顺序依赖
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""),
                BizIncentiveRecord.class);
    }

    private BizIncentiveRecord seed(Long id, Long customerId) {
        BizIncentiveRecord record = new BizIncentiveRecord();
        record.setId(id);
        record.setCustomerId(customerId);
        return record;
    }

    @Test
    void recordIdScopesQueryToItsCustomer() {
        BizIncentiveRecordMapper recMapper = mock(BizIncentiveRecordMapper.class);
        // 第一次 selectList=种子查询(命中 customerId=7), 第二次=主查询(返回空便于捕获 wrapper)
        when(recMapper.selectList(any())).thenReturn(List.of(seed(55L, 7L))).thenReturn(List.of());
        IncentiveService svc = newService(recMapper);

        List<?> result;
        try (MockedStatic<com.aigicrm.security.SecurityUtils> m = mockStatic(com.aigicrm.security.SecurityUtils.class)) {
            m.when(com.aigicrm.security.SecurityUtils::isAdmin).thenReturn(true);
            m.when(com.aigicrm.security.SecurityUtils::getCurrentUserId).thenReturn(1L);
            result = svc.getCustomerRecords(null, null, null, null, null, null, null, null, null, null, null, 55L);
        }

        @SuppressWarnings("unchecked") // LambdaQueryWrapper 泛型擦除，捕获必需的 unchecked（与 PeriodTest 同款）
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BizIncentiveRecord>> cap =
                ArgumentCaptor.forClass((Class<com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BizIncentiveRecord>>) (Class<?>) com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class);
        verify(recMapper, times(2)).selectList(cap.capture());
        String seedSql = cap.getAllValues().get(0).getExpression().getSqlSegment().toLowerCase();
        String mainSql = cap.getAllValues().get(1).getExpression().getSqlSegment().toLowerCase();
        assertTrue(seedSql.contains("id ="), "种子查询应按记录 id 等值定位，实际: " + seedSql);
        assertTrue(mainSql.contains("customer_id"), "主查询应收窄到种子记录所属客户，实际: " + mainSql);
        assertEquals(0, result.size(), "主查询空结果时返回空列表");
    }

    @Test
    void invisibleSeedReturnsEmptyWithoutMainQuery() {
        BizIncentiveRecordMapper recMapper = mock(BizIncentiveRecordMapper.class);
        when(recMapper.selectList(any())).thenReturn(List.of());
        IncentiveService svc = newService(recMapper);

        List<?> result;
        try (MockedStatic<com.aigicrm.security.SecurityUtils> m = mockStatic(com.aigicrm.security.SecurityUtils.class)) {
            m.when(com.aigicrm.security.SecurityUtils::isAdmin).thenReturn(true);
            m.when(com.aigicrm.security.SecurityUtils::getCurrentUserId).thenReturn(1L);
            result = svc.getCustomerRecords(null, null, null, null, null, null, null, null, null, null, null, 404L);
        }

        assertEquals(0, result.size(), "种子不可见/不存在应返回空列表");
        verify(recMapper, times(1)).selectList(any());
    }
}
