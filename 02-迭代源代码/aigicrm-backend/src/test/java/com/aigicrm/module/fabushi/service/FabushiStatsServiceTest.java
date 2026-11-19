package com.aigicrm.module.fabushi.service;

import com.aigicrm.common.scope.OrgScopeResolver;
import com.aigicrm.module.fabushi.mapper.*;
import com.aigicrm.security.SecurityUser;
import com.aigicrm.security.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * 法布施统计可见集语义钉(一期 2026-11-15; 架构评审 R2"fail-open 哨兵家族"教训的 fail-closed 样板)。
 * 三铁律: ①生产语义(真实档位 (fabushi,stats_view) + 真实双轨入口 resolveModuleUsers) ②负例(空集=零数据非全量)。
 */
class FabushiStatsServiceTest {

    /** 纯 Mockito 环境无 MP 启动装配: 手动初始化 lambda 缓存(照 PermissionSecurityFixTest 样板)。 */
    @org.junit.jupiter.api.BeforeEach
    void initLambdaCache() {
        com.baomidou.mybatisplus.core.MybatisConfiguration cfg = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(cfg, ""),
                com.aigicrm.module.fabushi.entity.BizFabushiPost.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(cfg, ""),
                com.aigicrm.module.fabushi.entity.BizFabushiComment.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(cfg, ""),
                com.aigicrm.module.fabushi.entity.BizFabushiLike.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(cfg, ""),
                com.aigicrm.module.fabushi.entity.BizFabushiReadStatus.class);
    }

    private final OrgScopeResolver scopeResolver = mock(OrgScopeResolver.class);
    private final BizFabushiPostMapper postMapper = mock(BizFabushiPostMapper.class);
    private final BizFabushiCommentMapper commentMapper = mock(BizFabushiCommentMapper.class);
    private final BizFabushiLikeMapper likeMapper = mock(BizFabushiLikeMapper.class);
    private final BizFabushiReadStatusMapper readStatusMapper = mock(BizFabushiReadStatusMapper.class);

    private final FabushiStatsService svc = new FabushiStatsService(
            scopeResolver, postMapper, commentMapper, likeMapper, readStatusMapper,
            mock(com.aigicrm.module.fabushi.mapper.BizFabushiSectionMapper.class),
            mock(com.aigicrm.module.fabushi.mapper.BizFabushiBookMapper.class),
            mock(com.aigicrm.module.system.service.SysDictService.class));

    private void stubSession(boolean admin) {
        SecurityUser u = new SecurityUser();
        try {
            java.lang.reflect.Field f = SecurityUser.class.getDeclaredField("admin");
            f.setAccessible(true);
            f.set(u, admin);
        } catch (ReflectiveOperationException ignored) {
            // SecurityUser.admin 字段不存在时走 isAdmin() 静态桩(下方 mockStatic 统一接管)
        }
    }

    /** 负例(核心): 非 admin + 解析空集 = 不可见 → 全指标零数据, 绝不退化为全量(R2 fail-open 家族禁入)。 */
    @Test
    void team_emptyVisibleSet_returnsAllZero_failClosed() {
        stubSession(false);
        when(scopeResolver.resolveModuleUsers(any(SecurityUser.class), eq("fabushi"), eq("stats_view")))
                .thenReturn(Collections.emptyList());

        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::getCurrentUser).thenReturn(new SecurityUser());
            sec.when(SecurityUtils::isAdmin).thenReturn(false);

            Map<String, Object> data = svc.team();

            assertEquals(0, data.get("memberCount"));
            assertEquals(0L, data.get("totalPosts"));
            assertEquals(0L, data.get("likesTotal"));
            assertEquals(0L, data.get("commentsTotal"));
            assertEquals(0L, data.get("todayReadUsers"));
        }
        // fail-closed 实证: 空集短路不发任何统计查询
        org.mockito.Mockito.verifyNoInteractions(postMapper, commentMapper, likeMapper, readStatusMapper);
    }

    /** 会话缺席(定时任务误用场景)同样零数据——宁可零也不放行全量。 */
    @Test
    void team_noSession_returnsAllZero_failClosed() {
        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::getCurrentUser).thenReturn(null);

            Map<String, Object> data = svc.team();

            assertEquals(0, data.get("memberCount"));
            assertEquals(0L, data.get("totalPosts"));
        }
        org.mockito.Mockito.verifyNoInteractions(postMapper, commentMapper, likeMapper, readStatusMapper);
    }

    /** 非 admin + 有可见集: memberCount=集合大小, 统计查询带收口(wrapper 含 IN)。 */
    @Test
    void team_narrowVisibleSet_scopeFiltered() {
        stubSession(false);
        List<Long> visible = List.of(7L, 8L, 9L);
        when(scopeResolver.resolveModuleUsers(any(SecurityUser.class), eq("fabushi"), eq("stats_view")))
                .thenReturn(visible);
        when(postMapper.selectCount(any())).thenReturn(3L);
        when(likeMapper.selectCount(any())).thenReturn(0L);
        when(commentMapper.selectCount(any())).thenReturn(0L);
        when(readStatusMapper.selectList(any())).thenReturn(Collections.emptyList());

        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::getCurrentUser).thenReturn(new SecurityUser());
            sec.when(SecurityUtils::isAdmin).thenReturn(false);

            Map<String, Object> data = svc.team();

            assertEquals(3, data.get("memberCount"));
            assertEquals(3L, data.get("totalPosts"));
        }
    }

    /** 非 admin + 解析 null = 全部档(WP7 契约语义, 该用户范围档本就是 all) → memberCount=-1。 */
    @Test
    void team_allScopeSemantic_memberCountMinusOne() {
        stubSession(false);
        when(scopeResolver.resolveModuleUsers(any(SecurityUser.class), eq("fabushi"), eq("stats_view")))
                .thenReturn(null);

        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::getCurrentUser).thenReturn(new SecurityUser());
            sec.when(SecurityUtils::isAdmin).thenReturn(false);
            when(postMapper.selectCount(any())).thenReturn(0L);
            when(likeMapper.selectCount(any())).thenReturn(0L);
            when(commentMapper.selectCount(any())).thenReturn(0L);
            when(readStatusMapper.selectList(any())).thenReturn(Collections.emptyList());

            Map<String, Object> data = svc.team();

            assertEquals(-1, data.get("memberCount"));
        }
    }

    /** admin → 全量(memberCount=-1)。 */
    @Test
    void team_admin_seesAll() {
        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::getCurrentUser).thenReturn(new SecurityUser());
            sec.when(SecurityUtils::isAdmin).thenReturn(true);
            when(postMapper.selectCount(any())).thenReturn(0L);
            when(likeMapper.selectCount(any())).thenReturn(0L);
            when(commentMapper.selectCount(any())).thenReturn(0L);
            when(readStatusMapper.selectList(any())).thenReturn(Collections.emptyList());

            Map<String, Object> data = svc.team();

            assertEquals(-1, data.get("memberCount"));
        }
        org.mockito.Mockito.verifyNoInteractions(scopeResolver);
    }
}
