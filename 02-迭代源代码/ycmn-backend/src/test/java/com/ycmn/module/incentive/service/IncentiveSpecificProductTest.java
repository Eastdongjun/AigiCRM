package com.ycmn.module.incentive.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ycmn.module.incentive.entity.BizIncentiveRecord;
import com.ycmn.module.incentive.mapper.BizIncentiveCategoryMapper;
import com.ycmn.module.incentive.mapper.BizIncentiveRecordMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class IncentiveSpecificProductTest {

    @BeforeAll
    static void initTableInfo() {
        // LambdaQueryWrapper.getSqlSegment() 需要实体 TableInfo（纯单测无 MyBatis 容器）
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), BizIncentiveRecord.class);
    }

    private IncentiveService newService(BizIncentiveRecordMapper recMapper, BizIncentiveCategoryMapper catMapper) {
        return new IncentiveService(
                catMapper, recMapper,
                mock(com.ycmn.module.system.mapper.SysUserMapper.class),
                mock(com.ycmn.module.file.mapper.BizFileMapper.class),
                mock(com.ycmn.module.customer.mapper.BizCustomerMapper.class),
                mock(com.ycmn.module.system.mapper.SysDeptMapper.class),
                mock(com.ycmn.common.scope.OrgScopeResolver.class),
                mock(org.springframework.jdbc.core.JdbcTemplate.class));
    }

    @Test
    void updateRecordMergesSpecificProduct() {
        BizIncentiveRecordMapper recMapper = mock(BizIncentiveRecordMapper.class);
        BizIncentiveCategoryMapper catMapper = mock(BizIncentiveCategoryMapper.class);

        BizIncentiveRecord existing = new BizIncentiveRecord();
        existing.setId(10L);
        existing.setCategoryId(1L);
        existing.setFilePreId("[1]");
        existing.setRemark("old");
        when(recMapper.selectById(10L)).thenReturn(existing);
        when(catMapper.selectById(eq(1L))).thenReturn(null); // 积分 base=0，不干扰

        IncentiveService svc = newService(recMapper, catMapper);
        BizIncentiveRecord update = new BizIncentiveRecord();
        update.setSpecificProduct("XX密封圈");

        svc.updateRecord(10L, update);

        ArgumentCaptor<BizIncentiveRecord> cap = ArgumentCaptor.forClass(BizIncentiveRecord.class);
        verify(recMapper).updateById(cap.capture());
        assertEquals("XX密封圈", cap.getValue().getSpecificProduct(), "具体产品应被合并保存");
        assertEquals("[1]", cap.getValue().getFilePreId(), "其他字段不受影响");
        assertEquals("old", cap.getValue().getRemark(), "其他字段不受影响");
    }

    @Test
    void updateRecord_preservesUploader_andRecordsModifier() {
        // 编辑(追加)走 updateRecord：初始上传人 userId 保持不变，记录最后修改人 updatedBy
        BizIncentiveRecordMapper recMapper = mock(BizIncentiveRecordMapper.class);
        BizIncentiveCategoryMapper catMapper = mock(BizIncentiveCategoryMapper.class);

        BizIncentiveRecord existing = new BizIncentiveRecord();
        existing.setId(10L);
        existing.setUserId(7L);          // 初始上传人
        existing.setCategoryId(1L);
        existing.setCreatedAt(java.time.LocalDateTime.now().minusDays(3));
        existing.setFilePreId("[1]");
        when(recMapper.selectById(10L)).thenReturn(existing);
        when(catMapper.selectById(eq(1L))).thenReturn(null);

        IncentiveService svc = newService(recMapper, catMapper);
        BizIncentiveRecord update = new BizIncentiveRecord();
        update.setShared(1);

        try (MockedStatic<com.ycmn.security.SecurityUtils> m = mockStatic(com.ycmn.security.SecurityUtils.class)) {
            m.when(com.ycmn.security.SecurityUtils::getCurrentUserId).thenReturn(99L);
            svc.updateRecord(10L, update);
        }

        ArgumentCaptor<BizIncentiveRecord> cap = ArgumentCaptor.forClass(BizIncentiveRecord.class);
        verify(recMapper).updateById(cap.capture());
        assertEquals(7L, cap.getValue().getUserId(), "初始上传人必须保持不变");
        assertEquals(99L, cap.getValue().getUpdatedBy(), "最后修改人应为当前登录人");
        assertEquals(null, cap.getValue().getShared(), "非本人不可改分享，shared 保持原值");
        assertEquals(java.time.LocalDateTime.now().minusDays(3).toLocalDate(),
                cap.getValue().getCreatedAt().toLocalDate(), "上传时间不得被重置");
    }

    @Test
    void updateRecord_ownerCanChangeShared() {
        // 本人编辑自己记录：shared 可改，updatedBy=本人
        BizIncentiveRecordMapper recMapper = mock(BizIncentiveRecordMapper.class);
        BizIncentiveCategoryMapper catMapper = mock(BizIncentiveCategoryMapper.class);

        BizIncentiveRecord existing = new BizIncentiveRecord();
        existing.setId(10L);
        existing.setUserId(1L);
        existing.setCategoryId(1L);
        when(recMapper.selectById(10L)).thenReturn(existing);
        when(catMapper.selectById(eq(1L))).thenReturn(null);

        IncentiveService svc = newService(recMapper, catMapper);
        BizIncentiveRecord update = new BizIncentiveRecord();
        update.setShared(0);

        try (MockedStatic<com.ycmn.security.SecurityUtils> m = mockStatic(com.ycmn.security.SecurityUtils.class)) {
            m.when(com.ycmn.security.SecurityUtils::getCurrentUserId).thenReturn(1L);
            svc.updateRecord(10L, update);
        }

        ArgumentCaptor<BizIncentiveRecord> cap = ArgumentCaptor.forClass(BizIncentiveRecord.class);
        verify(recMapper).updateById(cap.capture());
        assertEquals(0, cap.getValue().getShared(), "本人可修改分享");
        assertEquals(1L, cap.getValue().getUpdatedBy(), "最后修改人=本人");
    }

    @Test
    void updateRecord_nonOwnerSharedIgnored_businessStillApplied() {
        // 非本人编辑他人记录：shared 保持原值，业务字段照常更新
        BizIncentiveRecordMapper recMapper = mock(BizIncentiveRecordMapper.class);
        BizIncentiveCategoryMapper catMapper = mock(BizIncentiveCategoryMapper.class);

        BizIncentiveRecord existing = new BizIncentiveRecord();
        existing.setId(10L);
        existing.setUserId(1L);
        existing.setCategoryId(1L);
        existing.setShared(1);
        when(recMapper.selectById(10L)).thenReturn(existing);
        when(catMapper.selectById(eq(1L))).thenReturn(null);

        IncentiveService svc = newService(recMapper, catMapper);
        BizIncentiveRecord update = new BizIncentiveRecord();
        update.setShared(0);
        update.setSpecificProduct("XX密封圈");

        try (MockedStatic<com.ycmn.security.SecurityUtils> m = mockStatic(com.ycmn.security.SecurityUtils.class)) {
            m.when(com.ycmn.security.SecurityUtils::getCurrentUserId).thenReturn(99L);
            svc.updateRecord(10L, update);
        }

        ArgumentCaptor<BizIncentiveRecord> cap = ArgumentCaptor.forClass(BizIncentiveRecord.class);
        verify(recMapper).updateById(cap.capture());
        assertEquals(1, cap.getValue().getShared(), "非本人编辑 shared 保持原值");
        assertEquals("XX密封圈", cap.getValue().getSpecificProduct(), "业务字段照常更新");
        assertEquals(99L, cap.getValue().getUpdatedBy(), "最后修改人=编辑者");
    }

    @Test
    void getCustomerRecordsKeywordMatchesSpecificProduct() {
        BizIncentiveRecordMapper recMapper = mock(BizIncentiveRecordMapper.class);
        BizIncentiveCategoryMapper catMapper = mock(BizIncentiveCategoryMapper.class);
        when(recMapper.selectList(any())).thenReturn(List.of());
        IncentiveService svc = newService(recMapper, catMapper);

        try (MockedStatic<com.ycmn.security.SecurityUtils> m = mockStatic(com.ycmn.security.SecurityUtils.class)) {
            m.when(com.ycmn.security.SecurityUtils::isAdmin).thenReturn(true);
            m.when(com.ycmn.security.SecurityUtils::getCurrentUserId).thenReturn(1L);

            // keyword 命中"具体产品"场景（无客户名）
            svc.getCustomerRecords(null, null, null, null, null, null, null, null, null, "XX密封圈");
        }

        @SuppressWarnings("unchecked") // forClass 需具体类, LambdaQueryWrapper 泛型参数擦除, 必须的 unchecked
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.ycmn.module.incentive.entity.BizIncentiveRecord>> cap =
                ArgumentCaptor.forClass((Class<com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.ycmn.module.incentive.entity.BizIncentiveRecord>>) (Class<?>) com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class);
        verify(recMapper).selectList(cap.capture());
        String sql = cap.getValue().getExpression().getSqlSegment().toLowerCase();
        org.junit.jupiter.api.Assertions.assertTrue(
                sql.contains("specific_product"), "关键字搜索应包含 specific_product 条件，实际: " + sql);
    }
}
