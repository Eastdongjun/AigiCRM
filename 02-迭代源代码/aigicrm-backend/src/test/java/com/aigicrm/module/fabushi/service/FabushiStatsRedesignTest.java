package com.aigicrm.module.fabushi.service;

import com.aigicrm.module.fabushi.entity.BizFabushiBook;
import com.aigicrm.module.fabushi.entity.BizFabushiPost;
import com.aigicrm.module.fabushi.entity.BizFabushiReadStatus;
import com.aigicrm.module.fabushi.entity.BizFabushiSection;
import com.aigicrm.module.fabushi.entity.BizFabushiUserSetting;
import com.aigicrm.module.fabushi.mapper.BizFabushiBookMapper;
import com.aigicrm.module.fabushi.mapper.BizFabushiCommentMapper;
import com.aigicrm.module.fabushi.mapper.BizFabushiLikeMapper;
import com.aigicrm.module.fabushi.mapper.BizFabushiPostMapper;
import com.aigicrm.module.fabushi.mapper.BizFabushiReadStatusMapper;
import com.aigicrm.module.fabushi.mapper.BizFabushiSectionMapper;
import com.aigicrm.module.fabushi.mapper.BizFabushiUserSettingMapper;
import com.aigicrm.module.system.entity.SysDictData;
import com.aigicrm.module.system.entity.SysUser;
import com.aigicrm.module.system.mapper.SysDeptMapper;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.module.system.service.SysDictService;
import com.aigicrm.security.SecurityUser;
import com.aigicrm.security.SecurityUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 【UI-STATS-REDESIGN-001 §四】阅读优先重构聚合矩阵钉(fixture: 多用户×多书×多日、断日/跨月 streak、
 * 达成率分母规则、feed 帖排除、可见集收口=全部档)。QC 复算口径见批规格 §二。
 */
class FabushiStatsRedesignTest {

    private final FabushiStatsService statsService = mock(FabushiStatsService.class);
    private final BizFabushiPostMapper postMapper = mock(BizFabushiPostMapper.class);
    private final BizFabushiLikeMapper likeMapper = mock(BizFabushiLikeMapper.class);
    private final BizFabushiCommentMapper commentMapper = mock(BizFabushiCommentMapper.class);
    private final BizFabushiReadStatusMapper readStatusMapper = mock(BizFabushiReadStatusMapper.class);
    private final BizFabushiBookMapper bookMapper = mock(BizFabushiBookMapper.class);
    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final SysDeptMapper deptMapper = mock(SysDeptMapper.class);
    private final SysDictService dictService = mock(SysDictService.class);
    private final BizFabushiSectionMapper sectionMapper = mock(BizFabushiSectionMapper.class);
    private final BizFabushiUserSettingMapper settingMapper = mock(BizFabushiUserSettingMapper.class);

    private final FabushiDashboardService svc = new FabushiDashboardService(
            statsService, postMapper, likeMapper, commentMapper, readStatusMapper, bookMapper,
            userMapper, deptMapper, dictService, sectionMapper, settingMapper);

    private MockedStatic<SecurityUtils> sec;

    /** 【字典红线 §四-④】fixture 字典单一源: 断言一律从此派生, 不硬编码中文 label。 */
    private static final Map<String, String> DICT = Map.of(
            "guoxue", "国学建议书目", "tuanduibidu", "团队必读书目");

