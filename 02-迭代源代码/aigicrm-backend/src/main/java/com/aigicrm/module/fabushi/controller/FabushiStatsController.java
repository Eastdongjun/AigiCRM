package com.aigicrm.module.fabushi.controller;

import com.aigicrm.common.Result;
import com.aigicrm.module.fabushi.service.FabushiStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** 法布施统计端点(一期 2026-11-15)。团队统计=独立数据范围档 (fabushi, stats_view); 个人统计=入口码门。 */
@RestController
@RequestMapping("/api/fabushi/stats")
@RequiredArgsConstructor
public class FabushiStatsController {

    private final FabushiStatsService statsService;

    /** 团队统计看板(管理层): 今日已读人数/覆盖率/内容量/木鱼量/评论量, 可见集收口 fail-closed。 */
    @GetMapping("/team")
    @PreAuthorize("hasAuthority('fabushi:stats:view')")
    public Result<java.util.Map<String, Object>> team() {
        return Result.ok(statsService.team());
    }

    /** 个人统计(全员): 本人内容/获木鱼/评论/阅读数。 */
    @GetMapping("/personal")
    @PreAuthorize("hasAuthority('fabushi:view')")
    public Result<java.util.Map<String, Object>> personal() {
        return Result.ok(statsService.personal());
    }
}
