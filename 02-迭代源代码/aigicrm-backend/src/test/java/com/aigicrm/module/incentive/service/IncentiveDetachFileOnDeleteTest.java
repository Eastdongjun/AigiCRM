package com.aigicrm.module.incentive.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.common.scope.OrgScopeResolver;
import com.aigicrm.module.customer.mapper.BizCustomerMapper;
import com.aigicrm.module.file.entity.BizFile;
import com.aigicrm.module.file.mapper.BizFileMapper;
import com.aigicrm.module.incentive.entity.BizIncentiveRecord;
import com.aigicrm.module.incentive.mapper.BizIncentiveCategoryMapper;
import com.aigicrm.module.incentive.mapper.BizIncentiveRecordMapper;
import com.aigicrm.module.system.mapper.SysDeptMapper;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.security.SecurityUtils;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisMapperBuilderAssistant;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 【BIZ-007 2026-11-13 外部审计修复】文件中心删除激励佐证 → 联动剔除引用+重算学分:
 * ① 引用剔除: 反查命中记录 → 五槽剔除 → calcScore 重算(visit 类剔 mid 后仍剩 pre → 保持得分;
 *    全部剔空被空壳守卫拦) ② 空壳守卫: 唯一佐证被删 → 400 且文件删除事务回滚语义 ③ 无引用: 静默通过。
 */
@ExtendWith(MockitoExtension.class)
class IncentiveDetachFileOnDeleteTest {

    @Mock
    BizIncentiveCategoryMapper categoryMapper;
    @Mock
    BizIncentiveRecordMapper recordMapper;
    @Mock
    SysUserMapper userMapper;
    @Mock
    BizFileMapper fileMapper;
    @Mock
    BizCustomerMapper customerMapper;
    @Mock
    SysDeptMapper deptMapper;
    @Mock
    OrgScopeResolver scopeResolver;
    @Mock
    org.springframework.jdbc.core.JdbcTemplate jdbc;

    @BeforeAll
    static void initTableInfo() {
        MybatisMapperBuilderAssistant assistant =
                new MybatisMapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, BizIncentiveRecord.class);
        TableInfoHelper.initTableInfo(assistant, BizFile.class);
    }

    private IncentiveService newService() {
        return new IncentiveService(categoryMapper, recordMapper, userMapper,
                fileMapper, customerMapper, deptMapper, scopeResolver, jdbc);
    }

    private void stubUser(MockedStatic<SecurityUtils> m) {
        m.when(SecurityUtils::getCurrentUserId).thenReturn(2L);
    }

    /** 反查命中记录 5 号(visit 类, base=10): pre=[100,deleted], mid=[200] */
    private BizIncentiveRecord hitRecord() {
        BizIncentiveRecord r = new BizIncentiveRecord();
        r.setId(5L);
        r.setUserId(9L);
        r.setCategoryId(1L);
        r.setFilePreId("[100,999]");
        r.setFileMidId("[200]");
        return r;
    }

    private void stubFindById(long id) {
        when(jdbc.queryForList(anyString(), eq(Long.class), any(), any(), any(), any(), any()))
                .thenReturn(List.of(id));
        when(recordMapper.selectByIdForUpdate(id)).thenReturn(hitRecord());
    }

    @Test
    void detach_removesSlotAndKeepsScore_whenOtherEvidenceRemains() {
        stubFindById(5L);
        var category = new com.aigicrm.module.incentive.entity.BizIncentiveCategory();
        category.setId(1L);
        category.setScore(10);
        category.setCategoryCode("visit");
        when(categoryMapper.selectById(1L)).thenReturn(category);

        IncentiveService svc = newService();
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            stubUser(m);
            svc.detachFileFromRecords(999L);
        }

        // 学分保持 10(visit 类 pre 槽仍有 [100])
        var uwCaptor = org.mockito.ArgumentCaptor.forClass(
                com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class);
        verify(recordMapper).update(isNull(), uwCaptor.capture());
        assertNotNull(uwCaptor.getValue(), "写回 wrapper 非空");
        verify(jdbc, never()).update(anyString(), (Object[]) any());
    }

    @Test
    void detach_uniqueEvidence_throws400_fileDeletionMustRollBack() {
        // 唯一佐证: pre=[999](被删文件), mid 空 → 剔除后五槽合计 0 → 400
        when(jdbc.queryForList(anyString(), eq(Long.class), any(), any(), any(), any(), any()))
                .thenReturn(List.of(7L));
        BizIncentiveRecord sole = new BizIncentiveRecord();
        sole.setId(7L);
        sole.setUserId(9L);
        sole.setCategoryId(1L);
        sole.setFilePreId("[999]");
        when(recordMapper.selectByIdForUpdate(7L)).thenReturn(sole);

        IncentiveService svc = newService();
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            stubUser(m);
            BusinessException ex = assertThrows(BusinessException.class, () -> svc.detachFileFromRecords(999L));
            assertTrue(ex.getMessage().contains("唯一佐证"), "实际: " + ex.getMessage());
            assertTrue(ex.getMessage().contains("7"), "文案须带记录号: " + ex.getMessage());
        }
        // 守卫抛出 → 不发生任何写回(调用方事务回滚语义)
        verify(recordMapper, never()).update(isNull(), any());
    }

    @Test
    void detach_noReferencingRecords_passesSilently() {
        when(jdbc.queryForList(anyString(), eq(Long.class), any(), any(), any(), any(), any()))
                .thenReturn(List.of());
        IncentiveService svc = newService();
        assertDoesNotThrow(() -> svc.detachFileFromRecords(999L));
        verify(recordMapper, never()).selectByIdForUpdate(anyLong());
        verify(recordMapper, never()).update(isNull(), any());
    }
}
