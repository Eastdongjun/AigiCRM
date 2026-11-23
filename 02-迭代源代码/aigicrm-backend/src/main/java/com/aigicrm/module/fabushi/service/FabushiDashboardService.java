package com.aigicrm.module.fabushi.service;

import com.aigicrm.common.scope.OrgScopeResolver;
import com.aigicrm.module.fabushi.entity.BizFabushiBook;
import com.aigicrm.module.fabushi.entity.BizFabushiComment;
import com.aigicrm.module.fabushi.entity.BizFabushiLike;
import com.aigicrm.module.fabushi.entity.BizFabushiPost;
import com.aigicrm.module.fabushi.entity.BizFabushiReadStatus;
import com.aigicrm.module.fabushi.entity.BizFabushiRecitation;
import com.aigicrm.module.fabushi.entity.BizFabushiSection;
import com.aigicrm.module.fabushi.entity.BizFabushiUserSetting;
import com.aigicrm.module.fabushi.mapper.BizFabushiBookMapper;
import com.aigicrm.module.fabushi.mapper.BizFabushiCommentMapper;
import com.aigicrm.module.fabushi.mapper.BizFabushiLikeMapper;
import com.aigicrm.module.fabushi.mapper.BizFabushiPostMapper;
import com.aigicrm.module.fabushi.mapper.BizFabushiReadStatusMapper;
import com.aigicrm.module.fabushi.mapper.BizFabushiRecitationMapper;
import com.aigicrm.module.fabushi.mapper.BizFabushiSectionMapper;
import com.aigicrm.module.fabushi.mapper.BizFabushiUserSettingMapper;
import com.aigicrm.module.system.entity.SysDept;
import com.aigicrm.module.system.entity.SysDictData;
import com.aigicrm.module.system.entity.SysUser;
import com.aigicrm.module.system.mapper.SysDeptMapper;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.module.system.service.SysDictService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 法布施统计看板聚合(2026-11-16): 成员榜 / 类别热度 / 每日已读趋势。
 * 数据范围复用 FabushiStatsService 的 (fabushi, stats_view) fail-closed 口径。
 */
@Service
@RequiredArgsConstructor
public class FabushiDashboardService {
    // P0-5 batch A: duplicated string literals (Sonar java:S1192)
    private static final String LIT_MONTH = "month";
    private static final String LIT_MEMBERCOUNT = "memberCount";
    private static final String LIT_COVERAGEPCT = "coveragePct";
    private static final String LIT_RANKING = "ranking";
    private static final String LIT_DEPTPROGRESS = "deptProgress";
    private static final String LIT_TREND = "trend";
    private static final String LIT_PUBLISHED = "published";
    private static final String LIT_COUNT = "count";
    private static final String LIT_POSTS = "posts";
    private static final String LIT_LIKES = "likes";
    private static final String LIT_COMMENTS = "comments";
    private static final String LIT_READUSERS = "readUsers";
    private static final String LIT_DEPTNAME = "deptName";
    private static final String LIT_POSTCOUNT = "postCount";
    private static final String LIT_READDAYS = "readDays";
    // 【UI-STATS-REDESIGN-001 2026-11-19】阅读优先重构新增字段/口径
    private static final String LIT_CATEGORYNAME = "categoryName";
    private static final String LIT_READCOUNT = "readCount";
    private static final String LIT_ACTIVEUSERS = "activeUsers";
    private static final String LIT_BOOKRANKING = "bookRanking";
    private static final String LIT_CATEGORYREADS = "categoryReads";
    private static final String LIT_FREQ_WORKDAY = "workday";
    private static final String LIT_FREQ_WEEKEND = "weekend";


    private final FabushiStatsService statsService;
    private final BizFabushiPostMapper postMapper;
    private final BizFabushiLikeMapper likeMapper;
    private final BizFabushiCommentMapper commentMapper;
    private final BizFabushiReadStatusMapper readStatusMapper;
    private final BizFabushiBookMapper bookMapper;
    private final SysUserMapper userMapper;
    private final SysDeptMapper deptMapper;
    private final SysDictService dictService;
    private final BizFabushiSectionMapper sectionMapper;
    private final BizFabushiUserSettingMapper settingMapper;
    /** 【P12 2026-11-23】成员榜「复诵」指标（被复诵次数），只读。 */
    private final BizFabushiRecitationMapper recitationMapper;

