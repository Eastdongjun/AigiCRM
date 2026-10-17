package com.aigicrm.module.incentive.service;

import com.aigicrm.module.incentive.entity.BizIncentiveRecord;
import com.aigicrm.module.incentive.mapper.BizIncentiveCategoryMapper;
import com.aigicrm.module.incentive.mapper.BizIncentiveRecordMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 【P1-1/M2 2026-10-17】customer-records 的 period 过滤口径：
 * 前端激励统计页 useIncentiveStats 一直传 period，后端 /api/incentive/customer-records
 * 却不收该参数（无 period 与 period=2026H1 响应 md5 一致）。
 * 修复口径与 getRecords/categoryStats/scoreboard 一致：非空白 period 精确等值过滤，
 * 空白 period 不放行等值条件（空串当 period='' 匹配必然 0 条）。
 */
class IncentiveCustomerRecordsPeriodTest {

    private IncentiveService newService(BizIncentiveRecordMapper recMapper, BizIncentiveCategoryMapper catMapper) {
        return new IncentiveService(
                catMapper, recMapper,
                mock(com.aigicrm.module.system.mapper.SysUserMapper.class),
                mock(com.aigicrm.module.file.mapper.BizFileMapper.class),
                mock(com.aigicrm.module.customer.mapper.BizCustomerMapper.class),
                mock(com.aigicrm.module.system.mapper.SysDeptMapper.class),
                mock(com.aigicrm.common.scope.OrgScopeResolver.class),
                mock(org.springframework.jdbc.core.JdbcTemplate.class));
    }

    private String capturedSqlSegment(IncentiveService svc, String period) {
        BizIncentiveRecordMapper recMapper = mock(BizIncentiveRecordMapper.class);
        BizIncentiveCategoryMapper catMapper = mock(BizIncentiveCategoryMapper.class);
        when(recMapper.selectList(any())).thenReturn(java.util.List.of());
        IncentiveService target = (svc != null) ? svc : newService(recMapper, catMapper);
        try (MockedStatic<com.aigicrm.security.SecurityUtils> m = mockStatic(com.aigicrm.security.SecurityUtils.class)) {
            m.when(com.aigicrm.security.SecurityUtils::isAdmin).thenReturn(true);
            m.when(com.aigicrm.security.SecurityUtils::getCurrentUserId).thenReturn(1L);
            target.getCustomerRecords(null, null, null, null, null, null, period, null, null, null, null);
        }
        @SuppressWarnings("unchecked") // LambdaQueryWrapper 泛型擦除，捕获必需的 unchecked（与 IncentiveSpecificProductTest 同款）
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BizIncentiveRecord>> cap =
                ArgumentCaptor.forClass((Class<com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BizIncentiveRecord>>) (Class<?>) com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class);
        verify(recMapper).selectList(cap.capture());
        return cap.getValue().getExpression().getSqlSegment().toLowerCase();
    }

    @Test
    void nonBlankPeriodAppliesEqualityFilter() {
        String sql = capturedSqlSegment(null, "2026全年");
        assertTrue(sql.contains("period"), "非空白 period 应生成 period 等值条件，实际: " + sql);
    }

    @Test
    void blankPeriodMustNotApplyEqualityFilter() {
        // 与 categoryStats 同守卫：空串/空白不放行等值过滤（否则 period='' 必然 0 条）
        String sql = capturedSqlSegment(null, "   ");
        assertFalse(sql.contains("period"), "空白 period 不应生成 period 等值条件，实际: " + sql);
    }
}
