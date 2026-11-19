package com.aigicrm.module.fabushi.controller;

import com.aigicrm.common.Result;
import com.aigicrm.module.fabushi.service.FabushiStatsService;
import com.aigicrm.module.fabushi.service.FabushiDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** 法布施统计端点(一期 2026-11-15)。团队统计=独立数据范围档 (fabushi, stats_view); 个人统计=入口码门。 */
@RestController
@RequestMapping("/api/fabushi/stats")
@RequiredArgsConstructor
public class FabushiStatsController {
    // P0-5 batch A: duplicated string literals (Sonar java:S1192)
    private static final String LIT_COVERAGEPCT = "coveragePct";
    private static final String LIT_DEPTNAME = "deptName";
    private static final String LIT_READDAYS = "readDays";
    private static final String LIT_MONTH = "month";
    private static final String LIT_POSTS = "posts";
    private static final String LIT_LIKES = "likes";
    private static final String LIT_COMMENTS = "comments";
    private static final String LIT_READUSERS = "readUsers";
    // 【UI-STATS-REDESIGN-001】S1192 前置自查: 新增区块键名常量化(readCount ×6/categoryName ×4)
    private static final String LIT_READCOUNT = "readCount";
    private static final String LIT_CATEGORYNAME = "categoryName";
    private static final String LIT_BOOKRANKING = "bookRanking";
    private static final String LIT_CATEGORYREADS = "categoryReads";


    private final FabushiStatsService statsService;
    private final FabushiDashboardService dashboardService;

    /** 团队统计看板(管理层): 今日已读人数/覆盖率/内容量/木鱼量/评论量, 可见集收口 fail-closed。 */
    @GetMapping("/team")
    @PreAuthorize("hasAuthority('fabushi:stats:view')")
    public Result<java.util.Map<String, Object>> team() {
        return Result.ok(statsService.team());
    }

    /** 统计看板(管理层): 成员榜/类别热度/每日趋势 + 周期指标。 */
    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('fabushi:stats:view')")
    public Result<java.util.Map<String, Object>> dashboard(
            @RequestParam(defaultValue = "week") String period) {
        return Result.ok(dashboardService.dashboard(period));
    }

