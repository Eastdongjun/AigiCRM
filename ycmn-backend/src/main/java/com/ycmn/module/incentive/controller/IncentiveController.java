package com.ycmn.module.incentive.controller;

import com.ycmn.common.Result;
import com.ycmn.module.incentive.entity.BizIncentiveCategory;
import com.ycmn.module.incentive.entity.BizIncentiveRecord;
import com.ycmn.module.incentive.service.IncentiveService;
import com.ycmn.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/incentive")
@RequiredArgsConstructor
public class IncentiveController {

    private final IncentiveService incentiveService;

    @GetMapping("/categories")
    public Result<List<BizIncentiveCategory>> getCategoryTree() {
        return Result.ok(incentiveService.getCategoryTree());
    }

    @PostMapping("/categories")
    public Result<Void> createCategory(@RequestBody BizIncentiveCategory category) {
        incentiveService.createCategory(category);
        return Result.ok();
    }

    @PutMapping("/categories/{id}")
    public Result<Void> updateCategory(@PathVariable Long id, @RequestBody BizIncentiveCategory category) {
        category.setId(id);
        incentiveService.updateCategory(category);
        return Result.ok();
    }

    @DeleteMapping("/categories/{id}")
    public Result<Void> deleteCategory(@PathVariable Long id) {
        incentiveService.deleteCategory(id);
        return Result.ok();
    }

    @GetMapping("/records")
    public Result<List<BizIncentiveRecord>> getRecords(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) Long parentCategoryId) {
        if (userId == null) userId = SecurityUtils.getCurrentUserId();
        return Result.ok(incentiveService.getRecords(userId, period, parentCategoryId));
    }

    @PostMapping("/records")
    public Result<Void> addRecord(@RequestBody BizIncentiveRecord record) {
        incentiveService.addRecord(record);
        return Result.ok();
    }

    @DeleteMapping("/records/{id}")
    public Result<Void> deleteRecord(@PathVariable Long id) {
        incentiveService.deleteRecord(id);
        return Result.ok();
    }

    @GetMapping("/score/customer/{customerId}")
    public Result<Integer> getScoreByCustomer(@PathVariable Long customerId) {
        return Result.ok(incentiveService.getScoreByCustomerId(customerId));
    }

    @GetMapping("/scoreboard")
    public Result<List<Map<String, Object>>> scoreboard(@RequestParam(required = false) String period) {
        return Result.ok(incentiveService.scoreboard(period));
    }

    @GetMapping("/ranking")
    public Result<List<Map<String, Object>>> ranking(
            @RequestParam(defaultValue = "personal") String type,
            @RequestParam(required = false) String period) {
        if ("dept".equals(type)) {
            return Result.ok(incentiveService.deptRanking(period));
        }
        return Result.ok(incentiveService.scoreboard(period));
    }

    @GetMapping("/ranking/stats")
    public Result<Map<String, Object>> rankingStats(@RequestParam(required = false) String period) {
        return Result.ok(incentiveService.rankingStats(period));
    }

    /** 激励统计表(全员口径): 按子类聚合文件数/总学分 + 周期总计 */
    @GetMapping("/category-stats")
    public Result<Map<String, Object>> categoryStats(@RequestParam(required = false) String period) {
        return Result.ok(incentiveService.categoryStats(period));
    }
}
