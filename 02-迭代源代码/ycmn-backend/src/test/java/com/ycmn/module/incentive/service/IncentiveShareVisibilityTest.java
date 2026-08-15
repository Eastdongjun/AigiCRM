package com.ycmn.module.incentive.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisMapperBuilderAssistant;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ycmn.common.scope.OrgScopeResolver;
import com.ycmn.module.customer.mapper.BizCustomerMapper;
import com.ycmn.module.file.mapper.BizFileMapper;
import com.ycmn.module.incentive.entity.BizIncentiveRecord;
import com.ycmn.module.incentive.mapper.BizIncentiveCategoryMapper;
import com.ycmn.module.incentive.mapper.BizIncentiveRecordMapper;
import com.ycmn.module.system.mapper.SysDeptMapper;
import com.ycmn.module.system.mapper.SysUserMapper;
import com.ycmn.security.SecurityUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 激励模块分享制可见性（2026-08-14 RBAC 收敛）：
 * 可见性 = admin ∨ incentive:view-all/incentive:manage 权限码 ∨ 本人 ∨ (shared=1 AND incentive:view-shared)；
 * 「激励-分享可见」角色由 M1 迁移全员绑定，等价旧"无面板行默认可见"语义；
 * 统计三 tab（categoryStats/scoreboard）公司级全量不过滤；data_scope/上级可见不再作用。
 */
@ExtendWith(MockitoExtension.class)
class IncentiveShareVisibilityTest {

