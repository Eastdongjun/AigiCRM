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
        sb.append("覆盖率,").append(d.get("coveragePct") == null ? "" : d.get("coveragePct")).append("\n");
        sb.append("新增内容,").append(d.get("periodPosts")).append("\n");
        sb.append("木鱼,").append(d.get("periodLikes")).append("\n");
        sb.append("评论,").append(d.get("periodComments")).append("\n\n成员榜\n成员,部门,发布,获木鱼,已读天数\n");
        for (Object o : (Iterable<?>) d.getOrDefault("ranking", java.util.List.of())) {
            Map<?, ?> r = (Map<?, ?>) o;
            sb.append(r.get("userName")).append(',').append(r.get("deptName")).append(',')
              .append(r.get("postCount")).append(',').append(r.get("likesReceived")).append(',')
              .append(r.get("readDays")).append('\n');
        }
        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=fabushi-stats.csv")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8")).body(bytes);
    }

    /** 个人统计(全员): 本人内容/获木鱼/评论/阅读数。 */
    @GetMapping("/personal")
    @PreAuthorize("hasAuthority('fabushi:view')")
    public Result<java.util.Map<String, Object>> personal() {
        return Result.ok(statsService.personal());
    }
}
