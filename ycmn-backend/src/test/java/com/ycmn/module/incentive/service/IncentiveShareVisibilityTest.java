package com.ycmn.module.incentive.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisMapperBuilderAssistant;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ycmn.common.scope.OrgScopeResolver;
import com.ycmn.module.customer.mapper.BizCustomerMapper;
import com.ycmn.module.customer.mapper.BizCustomerShareMapper;
import com.ycmn.module.file.mapper.BizFileMapper;
import com.ycmn.module.incentive.entity.BizIncentiveRecord;
import com.ycmn.module.incentive.mapper.BizIncentiveCategoryMapper;
import com.ycmn.module.incentive.mapper.BizIncentiveRecordMapper;
import com.ycmn.module.system.mapper.SysDeptMapper;
import com.ycmn.module.system.mapper.SysOrgMapper;
import com.ycmn.module.system.mapper.SysUserMapper;
import com.ycmn.security.SecurityUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 激励模块分享制可见性（2026-08-08 定稿）：
 * 可见性 = admin ∨ 专项授权(can_edit/can_delete) ∨ 本人 ∨ shared=1；
 * 统计三 tab（categoryStats/scoreboard）公司级全量不过滤；data_scope/上级可见不再作用。
 */
@ExtendWith(MockitoExtension.class)
class IncentiveShareVisibilityTest {

    @Mock
    OrgScopeResolver resolver;
    @Mock
    BizIncentiveRecordMapper recordMapper;
    @Mock
    BizCustomerShareMapper shareMapper;
    @Mock
    JdbcTemplate jdbc;