    @Mock
    OrgScopeResolver resolver;
    @Mock
    BizIncentiveRecordMapper recordMapper;

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
                mock(SysDeptMapper.class),
                resolver);
    }

    /** 注入权限码集合（等价 SecurityUtils.hasAuthority 判定；未声明的码默认 false） */
    private void stubAuthorities(MockedStatic<SecurityUtils> m, String... perms) {
        java.util.Set<String> set = java.util.Set.of(perms);
        m.when(() -> SecurityUtils.hasAuthority(anyString()))
                .thenAnswer(inv -> set.contains(inv.getArgument(0)));
    }

    private String visibleSqlOf(Long self, String... perms) {
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentUserId).thenReturn(self);
            stubAuthorities(m, perms);
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

            IncentiveService svc = newService();
            assertTrue(Boolean.TRUE.equals((Boolean) ReflectionTestUtils.invokeMethod(svc, "canSeeAllIncentive")),
                    "admin 必须 canSeeAllIncentive");

            LambdaQueryWrapper<BizIncentiveRecord> wrapper = new LambdaQueryWrapper<>();
            ReflectionTestUtils.invokeMethod(svc, "applyShareVisibility", wrapper);
            assertFalse(wrapper.getSqlSegment().contains("user_id"),
                    "admin 看全部不加 user_id 过滤，实际: " + wrapper.getSqlSegment());
        }
    }

    /** 持 incentive:manage 权限码（M1 迁移绑定 admin+销售总监）→ canSeeAll，看全部 */
    @Test
    void authorizedUserSeesAll() {
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            stubAuthorities(m, "incentive:manage");

            IncentiveService svc = newService();
            assertTrue(Boolean.TRUE.equals((Boolean) ReflectionTestUtils.invokeMethod(svc, "canSeeAllIncentive")),
                    "持 incentive:manage 必须 canSeeAllIncentive");
        }
    }

    /** 仅 incentive:view-all → canSeeAll 为真（只读监控，不含编辑/删除） */
    @Test
    void viewAllOnlySeesAllButNotEditDelete() {
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            stubAuthorities(m, "incentive:view-all");

            IncentiveService svc = newService();
            assertTrue(Boolean.TRUE.equals((Boolean) ReflectionTestUtils.invokeMethod(svc, "canSeeAllIncentive")),
                    "仅 incentive:view-all 必须 canSeeAllIncentive");
            assertFalse(Boolean.TRUE.equals((Boolean) ReflectionTestUtils.invokeMethod(svc, "canEdit")),
                    "仅看全部不隐含编辑他人");
            assertFalse(Boolean.TRUE.equals((Boolean) ReflectionTestUtils.invokeMethod(svc, "canDelete")),
                    "仅看全部不隐含删除他人");
        }
    }

    /** 普通用户（M1 全员绑定「激励-分享可见」→ 持 incentive:view-shared）→ applyShareVisibility 加 (user_id = 本人 OR shared = 1) */
    @Test
    void plainUserOnlyOwnAndShared() {
        String sql = visibleSqlOf(1L, "incentive:view-shared");
        assertTrue(sql.contains("user_id") && sql.contains("shared") && sql.contains("OR"),
                "普通用户应过滤为 本人 OR shared=1，实际: " + sql);
    }

    /** 未绑「激励-分享可见」（无 incentive:view-shared）→ applyShareVisibility 只过滤本人，不再含 shared 条件 */
    @Test
    void noSharedPermissionFiltersOnlyOwn() {
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            IncentiveService svc = newService();
            LambdaQueryWrapper<BizIncentiveRecord> wrapper = new LambdaQueryWrapper<>();
            ReflectionTestUtils.invokeMethod(svc, "applyShareVisibility", wrapper);
            String sql = wrapper.getSqlSegment();
            assertTrue(sql.contains("user_id"), "应过滤本人，实际: " + sql);
            assertFalse(sql.contains("shared"), "无查看已分享权限后不应含 shared 条件，实际: " + sql);
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
            assertTrue(Boolean.TRUE.equals((Boolean) ReflectionTestUtils.invokeMethod(svc, "canViewIncentiveUser", 999L)));
        }
    }

    /** 非授权用户查看本人 → 可见（本人恒可见） */
    @Test
    void canViewIncentiveUser_selfVisible() {
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentUserId).thenReturn(1L);

            IncentiveService svc = newService();
            assertTrue(Boolean.TRUE.equals((Boolean) ReflectionTestUtils.invokeMethod(svc, "canViewIncentiveUser", 1L)),
                    "本人必须可见");
        }
    }

    /** 普通用户（持 incentive:view-shared）查看他人：他人有 shared=1 记录 → 可见；否则不可见 */
    @Test
    void canViewIncentiveUser_otherDependsOnSharedRecord() {
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            stubAuthorities(m, "incentive:view-shared");
            when(recordMapper.selectCount(any())).thenReturn(1L, 0L);

            IncentiveService svc = newService();
            assertTrue(Boolean.TRUE.equals((Boolean) ReflectionTestUtils.invokeMethod(svc, "canViewIncentiveUser", 2L)),
                    "他人存在 shared=1 记录时应可查看");
            assertFalse(Boolean.TRUE.equals((Boolean) ReflectionTestUtils.invokeMethod(svc, "canViewIncentiveUser", 3L)),
                    "他人无 shared=1 记录时应拒绝");
        }
    }

    // ==================== getRecords：显式选人 ====================

    /** 非授权（持 view-shared）查看他人 → 仅返回该用户 shared=1 的记录 */
    @Test
    void getRecords_otherUser_onlyShared() {
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            stubAuthorities(m, "incentive:view-shared");
            when(recordMapper.selectCount(any())).thenReturn(1L); // 他人有 shared 记录
            when(recordMapper.selectList(any())).thenReturn(List.of());

            IncentiveService svc = newService();
            svc.getRecords(2L, null, null);
            @SuppressWarnings("unchecked")

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
            when(recordMapper.selectList(any())).thenReturn(List.of());

            IncentiveService svc = newService();
            svc.getRecords(1L, null, null);
            @SuppressWarnings("unchecked")

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
        @SuppressWarnings("unchecked")

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
        @SuppressWarnings("unchecked")

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
        // 【2026-08-12】分享仅本人可改：mock 当前用户=existing.userId(1L) 使 shared 写回
        try (MockedStatic<com.ycmn.security.SecurityUtils> m = mockStatic(com.ycmn.security.SecurityUtils.class)) {
            m.when(com.ycmn.security.SecurityUtils::getCurrentUserId).thenReturn(1L);
            svc.updateRecord(1L, update);
        }

        ArgumentCaptor<BizIncentiveRecord> cap = ArgumentCaptor.forClass(BizIncentiveRecord.class);
        verify(recordMapper).updateById(cap.capture());
        assertEquals(1, cap.getValue().getShared(), "本人修改分享，shared 字段应被写回为 1");
    }
}
