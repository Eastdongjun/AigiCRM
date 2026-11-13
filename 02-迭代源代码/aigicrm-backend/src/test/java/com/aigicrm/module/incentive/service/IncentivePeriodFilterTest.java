package com.aigicrm.module.incentive.service;

import com.aigicrm.common.scope.OrgScopeResolver;
import com.aigicrm.module.customer.mapper.BizCustomerMapper;
import com.aigicrm.module.file.mapper.BizFileMapper;
import com.aigicrm.module.incentive.entity.BizIncentiveCategory;
import com.aigicrm.module.incentive.entity.BizIncentiveRecord;
import com.aigicrm.module.incentive.mapper.BizIncentiveCategoryMapper;
import com.aigicrm.module.incentive.mapper.BizIncentiveRecordMapper;
import com.aigicrm.module.system.mapper.SysDeptMapper;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.security.SecurityUtils;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 激励记录列表的周期过滤口径（2026-09-12 回归）。
 *
 * <p>缺陷背景：{@code getRecords} 原来用 {@code if (period != null) eq(...)}，
 * 前端传来的空串会被当成 {@code period = ''} 精确匹配 ⇒ 一律 0 条；而同类的
 * categoryStats/scoreboard 都带 {@code !period.isBlank()} 守卫。叠加"前端默认周期
 * 2026上半年、库内数据全是 2026全年"后，激励页打开即显示"没数据"。</p>
 *
 * <p>本测试锁定两侧口径：空白周期**不得**产生 period 条件；显式周期**必须**仍然过滤。</p>
 */
class IncentivePeriodFilterTest {

    private final BizIncentiveCategoryMapper categoryMapper = mock(BizIncentiveCategoryMapper.class);    private final BizIncentiveRecordMapper recordMapper = mock(BizIncentiveRecordMapper.class);
    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final BizFileMapper fileMapper = mock(BizFileMapper.class);
    private final BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
    private final SysDeptMapper deptMapper = mock(SysDeptMapper.class);
    private final OrgScopeResolver scopeResolver = mock(OrgScopeResolver.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);

    /** MyBatis-Plus 的 lambda 条件需要实体 TableInfo 缓存；纯单测无 Spring 上下文，这里显式初始化。 */
    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(cfg, ""), BizIncentiveRecord.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(cfg, ""), BizIncentiveCategory.class);
    }

    private IncentiveService service() {
        return new IncentiveService(categoryMapper, recordMapper, userMapper,
                fileMapper, customerMapper, deptMapper, scopeResolver, null, jdbc);
    }

    @SuppressWarnings("unchecked")
    private String sqlSegmentOfLastSelect() {
        ArgumentCaptor<Wrapper<BizIncentiveRecord>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(recordMapper).selectList(captor.capture());
        return captor.getValue().getExpression().getSqlSegment();
    }

    @Test
    void blankPeriodMustNotAddPeriodCondition() {
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::isAdmin).thenReturn(true);
            when(recordMapper.selectList(any())).thenReturn(List.of());

            service().getRecords(null, "   ", null, null);

            assertFalse(sqlSegmentOfLastSelect().contains("period"),
                    "空白周期不应产生 period 条件（否则前端默认/全部会把列表过滤成 0 条）");
        }
    }

    @Test
    void nullPeriodMustNotAddPeriodCondition() {
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::isAdmin).thenReturn(true);
            when(recordMapper.selectList(any())).thenReturn(List.of());

            service().getRecords(null, null, null, null);

            assertFalse(sqlSegmentOfLastSelect().contains("period"));
        }
    }

    @Test
    void explicitPeriodStillFilters() {
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::isAdmin).thenReturn(true);
            when(recordMapper.selectList(any())).thenReturn(List.of());

            service().getRecords(null, "2026全年", null, null);

            assertTrue(sqlSegmentOfLastSelect().contains("period"),
                    "显式周期必须仍然参与过滤");
        }
    }
}