    /** 纯 mock 单测，手动注册 TableInfo 供 LambdaQueryWrapper 渲染 SQL 断言 */
    @BeforeAll
    static void initTableInfo() {
        MybatisMapperBuilderAssistant assistant =
                new MybatisMapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, BizIncentiveRecord.class);
    }

    private IncentiveService newService() {
        return new IncentiveService(
                mock(BizIncentiveCategoryMapper.class),
                recordMapper,
                mock(SysUserMapper.class),
                mock(BizFileMapper.class),
                mock(BizCustomerMapper.class),
                shareMapper,
                mock(SysDeptMapper.class),
                mock(SysOrgMapper.class),
                jdbc,
                resolver);
    }

    private Map<String, Object> permRow(int viewAll, int shared, int edit, int del) {
        return Map.of("can_view_all", viewAll, "can_view_shared", shared, "can_edit", edit, "can_delete", del);
    }

    /** 未授权：查看已分享默认开(基础)，无查看全部/编辑/删除 */
    private void stubJdbcAsUnauthorized() {
        when(jdbc.queryForMap(anyString(), any(Object[].class))).thenReturn(permRow(0, 1, 0, 0));
    }

    /** 仅"看全部"独立开关：可看全部(含未分享)，但不含编辑/删除他人 */
    private void stubJdbcAsViewAllOnly() {
        when(jdbc.queryForMap(anyString(), any(Object[].class))).thenReturn(permRow(1, 1, 0, 0));
    }

    /** 编辑授权（隐含看全部） */
    private void stubJdbcAsEditor() {
        when(jdbc.queryForMap(anyString(), any(Object[].class))).thenReturn(permRow(1, 1, 1, 0));
    }

    /** 面板收窄：取消"查看已分享"（连分享记录也不可见） */
    private void stubJdbcAsNoShared() {
        when(jdbc.queryForMap(anyString(), any(Object[].class))).thenReturn(permRow(0, 0, 0, 0));
    }

    private String visibleSqlOf(boolean canSeeAll, Long self) {
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentUserId).thenReturn(self);
            if (canSeeAll) stubJdbcAsEditor(); else stubJdbcAsUnauthorized();
            IncentiveService svc = newService();
            LambdaQueryWrapper<BizIncentiveRecord> wrapper = new LambdaQueryWrapper<>();
            ReflectionTestUtils.invokeMethod(svc, "applyShareVisibility", wrapper);
            return wrapper.getSqlSegment();
        }
    }

    // ==================== canSeeAllIncentive / applyShareVisibility ====================

    /** admin → canSeeAll，applyShareVisibility 不加任何过滤条件 */
    @Test
    void adminSeesAll_noFilter() {
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(true);
            m.when(SecurityUtils::getCurrentUserId).thenReturn(1L);

            IncentiveService svc = newService();
            assertTrue((Boolean) ReflectionTestUtils.invokeMethod(svc, "canSeeAllIncentive"),
                    "admin 必须 canSeeAllIncentive");

            LambdaQueryWrapper<BizIncentiveRecord> wrapper = new LambdaQueryWrapper<>();
            ReflectionTestUtils.invokeMethod(svc, "applyShareVisibility", wrapper);
            assertFalse(wrapper.getSqlSegment().contains("user_id"),
                    "admin 看全部不加 user_id 过滤，实际: " + wrapper.getSqlSegment());
        }
    }

    /** 专项授权(can_edit=1) → canSeeAll，看全部 */
    @Test
    void authorizedUserSeesAll() {
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentUserId).thenReturn(51L);
            stubJdbcAsEditor();

            IncentiveService svc = newService();
            assertTrue((Boolean) ReflectionTestUtils.invokeMethod(svc, "canSeeAllIncentive"),
                    "专项授权用户必须 canSeeAllIncentive");
        }
    }

    /** 仅"看全部"独立开关(can_view_all=1) → canSeeAll 为真（只读监控，不含编辑/删除） */
    @Test
    void viewAllOnlySeesAllButNotEditDelete() {
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentUserId).thenReturn(60L);
            stubJdbcAsViewAllOnly();

            IncentiveService svc = newService();
            assertTrue((Boolean) ReflectionTestUtils.invokeMethod(svc, "canSeeAllIncentive"),
                    "仅开看全部必须 canSeeAllIncentive");
            assertFalse((Boolean) ReflectionTestUtils.invokeMethod(svc, "canEdit", 60L),
                    "仅看全部不隐含编辑他人");
            assertFalse((Boolean) ReflectionTestUtils.invokeMethod(svc, "canDelete", 60L),
                    "仅看全部不隐含删除他人");
        }
    }

    /** 未授权普通用户 → applyShareVisibility 加 (user_id = 本人 OR shared = 1) */
    @Test
    void plainUserOnlyOwnAndShared() {
        String sql = visibleSqlOf(false, 1L);
        assertTrue(sql.contains("user_id") && sql.contains("shared") && sql.contains("OR"),
                "未授权用户应过滤为 本人 OR shared=1，实际: " + sql);
    }

    /** 面板收窄(取消"查看已分享") → applyShareVisibility 只过滤本人，不再含 shared 条件 */
    @Test
    void noSharedPermissionFiltersOnlyOwn() {
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            stubJdbcAsNoShared();
            IncentiveService svc = newService();
            LambdaQueryWrapper<BizIncentiveRecord> wrapper = new LambdaQueryWrapper<>();
            ReflectionTestUtils.invokeMethod(svc, "applyShareVisibility", wrapper);
            String sql = wrapper.getSqlSegment();
            assertTrue(sql.contains("user_id"), "应过滤本人，实际: " + sql);
            assertFalse(sql.contains("shared"), "取消查看已分享后不应含 shared 条件，实际: " + sql);
        }
    }

    // ==================== canViewIncentiveUser ====================

    /** admin → 任意 userId 可见 */
    @Test
    void canViewIncentiveUser_adminAll() {
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(true);
            m.when(SecurityUtils::getCurrentUserId).thenReturn(1L);

            IncentiveService svc = newService();
            assertTrue((Boolean) ReflectionTestUtils.invokeMethod(svc, "canViewIncentiveUser", 999L));
        }
    }

    /** 非授权用户查看本人 → 可见（本人恒可见） */
    @Test
    void canViewIncentiveUser_selfVisible() {
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            stubJdbcAsUnauthorized();

            IncentiveService svc = newService();
            assertTrue((Boolean) ReflectionTestUtils.invokeMethod(svc, "canViewIncentiveUser", 1L),
                    "本人必须可见");
        }
    }

    /** 非授权用户查看他人：他人有 shared=1 记录 → 可见；否则不可见 */
    @Test
    void canViewIncentiveUser_otherDependsOnSharedRecord() {
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            stubJdbcAsUnauthorized();
            when(recordMapper.selectCount(any())).thenReturn(1L, 0L);

            IncentiveService svc = newService();
            assertTrue((Boolean) ReflectionTestUtils.invokeMethod(svc, "canViewIncentiveUser", 2L),
                    "他人存在 shared=1 记录时应可查看");
            assertFalse((Boolean) ReflectionTestUtils.invokeMethod(svc, "canViewIncentiveUser", 3L),
                    "他人无 shared=1 记录时应拒绝");
        }
    }

    // ==================== getRecords：显式选人 ====================

    /** 非授权查看他人 → 仅返回该用户 shared=1 的记录 */
    @Test
    void getRecords_otherUser_onlyShared() {
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            stubJdbcAsUnauthorized();
            when(recordMapper.selectCount(any())).thenReturn(1L); // 他人有 shared 记录
            when(recordMapper.selectList(any())).thenReturn(List.of());

            IncentiveService svc = newService();
            svc.getRecords(2L, null, null);

            ArgumentCaptor<LambdaQueryWrapper<BizIncentiveRecord>> cap = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
            verify(recordMapper).selectList(cap.capture());
            String sql = cap.getValue().getSqlSegment();
            assertTrue(sql.contains("user_id") && sql.contains("shared"),
                    "查看他人应加 user_id 与 shared=1 过滤，实际: " + sql);
        }
    }

    /** 非授权查看本人 → 返回本人全部记录（不得加 shared 过滤） */
    @Test
    void getRecords_self_allOwn() {
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            stubJdbcAsUnauthorized();
            when(recordMapper.selectList(any())).thenReturn(List.of());

            IncentiveService svc = newService();
            svc.getRecords(1L, null, null);

            ArgumentCaptor<LambdaQueryWrapper<BizIncentiveRecord>> cap = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
            verify(recordMapper).selectList(cap.capture());
            String sql = cap.getValue().getSqlSegment();
            assertTrue(sql.contains("user_id"), "本人应过滤 user_id，实际: " + sql);
            assertFalse(sql.contains("shared"), "本人不应加 shared 过滤，实际: " + sql);
        }
    }

    // ==================== 统计三 tab：公司级全量不过滤 ====================

    /** categoryStats 不做任何可见性过滤（公司级展示） */
    @Test
    void categoryStats_companyWideNoFilter() {
        when(recordMapper.selectList(any())).thenReturn(List.of());

        IncentiveService svc = newService();
        svc.categoryStats(null);

        ArgumentCaptor<LambdaQueryWrapper<BizIncentiveRecord>> cap = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(recordMapper).selectList(cap.capture());
        String sql = cap.getValue().getSqlSegment();
        assertFalse(sql.contains("user_id") && sql.contains("shared"),
                "统计表应公司级全量，不加可见性过滤，实际: " + sql);
    }

    /** scoreboard 个人排行不做任何可见性过滤（公司级展示） */
    @Test
    void scoreboard_companyWideNoFilter() {
        when(recordMapper.selectList(any())).thenReturn(List.of());

        IncentiveService svc = newService();
        svc.scoreboard(null);

        ArgumentCaptor<LambdaQueryWrapper<BizIncentiveRecord>> cap = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(recordMapper).selectList(cap.capture());
        String sql = cap.getValue().getSqlSegment();
        assertFalse(sql.contains("user_id") && sql.contains("shared"),
                "个人排行应公司级全量，不加可见性过滤，实际: " + sql);
    }

    // ==================== updateRecord：支持分享开关字段 ====================

    /** 更新记录带 shared 字段 → 写回 shared（本人/授权可开关分享） */
    @Test
    void updateRecord_appliesSharedField() {
        BizIncentiveRecord existing = new BizIncentiveRecord();
        existing.setId(1L);
        existing.setCategoryId(1L);
        existing.setShared(0);
        existing.setUserId(1L);
        when(recordMapper.selectById(1L)).thenReturn(existing);
        com.ycmn.module.incentive.entity.BizIncentiveCategory cat =
                new com.ycmn.module.incentive.entity.BizIncentiveCategory();
        cat.setId(1L);
        cat.setScore(10);
        // 通过 newService 后注入 categoryMapper 替身（calcScore 需要 category）
        IncentiveService svc = newService();
        com.ycmn.module.incentive.mapper.BizIncentiveCategoryMapper catMapper =
                mock(com.ycmn.module.incentive.mapper.BizIncentiveCategoryMapper.class);
        when(catMapper.selectById(1L)).thenReturn(cat);
        org.springframework.test.util.ReflectionTestUtils.setField(svc, "categoryMapper", catMapper);

        BizIncentiveRecord update = new BizIncentiveRecord();
        update.setShared(1);
        svc.updateRecord(1L, update);

        ArgumentCaptor<BizIncentiveRecord> cap = ArgumentCaptor.forClass(BizIncentiveRecord.class);
        verify(recordMapper).updateById(cap.capture());
        assertTrue(cap.getValue().getShared() == 1, "shared 字段应被写回为 1");
    }
}
