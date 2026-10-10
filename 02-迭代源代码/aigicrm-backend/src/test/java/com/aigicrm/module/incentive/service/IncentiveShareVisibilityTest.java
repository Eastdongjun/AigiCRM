package com.aigicrm.module.incentive.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisMapperBuilderAssistant;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.aigicrm.common.scope.OrgScopeResolver;
import com.aigicrm.module.customer.mapper.BizCustomerMapper;
import com.aigicrm.module.file.mapper.BizFileMapper;
import com.aigicrm.module.incentive.entity.BizIncentiveRecord;
import com.aigicrm.module.incentive.mapper.BizIncentiveCategoryMapper;
import com.aigicrm.module.incentive.mapper.BizIncentiveRecordMapper;
import com.aigicrm.module.system.mapper.SysDeptMapper;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.security.SecurityUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 激励模块分享制可见性（2026-08-16 授权表化）：
 * 可见性 = admin ∨ 授权表 can_view_all/can_edit/can_delete ∨ 本人 ∨ (shared=1 AND can_view_shared)；
 * can_view_shared 无授权记录时默认开（全员可看他人已分享），授权行=0 即收窄；
 * 统计三 tab（categoryStats/scoreboard）公司级全量不过滤。
 */
@ExtendWith(MockitoExtension.class)
class IncentiveShareVisibilityTest {

