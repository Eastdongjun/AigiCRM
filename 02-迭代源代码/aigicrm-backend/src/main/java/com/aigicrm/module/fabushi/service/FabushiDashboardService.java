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

        // 榜单：发布数 / 获木鱼 / 已读天数
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
        List<BizFabushiReadStatus> periodReads = readStatusMapper.selectList(new LambdaQueryWrapper<BizFabushiReadStatus>()
                .ge(BizFabushiReadStatus::getReadAt, startAt));
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
        if (!candidateIds.isEmpty()) {
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
            if (ranking.size() > 10) ranking = ranking.subList(0, 10);
        }
        data.put("ranking", ranking);

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
}
