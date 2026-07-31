package com.ycmn.module.incentive.controller;

import com.ycmn.common.Result;
import com.ycmn.module.incentive.entity.BizIncentiveCategory;
import com.ycmn.module.incentive.entity.BizIncentiveRecord;
import com.ycmn.module.incentive.service.IncentiveService;
import com.ycmn.security.SecurityUser;
import com.ycmn.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
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

    @PreAuthorize("hasAuthority('incentive:edit')")
    @PostMapping("/categories")
    public Result<Void> createCategory(@RequestBody BizIncentiveCategory category) {
        incentiveService.createCategory(category);
        return Result.ok();
    }

    @PreAuthorize("hasAuthority('incentive:edit')")
    @PutMapping("/categories/{id}")
    public Result<Void> updateCategory(@PathVariable Long id, @RequestBody BizIncentiveCategory category) {
        category.setId(id);
        incentiveService.updateCategory(category);
        return Result.ok();
    }

    @PreAuthorize("hasAuthority('incentive:edit')")
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
        if (userId == null) {
            SecurityUser user = SecurityUtils.getCurrentUser();
            if (user != null) {
                // admin 或有激励权限的人可看全部数据
                boolean canSeeAll = SecurityUtils.isAdmin()
                    || incentiveService.canEdit(user.getUserId(), SecurityUtils.getCurrentRoleCode())
                    || incentiveService.canDelete(user.getUserId(), SecurityUtils.getCurrentRoleCode());
                userId = canSeeAll ? null : user.getUserId();
            }
        }
        return Result.ok(incentiveService.getRecords(userId, period, parentCategoryId));
    }

    @PreAuthorize("hasAuthority('incentive:edit')")
    @PostMapping("/records")
    public Result<Void> addRecord(@RequestBody BizIncentiveRecord record) {
        incentiveService.addRecord(record);
        return Result.ok();
    }

    @PreAuthorize("hasAuthority('incentive:delete')")
    @DeleteMapping("/records/{id}")
    public Result<Void> deleteRecord(@PathVariable Long id) {
        SecurityUser user = SecurityUtils.getCurrentUser();
        boolean isAdmin = SecurityUtils.isAdmin();
        // 检查激励权限表：admin 或 有 can_delete 权限的可删除任意记录
        if (!isAdmin && !incentiveService.canDelete(user.getUserId(), SecurityUtils.getCurrentRoleCode())) {
            BizIncentiveRecord record = incentiveService.getRecordById(id);
            if (record == null || !record.getUserId().equals(user.getUserId())) {
                return Result.error(403, "无权删除他人记录");
            }
        }
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

    /** 时间线查询：按上传时间排序 */
    @GetMapping("/timeline")
    public Result<List<Map<String, Object>>> timeline(
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String salesperson,
            @RequestParam(required = false) String industry,
            @RequestParam(required = false) Long mainCategoryId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return Result.ok(incentiveService.timeline(customerName, salesperson, industry, mainCategoryId, startDate, endDate));
    }

    /** 按客户分组查询激励记录（全员可见）— 支持多维度筛选 */
    @GetMapping("/customer-records")
    public Result<List<Map<String, Object>>> customerRecords(
            @RequestParam(required = false) String industry,
            @RequestParam(required = false) Long mainCategoryId,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String salesperson,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return Result.ok(incentiveService.getCustomerRecords(industry, mainCategoryId, customerName, salesperson, startDate, endDate));
    }

    /** 激励统计查询（全员可见）：支持大类/子类/行业/产品/周期筛选 */
    @GetMapping("/records/query")
    public Result<List<BizIncentiveRecord>> queryRecords(
            @RequestParam(required = false) Long mainCategoryId,
            @RequestParam(required = false) Long subCategoryId,
            @RequestParam(required = false) String industry,
            @RequestParam(required = false) String product,
            @RequestParam(required = false) String period) {
        return Result.ok(incentiveService.queryRecords(mainCategoryId, subCategoryId, industry, product, period));
    }

    /** 激励统计表(全员口径): 按子类聚合文件数/总学分 + 周期总计 */
    @GetMapping("/category-stats")
    public Result<Map<String, Object>> categoryStats(@RequestParam(required = false) String period) {
        return Result.ok(incentiveService.categoryStats(period));
    }

    /** 人员选择列表(公开): 用于筛选下拉框 */
    @GetMapping("/users")
    public Result<List<Map<String, Object>>> userList() {
        return Result.ok(incentiveService.getUserList());
    }
}