    public Map<String, Object> dashboard(String period) {
        boolean month = LIT_MONTH.equalsIgnoreCase(period);
        LocalDate today = LocalDate.now();
        LocalDate start = month ? today.withDayOfMonth(1) : today.with(DayOfWeek.MONDAY);
        LocalDateTime startAt = start.atStartOfDay();
        Set<Long> visible = statsService.visibleUserIdsOrNull(com.aigicrm.security.SecurityUtils.getCurrentUser());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("period", month ? LIT_MONTH : "week");
        data.put("periodStart", start.toString());
        data.put(LIT_MEMBERCOUNT, visible == null ? -1 : visible.size());
        if (visible != null && visible.isEmpty()) {
            return emptyDashboard(data);
        }

        List<BizFabushiPost> periodPosts = loadPeriodPosts(visible, startAt);
        List<BizFabushiLike> periodLikes = loadPeriodLikes(visible, startAt);
        List<BizFabushiComment> periodComments = loadPeriodComments(visible, startAt);
        Set<Long> todayReaders = todayReaderIds(visible, today.atStartOfDay());

        data.put("todayReadUsers", todayReaders.size());
        data.put(LIT_COVERAGEPCT, visible == null ? null
                : Math.round(todayReaders.size() * 1000.0 / visible.size()) / 10.0);
        data.put("periodPosts", (long) periodPosts.size());
        data.put("periodLikes", (long) periodLikes.size());
        data.put("periodComments", (long) periodComments.size());

        List<BizFabushiReadStatus> periodReads = readStatusMapper.selectList(new LambdaQueryWrapper<BizFabushiReadStatus>()
                .ge(BizFabushiReadStatus::getReadAt, startAt));

        // 【UI-STATS-REDESIGN-001】阅读优先 KPI: 周期已读人数(distinct user, 可见集收口)与周期覆盖率
        List<BizFabushiReadStatus> visibleReads = periodReads.stream()
                .filter(r -> visible == null || visible.contains(r.getUserId())).toList();
        long periodReadUsers = visibleReads.stream().map(BizFabushiReadStatus::getUserId).distinct().count();
        data.put("periodReadUsers", periodReadUsers);
        data.put("periodCoveragePct", visible == null ? null
                : Math.round(periodReadUsers * 1000.0 / visible.size()) / 10.0);
        // 每日一读达成率(口径见规格 §二-4, 分母=提醒人日, 无分母=null 前端显示 —)
        data.put("dailyGoalRate", buildDailyGoalRate(visible, visibleReads, start, today));

        data.put(LIT_RANKING, buildRanking(visible, periodPosts, visibleReads, startAt));
        data.put(LIT_DEPTPROGRESS, buildDeptProgress(visible, periodPosts, periodLikes, periodComments, visibleReads));
        // 【重构】类别分布换已读人次口径(原 categoryHeat 发布口径下线); 书目阅读榜新表
        BookReadRollup rollup = buildBookReadRollup(visibleReads);
        data.put(LIT_BOOKRANKING, rollup.bookRanking());
        data.put(LIT_CATEGORYREADS, buildCategoryReads(rollup));
        data.put(LIT_TREND, buildTrend(visible, today));
        return data;
    }

    private Map<String, Object> emptyDashboard(Map<String, Object> data) {
        data.put("todayReadUsers", 0);
        data.put(LIT_COVERAGEPCT, null);
        data.put("periodPosts", 0);
        data.put("periodLikes", 0);
        data.put("periodComments", 0);
        data.put("periodReadUsers", 0L);
        data.put("periodCoveragePct", null);
        data.put("dailyGoalRate", null);
        data.put(LIT_RANKING, List.of());
        data.put(LIT_DEPTPROGRESS, List.of());
        data.put(LIT_BOOKRANKING, List.of());
        data.put(LIT_CATEGORYREADS, List.of());
        data.put(LIT_TREND, List.of());
        return data;
    }

    private List<BizFabushiPost> loadPeriodPosts(Set<Long> visible, LocalDateTime startAt) {
        LambdaQueryWrapper<BizFabushiPost> pq = new LambdaQueryWrapper<BizFabushiPost>()
                .eq(BizFabushiPost::getStatus, LIT_PUBLISHED)
                .ge(BizFabushiPost::getCreatedAt, startAt);
        if (visible != null) pq.in(BizFabushiPost::getUserId, visible);
        return postMapper.selectList(pq);
    }

    private List<BizFabushiLike> loadPeriodLikes(Set<Long> visible, LocalDateTime startAt) {
        LambdaQueryWrapper<BizFabushiLike> lq = new LambdaQueryWrapper<BizFabushiLike>()
                .ge(BizFabushiLike::getCreatedAt, startAt);
        if (visible != null) lq.in(BizFabushiLike::getUserId, visible);
        return likeMapper.selectList(lq);
    }

    private List<BizFabushiComment> loadPeriodComments(Set<Long> visible, LocalDateTime startAt) {
        LambdaQueryWrapper<BizFabushiComment> cq = new LambdaQueryWrapper<BizFabushiComment>()
                .eq(BizFabushiComment::getStatus, LIT_PUBLISHED)
                .ge(BizFabushiComment::getCreatedAt, startAt);
        if (visible != null) cq.in(BizFabushiComment::getUserId, visible);
        return commentMapper.selectList(cq);
    }

    private Set<Long> todayReaderIds(Set<Long> visible, LocalDateTime todayAt) {
        return readStatusMapper.selectList(new LambdaQueryWrapper<BizFabushiReadStatus>()
                        .ge(BizFabushiReadStatus::getReadAt, todayAt))
                .stream()
                .map(BizFabushiReadStatus::getUserId)
                .filter(uid -> visible == null || visible.contains(uid))
                .collect(Collectors.toSet());
    }

    // ==================== 【UI-STATS-REDESIGN-001】阅读优先聚合(口径=规格 §二, QC 按此复算) ====================

    /** 书目阅读 rollup 内部件: 每条已读记录解析到的 book 行(未解析=feed 帖/未发布, 已剔除)。 */
    private record ResolvedRead(Long userId, Long bookId, LocalDateTime readAt) {}

    private record BookReadRollup(List<Map<String, Object>> bookRanking, List<ResolvedRead> resolvedReads) {}