    @Mock
    OrgScopeResolver resolver;
    @Mock
    BizIncentiveRecordMapper recordMapper;
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
                mock(SysDeptMapper.class),
                resolver,
                jdbc);
    }

    /** 授权表 stub：无记录（默认语义） */
    private void stubNoGrantRow(Long userId) {
        when(jdbc.queryForMap(anyString(), eq(userId)))
                .thenThrow(new EmptyResultDataAccessException(1));
    }

    /** 授权表 stub：存在授权行 */
    private void stubGrantRow(Long userId, Boolean viewShared, Boolean viewAll, Boolean edit, Boolean delete) {
        Map<String, Object> row = new HashMap<>();
        if (viewShared != null) row.put("can_view_shared", viewShared ? 1 : 0);
        if (viewAll != null) row.put("can_view_all", viewAll ? 1 : 0);
        if (edit != null) row.put("can_edit", edit ? 1 : 0);
        if (delete != null) row.put("can_delete", delete ? 1 : 0);
        when(jdbc.queryForMap(anyString(), eq(userId))).thenReturn(row);
    }

    private String visibleSqlOf(Long self) {
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentUserId).thenReturn(self);
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

    /** 授权表 can_edit=1（销售总监迁移语义）→ canSeeAll，看全部 */
    @Test
    void grantEditSeesAll() {
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            stubGrantRow(1L, null, null, true, false);

            IncentiveService svc = newService();
            assertTrue(Boolean.TRUE.equals((Boolean) ReflectionTestUtils.invokeMethod(svc, "canSeeAllIncentive")),
                    "can_edit=1 必须 canSeeAllIncentive（能编辑他人隐含看全部）");
        }
    }

    /** 仅 can_view_all=1 → canSeeAll 为真（只读监控，不含编辑/删除） */
    @Test
    void viewAllOnlySeesAllButNotEditDelete() {
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            stubGrantRow(1L, null, true, false, false);

            IncentiveService svc = newService();
            assertTrue(Boolean.TRUE.equals((Boolean) ReflectionTestUtils.invokeMethod(svc, "canSeeAllIncentive")),
                    "仅 can_view_all=1 必须 canSeeAllIncentive");
            assertFalse(Boolean.TRUE.equals((Boolean) ReflectionTestUtils.invokeMethod(svc, "canEdit")),
                    "仅看全部不隐含编辑他人");
            assertFalse(Boolean.TRUE.equals((Boolean) ReflectionTestUtils.invokeMethod(svc, "canDelete")),
                    "仅看全部不隐含删除他人");
        }
    }

    /** 普通用户无授权记录（分享可见默认开）→ applyShareVisibility 加 (user_id = 本人 OR shared = 1) */
    @Test
    void plainUserDefaultOnlyOwnAndShared() {
        String sql = visibleSqlOf(1L);
        assertTrue(sql.contains("user_id") && sql.contains("shared") && sql.contains("OR"),
                "普通用户默认应过滤为 本人 OR shared=1，实际: " + sql);
    }

    /** 授权行 can_view_shared=0（被收窄）→ applyShareVisibility 只过滤本人，不再含 shared 条件 */
    @Test
    void sharedOffFiltersOnlyOwn() {
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            stubGrantRow(1L, false, null, null, null);
            IncentiveService svc = newService();
            LambdaQueryWrapper<BizIncentiveRecord> wrapper = new LambdaQueryWrapper<>();
            ReflectionTestUtils.invokeMethod(svc, "applyShareVisibility", wrapper);
            String sql = wrapper.getSqlSegment();
            assertTrue(sql.contains("user_id"), "应过滤本人，实际: " + sql);
            assertFalse(sql.contains("shared"), "被收窄后不应含 shared 条件，实际: " + sql);
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

    /** 普通用户（分享可见默认开）查看他人：他人有 shared=1 记录 → 可见；否则不可见 */
    @Test
    void canViewIncentiveUser_otherDependsOnSharedRecord() {
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            stubNoGrantRow(1L);
            when(recordMapper.selectCount(any())).thenReturn(1L, 0L);

            IncentiveService svc = newService();
            assertTrue(Boolean.TRUE.equals((Boolean) ReflectionTestUtils.invokeMethod(svc, "canViewIncentiveUser", 2L)),
                    "他人存在 shared=1 记录时应可查看");
            assertFalse(Boolean.TRUE.equals((Boolean) ReflectionTestUtils.invokeMethod(svc, "canViewIncentiveUser", 3L)),
                    "他人无 shared=1 记录时应拒绝");
        }
    }

    // ==================== getRecords：显式选人 ====================

    /** 普通用户（分享可见默认开）查看他人 → 仅返回该用户 shared=1 的记录 */
    @Test
    void getRecords_otherUser_onlyShared() {
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            stubNoGrantRow(1L);
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

    /** 已读标记用唯一键 upsert，PC 与移动端重复打开同一记录必须幂等。
     * 【PR-01 P0-04】markRead 先过可见性：此处桩出"本人+同集团"可见记录。 */
    @Test
    void markReadUsesIdempotentUpsert() {
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(7L);
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentGroupIds).thenReturn(List.of(1L));
            when(jdbc.queryForMap(anyString(), eq(99L))).thenReturn(
                    java.util.Map.of("user_id", 7L, "shared", 1, "tenant_id", 1L));

            IncentiveService svc = newService();
            svc.markRead(99L);

            ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
            verify(jdbc).update(sql.capture(), eq(7L), eq(99L));
            assertTrue(sql.getValue().contains("ON DUPLICATE KEY UPDATE"),
                    "markRead 必须可重复调用且不插入重复阅读记录，实际: " + sql.getValue());
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

    /**
     * 【C3 2026-10-08】scoreboard 缓存键必须含集团+身份两维:同 period 下
     * 同身份同集团二次调用命中缓存(不再查库);切 admin(全集团榜)或第二集团(本集团榜)
     * 都必须换键重算——否则 admin 榜与非 admin 榜/跨集团榜互相污染。
     */
    @Test
    void scoreboardCache_separatesAdminAndGroupDimensions() {
        when(recordMapper.selectList(any())).thenReturn(List.of());
        IncentiveService svc = newService();
        java.util.concurrent.atomic.AtomicBoolean admin = new java.util.concurrent.atomic.AtomicBoolean(false);
        com.aigicrm.security.SecurityUser group1User = new com.aigicrm.security.SecurityUser();
        group1User.setCurrentGroupId(1L);
        com.aigicrm.security.SecurityUser group2User = new com.aigicrm.security.SecurityUser();
        group2User.setCurrentGroupId(2L);
        java.util.concurrent.atomic.AtomicReference<com.aigicrm.security.SecurityUser> userRef =
                new java.util.concurrent.atomic.AtomicReference<>(group1User);

        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::isAdmin).thenAnswer(inv -> admin.get());
            sec.when(SecurityUtils::getCurrentUser).thenAnswer(inv -> userRef.get());

            // 同身份同集团:第二次命中 30s 缓存,不重算
            svc.scoreboard("2026-10");
            svc.scoreboard("2026-10");
            verify(recordMapper, org.mockito.Mockito.times(1)).selectList(any());

            // admin 全集团榜:不同键 → 重算
            admin.set(true);
            svc.scoreboard("2026-10");
            verify(recordMapper, org.mockito.Mockito.times(2)).selectList(any());

            // 第二集团非 admin 本集团榜:又一不同键 → 重算
            admin.set(false);
            userRef.set(group2User);
            svc.scoreboard("2026-10");
            verify(recordMapper, org.mockito.Mockito.times(3)).selectList(any());
        }
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
        when(recordMapper.selectByIdForUpdate(1L)).thenReturn(existing);
        com.aigicrm.module.incentive.entity.BizIncentiveCategory cat =
                new com.aigicrm.module.incentive.entity.BizIncentiveCategory();
        cat.setId(1L);
        cat.setScore(10);
        IncentiveService svc = newService();
        com.aigicrm.module.incentive.mapper.BizIncentiveCategoryMapper catMapper =
                mock(com.aigicrm.module.incentive.mapper.BizIncentiveCategoryMapper.class);
        when(catMapper.selectById(1L)).thenReturn(cat);
        org.springframework.test.util.ReflectionTestUtils.setField(svc, "categoryMapper", catMapper);

        BizIncentiveRecord update = new BizIncentiveRecord();
        update.setShared(1);
        try (MockedStatic<com.aigicrm.security.SecurityUtils> m = mockStatic(com.aigicrm.security.SecurityUtils.class)) {
            m.when(com.aigicrm.security.SecurityUtils::getCurrentUserId).thenReturn(1L);
            svc.updateRecord(1L, update);
        }

        ArgumentCaptor<BizIncentiveRecord> cap = ArgumentCaptor.forClass(BizIncentiveRecord.class);
        verify(recordMapper).updateById(cap.capture());
        assertEquals(1, cap.getValue().getShared(), "本人修改分享，shared 字段应被写回为 1");
    }
}
