package com.aigicrm.module.fabushi.service;

import com.aigicrm.common.scope.OrgScopeResolver;
import com.aigicrm.module.fabushi.entity.BizFabushiBook;
import com.aigicrm.module.fabushi.entity.BizFabushiComment;
import com.aigicrm.module.fabushi.entity.BizFabushiLike;
import com.aigicrm.module.fabushi.entity.BizFabushiPost;
import com.aigicrm.module.fabushi.entity.BizFabushiReadStatus;
import com.aigicrm.module.fabushi.mapper.BizFabushiBookMapper;
import com.aigicrm.module.fabushi.mapper.BizFabushiCommentMapper;
import com.aigicrm.module.fabushi.mapper.BizFabushiLikeMapper;
import com.aigicrm.module.fabushi.mapper.BizFabushiPostMapper;
import com.aigicrm.module.fabushi.mapper.BizFabushiReadStatusMapper;
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

    private final FabushiStatsService statsService;
    private final BizFabushiPostMapper postMapper;
    private final BizFabushiLikeMapper likeMapper;
    private final BizFabushiCommentMapper commentMapper;
    private final BizFabushiReadStatusMapper readStatusMapper;
    private final BizFabushiBookMapper bookMapper;
    private final SysUserMapper userMapper;
    private final SysDeptMapper deptMapper;
    private final SysDictService dictService;

    public Map<String, Object> dashboard(String period) {
        boolean month = "month".equalsIgnoreCase(period);
        LocalDate today = LocalDate.now();
        LocalDate start = month ? today.withDayOfMonth(1) : today.with(DayOfWeek.MONDAY);
        LocalDateTime startAt = start.atStartOfDay();
        Set<Long> visible = statsService.visibleUserIdsOrNull(com.aigicrm.security.SecurityUtils.getCurrentUser());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("period", month ? "month" : "week");
        data.put("periodStart", start.toString());
        data.put("memberCount", visible == null ? -1 : visible.size());
        if (visible != null && visible.isEmpty()) {
            data.put("todayReadUsers", 0);
            data.put("coveragePct", null);
            data.put("periodPosts", 0);
            data.put("periodLikes", 0);
            data.put("periodComments", 0);
            data.put("ranking", List.of());
            data.put("deptProgress", List.of());
            data.put("categoryHeat", List.of());
            data.put("trend", List.of());
            return data;
        }

        LambdaQueryWrapper<BizFabushiPost> pq = new LambdaQueryWrapper<BizFabushiPost>()
                .eq(BizFabushiPost::getStatus, "published")
                .ge(BizFabushiPost::getCreatedAt, startAt);
        if (visible != null) pq.in(BizFabushiPost::getUserId, visible);
        List<BizFabushiPost> periodPosts = postMapper.selectList(pq);

        LambdaQueryWrapper<BizFabushiLike> lq = new LambdaQueryWrapper<BizFabushiLike>()
                .ge(BizFabushiLike::getCreatedAt, startAt);
        if (visible != null) lq.in(BizFabushiLike::getUserId, visible);
        List<BizFabushiLike> periodLikes = likeMapper.selectList(lq);

        LambdaQueryWrapper<BizFabushiComment> cq = new LambdaQueryWrapper<BizFabushiComment>()
                .eq(BizFabushiComment::getStatus, "published")
                .ge(BizFabushiComment::getCreatedAt, startAt);
        if (visible != null) cq.in(BizFabushiComment::getUserId, visible);
        List<BizFabushiComment> periodComments = commentMapper.selectList(cq);

        LocalDateTime todayAt = today.atStartOfDay();
        LambdaQueryWrapper<BizFabushiReadStatus> todayRq = new LambdaQueryWrapper<BizFabushiReadStatus>()
                .ge(BizFabushiReadStatus::getReadAt, todayAt);
        List<BizFabushiReadStatus> todayReads = readStatusMapper.selectList(todayRq);
        Set<Long> todayReaders = todayReads.stream()
                .map(BizFabushiReadStatus::getUserId)
                .filter(uid -> visible == null || visible.contains(uid))
                .collect(Collectors.toSet());

        data.put("todayReadUsers", todayReaders.size());
        data.put("coveragePct", visible == null ? null
                : Math.round(todayReaders.size() * 1000.0 / visible.size()) / 10.0);
        data.put("periodPosts", (long) periodPosts.size());
        data.put("periodLikes", (long) periodLikes.size());
        data.put("periodComments", (long) periodComments.size());

        List<BizFabushiReadStatus> periodReads = readStatusMapper.selectList(new LambdaQueryWrapper<BizFabushiReadStatus>()
                .ge(BizFabushiReadStatus::getReadAt, startAt));

        // 榜单：发布数 / 获木鱼 / 已读天数（【2026-11-16 P3】提取为共用方法, 月度报告复用同一口径）
        data.put("ranking", buildRanking(visible, periodPosts, periodReads, startAt));
        // 【2026-11-16 P3】部门进度: 同一周期数据按 sys_user.dept_id → sys_dept 内存分组
        // (口径与 score/statistics 部门排行先例同构), 覆盖率分母=可见集成员数
        data.put("deptProgress", buildDeptProgress(visible, periodPosts, periodLikes, periodComments, periodReads));

        // 类别热度
        Map<String, String> catNames = dictService.listDataByCode("fabushi_category").stream()
                .collect(Collectors.toMap(SysDictData::getDictValue, SysDictData::getDictLabel, (a, b) -> a));
        Set<Long> bookIds = periodPosts.stream().map(BizFabushiPost::getBookId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> bookCategories = bookIds.isEmpty() ? Collections.emptyMap()
                : bookMapper.selectBatchIds(bookIds).stream()
                .collect(Collectors.toMap(BizFabushiBook::getId, BizFabushiBook::getCategoryCode));
        Map<String, Long> heat = new HashMap<>();
        periodPosts.forEach(p -> {
            String code = bookCategories.get(p.getBookId());
            if (code != null) heat.merge(code, 1L, Long::sum);
        });
        List<Map<String, Object>> categoryHeat = heat.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .map(e -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("categoryCode", e.getKey());
                    row.put("categoryName", catNames.getOrDefault(e.getKey(), e.getKey()));
                    row.put("count", e.getValue());
                    return row;
                }).collect(Collectors.toList());
        data.put("categoryHeat", categoryHeat);

        // 每日已读趋势（最近 7 天）
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
        List<Map<String, Object>> trend = dailyReaders.entrySet().stream().map(e -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", e.getKey().toString());
            row.put("label", e.getKey().getMonthValue() + "-" + String.format("%02d", e.getKey().getDayOfMonth()));
            row.put("count", e.getValue().size());
            return row;
        }).collect(Collectors.toList());
        data.put("trend", trend);
        return data;
    }

    /**
     * 【2026-11-16 P3 月度报告】按自然月聚合: 汇总指标 + 环比上月 + 每日已读趋势
     * + 部门进度 + 活跃榜。可见集与 dashboard 同一 fail-closed 口径
     * (fabushi, stats_view); month 参数为 YYYY-MM, 缺省/非法回落当月。
     */
    public Map<String, Object> monthly(String month) {
        LocalDate today = LocalDate.now();
        YearMonth ym;
        try {
            ym = (month == null || month.isBlank()) ? YearMonth.from(today) : YearMonth.parse(month.trim());
        } catch (DateTimeParseException e) {
            ym = YearMonth.from(today);
        }
        LocalDate start = ym.atDay(1);
        LocalDate nextMonthStart = ym.plusMonths(1).atDay(1);
        LocalDateTime startAt = start.atStartOfDay();
        LocalDateTime endAt = nextMonthStart.atStartOfDay();
        Set<Long> visible = statsService.visibleUserIdsOrNull(com.aigicrm.security.SecurityUtils.getCurrentUser());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("month", ym.toString());
        data.put("periodStart", start.toString());
        data.put("periodEnd", nextMonthStart.minusDays(1).toString());
        data.put("memberCount", visible == null ? -1 : visible.size());
        if (visible != null && visible.isEmpty()) {
            data.put("posts", 0L);
            data.put("likes", 0L);
            data.put("comments", 0L);
            data.put("readCount", 0L);
            data.put("readUsers", 0);
            data.put("activeUsers", 0);
            data.put("coveragePct", null);
            data.put("prev", Map.of());
            data.put("deptProgress", List.of());
            data.put("ranking", List.of());
            data.put("trend", List.of());
            return data;
        }

        List<BizFabushiPost> posts = postMapper.selectList(new LambdaQueryWrapper<BizFabushiPost>()
                .eq(BizFabushiPost::getStatus, "published")
                .ge(BizFabushiPost::getCreatedAt, startAt).lt(BizFabushiPost::getCreatedAt, endAt));
        if (visible != null) posts = posts.stream().filter(p -> visible.contains(p.getUserId())).collect(Collectors.toList());
        List<BizFabushiLike> likes = likeMapper.selectList(new LambdaQueryWrapper<BizFabushiLike>()
                .ge(BizFabushiLike::getCreatedAt, startAt).lt(BizFabushiLike::getCreatedAt, endAt));
        if (visible != null) likes = likes.stream().filter(l -> visible.contains(l.getUserId())).collect(Collectors.toList());
        List<BizFabushiComment> comments = commentMapper.selectList(new LambdaQueryWrapper<BizFabushiComment>()
                .eq(BizFabushiComment::getStatus, "published")
                .ge(BizFabushiComment::getCreatedAt, startAt).lt(BizFabushiComment::getCreatedAt, endAt));
        if (visible != null) comments = comments.stream().filter(c -> visible.contains(c.getUserId())).collect(Collectors.toList());
        List<BizFabushiReadStatus> reads = readStatusMapper.selectList(new LambdaQueryWrapper<BizFabushiReadStatus>()
                .ge(BizFabushiReadStatus::getReadAt, startAt).lt(BizFabushiReadStatus::getReadAt, endAt));
        if (visible != null) reads = reads.stream().filter(r -> visible.contains(r.getUserId())).collect(Collectors.toList());

        Set<Long> readUsers = reads.stream().map(BizFabushiReadStatus::getUserId).collect(Collectors.toSet());
        Set<Long> activeUsers = new HashSet<>(readUsers);
        posts.forEach(p -> activeUsers.add(p.getUserId()));
        likes.forEach(l -> activeUsers.add(l.getUserId()));
        comments.forEach(c -> activeUsers.add(c.getUserId()));
        activeUsers.remove(null);

        data.put("posts", (long) posts.size());
        data.put("likes", (long) likes.size());
        data.put("comments", (long) comments.size());
        data.put("readCount", (long) reads.size());
        data.put("readUsers", readUsers.size());
        data.put("activeUsers", activeUsers.size());
        data.put("coveragePct", visible == null ? null
                : Math.round(readUsers.size() * 1000.0 / visible.size()) / 10.0);

        // 环比上月(同口径同可见集; 上月基数为 0 时百分比给 null, 前端显示 "—")
        Map<String, Object> prev = new LinkedHashMap<>();
        LocalDate prevStart = ym.minusMonths(1).atDay(1);
        LocalDateTime prevStartAt = prevStart.atStartOfDay();
        Long prevPosts = postMapper.selectCount(new LambdaQueryWrapper<BizFabushiPost>()
                .eq(BizFabushiPost::getStatus, "published")
                .ge(BizFabushiPost::getCreatedAt, prevStartAt).lt(BizFabushiPost::getCreatedAt, startAt)
                .in(visible != null, BizFabushiPost::getUserId, visible == null ? List.of(-1L) : visible));
        Long prevLikes = likeMapper.selectCount(new LambdaQueryWrapper<BizFabushiLike>()
                .ge(BizFabushiLike::getCreatedAt, prevStartAt).lt(BizFabushiLike::getCreatedAt, startAt)
                .in(visible != null, BizFabushiLike::getUserId, visible == null ? List.of(-1L) : visible));
        Long prevComments = commentMapper.selectCount(new LambdaQueryWrapper<BizFabushiComment>()
                .eq(BizFabushiComment::getStatus, "published")
                .ge(BizFabushiComment::getCreatedAt, prevStartAt).lt(BizFabushiComment::getCreatedAt, startAt)
                .in(visible != null, BizFabushiComment::getUserId, visible == null ? List.of(-1L) : visible));
        Long prevReadUsers = readStatusMapper.selectList(new LambdaQueryWrapper<BizFabushiReadStatus>()
                        .ge(BizFabushiReadStatus::getReadAt, prevStartAt).lt(BizFabushiReadStatus::getReadAt, startAt)
                        .select(BizFabushiReadStatus::getUserId))
                .stream().map(BizFabushiReadStatus::getUserId)
                .filter(uid -> visible == null || visible.contains(uid))
                .distinct().count();
        prev.put("month", ym.minusMonths(1).toString());
        prev.put("posts", prevPosts == null ? 0L : prevPosts);
        prev.put("likes", prevLikes == null ? 0L : prevLikes);
        prev.put("comments", prevComments == null ? 0L : prevComments);
        prev.put("readUsers", prevReadUsers);
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
            row.put("count", e.getValue().size());
            return row;
        }).collect(Collectors.toList());
        data.put("trend", trend);

        data.put("deptProgress", buildDeptProgress(visible, posts, likes, comments, reads));
        data.put("ranking", buildRanking(visible, posts, reads, startAt));
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
        Set<Long> allPostIdsOfVisible = postMapper.selectList(new LambdaQueryWrapper<BizFabushiPost>()
                        .eq(BizFabushiPost::getStatus, "published")
                        .in(BizFabushiPost::getUserId, visible == null ? List.of(-1L) : visible)
                        .select(BizFabushiPost::getId, BizFabushiPost::getUserId))
                .stream().map(BizFabushiPost::getId).collect(Collectors.toSet());
        Map<Long, Long> likesReceived = new HashMap<>();
        if (!allPostIdsOfVisible.isEmpty()) {
            List<BizFabushiLike> likes = likeMapper.selectList(new LambdaQueryWrapper<BizFabushiLike>()
                    .in(BizFabushiLike::getPostId, allPostIdsOfVisible)
                    .ge(BizFabushiLike::getCreatedAt, startAt));
            Map<Long, Long> postOwner = postMapper.selectList(new LambdaQueryWrapper<BizFabushiPost>()
                            .in(BizFabushiPost::getId, allPostIdsOfVisible)
                            .select(BizFabushiPost::getId, BizFabushiPost::getUserId))
                    .stream().collect(Collectors.toMap(BizFabushiPost::getId, BizFabushiPost::getUserId));
            likes.forEach(l -> likesReceived.merge(postOwner.get(l.getPostId()), 1L, Long::sum));
        }
        Map<Long, Set<LocalDate>> readDays = new HashMap<>();
        periodReads.stream()
                .filter(r -> visible == null || visible.contains(r.getUserId()))
                .forEach(r -> readDays.computeIfAbsent(r.getUserId(), k -> new HashSet<>()).add(r.getReadAt().toLocalDate()));

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
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userId", uid);
            row.put("userName", u.getRealName());
            row.put("deptName", depts.getOrDefault(u.getDeptId(), "—"));
            row.put("postCount", p);
            row.put("likesReceived", likesReceived.getOrDefault(uid, 0L));
            row.put("readDays", readDays.getOrDefault(uid, Set.of()).size());
            row.put("activityPct", Math.round(p * 100.0 / Math.max(maxPost, 1)));
            ranking.add(row);
        }
        ranking.sort((a, b) -> Long.compare((Long) b.get("postCount"), (Long) a.get("postCount")));
        return ranking.size() > 10 ? new ArrayList<>(ranking.subList(0, 10)) : ranking;
    }

    /**
     * 部门进度(共用): 可见集成员按 sys_user.dept_id → sys_dept.dept_name 内存分组
     * (口径同 score/statistics 部门排行先例, 无 deptId 归"未分配")。
     * 每行: deptName/memberCount/readUsers/coveragePct/posts/likes/comments/readDays。
     */
    private List<Map<String, Object>> buildDeptProgress(Set<Long> visible, List<BizFabushiPost> posts,
                                                        List<BizFabushiLike> likes, List<BizFabushiComment> comments,
                                                        List<BizFabushiReadStatus> reads) {
        List<SysUser> members = (visible == null)
                ? userMapper.selectList(new LambdaQueryWrapper<SysUser>().select(SysUser::getId, SysUser::getDeptId, SysUser::getRealName))
                : userMapper.selectBatchIds(visible);
        if (members.isEmpty()) return List.of();

        Set<Long> deptIds = members.stream().map(SysUser::getDeptId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> depts = deptIds.isEmpty() ? Collections.emptyMap()
                : deptMapper.selectBatchIds(deptIds).stream()
                .collect(Collectors.toMap(SysDept::getId, SysDept::getDeptName, (a, b) -> a));

        // 部门键 = deptId; 无部门用户单独归"未分配"
        Map<Long, Set<Long>> deptMembers = new LinkedHashMap<>();
        long noDeptCount = 0;
        for (SysUser u : members) {
            if (u.getDeptId() == null) { noDeptCount++; continue; }
            deptMembers.computeIfAbsent(u.getDeptId(), k -> new HashSet<>()).add(u.getId());
        }

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

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<Long, Set<Long>> e : deptMembers.entrySet()) {
            Set<Long> uids = e.getValue();
            long readUsers = deptReaders.getOrDefault(e.getKey(), Set.of()).size();
            long readDaysSum = uids.stream().mapToLong(uid -> userReadDays.getOrDefault(uid, Set.of()).size()).sum();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("deptId", e.getKey());
            row.put("deptName", depts.getOrDefault(e.getKey(), "—"));
            row.put("memberCount", uids.size());
            row.put("readUsers", readUsers);
            row.put("coveragePct", Math.round(readUsers * 1000.0 / uids.size()) / 10.0);
            row.put("posts", uids.stream().mapToLong(uid -> postCnt.getOrDefault(uid, 0L)).sum());
            row.put("likes", uids.stream().mapToLong(uid -> likeCnt.getOrDefault(uid, 0L)).sum());
            row.put("comments", uids.stream().mapToLong(uid -> commentCnt.getOrDefault(uid, 0L)).sum());
            row.put("readDays", readDaysSum);
            rows.add(row);
        }
        if (noDeptCount > 0) {
            // 无部门成员单独一行, 不并入任何部门(口径与成员榜 deptName="—" 一致)
            Set<Long> noDeptIds = members.stream().filter(u -> u.getDeptId() == null).map(SysUser::getId).collect(Collectors.toSet());
            long readUsers = reads.stream().map(BizFabushiReadStatus::getUserId)
                    .filter(noDeptIds::contains).distinct().count();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("deptId", null);
            row.put("deptName", "未分配");
            row.put("memberCount", noDeptCount);
            row.put("readUsers", readUsers);
            row.put("coveragePct", Math.round(readUsers * 1000.0 / noDeptCount) / 10.0);
            row.put("posts", noDeptIds.stream().mapToLong(uid -> postCnt.getOrDefault(uid, 0L)).sum());
            row.put("likes", noDeptIds.stream().mapToLong(uid -> likeCnt.getOrDefault(uid, 0L)).sum());
            row.put("comments", noDeptIds.stream().mapToLong(uid -> commentCnt.getOrDefault(uid, 0L)).sum());
            row.put("readDays", noDeptIds.stream().mapToLong(uid -> userReadDays.getOrDefault(uid, Set.of()).size()).sum());
            rows.add(row);
        }
        // 排序: 覆盖率降序 → 已读人数降序 → 成员数降序
        // (memberCount 来自 Set.size() 是 Integer、readUsers 是 long——统一经 Number 取值,
        //  【2026-11-16 实测教训】直接 (Long) 强转 Integer 在运行时 ClassCastException→500)
        rows.sort((a, b) -> {
            int c = Double.compare(((Number) b.get("coveragePct")).doubleValue(), ((Number) a.get("coveragePct")).doubleValue());
            if (c != 0) return c;
            c = Long.compare(((Number) b.get("readUsers")).longValue(), ((Number) a.get("readUsers")).longValue());
            if (c != 0) return c;
            return Long.compare(((Number) b.get("memberCount")).longValue(), ((Number) a.get("memberCount")).longValue());
        });
        return rows;
    }
}
