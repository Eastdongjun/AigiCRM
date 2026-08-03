package com.ycmn.module.statistics.controller;

import com.ycmn.common.Result;
import com.ycmn.module.statistics.service.StatisticsQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 统计报表接口
 *
 * 【需求】统计看板/日报/周报/指标排行/经营分析/团队动态
 * 【方法】数据查询与组装全部委托 StatisticsQueryService（分层约束：Controller 不直查 Mapper）
 * 【业务规则】统计接口权限：statistics:view / dashboard:view；指标详情需 statistics:view
 */
@RestController
@RequestMapping("/api/statistics")
@RequiredArgsConstructor
public class StatisticsController {

    private final StatisticsQueryService statisticsQueryService;

    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyAuthority('statistics:view','dashboard:view')")
    public Result<Map<String, Object>> dashboard() {
        return Result.ok(statisticsQueryService.dashboard());
    }

    @GetMapping("/daily-stats")
    @PreAuthorize("hasAnyAuthority('statistics:view','dashboard:view')")
    public Result<Map<String, Object>> dailyStats() {
        return Result.ok(statisticsQueryService.dailyStats());
    }

    @GetMapping("/weekly-stats")
    @PreAuthorize("hasAnyAuthority('statistics:view','dashboard:view')")
    public Result<Map<String, Object>> weeklyStats() {
        return Result.ok(statisticsQueryService.weeklyStats());
    }

    @GetMapping("/indicator-stats")
    @PreAuthorize("hasAuthority('statistics:view')")
    public Result<Map<String, Object>> indicatorStats() {
        return Result.ok(statisticsQueryService.indicatorStats());
    }

    @GetMapping("/analysis")
    @PreAuthorize("hasAuthority('statistics:view')")
    public Result<Map<String, Object>> analysis(@RequestParam(defaultValue = "0") int year,
                                                @RequestParam(required = false) Long officeId) {
        return Result.ok(statisticsQueryService.analysis(year, officeId));
    }

    @GetMapping("/team-activities")
    @PreAuthorize("hasAnyAuthority('statistics:view','dashboard:view')")
    public Result<List<Map<String, Object>>> teamActivities() {
        return Result.ok(statisticsQueryService.teamActivities());
    }
}
