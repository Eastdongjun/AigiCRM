package com.aigicrm.module.fabushi.service;

import com.aigicrm.common.scope.OrgScopeResolver;
import com.aigicrm.module.fabushi.entity.BizFabushiComment;
import com.aigicrm.module.fabushi.entity.BizFabushiLike;
import com.aigicrm.module.fabushi.entity.BizFabushiPost;
import com.aigicrm.module.fabushi.entity.BizFabushiReadStatus;
import com.aigicrm.module.fabushi.entity.BizFabushiRecitation;
import com.aigicrm.module.fabushi.mapper.*;
import com.aigicrm.module.system.entity.SysDept;
import com.aigicrm.module.system.entity.SysUser;
import com.aigicrm.module.system.mapper.SysDeptMapper;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.module.system.service.SysDictService;
import com.aigicrm.security.SecurityUser;
import com.aigicrm.security.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 法布施月度报告/部门进度语义钉(2026-11-16 P3)。
 * 三铁律对齐 FabushiStatsServiceTest: ①生产语义(同一 (fabushi,stats_view) fail-closed 口径)
 * ②负例(空可见集=零数据/不发查询; 无部门成员归"未分配"不并入) ③口径单源(deptProgress 与
 * dashboard 共用 buildDeptProgress/buildRanking)。
 */
class FabushiMonthlyReportTest {

