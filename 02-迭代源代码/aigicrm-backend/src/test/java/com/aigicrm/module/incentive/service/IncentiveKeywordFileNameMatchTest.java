package com.aigicrm.module.incentive.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.aigicrm.module.incentive.entity.BizIncentiveRecord;
import com.aigicrm.module.incentive.mapper.BizIncentiveCategoryMapper;
import com.aigicrm.module.incentive.mapper.BizIncentiveRecordMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 【修复 2026-11-09】六路检索第⑥路(文件名)的精确匹配口径：
 * 四槽位文件列(file_pre_id 等)存 JSON 数组(如 [178,179])，旧 REGEXP 子串匹配把
 * [193]/[1119]/[1903] 误当 19 命中——生产实证文件ID=19 误报 35 条真命中 0 条。
 * 修复=JSON_OVERLAPS 精确元素匹配({0} 绑定 '[19]' 经 CAST 成 JSON 数组)。
 * 本钉断言 SQL 段含 json_overlaps、无 regexp，且绑定参数为 JSON 数组字面量。
 */
class IncentiveKeywordFileNameMatchTest {

    @BeforeAll
    static void initTableInfo() {
        // LambdaQueryWrapper.getSqlSegment() 需要实体 TableInfo（纯单测无 MyBatis 容器）
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), BizIncentiveRecord.class);
    }

    @Test
    void keywordFileNameBranchUsesJsonOverlapsExactMatch() {
        BizIncentiveRecordMapper recMapper = mock(BizIncentiveRecordMapper.class);
        BizIncentiveCategoryMapper catMapper = mock(BizIncentiveCategoryMapper.class);
        com.aigicrm.module.file.mapper.BizFileMapper fileMapper =
                mock(com.aigicrm.module.file.mapper.BizFileMapper.class);
        // 【性能 2026-11-11 检索优化】第⑥路拆独立预查询: 第1次 selectList=JSON 预查询(返回命中 id=19),
        // 第2次=主查询(五路 OR + IN(预查询命中集), 返回空便于只验 SQL)
        com.aigicrm.module.incentive.entity.BizIncentiveRecord jsonHit = new com.aigicrm.module.incentive.entity.BizIncentiveRecord();
        jsonHit.setId(19L);
        when(recMapper.selectList(any())).thenReturn(List.of(jsonHit)).thenReturn(List.of());
        // biz_file 反查: 文件名含关键字的文件 ID=19(其余 customer/category/user 路径 mock 默认空集跳过)
        com.aigicrm.module.file.entity.BizFile file = new com.aigicrm.module.file.entity.BizFile();
        file.setId(19L);
        file.setFileName("现场照片19.jpg");
        when(fileMapper.selectList(any())).thenReturn(List.of(file));

        IncentiveService svc = new IncentiveService(
                catMapper, recMapper,
                mock(com.aigicrm.module.system.mapper.SysUserMapper.class),
                fileMapper,
                mock(com.aigicrm.module.customer.mapper.BizCustomerMapper.class),
                mock(com.aigicrm.module.system.mapper.SysDeptMapper.class),
                mock(com.aigicrm.common.scope.OrgScopeResolver.class),
                mock(org.springframework.jdbc.core.JdbcTemplate.class));

        try (MockedStatic<com.aigicrm.security.SecurityUtils> m = mockStatic(com.aigicrm.security.SecurityUtils.class)) {
            m.when(com.aigicrm.security.SecurityUtils::isAdmin).thenReturn(true);
            m.when(com.aigicrm.security.SecurityUtils::getCurrentUserId).thenReturn(1L);
            svc.getCustomerRecords(null, null, null, null, null, null, null, null, null, null, "现场照片", null);
        }

        @SuppressWarnings("unchecked") // forClass 需具体类, LambdaQueryWrapper 泛型参数擦除, 必须的 unchecked
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BizIncentiveRecord>> cap =
                ArgumentCaptor.forClass((Class<com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BizIncentiveRecord>>) (Class<?>) com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class);
        verify(recMapper, org.mockito.Mockito.times(2)).selectList(cap.capture());
        String jsonSql = cap.getAllValues().get(0).getExpression().getSqlSegment().toLowerCase();
        String mainSql = cap.getAllValues().get(1).getExpression().getSqlSegment().toLowerCase();
        assertTrue(jsonSql.contains("json_overlaps"), "预查询应为 JSON_OVERLAPS 精确元素匹配, 实际: " + jsonSql);
        assertTrue(jsonSql.contains("as json)"), "{0} 绑定值须经 CAST(... AS JSON) 转数组, 实际: " + jsonSql);
        assertFalse(jsonSql.contains("regexp"), "JSON 数组列禁止 REGEXP 子串匹配(会把 [193] 误当 19), 实际: " + jsonSql);
        assertTrue(cap.getAllValues().get(0).getParamNameValuePairs().containsValue("[19]"),
                "预查询绑定参数应为 JSON 数组字面量 [19], 实际: " + cap.getAllValues().get(0).getParamNameValuePairs());
        assertTrue(mainSql.contains("in"), "主查询应合并预查询命中集(IN 分支), 实际: " + mainSql);
    }
}