    @BeforeEach
    void setUp() {
        // Lambda wrapper 缓存预热(本套件标准坑: MP 条件 SQL 懒渲染前需 TableInfo)
        com.baomidou.mybatisplus.core.MybatisConfiguration cfg = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        org.apache.ibatis.builder.MapperBuilderAssistant assistant =
                new org.apache.ibatis.builder.MapperBuilderAssistant(cfg, "");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, BizFabushiPost.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, BizFabushiReadStatus.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant,
                com.aigicrm.module.fabushi.entity.BizFabushiLike.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant,
                com.aigicrm.module.fabushi.entity.BizFabushiComment.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, BizFabushiUserSetting.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, SysUser.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant,
                com.aigicrm.module.system.entity.SysDept.class);
        sec = org.mockito.Mockito.mockStatic(SecurityUtils.class);
        SecurityUser su = new SecurityUser();
        su.setUserId(1L);
        su.setUsername("admin");
        sec.when(SecurityUtils::getCurrentUser).thenReturn(su);
        lenient().when(statsService.visibleUserIdsOrNull(any())).thenReturn(null); // 全部档
        lenient().when(dictService.listDataByCode(anyString())).thenReturn(DICT.entrySet().stream()
                .map(e -> dict(e.getKey(), e.getValue())).collect(java.util.stream.Collectors.toList()));
        lenient().when(postMapper.selectList(any())).thenReturn(List.<BizFabushiPost>of());
        lenient().when(likeMapper.selectList(any())).thenReturn(List.of());
        lenient().when(commentMapper.selectList(any())).thenReturn(List.of());
        lenient().when(readStatusMapper.selectList(any())).thenReturn(List.<BizFabushiReadStatus>of());
        lenient().when(settingMapper.selectList(any())).thenReturn(List.<BizFabushiUserSetting>of());
        lenient().when(userMapper.selectBatchIds(any())).thenReturn(List.<SysUser>of());
        lenient().when(deptMapper.selectBatchIds(any())).thenReturn(List.of());
        lenient().when(postMapper.selectCount(any())).thenReturn(0L);
        lenient().when(likeMapper.selectCount(any())).thenReturn(0L);
        lenient().when(commentMapper.selectCount(any())).thenReturn(0L);
    }

    @AfterEach
    void tearDown() {
        sec.close();
    }

    private SysDictData dict(String value, String label) {
        SysDictData d = new SysDictData();
        d.setDictValue(value);
        d.setDictLabel(label);
        return d;
    }

    private BizFabushiPost post(long id, Long sectionId, String status) {
        BizFabushiPost p = new BizFabushiPost();
        p.setId(id);
        p.setUserId(5L);
        p.setSectionId(sectionId);
        p.setStatus(status);
        return p;
    }

    private BizFabushiReadStatus read(long postId, long userId, LocalDateTime at) {
        BizFabushiReadStatus r = new BizFabushiReadStatus();
        r.setPostId(postId);
        r.setUserId(userId);
        r.setReadAt(at);
        return r;
    }

    // ===== §二-2 书目阅读 rollup: 链路/feed 排除/仅 PUBLISHED/distinct 与人次 =====

    @Test
    @SuppressWarnings("unchecked")
    void bookRollup_feedAndUnpublishedExcluded_distinctUsersCountLatest() {
        // 帖 10(published, section 100→book 1) / 11(feed 无 section) / 12(offline, section 101→book 2)
        when(postMapper.selectBatchIds(any())).thenReturn(List.of(
                post(10L, 100L, "published"), post(11L, null, "published"), post(12L, 101L, "offline")));
        when(sectionMapper.selectBatchIds(any())).thenReturn(List.of(section(100L, 1L)));
        when(bookMapper.selectBatchIds(any())).thenReturn(List.of(book(1L, "了凡四训", "tuanduibidu")));
        LocalDateTime t1 = LocalDateTime.now().minusDays(1);
        LocalDateTime t2 = LocalDateTime.now();
        when(readStatusMapper.selectList(any())).thenReturn(List.of(
                read(10L, 5L, t1), read(10L, 6L, t2), read(10L, 5L, t2),   // book1: 2 人 3 人次
                read(11L, 5L, t2),                                            // feed 帖: 排除
                read(12L, 5L, t2)));                                          // offline 帖: 排除
        SysUser u5 = user(5L);
        SysUser u6 = user(6L);
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(u5, u6));
        Map<String, Object> data = svc.dashboard("week");
        List<Map<String, Object>> books = (List<Map<String, Object>>) data.get("bookRanking");
        assertEquals(1, books.size(), "仅章节链 PUBLISHED 帖计入(feed/offline 排除)");
        Map<String, Object> row = books.get(0);
        assertEquals("了凡四训", row.get("title"));
        assertEquals(DICT.get("tuanduibidu"), row.get("categoryName"));
        assertEquals(2L, row.get("readUsers"), "distinct 用户=2");
        assertEquals(3L, row.get("readCount"), "人次=3");
        assertEquals(t2.toString(), row.get("latestReadAt"), "最新阅读=max(readAt)");
        // 成员榜行扩 readCount(规格 #3: 全量阅读口径——feed/offline 帖的阅读也计入个人人次)
        List<Map<String, Object>> ranking = (List<Map<String, Object>>) data.get("ranking");
        assertTrue(ranking.stream().anyMatch(r -> Long.valueOf(4L).equals(r.get("readCount"))),
                "榜行含已读人次(u5 全量阅读=4, 含 feed 帖)");
    }

    private SysUser user(long id) {
        SysUser u = new SysUser();
        u.setId(id);
        u.setRealName("用户" + id);
        u.setDeptId(null);
        return u;
    }

    private BizFabushiSection section(long id, long bookId) {
        BizFabushiSection s = new BizFabushiSection();
        s.setId(id);
        s.setBookId(bookId);
        return s;
    }

    private BizFabushiBook book(long id, String title, String category) {
        BizFabushiBook b = new BizFabushiBook();
        b.setId(id);
        b.setTitle(title);
        b.setCategoryCode(category);
        return b;
    }

    // ===== §二-3 类别已读口径 =====

    @Test
    @SuppressWarnings("unchecked")
    void categoryReads_byReadCountNotPosts() {
        when(postMapper.selectBatchIds(any())).thenReturn(List.of(post(10L, 100L, "published")));
        when(sectionMapper.selectBatchIds(any())).thenReturn(List.of(section(100L, 1L)));
        when(bookMapper.selectBatchIds(any())).thenReturn(List.of(book(1L, "大学", "guoxue")));
        when(readStatusMapper.selectList(any())).thenReturn(List.of(
                read(10L, 5L, LocalDateTime.now()), read(10L, 5L, LocalDateTime.now().minusDays(1))));
        Map<String, Object> data = svc.dashboard("week");
        List<Map<String, Object>> cats = (List<Map<String, Object>>) data.get("categoryReads");
        assertEquals(1, cats.size());
        assertEquals("guoxue", cats.get(0).get("categoryCode"));
        assertEquals(2L, cats.get(0).get("readCount"), "已读人次口径(同帖两次阅读计 2)");
    }

    // ===== §二-4 达成率: 分母规则(开提醒∧频率匹配)与分子 =====

    @Test
    @SuppressWarnings("unchecked")
    void dailyGoalRate_denominatorRules() {
        LocalDate today = LocalDate.now();
        LocalDate monday = today.with(java.time.DayOfWeek.MONDAY);
        long pastDays = java.time.temporal.ChronoUnit.DAYS.between(monday, today) + 1;
        // u5: daily 开提醒, 今/昨有读 → 贡献分母 pastDays, 分子 2
        // u6: daily 开提醒, 零阅读 → 分母 pastDays, 分子 0
        // u7: 提醒关闭但有读 → 不入分母
        // u8: 无设置行但有读 → 不入分母
        when(settingMapper.selectList(any())).thenReturn(List.of(
                setting(5L, 1, "daily"), setting(6L, 1, "daily"), setting(7L, 0, "daily")));
        when(readStatusMapper.selectList(any())).thenReturn(List.of(
                read(10L, 5L, today.atTime(9, 0)),
                read(10L, 5L, today.minusDays(1).atTime(9, 0)),
                read(10L, 7L, today.atTime(9, 0)),
                read(10L, 8L, today.atTime(9, 0))));
        Map<String, Object> data = svc.dashboard("week");
        double expected = Math.round(2 * 1000.0 / (2 * pastDays)) / 10.0;
        assertEquals(expected, data.get("dailyGoalRate"), "分母=开提醒人日(7/8 排除), 分子=其中实读人日");
    }

    @Test
    void dailyGoalRate_noAudience_null() {
        when(settingMapper.selectList(any())).thenReturn(List.of(setting(9L, 0, "daily")));
        assertNull(svc.dashboard("week").get("dailyGoalRate"), "无人开提醒 → null(前端显示 —)");
    }

    private BizFabushiUserSetting setting(long userId, int enabled, String freq) {
        BizFabushiUserSetting s = new BizFabushiUserSetting();
        s.setUserId(userId);
        s.setReminderEnabled(enabled);
        s.setFreqCode(freq);
        return s;
    }

    // ===== §二-5/6 personal: streak(断日/跨月/今日未读) + 最常读类别 =====

    @Test
    void streak_breakCrossMonthTodayAbsent() {
        FabushiStatsService real = new FabushiStatsService(
                null, postMapper, commentMapper, likeMapper, readStatusMapper, sectionMapper, bookMapper, dictService);
        // 断日: 有 today-1, today-3 无 today-2 → 自昨日回溯 1 天
        assertEquals(1, real.streakDays(java.util.Set.of(today(1), today(3)), LocalDate.now()));
        // 连续: today-2..today → 3
        assertEquals(3, real.streakDays(java.util.Set.of(today(0), today(1), today(2)), LocalDate.now()));
        // 跨月(固定日期): 10-30,10-31,11-01,11-02 连续 4 天
        assertEquals(4, real.streakDays(java.util.Set.of(
                LocalDate.of(2026, 10, 30), LocalDate.of(2026, 10, 31),
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 2)), LocalDate.of(2026, 11, 2)));
        // 今日未读但昨日止连 2
        assertEquals(2, real.streakDays(java.util.Set.of(
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 2)), LocalDate.of(2026, 11, 3)));
        assertEquals(0, real.streakDays(java.util.Set.of(), LocalDate.now()));
    }

    private LocalDate today(int minus) {
        return LocalDate.now().minusDays(minus);
    }

    @Test
    void personal_topCategoryAndReadDays() {
        SecurityUser me = new SecurityUser();
        me.setUserId(5L);
        me.setUsername("NO005");
        sec.when(SecurityUtils::getCurrentUser).thenReturn(me);
        when(postMapper.selectList(any())).thenReturn(List.<BizFabushiPost>of()); // myPosts ids 空
        when(postMapper.selectBatchIds(any())).thenReturn(List.of(post(10L, 100L, "published"), post(11L, null, "published")));
        when(sectionMapper.selectBatchIds(any())).thenReturn(List.of(section(100L, 1L)));
        when(bookMapper.selectBatchIds(any())).thenReturn(List.of(book(1L, "大学", "guoxue")));
        when(readStatusMapper.selectList(any())).thenReturn(List.of(
                read(10L, 5L, LocalDateTime.now()), read(10L, 5L, LocalDateTime.now().minusDays(1)),
                read(11L, 5L, LocalDateTime.now())));
        FabushiStatsService real = new FabushiStatsService(
                null, postMapper, commentMapper, likeMapper, readStatusMapper, sectionMapper, bookMapper, dictService);
        Map<String, Object> p = real.personal();
        assertEquals(2, ((Number) p.get("myReadDays")).intValue(), "累计 distinct 日期=2");
        assertEquals(DICT.get("guoxue"), p.get("myTopCategory"), "最常读类别=人次 top1(feed 帖不计)");
        assertEquals(2, p.get("myStreakDays"), "今日+昨日连续 → 2");
    }

    // ===== monthly 扩字段(CSV 任意月口径) =====

    @Test
    @SuppressWarnings("unchecked")
    void monthly_containsBookRollup() {
        when(postMapper.selectBatchIds(any())).thenReturn(List.of(post(10L, 100L, "published")));
        when(sectionMapper.selectBatchIds(any())).thenReturn(List.of(section(100L, 1L)));
        when(bookMapper.selectBatchIds(any())).thenReturn(List.of(book(1L, "大学", "guoxue")));
        when(readStatusMapper.selectList(any())).thenReturn(List.of(read(10L, 5L, LocalDateTime.now())));
        Map<String, Object> data = svc.monthly(null);
        List<Map<String, Object>> books = (List<Map<String, Object>>) data.get("bookRanking");
        assertEquals(1, books.size(), "月报含书目阅读榜(任意月 rollup, CSV 同源)");
        assertEquals(1L, ((List<Map<String, Object>>) data.get("categoryReads")).get(0).get("readCount"));
    }
}