    /**
     * 书目阅读 rollup(§二-2): read→post(仅 PUBLISHED)→section(book_id)→book。
     * feed 帖(无 section 链)排除——post 只挂 bookId 未挂 section 的同样视为无章节链不计入(口径写死)。
     * 输出行: bookId/title/category/categoryName/readUsers(distinct)/readCount/max(readAt); Top10 按人次 desc。
     */
    private BookReadRollup buildBookReadRollup(List<BizFabushiReadStatus> visibleReads) {
        List<ResolvedRead> resolved = resolveReadsToBooks(visibleReads);
        Map<String, String> catNames = categoryNames();
        Map<Long, List<ResolvedRead>> byBook = resolved.stream()
                .collect(Collectors.groupingBy(ResolvedRead::bookId));
        Map<Long, BizFabushiBook> books = byBook.keySet().isEmpty() ? Collections.emptyMap()
                : bookMapper.selectBatchIds(byBook.keySet()).stream()
                .collect(Collectors.toMap(BizFabushiBook::getId, b -> b));
        List<Map<String, Object>> rows = byBook.entrySet().stream()
                .map(e -> {
                    List<ResolvedRead> rs = e.getValue();
                    BizFabushiBook b = books.get(e.getKey());
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("bookId", e.getKey());
                    row.put("title", b == null ? "已删除书目" : b.getTitle());
                    row.put("category", b == null ? null : b.getCategoryCode());
                    row.put(LIT_CATEGORYNAME, b == null ? null : catNames.get(b.getCategoryCode()));
                    row.put(LIT_READUSERS, rs.stream().map(ResolvedRead::userId).distinct().count());
                    row.put(LIT_READCOUNT, (long) rs.size());
                    row.put("latestReadAt", rs.stream().map(ResolvedRead::readAt)
                            .max(LocalDateTime::compareTo).map(Object::toString).orElse(null));
                    return row;
                })
                .sorted((a, b) -> Long.compare((Long) b.get(LIT_READCOUNT), (Long) a.get(LIT_READCOUNT)))
                .limit(10)
                .toList();
        return new BookReadRollup(rows, resolved);
    }

    /** read→book 解析(§二-2 链路): 仅 PUBLISHED 且有 sectionId 的帖; section→bookId; 书/章删失剔除。 */
    private List<ResolvedRead> resolveReadsToBooks(List<BizFabushiReadStatus> reads) {
        if (reads.isEmpty()) return List.of();
        Set<Long> postIds = reads.stream().map(BizFabushiReadStatus::getPostId).collect(Collectors.toSet());
        Map<Long, BizFabushiPost> posts = postMapper.selectBatchIds(postIds).stream()
                .filter(p -> LIT_PUBLISHED.equals(p.getStatus()) && p.getSectionId() != null)
                .collect(Collectors.toMap(BizFabushiPost::getId, p -> p));
        if (posts.isEmpty()) return List.of();
        Map<Long, Long> sectionBook = sectionMapper.selectBatchIds(posts.values().stream()
                        .map(BizFabushiPost::getSectionId).collect(Collectors.toSet())).stream()
                .filter(s -> s.getBookId() != null)
                .collect(Collectors.toMap(BizFabushiSection::getId, BizFabushiSection::getBookId));
        return reads.stream()
                .filter(r -> posts.containsKey(r.getPostId()))
                .map(r -> {
                    Long bookId = sectionBook.get(posts.get(r.getPostId()).getSectionId());
                    return bookId == null ? null : new ResolvedRead(r.getUserId(), bookId, r.getReadAt());
                })
                .filter(Objects::nonNull)
                .toList();
    }

    /** 类别已读口径(§二-3): 书目 rollup 按 category 再聚合已读人次; 行: categoryCode/categoryName/readCount。 */
    private List<Map<String, Object>> buildCategoryReads(BookReadRollup rollup) {
        Map<Long, BizFabushiBook> books = rollup.resolvedReads().isEmpty() ? Collections.emptyMap()
                : bookMapper.selectBatchIds(rollup.resolvedReads().stream()
                        .map(ResolvedRead::bookId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(BizFabushiBook::getId, b -> b));
        Map<String, String> catNames = categoryNames();
        Map<String, Long> heat = new HashMap<>();
        rollup.resolvedReads().forEach(r -> {
            BizFabushiBook b = books.get(r.bookId());
            if (b != null && b.getCategoryCode() != null) heat.merge(b.getCategoryCode(), 1L, Long::sum);
        });
        return heat.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .map(e -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("categoryCode", e.getKey());
                    row.put(LIT_CATEGORYNAME, catNames.getOrDefault(e.getKey(), e.getKey()));
                    row.put(LIT_READCOUNT, e.getValue());
                    return row;
                }).toList();
    }

    private Map<String, String> categoryNames() {
        return dictService.listDataByCode(FabushiStatsService.DICT_FABUSHI_CATEGORY).stream()
                .collect(Collectors.toMap(SysDictData::getDictValue, SysDictData::getDictLabel, (a, b) -> a));
    }

    /**
     * 每日一读达成率(§二-4): 分母=周期内已过去自然日 × 每日"提醒开启∧频率匹配当日"的用户的人日
     * (频率判定与 F4a freqMatchesToday 同一规则: daily=恒真/未知按 daily; workday=周一~五; weekend=周六日);
     * 分子=其中当日有 ≥1 条 read 的人日; 无分母(无人开提醒)返回 null(前端显示 —)。
     * 设置按请求一次性加载(不缓存——设置可变, 单例缓存会过期)。
     */
    private Object buildDailyGoalRate(Set<Long> visible, List<BizFabushiReadStatus> visibleReads,
                                      LocalDate start, LocalDate today) {
        Map<Long, String> freqByUser = settingMapper.selectList(new LambdaQueryWrapper<BizFabushiUserSetting>()
                        .eq(BizFabushiUserSetting::getReminderEnabled, 1))
                .stream()
                // Java 侧守卫(belt-and-suspenders): wrapper 条件外的二次过滤, 兼作 mock 可测缝
                .filter(s -> Integer.valueOf(1).equals(s.getReminderEnabled()))
                .filter(s -> visible == null || visible.contains(s.getUserId()))
                .collect(Collectors.toMap(BizFabushiUserSetting::getUserId,
                        s -> s.getFreqCode() == null ? "" : s.getFreqCode(), (a, b) -> a));
        if (freqByUser.isEmpty()) return null;
        Map<Long, Set<LocalDate>> readDates = visibleReads.stream().collect(Collectors.groupingBy(
                BizFabushiReadStatus::getUserId,
                Collectors.mapping(r -> r.getReadAt().toLocalDate(), Collectors.toSet())));
        long denom = 0;
        long numer = 0;
        for (Map.Entry<Long, String> e : freqByUser.entrySet()) {
            Set<LocalDate> mine = readDates.getOrDefault(e.getKey(), Set.of());
            for (LocalDate d = start; !d.isAfter(today); d = d.plusDays(1)) {
                if (!freqMatchesDay(e.getValue(), d)) continue;
                denom++;
                if (mine.contains(d)) numer++;
            }
        }
        return denom == 0 ? null : Math.round(numer * 1000.0 / denom) / 10.0;
    }

