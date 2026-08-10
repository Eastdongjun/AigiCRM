package com.ycmn.module.score.controller;
import org.springframework.security.access.prepost.PreAuthorize;

import com.ycmn.common.Result;
import com.ycmn.module.score.entity.BizScoreItem;
import com.ycmn.module.score.service.ScoreService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/score")
@RequiredArgsConstructor
public class ScoreController {

    private final ScoreService scoreService;
    private final com.ycmn.module.system.service.StatsPermissionService statsPermissionService;

    // ====== 积分项 ======

    @GetMapping("/items")
    @PreAuthorize("hasAuthority('score:view')")
    public Result<List<BizScoreItem>> listItems() {
        return Result.ok(scoreService.listItems());
    }

    @PostMapping("/items")
    @PreAuthorize("hasAuthority('score:manage')")
    public Result<BizScoreItem> addItem(@RequestBody BizScoreItem item) {
        return Result.ok(scoreService.addItem(item));
    }

    @PutMapping("/items/{id}")
    @PreAuthorize("hasAuthority('score:manage')")
    public Result<Void> updateItem(@PathVariable Long id, @RequestBody BizScoreItem item) {
        item.setId(id);
        scoreService.updateItem(item);
        return Result.ok();
    }

    @DeleteMapping("/items/{id}")
    @PreAuthorize("hasAuthority('score:manage')")
    public Result<Void> deleteItem(@PathVariable Long id) {
        scoreService.deleteItem(id);
        return Result.ok();
    }

    // ====== 评分表格 ======

    @GetMapping("/grid")
    @PreAuthorize("hasAuthority('score:view')")
    public Result<Map<String, Object>> getGrid(@RequestParam String period) {
        return Result.ok(scoreService.getGrid(period));
    }

    @PreAuthorize("hasAuthority('score:entry')")
    @PostMapping("/entries/batch")
    @SuppressWarnings("unchecked")
    public Result<Void> batchSave(@RequestBody Map<String, Object> body) {
        String period = (String) body.get("period");
        List<Map<String, Object>> entries = (List<Map<String, Object>>) body.get("entries");
        List<Long> participantIds = new java.util.ArrayList<>();
        Object pids = body.get("participantIds");
        if (pids instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Number n) participantIds.add(n.longValue());
            }
        }
        scoreService.batchSave(period, entries, participantIds);
        return Result.ok();
    }

    // ====== 排行榜 ======

    @GetMapping("/ranking")
    @PreAuthorize("hasAuthority('score:view')")
    public Result<List<Map<String, Object>>> ranking(
            @RequestParam(required = false) String period,
            @RequestParam(defaultValue = "personal") String type) {
        // M5 2026-08-10: 全员学分排行仅管理员勾选的用户可见
        if (!statsPermissionService.canViewCompany()) {
            throw new com.ycmn.common.BusinessException(403, "无权限查看全员学分排行");
        }
        if ("dept".equals(type)) {
            return Result.ok(scoreService.deptRanking(period));
        }
        return Result.ok(scoreService.ranking(period));
    }

    @GetMapping("/ranking/stats")
    @PreAuthorize("hasAuthority('score:view')")
    public Result<Map<String, Object>> rankingStats(
            @RequestParam(required = false) String period) {
        // M5 2026-08-10: 全员学分排行仅管理员勾选的用户可见
        if (!statsPermissionService.canViewCompany()) {
            throw new com.ycmn.common.BusinessException(403, "无权限查看全员学分排行");
        }
        return Result.ok(scoreService.rankingStats(period));
    }

    // ====== 导出 ======

    @GetMapping("/export/grid")
    @PreAuthorize("hasAuthority('score:view')")
    public void exportGrid(@RequestParam String period, HttpServletResponse response) throws Exception {
        scoreService.exportGrid(period, response);
    }
}