    @BeforeEach
    void initLambdaCache() {
        com.baomidou.mybatisplus.core.MybatisConfiguration cfg = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        org.apache.ibatis.builder.MapperBuilderAssistant assistant =
                new org.apache.ibatis.builder.MapperBuilderAssistant(cfg, "");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, BizFabushiPost.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, BizFabushiComment.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, BizFabushiLike.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, BizFabushiReadStatus.class);
        // 【P12 2026-11-23】成员榜新增「被复诵」指标，buildRanking 会建 recitation 的 Lambda wrapper
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, BizFabushiRecitation.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, SysUser.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, SysDept.class);
    }

    private final OrgScopeResolver scopeResolver = mock(OrgScopeResolver.class);
    private final BizFabushiPostMapper postMapper = mock(BizFabushiPostMapper.class);
    private final BizFabushiCommentMapper commentMapper = mock(BizFabushiCommentMapper.class);
    private final BizFabushiLikeMapper likeMapper = mock(BizFabushiLikeMapper.class);
    private final BizFabushiReadStatusMapper readStatusMapper = mock(BizFabushiReadStatusMapper.class);
    private final BizFabushiBookMapper bookMapper = mock(BizFabushiBookMapper.class);
    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final SysDeptMapper deptMapper = mock(SysDeptMapper.class);
    private final SysDictService dictService = mock(SysDictService.class);
    private final FabushiStatsService statsService = mock(FabushiStatsService.class);

    private final FabushiDashboardService svc = new FabushiDashboardService(
            statsService, postMapper, likeMapper, commentMapper, readStatusMapper,
            bookMapper, userMapper, deptMapper, dictService,
            mock(BizFabushiSectionMapper.class), mock(BizFabushiUserSettingMapper.class),
            mock(BizFabushiRecitationMapper.class));

    /** 负例(核心): 非 admin + 解析空集 = 零数据 + 不发任何统计查询(fail-closed)。 */
    @Test
    void monthly_emptyVisibleSet_returnsAllZero_failClosed() {
        when(statsService.visibleUserIdsOrNull(any(SecurityUser.class)))
                .thenReturn(Collections.emptySet());
        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::getCurrentUser).thenReturn(new SecurityUser());
            sec.when(SecurityUtils::isAdmin).thenReturn(false);

            Map<String, Object> data = svc.monthly("2026-10");

            assertEquals(0, data.get("memberCount"));
            assertEquals(0L, data.get("posts"));
            assertEquals(0L, data.get("likes"));
            assertEquals(0L, data.get("comments"));
            assertEquals(0L, data.get("readCount"));
            assertEquals(0, data.get("readUsers"));
            assertEquals(0, data.get("activeUsers"));
            assertEquals("2026-10", data.get("month"));
            assertEquals(List.of(), data.get("deptProgress"));
            assertEquals(List.of(), data.get("ranking"));
        }
        verifyNoInteractions(postMapper, likeMapper, commentMapper, readStatusMapper, userMapper, deptMapper);
    }

    /** 会话缺席(定时任务误用场景)同样零数据——宁可零也不放行全量。 */
    @Test
    void monthly_noSession_returnsAllZero_failClosed() {
        when(statsService.visibleUserIdsOrNull(any())).thenReturn(Collections.emptySet());
        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::getCurrentUser).thenReturn(null);

            Map<String, Object> data = svc.monthly(null);

            assertEquals(0, data.get("memberCount"));
            assertEquals(0L, data.get("posts"));
        }
        verifyNoInteractions(postMapper, likeMapper, commentMapper, readStatusMapper);
    }

    /** month 参数: 非法值回落当月(不抛 500); trend 按自然日补零整月。 */
    @Test
    void monthly_illegMonth_fallsBackToCurrentMonth_andTrendIsFullMonth() {
        when(statsService.visibleUserIdsOrNull(any())).thenReturn(null); // admin 全量档
        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::getCurrentUser).thenReturn(new SecurityUser());
            sec.when(SecurityUtils::isAdmin).thenReturn(true);

            when(postMapper.selectList(any())).thenReturn(List.of());
            when(likeMapper.selectList(any())).thenReturn(List.of());
            when(commentMapper.selectList(any())).thenReturn(List.of());
            when(readStatusMapper.selectList(any())).thenReturn(List.of());
            when(postMapper.selectCount(any())).thenReturn(0L);
            when(userMapper.selectList(any())).thenReturn(List.of());

            Map<String, Object> data = svc.monthly("not-a-month");

            assertEquals(java.time.YearMonth.now().toString(), data.get("month"));
            // 当月整月自然日: label 序列长度 = 当月天数
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> trend = (List<Map<String, Object>>) data.get("trend");
            assertEquals(java.time.YearMonth.now().lengthOfMonth(), trend.size());
            assertEquals(0, trend.get(0).get("count"));
        }
    }

    /** 部门分组: 两部门聚合各自成员的读/发/赞; 无部门成员归"未分配"独立行, 不并入任何部门。 */
    @Test
    void monthly_deptProgress_groupsByDept_andNoDeptBecomesOwnRow() {
        when(statsService.visibleUserIdsOrNull(any())).thenReturn(null); // admin 全量
        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::getCurrentUser).thenReturn(new SecurityUser());
            sec.when(SecurityUtils::isAdmin).thenReturn(true);

            // 3 用户: u1/u2 部门10, u3 无部门
            SysUser u1 = user(11L, 10L);
            SysUser u2 = user(12L, 10L);
            SysUser u3 = user(13L, null);
            when(userMapper.selectList(any())).thenReturn(List.of(u1, u2, u3));
            SysDept d10 = new SysDept();
            d10.setId(10L);
            d10.setDeptName("太原一部");
            when(deptMapper.selectBatchIds(anyCollection())).thenReturn(List.of(d10));

            // 【2026-11-16 审计修复】固定时间：原 LocalDateTime.now() 在 23 点后 plusHours(1) 跨日，
            // readDays 由 2 变 3（测试时间依赖，曾在夜间随机红）。
            LocalDateTime now = LocalDateTime.of(2026, 11, 10, 10, 0);
            BizFabushiPost p1 = post(11L, now);
            BizFabushiLike l3 = like(13L, now);
            BizFabushiReadStatus r11a = read(11L, now);
            BizFabushiReadStatus r11b = read(11L, now.plusHours(1)); // 同人第二读: readDays 仍 1 天
            BizFabushiReadStatus r12 = read(12L, now);
            when(postMapper.selectList(any())).thenReturn(List.of(p1));
            when(likeMapper.selectList(any())).thenReturn(List.of(l3));
            when(commentMapper.selectList(any())).thenReturn(List.of());
            when(readStatusMapper.selectList(any())).thenReturn(List.of(r11a, r11b, r12));
            when(postMapper.selectCount(any())).thenReturn(0L);

            Map<String, Object> data = svc.monthly("2026-11");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> dp = (List<Map<String, Object>>) data.get("deptProgress");
            // 太原一部: 2 人, 2 人已读(覆盖率100%), 1 发, 0 赞, 0 评论, 已读天数 2(11=1天+12=1天)
            Map<String, Object> dept10 = dp.stream()
                    .filter(r -> "太原一部".equals(r.get("deptName"))).findFirst().orElseThrow();
            assertEquals(2, dept10.get("memberCount"));
            assertEquals(2L, dept10.get("readUsers"));
            assertEquals(100.0, dept10.get("coveragePct"));
            assertEquals(1L, dept10.get("posts"));
            assertEquals(2L, dept10.get("readDays"));
            // 未分配行: 1 人, 0 读, 1 赞
            Map<String, Object> noDept = dp.stream()
                    .filter(r -> "未分配".equals(r.get("deptName"))).findFirst().orElseThrow();
            assertEquals(1L, noDept.get("memberCount"));
            assertEquals(0L, noDept.get("readUsers"));
            assertEquals(1L, noDept.get("likes"));
            // 汇总: readCount=3(人次), readUsers=2, activeUsers=3(读2∪发1∪赞1, 11/12/13)
            assertEquals(3L, data.get("readCount"));
            assertEquals(2, data.get("readUsers"));
            assertEquals(3, data.get("activeUsers"));
            // coveragePct: admin 全量档 memberCount=-1 → null
            assertNull(data.get("coveragePct"));
        }
    }

    private SysUser user(long id, Long deptId) {
        SysUser u = new SysUser();
        u.setId(id);
        u.setDeptId(deptId);
        u.setRealName("用户" + id);
        return u;
    }

    private BizFabushiPost post(long userId, LocalDateTime at) {
        BizFabushiPost p = new BizFabushiPost();
        p.setId((long) (at.hashCode() & 0xffff));
        p.setUserId(userId);
        p.setStatus("published");
        p.setCreatedAt(at);
        return p;
    }

    private BizFabushiLike like(long userId, LocalDateTime at) {
        BizFabushiLike l = new BizFabushiLike();
        l.setId((long) (at.hashCode() & 0xffff));
        l.setUserId(userId);
        l.setCreatedAt(at);
        return l;
    }

    private BizFabushiReadStatus read(long userId, LocalDateTime at) {
        BizFabushiReadStatus r = new BizFabushiReadStatus();
        r.setUserId(userId);
        r.setReadAt(at);
        return r;
    }
}
