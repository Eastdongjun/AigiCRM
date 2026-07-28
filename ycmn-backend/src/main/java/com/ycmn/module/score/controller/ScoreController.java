package com.ycmn.module.score.controller;

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

    // ====== 积分项 ======

    @GetMapping("/items")
    public Result<List<BizScoreItem>> listItems() {
        return Result.ok(scoreService.listItems());
    }

    @PostMapping("/items")
    public Result<BizScoreItem> addItem(@RequestBody BizScoreItem item) {
        return Result.ok(scoreService.addItem(item));
    }

    @PutMapping("/items/{id}")
    public Result<Void> updateItem(@PathVariable Long id, @RequestBody BizScoreItem item) {
        item.setId(id);
        scoreService.updateItem(item);
        return Result.ok();
    }

    @DeleteMapping("/items/{id}")
    public Result<Void> deleteItem(@PathVariable Long id) {
        scoreService.deleteItem(id);
        return Result.ok();
    }

    // ====== 评分表格 ======

    @GetMapping("/grid")
    public Result<Map<String, Object>> getGrid(@RequestParam String period) {
        return Result.ok(scoreService.getGrid(period));
    }

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
    public Result<List<Map<String, Object>>> ranking(
            @RequestParam(required = false) String period,
            @RequestParam(defaultValue = "personal") String type) {
        if ("dept".equals(type)) {
            return Result.ok(scoreService.deptRanking(period));
        }
        return Result.ok(scoreService.ranking(period));
    }

    @GetMapping("/ranking/stats")
    public Result<Map<String, Object>> rankingStats(
            @RequestParam(required = false) String period) {
        return Result.ok(scoreService.rankingStats(period));
    }

    // ====== 导出 ======

    @GetMapping("/export/grid")
    public void exportGrid(@RequestParam String period, HttpServletResponse response) throws Exception {
        scoreService.exportGrid(period, response);
    }
}