    /** 统计导出 CSV(二期): fabushi:stats:export。 */
    @GetMapping("/export")
    @PreAuthorize("hasAuthority('fabushi:stats:export')")
    public ResponseEntity<byte[]> export(@RequestParam(defaultValue = "week") String period) {
        Map<String, Object> d = dashboardService.dashboard(period);
        StringBuilder sb = new StringBuilder("\uFEFF指标,值\n");
        sb.append("周期,").append(d.get("period")).append("\n");
        sb.append("今日已读人数,").append(d.get("todayReadUsers")).append("\n");
        sb.append("覆盖率,").append(d.get(LIT_COVERAGEPCT) == null ? "" : d.get(LIT_COVERAGEPCT)).append("\n");
        // 【UI-STATS-REDESIGN-001 §三】周报头扩: 周期已读人数 + 每日一读达成率(分母 0 输出空)
        sb.append("周期已读人数,").append(d.get("periodReadUsers")).append("\n");
        sb.append("每日一读达成率%,").append(d.get("dailyGoalRate") == null ? "" : d.get("dailyGoalRate")).append("\n");
        sb.append("新增内容,").append(d.get("periodPosts")).append("\n");
        sb.append("木鱼,").append(d.get("periodLikes")).append("\n");
        sb.append("评论,").append(d.get("periodComments")).append("\n\n成员榜\n成员,部门,已读天数,已读人次,发布,获木鱼\n");
        for (Object o : (Iterable<?>) d.getOrDefault("ranking", java.util.List.of())) {
            Map<?, ?> r = (Map<?, ?>) o;
            sb.append(r.get("userName")).append(',').append(r.get(LIT_DEPTNAME)).append(',')
              .append(r.get(LIT_READDAYS)).append(',').append(r.get(LIT_READCOUNT)).append(',')
              .append(r.get("postCount")).append(',').append(r.get("likesReceived")).append('\n');
        }
        // 【§三】书目阅读区块 + 类别阅读区块(与页面同源同值)
        sb.append('\n').append("书目阅读榜\n书名,类别,已读人数,已读人次,最新阅读时间\n");
        for (Object o : (Iterable<?>) d.getOrDefault(LIT_BOOKRANKING, java.util.List.of())) {
            Map<?, ?> r = (Map<?, ?>) o;
            sb.append(csv(r.get("title"))).append(',').append(csv(r.get(LIT_CATEGORYNAME))).append(',')
              .append(r.get(LIT_READUSERS)).append(',').append(r.get(LIT_READCOUNT)).append(',')
              .append(csv(r.get("latestReadAt"))).append('\n');
        }
        sb.append('\n').append("阅读类别分布\n类别,已读人次\n");
        for (Object o : (Iterable<?>) d.getOrDefault(LIT_CATEGORYREADS, java.util.List.of())) {
            Map<?, ?> r = (Map<?, ?>) o;
            sb.append(csv(r.get(LIT_CATEGORYNAME))).append(',').append(r.get(LIT_READCOUNT)).append('\n');
        }
        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=fabushi-stats.csv")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8")).body(bytes);
    }

    /**
     * 【2026-11-16 P3】月度报告: 汇总指标+环比上月+每日趋势+部门进度+活跃榜。
     * month=YYYY-MM, 缺省/非法回落当月; 可见集与 dashboard 同一 fail-closed 口径。
     */
    @GetMapping("/monthly")
    @PreAuthorize("hasAuthority('fabushi:stats:view')")
    public Result<java.util.Map<String, Object>> monthly(
            @RequestParam(required = false) String month) {
        return Result.ok(dashboardService.monthly(month));
    }

    /** 【2026-11-16 P3】月度报告 CSV 导出: 汇总段+环比段+部门段+成员榜段。 */
    @GetMapping("/monthly/export")
    @PreAuthorize("hasAuthority('fabushi:stats:export')")
    public ResponseEntity<byte[]> monthlyExport(@RequestParam(required = false) String month) {
        Map<String, Object> d = dashboardService.monthly(month);
        StringBuilder sb = new StringBuilder("\uFEFF月度报告,值\n");
        sb.append(csv("月份")).append(',').append(csv(d.get(LIT_MONTH))).append('\n');
        sb.append(csv("周期")).append(',').append(d.get("periodStart")).append(" ~ ").append(d.get("periodEnd")).append('\n');
        sb.append(csv("新增内容")).append(',').append(d.get(LIT_POSTS)).append('\n');
        sb.append(csv("木鱼")).append(',').append(d.get(LIT_LIKES)).append('\n');
        sb.append(csv("评论")).append(',').append(d.get(LIT_COMMENTS)).append('\n');
        sb.append(csv("已读人次")).append(',').append(d.get(LIT_READCOUNT)).append('\n');
        sb.append(csv("已读人数")).append(',').append(d.get(LIT_READUSERS)).append('\n');
        sb.append(csv("活跃人数")).append(',').append(d.get("activeUsers")).append('\n');
        sb.append(csv("覆盖率%")).append(',').append(csv(d.get(LIT_COVERAGEPCT))).append('\n');
        Map<?, ?> prev = (Map<?, ?>) d.getOrDefault("prev", Map.of());
        sb.append('\n').append(csv("环比上月")).append(',').append(csv(prev.get(LIT_MONTH))).append('\n');
        sb.append(csv("指标")).append(",上月,环比%\n");
        sb.append(csv("新增内容")).append(',').append(prev.get(LIT_POSTS)).append(',').append(csv(prev.get("postsPct"))).append('\n');
        sb.append(csv("木鱼")).append(',').append(prev.get(LIT_LIKES)).append(',').append(csv(prev.get("likesPct"))).append('\n');
        sb.append(csv("评论")).append(',').append(prev.get(LIT_COMMENTS)).append(',').append(csv(prev.get("commentsPct"))).append('\n');
        sb.append(csv("已读人数")).append(',').append(prev.get(LIT_READUSERS)).append(',').append(csv(prev.get("readUsersPct"))).append('\n');
        sb.append('\n').append(csv("部门进度")).append('\n');
        sb.append("部门,成员数,已读人数,覆盖率%,新增内容,木鱼,评论,已读天数\n");
        for (Object o : (Iterable<?>) d.getOrDefault("deptProgress", java.util.List.of())) {
            Map<?, ?> r = (Map<?, ?>) o;
            sb.append(csv(r.get(LIT_DEPTNAME))).append(',').append(r.get("memberCount")).append(',')
              .append(r.get(LIT_READUSERS)).append(',').append(r.get(LIT_COVERAGEPCT)).append(',')
              .append(r.get(LIT_POSTS)).append(',').append(r.get(LIT_LIKES)).append(',')
              .append(r.get(LIT_COMMENTS)).append(',').append(r.get(LIT_READDAYS)).append('\n');
        }
        sb.append('\n').append(csv("活跃榜")).append('\n');
        sb.append("成员,部门,已读天数,已读人次,发布,获木鱼\n");
        for (Object o : (Iterable<?>) d.getOrDefault("ranking", java.util.List.of())) {
            Map<?, ?> r = (Map<?, ?>) o;
            sb.append(csv(r.get("userName"))).append(',').append(csv(r.get(LIT_DEPTNAME))).append(',')
              .append(r.get(LIT_READDAYS)).append(',').append(r.get(LIT_READCOUNT)).append(',')
              .append(r.get("postCount")).append(',').append(r.get("likesReceived")).append('\n');
        }
        // 【UI-STATS-REDESIGN-001 §三】月报同步扩: 书目阅读 + 类别阅读区块(monthly() 已扩两字段, 任意月口径)
        sb.append('\n').append(csv("书目阅读榜")).append('\n');
        sb.append("书名,类别,已读人数,已读人次,最新阅读时间\n");
        for (Object o : (Iterable<?>) d.getOrDefault(LIT_BOOKRANKING, java.util.List.of())) {
            Map<?, ?> r = (Map<?, ?>) o;
            sb.append(csv(r.get("title"))).append(',').append(csv(r.get(LIT_CATEGORYNAME))).append(',')
              .append(r.get(LIT_READUSERS)).append(',').append(r.get(LIT_READCOUNT)).append(',')
              .append(csv(r.get("latestReadAt"))).append('\n');
        }
        sb.append('\n').append(csv("阅读类别分布")).append('\n');
        sb.append("类别,已读人次\n");
        for (Object o : (Iterable<?>) d.getOrDefault(LIT_CATEGORYREADS, java.util.List.of())) {
            Map<?, ?> r = (Map<?, ?>) o;
            sb.append(csv(r.get(LIT_CATEGORYNAME))).append(',').append(r.get(LIT_READCOUNT)).append('\n');
        }
        String filename = "fabushi-monthly-" + d.get(LIT_MONTH) + ".csv";
        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=" + filename)
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8")).body(bytes);
    }

    /** CSV 单元格转义: 含逗号/引号/换行时加引号包裹(内部引号翻倍)。 */
    private static String csv(Object cell) {
        String s = cell == null ? "" : String.valueOf(cell);
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return '"' + s.replace("\"", "\"\"") + '"';
        }
        return s;
    }

    /** 个人统计(全员): 本人内容/获木鱼/评论/阅读数。 */
    @GetMapping("/personal")
    @PreAuthorize("hasAuthority('fabushi:view')")
    public Result<java.util.Map<String, Object>> personal() {
        return Result.ok(statsService.personal());
    }
}
