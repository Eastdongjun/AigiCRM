package com.aigicrm.module.score.controller;
import org.springframework.security.access.prepost.PreAuthorize;

import com.aigicrm.common.BusinessException;
import com.aigicrm.common.Result;
import com.aigicrm.module.score.entity.BizScoreItem;
import com.aigicrm.module.score.service.ScoreService;
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

    // ====== 学分项 ======

    @PostMapping("/items")
    @PreAuthorize("hasAuthority('score:manage')")
    public Result<BizScoreItem> addItem(@RequestBody BizScoreItem item) {
        // 【六层验收 2026-11-08】空body 500 修复：空对象缺类别/产品直达 DB NOT NULL 报 500，前置 400
        if (item == null || item.getCategory() == null || item.getCategory().isBlank()) {
            throw new BusinessException(400, "学分类别不能为空");
        }
        if (item.getProduct() == null || item.getProduct().isBlank()) {
            throw new BusinessException(400, "学分产品不能为空");
        }
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
        // 【六层验收 2026-11-08】空body 500 修复：空对象缺周期/数据, entries=null 在 Service 层 NPE 报 500，前置 400
        String period = body == null ? null : (String) body.get("period");
        List<Map<String, Object>> entries = body == null ? null : (List<Map<String, Object>>) body.get("entries");
        if (period == null || period.isBlank()) {
            throw new BusinessException(400, "评分周期不能为空");
        }
        if (entries == null || entries.isEmpty()) {
            throw new BusinessException(400, "评分数据不能为空");
        }
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
        if ("dept".equals(type)) {
            return Result.ok(scoreService.deptRanking(period));
        }
        return Result.ok(scoreService.ranking(period));
    }

    @GetMapping("/ranking/stats")
    @PreAuthorize("hasAuthority('score:view')")
    public Result<Map<String, Object>> rankingStats(
            @RequestParam(required = false) String period) {
        return Result.ok(scoreService.rankingStats(period));
    }

    /** 【2026-11-07 用户令"部门排行做成明细, PC端也一样"】部门成员明细: 部门行点开看成员得分,
     *  双端(MP 明细页 dept 模式 / PC 排行页部门行弹窗)共用; 与排行同门 score:view */
    @GetMapping("/ranking/dept-members")
    @PreAuthorize("hasAuthority('score:view')")
    public Result<Map<String, Object>> deptMembers(
            @RequestParam(required = false) String period,
            @RequestParam String deptName) {
        return Result.ok(scoreService.deptMembers(period, deptName));
    }

    // ====== 导出 ======

    @GetMapping("/export/grid")
    // 【修复 2026-10-08 需求对齐】导出与前端按钮同门(score:manage,录入页仅管理员可见导出按钮);
    // 原挂 score:view 使只读查看者可直连 API 导出全量学分
    @PreAuthorize("hasAuthority('score:manage')")
    public void exportGrid(@RequestParam String period, HttpServletResponse response) throws Exception {
        scoreService.exportGrid(period, response);
    }
}