    /** F4a freqMatchesToday 服务端同构: 未知/空→daily 兜底, workday=周一~五, weekend=周六日。 */
    private boolean freqMatchesDay(String freqCode, LocalDate day) {
        if (LIT_FREQ_WORKDAY.equals(freqCode)) {
            int dow = day.getDayOfWeek().getValue();
            return dow >= 1 && dow <= 5;
        }
        if (LIT_FREQ_WEEKEND.equals(freqCode)) {
            int dow = day.getDayOfWeek().getValue();
            return dow == 6 || dow == 7;
        }
        return true;
    }

    private List<Map<String, Object>> buildTrend(Set<Long> visible, LocalDate today) {
        Map<LocalDate, Set<Long>> dailyReaders = new TreeMap<>();
        for (int i = 6; i >= 0; i--) dailyReaders.put(today.minusDays(i), new HashSet<>());
        readStatusMapper.selectList(new LambdaQueryWrapper<BizFabushiReadStatus>()
                        .ge(BizFabushiReadStatus::getReadAt, today.minusDays(6).atStartOfDay()))
                .stream()
                .filter(r -> visible == null || visible.contains(r.getUserId()))
                .forEach(r -> {
                    LocalDate d = r.getReadAt().toLocalDate();
                    if (dailyReaders.containsKey(d)) dailyReaders.get(d).add(r.getUserId());
                });
        return dailyReaders.entrySet().stream().map(e -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", e.getKey().toString());
            row.put("label", e.getKey().getMonthValue() + "-" + String.format("%02d", e.getKey().getDayOfMonth()));
            row.put(LIT_COUNT, e.getValue().size());
            return row;
        }).toList();
    }

    /**
     * 【2026-11-16 P3 月度报告】按自然月聚合: 汇总指标 + 环比上月 + 每日已读趋势
     * + 部门进度 + 活跃榜。可见集与 dashboard 同一 fail-closed 口径
     * (fabushi, stats_view); month 参数为 YYYY-MM, 缺省/非法回落当月。
     */
    private record MonthlyRecords(List<BizFabushiPost> posts, List<BizFabushiLike> likes,
                                  List<BizFabushiComment> comments, List<BizFabushiReadStatus> reads) {}

    private YearMonth parseMonth(String month) {
        LocalDate today = LocalDate.now();
        try {
            return (month == null || month.isBlank()) ? YearMonth.from(today) : YearMonth.parse(month.trim());
        } catch (DateTimeParseException e) {
            return YearMonth.from(today);
        }
    }

    private Map<String, Object> baseMonthlyData(YearMonth ym, LocalDate start, LocalDate nextMonthStart, Set<Long> visible) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(LIT_MONTH, ym.toString());
        data.put("periodStart", start.toString());
        data.put("periodEnd", nextMonthStart.minusDays(1).toString());
        data.put(LIT_MEMBERCOUNT, visible == null ? -1 : visible.size());
        return data;
    }

    private Map<String, Object> emptyMonthlyData(Map<String, Object> data) {
        data.put(LIT_POSTS, 0L);
        data.put(LIT_LIKES, 0L);
        data.put(LIT_COMMENTS, 0L);
        data.put(LIT_READCOUNT, 0L);
        data.put(LIT_READUSERS, 0);
        data.put(LIT_ACTIVEUSERS, 0);
        data.put(LIT_COVERAGEPCT, null);
        data.put("prev", Map.of());
        data.put(LIT_DEPTPROGRESS, List.of());
        data.put(LIT_RANKING, List.of());
        data.put(LIT_TREND, List.of());
        return data;
    }

    private MonthlyRecords loadMonthlyRecords(LocalDateTime startAt, LocalDateTime endAt, Set<Long> visible) {
        List<BizFabushiPost> posts = postMapper.selectList(new LambdaQueryWrapper<BizFabushiPost>()
                .eq(BizFabushiPost::getStatus, LIT_PUBLISHED)
                .ge(BizFabushiPost::getCreatedAt, startAt).lt(BizFabushiPost::getCreatedAt, endAt));
        if (visible != null) posts = posts.stream().filter(p -> visible.contains(p.getUserId())).toList();
        List<BizFabushiLike> likes = likeMapper.selectList(new LambdaQueryWrapper<BizFabushiLike>()
                .ge(BizFabushiLike::getCreatedAt, startAt).lt(BizFabushiLike::getCreatedAt, endAt));
        if (visible != null) likes = likes.stream().filter(l -> visible.contains(l.getUserId())).toList();
        List<BizFabushiComment> comments = commentMapper.selectList(new LambdaQueryWrapper<BizFabushiComment>()
                .eq(BizFabushiComment::getStatus, LIT_PUBLISHED)
                .ge(BizFabushiComment::getCreatedAt, startAt).lt(BizFabushiComment::getCreatedAt, endAt));
        if (visible != null) comments = comments.stream().filter(c -> visible.contains(c.getUserId())).toList();
        List<BizFabushiReadStatus> reads = readStatusMapper.selectList(new LambdaQueryWrapper<BizFabushiReadStatus>()
                .ge(BizFabushiReadStatus::getReadAt, startAt).lt(BizFabushiReadStatus::getReadAt, endAt));
        if (visible != null) reads = reads.stream().filter(r -> visible.contains(r.getUserId())).toList();
        return new MonthlyRecords(posts, likes, comments, reads);
    }

    public Map<String, Object> monthly(String month) {
        YearMonth ym = parseMonth(month);
        LocalDate start = ym.atDay(1);
        LocalDate nextMonthStart = ym.plusMonths(1).atDay(1);
        LocalDateTime startAt = start.atStartOfDay();
        LocalDateTime endAt = nextMonthStart.atStartOfDay();
        Set<Long> visible = statsService.visibleUserIdsOrNull(com.aigicrm.security.SecurityUtils.getCurrentUser());

        Map<String, Object> data = baseMonthlyData(ym, start, nextMonthStart, visible);
        if (visible != null && visible.isEmpty()) {
            return emptyMonthlyData(data);
        }
        MonthlyRecords records = loadMonthlyRecords(startAt, endAt, visible);
        List<BizFabushiPost> posts = records.posts();
        List<BizFabushiLike> likes = records.likes();
        List<BizFabushiComment> comments = records.comments();
        List<BizFabushiReadStatus> reads = records.reads();

        Set<Long> readUsers = reads.stream().map(BizFabushiReadStatus::getUserId).collect(Collectors.toSet());
        Set<Long> activeUsers = new HashSet<>(readUsers);
        posts.forEach(p -> activeUsers.add(p.getUserId()));
        likes.forEach(l -> activeUsers.add(l.getUserId()));
        comments.forEach(c -> activeUsers.add(c.getUserId()));
        activeUsers.remove(null);

        data.put(LIT_POSTS, (long) posts.size());
        data.put(LIT_LIKES, (long) likes.size());
        data.put(LIT_COMMENTS, (long) comments.size());
        data.put(LIT_READCOUNT, (long) reads.size());
        data.put(LIT_READUSERS, readUsers.size());
        data.put(LIT_ACTIVEUSERS, activeUsers.size());
        data.put(LIT_COVERAGEPCT, visible == null ? null
                : Math.round(readUsers.size() * 1000.0 / visible.size()) / 10.0);

        // 环比上月(同口径同可见集; 上月基数为 0 时百分比给 null, 前端显示 "—")
        Map<String, Object> prev = new LinkedHashMap<>();
        LocalDate prevStart = ym.minusMonths(1).atDay(1);
        LocalDateTime prevStartAt = prevStart.atStartOfDay();
        Long prevPosts = postMapper.selectCount(new LambdaQueryWrapper<BizFabushiPost>()
                .eq(BizFabushiPost::getStatus, LIT_PUBLISHED)
                .ge(BizFabushiPost::getCreatedAt, prevStartAt).lt(BizFabushiPost::getCreatedAt, startAt)
                .in(visible != null, BizFabushiPost::getUserId, visible == null ? List.of(-1L) : visible));
        Long prevLikes = likeMapper.selectCount(new LambdaQueryWrapper<BizFabushiLike>()
                .ge(BizFabushiLike::getCreatedAt, prevStartAt).lt(BizFabushiLike::getCreatedAt, startAt)
                .in(visible != null, BizFabushiLike::getUserId, visible == null ? List.of(-1L) : visible));
        Long prevComments = commentMapper.selectCount(new LambdaQueryWrapper<BizFabushiComment>()
                .eq(BizFabushiComment::getStatus, LIT_PUBLISHED)
                .ge(BizFabushiComment::getCreatedAt, prevStartAt).lt(BizFabushiComment::getCreatedAt, startAt)
                .in(visible != null, BizFabushiComment::getUserId, visible == null ? List.of(-1L) : visible));
        Long prevReadUsers = readStatusMapper.selectList(new LambdaQueryWrapper<BizFabushiReadStatus>()
                        .ge(BizFabushiReadStatus::getReadAt, prevStartAt).lt(BizFabushiReadStatus::getReadAt, startAt)
                        .select(BizFabushiReadStatus::getUserId))
                .stream().map(BizFabushiReadStatus::getUserId)
                .filter(uid -> visible == null || visible.contains(uid))
                .distinct().count();
        prev.put(LIT_MONTH, ym.minusMonths(1).toString());
        prev.put(LIT_POSTS, prevPosts == null ? 0L : prevPosts);
        prev.put(LIT_LIKES, prevLikes == null ? 0L : prevLikes);
        prev.put(LIT_COMMENTS, prevComments == null ? 0L : prevComments);
        prev.put(LIT_READUSERS, prevReadUsers);
        prev.put("postsPct", pct((long) posts.size(), prevPosts));
        prev.put("likesPct", pct((long) likes.size(), prevLikes));
        prev.put("commentsPct", pct((long) comments.size(), prevComments));
        prev.put("readUsersPct", pct((long) readUsers.size(), prevReadUsers));
        data.put("prev", prev);

        // 每日已读趋势(整月自然日补零)
        Map<LocalDate, Set<Long>> daily = new TreeMap<>();
        for (LocalDate d = start; d.isBefore(nextMonthStart); d = d.plusDays(1)) daily.put(d, new HashSet<>());
        reads.forEach(r -> {
            LocalDate d = r.getReadAt().toLocalDate();
            Set<Long> s = daily.get(d);
            if (s != null) s.add(r.getUserId());
        });
        List<Map<String, Object>> trend = daily.entrySet().stream().map(e -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", e.getKey().toString());
            row.put("label", String.format("%02d-%02d", e.getKey().getMonthValue(), e.getKey().getDayOfMonth()));
            row.put(LIT_COUNT, e.getValue().size());
            return row;
        }).toList();
        data.put(LIT_TREND, trend);

        data.put(LIT_DEPTPROGRESS, buildDeptProgress(visible, posts, likes, comments, reads));
        data.put(LIT_RANKING, buildRanking(visible, posts, reads, startAt));
        // 【UI-STATS-REDESIGN-001】月报 CSV 需任意月的书目/类别阅读区块 → 后端扩两字段(非零改动, 如实申报)
        BookReadRollup rollup = buildBookReadRollup(reads);
        data.put(LIT_BOOKRANKING, rollup.bookRanking());
        data.put(LIT_CATEGORYREADS, buildCategoryReads(rollup));
        return data;
    }

    /** 环比百分比: 上月基数 0 或缺失时不计(返回 null, 前端显示"—")。 */
    private Object pct(long current, Long prevBase) {
        if (prevBase == null || prevBase <= 0) return null;
        return Math.round((current - prevBase) * 1000.0 / prevBase) / 10.0;
    }

    /** 活跃榜(共用): 发布数 / 获木鱼(区间内本人帖收赞归属) / 已读天数, 按 postCount 降序 Top 10。 */
    private List<Map<String, Object>> buildRanking(Set<Long> visible, List<BizFabushiPost> periodPosts,
                                                   List<BizFabushiReadStatus> periodReads, LocalDateTime startAt) {
        Map<Long, Long> postCount = periodPosts.stream()
                .collect(Collectors.groupingBy(BizFabushiPost::getUserId, Collectors.counting()));
        // 【PROTO-AUDIT-20261123 P12】成员榜按 D1 三类型拆列（原型列：诵读/随施/再读/获木鱼/活跃度）。
        // 数据源就是本方法已加载的 periodPosts，不新增查询、不新增依赖。
        Map<Long, Map<String, Long>> typeCount = periodPosts.stream()
                .filter(p -> p.getScopeType() != null && p.getUserId() != null)
                .collect(Collectors.groupingBy(BizFabushiPost::getUserId,
                        Collectors.groupingBy(BizFabushiPost::getScopeType, Collectors.counting())));
        // 【BUGFIX 2026-11-23】「获木鱼」在 visible==null（全集团）时恒为 0：
        // 原写法 `.in(userId, visible == null ? List.of(-1L) : visible)` 把"不限范围"当成
        // "只查 userId=-1"，于是作者帖集合永远为空，整块点赞归属被跳过。
        // 现在 visible==null 时不加用户条件；同时把重复查两次的帖集合收成一次查询。
        LambdaQueryWrapper<BizFabushiPost> ownerQw = new LambdaQueryWrapper<BizFabushiPost>()
                .eq(BizFabushiPost::getStatus, LIT_PUBLISHED)
                .select(BizFabushiPost::getId, BizFabushiPost::getUserId);
        if (visible != null) {
            ownerQw.in(BizFabushiPost::getUserId, visible.isEmpty() ? List.of(-1L) : visible);
        }
        Map<Long, Long> postOwner = postMapper.selectList(ownerQw).stream()
                .collect(Collectors.toMap(BizFabushiPost::getId, BizFabushiPost::getUserId, (a, b) -> a));
        Set<Long> allPostIdsOfVisible = postOwner.keySet();
        Map<Long, Long> likesReceived = new HashMap<>();
        Map<Long, Long> recitationsReceived = new HashMap<>();
        if (!allPostIdsOfVisible.isEmpty()) {
            List<BizFabushiLike> likes = likeMapper.selectList(new LambdaQueryWrapper<BizFabushiLike>()
                    .in(BizFabushiLike::getPostId, allPostIdsOfVisible)
                    .ge(BizFabushiLike::getCreatedAt, startAt));
            likes.forEach(l -> likesReceived.merge(postOwner.get(l.getPostId()), 1L, Long::sum));
            // 【P12 2026-11-23】被复诵次数：口径与「获木鱼」并列（他人对本人在区间内帖的复诵行数），
            // 复用同一份 postOwner 映射，不额外查帖；区间过滤与获木鱼一致。
            List<BizFabushiRecitation> recs = recitationMapper.selectList(
                    new LambdaQueryWrapper<BizFabushiRecitation>()
                            .in(BizFabushiRecitation::getPostId, allPostIdsOfVisible)
                            .ge(BizFabushiRecitation::getCreatedAt, startAt));
            recs.forEach(r -> recitationsReceived.merge(postOwner.get(r.getPostId()), 1L, Long::sum));
        }
        Map<Long, Set<LocalDate>> readDays = new HashMap<>();
        Map<Long, Long> readCount = new HashMap<>();
        periodReads.stream()
                .filter(r -> visible == null || visible.contains(r.getUserId()))
                .forEach(r -> {
                    readDays.computeIfAbsent(r.getUserId(), k -> new HashSet<>()).add(r.getReadAt().toLocalDate());
                    readCount.merge(r.getUserId(), 1L, Long::sum);
                });

        Set<Long> candidateIds = new LinkedHashSet<>();
        candidateIds.addAll(postCount.keySet());
        candidateIds.addAll(likesReceived.keySet());
        candidateIds.addAll(readDays.keySet());
        candidateIds.remove(null);
        List<Map<String, Object>> ranking = new ArrayList<>();
        if (candidateIds.isEmpty()) return ranking;
        Map<Long, SysUser> users = userMapper.selectBatchIds(candidateIds).stream()
                .collect(Collectors.toMap(SysUser::getId, u -> u));
        Set<Long> deptIds = users.values().stream().map(SysUser::getDeptId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> depts = deptIds.isEmpty() ? Collections.emptyMap()
                : deptMapper.selectBatchIds(deptIds).stream()
                .collect(Collectors.toMap(SysDept::getId, SysDept::getDeptName));
        long maxPost = postCount.values().stream().mapToLong(Long::longValue).max().orElse(1);
        for (Long uid : candidateIds) {
            SysUser u = users.get(uid);
            if (u == null) continue;
            long p = postCount.getOrDefault(uid, 0L);
            Map<String, Long> byType = typeCount.getOrDefault(uid, Map.of());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userId", uid);
            row.put("userName", u.getRealName());
            row.put(LIT_DEPTNAME, depts.getOrDefault(u.getDeptId(), "—"));
            row.put(LIT_READDAYS, readDays.getOrDefault(uid, Set.of()).size());
            row.put(LIT_READCOUNT, readCount.getOrDefault(uid, 0L));
            row.put(LIT_POSTCOUNT, p);
            // 三类型拆分（D1）：前端成员榜按原型列「诵读 / 随施 / 再读」展示
            row.put("songduCount", byType.getOrDefault("songdu", 0L));
            row.put("suishiCount", byType.getOrDefault("suishi", 0L));
            row.put("zaiduCount", byType.getOrDefault("zaidu", 0L));
            row.put("likesReceived", likesReceived.getOrDefault(uid, 0L));
            row.put("recitationsReceived", recitationsReceived.getOrDefault(uid, 0L));
            row.put("activityPct", Math.round(p * 100.0 / Math.max(maxPost, 1)));
            ranking.add(row);
        }
        ranking.sort((a, b) -> Long.compare((Long) b.get(LIT_POSTCOUNT), (Long) a.get(LIT_POSTCOUNT)));
        // 【UI-STATS-PAGE-001 2026-11-19】成员榜改前端分页(默认每页 10): 服务端不再截断 Top10,
        // 返回全量有记录名单, 仅保留 500 防御性上限防极端组织拉爆载荷
        return ranking.size() > 500 ? new ArrayList<>(ranking.subList(0, 500)) : ranking;
    }

    /**
     * 部门进度(共用): 可见集成员按 sys_user.dept_id → sys_dept.dept_name 内存分组
     * (口径同 score/statistics 部门排行先例, 无 deptId 归"未分配")。
     * 每行: deptName/memberCount/readUsers/coveragePct/posts/likes/comments/readDays。
     */
    private List<Map<String, Object>> buildDeptProgress(Set<Long> visible, List<BizFabushiPost> posts,
                                                        List<BizFabushiLike> likes, List<BizFabushiComment> comments,
                                                        List<BizFabushiReadStatus> reads) {
        List<SysUser> members = loadDeptMembers(visible);
        if (members.isEmpty()) return List.of();

        Map<Long, String> depts = deptNameMap(members);
        Set<Long> rootDeptIds = rootDeptIds();
        DeptGroups groups = buildDeptGroups(members);
        DeptAggregates agg = buildDeptAggregates(posts, likes, comments, reads, visible, members);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<Long, Set<Long>> e : groups.byDept().entrySet()) {
            rows.add(deptRow(e.getKey(), e.getValue(), depts, agg.deptReaders(), agg.counts(), rootDeptIds));
        }
        if (groups.noDeptCount() > 0) {
            // 无部门成员单独一行, 不并入任何部门(口径与成员榜 deptName="—" 一致)
            rows.add(noDeptRow(members, groups.noDeptCount(), reads, agg.counts()));
        }
        sortDeptRows(rows);
        return rows;
    }

    private List<SysUser> loadDeptMembers(Set<Long> visible) {
        return (visible == null)
                ? userMapper.selectList(new LambdaQueryWrapper<SysUser>().select(SysUser::getId, SysUser::getDeptId, SysUser::getRealName))
                : userMapper.selectBatchIds(visible);
    }

    private Map<Long, String> deptNameMap(List<SysUser> members) {
        Set<Long> deptIds = members.stream().map(SysUser::getDeptId).filter(Objects::nonNull).collect(Collectors.toSet());
        return deptIds.isEmpty() ? Collections.emptyMap()
                : deptMapper.selectBatchIds(deptIds).stream()
                .collect(Collectors.toMap(SysDept::getId, SysDept::getDeptName, (a, b) -> a));
    }

    /** 【UI-STATS-PAGE-001 2026-11-19】公司级部门 id 集: 根节点(parentId 为 null 或 0, 与全仓根判定惯例一致) */
    private Set<Long> rootDeptIds() {
        return deptMapper.selectList(new LambdaQueryWrapper<SysDept>()
                        .select(SysDept::getId, SysDept::getParentId)
                        .and(w -> w.isNull(SysDept::getParentId).or().eq(SysDept::getParentId, 0L)))
                .stream().map(SysDept::getId).collect(Collectors.toSet());
    }

    private record DeptGroups(Map<Long, Set<Long>> byDept, long noDeptCount) {}

    private DeptGroups buildDeptGroups(List<SysUser> members) {
        // 部门键 = deptId; 无部门用户单独归"未分配"
        Map<Long, Set<Long>> deptMembers = new LinkedHashMap<>();
        long noDeptCount = 0;
        for (SysUser u : members) {
            if (u.getDeptId() == null) { noDeptCount++; continue; }
            deptMembers.computeIfAbsent(u.getDeptId(), k -> new HashSet<>()).add(u.getId());
        }
        return new DeptGroups(deptMembers, noDeptCount);
    }

    private record DeptAggregates(DeptCounts counts, Map<Long, Set<Long>> deptReaders) {}

    private DeptAggregates buildDeptAggregates(List<BizFabushiPost> posts, List<BizFabushiLike> likes,
                                               List<BizFabushiComment> comments, List<BizFabushiReadStatus> reads,
                                               Set<Long> visible, List<SysUser> members) {
        Map<Long, Long> postCnt = posts.stream().filter(p -> p.getUserId() != null)
                .collect(Collectors.groupingBy(BizFabushiPost::getUserId, Collectors.counting()));
        Map<Long, Long> likeCnt = likes.stream().filter(l -> l.getUserId() != null)
                .collect(Collectors.groupingBy(BizFabushiLike::getUserId, Collectors.counting()));
        Map<Long, Long> commentCnt = comments.stream().filter(c -> c.getUserId() != null)
                .collect(Collectors.groupingBy(BizFabushiComment::getUserId, Collectors.counting()));
        Map<Long, Set<LocalDate>> userReadDays = new HashMap<>();
        Map<Long, Set<Long>> deptReaders = new HashMap<>();
        reads.stream().filter(r -> r.getUserId() != null && (visible == null || visible.contains(r.getUserId())))
                .forEach(r -> userReadDays.computeIfAbsent(r.getUserId(), k -> new HashSet<>()).add(r.getReadAt().toLocalDate()));
        Map<Long, Long> userDept = members.stream().filter(u -> u.getDeptId() != null)
                .collect(Collectors.toMap(SysUser::getId, SysUser::getDeptId, (a, b) -> a));
        reads.stream().filter(r -> r.getUserId() != null)
                .forEach(r -> {
                    Long d = userDept.get(r.getUserId());
                    if (d != null) deptReaders.computeIfAbsent(d, k -> new HashSet<>()).add(r.getUserId());
                });
        return new DeptAggregates(new DeptCounts(postCnt, likeCnt, commentCnt, userReadDays), deptReaders);
    }

    private void sortDeptRows(List<Map<String, Object>> rows) {
        // 排序: 覆盖率降序 → 已读人数降序 → 成员数降序
        // (memberCount 来自 Set.size() 是 Integer、readUsers 是 long——统一经 Number 取值,
        //  【2026-11-16 实测教训】直接 (Long) 强转 Integer 在运行时 ClassCastException→500)
        rows.sort((a, b) -> {
            int c = Double.compare(((Number) b.get(LIT_COVERAGEPCT)).doubleValue(), ((Number) a.get(LIT_COVERAGEPCT)).doubleValue());
            if (c != 0) return c;
            c = Long.compare(((Number) b.get(LIT_READUSERS)).longValue(), ((Number) a.get(LIT_READUSERS)).longValue());
            if (c != 0) return c;
            return Long.compare(((Number) b.get(LIT_MEMBERCOUNT)).longValue(), ((Number) a.get(LIT_MEMBERCOUNT)).longValue());
        });
    }

    private record DeptCounts(Map<Long, Long> postCnt, Map<Long, Long> likeCnt,
                              Map<Long, Long> commentCnt, Map<Long, Set<LocalDate>> userReadDays) {}

    private Map<String, Object> deptRow(Long deptId, Set<Long> uids, Map<Long, String> depts,
                                        Map<Long, Set<Long>> deptReaders, DeptCounts c, Set<Long> rootDeptIds) {
        long readUsers = deptReaders.getOrDefault(deptId, Set.of()).size();
        long readDaysSum = uids.stream().mapToLong(uid -> c.userReadDays().getOrDefault(uid, Set.of()).size()).sum();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("deptId", deptId);
        // 【UI-STATS-PAGE-001 2026-11-19】公司级部门(根节点)标记, 前端渲染"公司直隶"章
        row.put("companyLevel", rootDeptIds.contains(deptId));
        row.put(LIT_DEPTNAME, depts.getOrDefault(deptId, "—"));
        row.put(LIT_MEMBERCOUNT, uids.size());
        row.put(LIT_READUSERS, readUsers);
        row.put(LIT_COVERAGEPCT, Math.round(readUsers * 1000.0 / uids.size()) / 10.0);
        row.put(LIT_POSTS, uids.stream().mapToLong(uid -> c.postCnt().getOrDefault(uid, 0L)).sum());
        row.put(LIT_LIKES, uids.stream().mapToLong(uid -> c.likeCnt().getOrDefault(uid, 0L)).sum());
        row.put(LIT_COMMENTS, uids.stream().mapToLong(uid -> c.commentCnt().getOrDefault(uid, 0L)).sum());
        row.put(LIT_READDAYS, readDaysSum);
        return row;
    }

    private Map<String, Object> noDeptRow(List<SysUser> members, long noDeptCount,
                                          List<BizFabushiReadStatus> reads, DeptCounts c) {
        Set<Long> noDeptIds = members.stream().filter(u -> u.getDeptId() == null).map(SysUser::getId).collect(Collectors.toSet());
        long readUsers = reads.stream().map(BizFabushiReadStatus::getUserId)
                .filter(noDeptIds::contains).distinct().count();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("deptId", null);
        row.put(LIT_DEPTNAME, "未分配");
        row.put(LIT_MEMBERCOUNT, noDeptCount);
        row.put(LIT_READUSERS, readUsers);
        row.put(LIT_COVERAGEPCT, Math.round(readUsers * 1000.0 / noDeptCount) / 10.0);
        row.put(LIT_POSTS, noDeptIds.stream().mapToLong(uid -> c.postCnt().getOrDefault(uid, 0L)).sum());
        row.put(LIT_LIKES, noDeptIds.stream().mapToLong(uid -> c.likeCnt().getOrDefault(uid, 0L)).sum());
        row.put(LIT_COMMENTS, noDeptIds.stream().mapToLong(uid -> c.commentCnt().getOrDefault(uid, 0L)).sum());
        row.put(LIT_READDAYS, noDeptIds.stream().mapToLong(uid -> c.userReadDays().getOrDefault(uid, Set.of()).size()).sum());
        return row;
    }
}
