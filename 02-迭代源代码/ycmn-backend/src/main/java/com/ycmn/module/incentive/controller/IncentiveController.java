package com.ycmn.module.incentive.controller;

import com.ycmn.common.BusinessException;
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

    @PreAuthorize("hasAuthority('incentive:view')")
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

    @PreAuthorize("hasAuthority('incentive:view')")
    @GetMapping("/records")
    public Result<List<BizIncentiveRecord>> getRecords(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) Long parentCategoryId,
            @RequestParam(required = false) Long customerId) {
        // 【修复 2026-08-07】不再对普通用户强制 userId=本人：
        // - 未传 userId → service 按数据范围过滤（含系统级开关，上级/负责人可见下级）
        // - 显式传 userId → service 内校验是否在可见范围内（防越权看任意人）
        // customerId 可选：客户详情 Tab 按客户过滤
        return Result.ok(incentiveService.getRecords(userId, period, parentCategoryId, customerId));
    }

    /** 上传新记录：incentive:add 独立权限码（设计 §3.2：上传语义从 edit 拆出） */
    @PreAuthorize("hasAuthority('incentive:add')")
    @PostMapping("/records")
    public Result<Void> addRecord(@RequestBody BizIncentiveRecord record) {
        incentiveService.addRecord(record);
        return Result.ok();
    }

    /**
     * 更新记录（追加/删除文件时调用，自动重算积分）。
     * 设计 §6：归属校验全部交给方法内守卫（isAdmin ∨ can_edit 迁移授权 ∨ 记录本人）——
     * 去掉方法级 @PreAuthorize 硬码，已卸 edit 码的销售员仍可改本人记录（本人恒可）。
     * 登录保护由 SecurityConfig anyRequest().authenticated() 兜底。
     */
    @PutMapping("/records/{id}")
    public Result<Void> updateRecord(@PathVariable Long id, @RequestBody BizIncentiveRecord record) {
        SecurityUser user = SecurityUtils.getCurrentUser();
        boolean isAdmin = SecurityUtils.isAdmin();
        // 【2026-08-14 RBAC 收敛】授权判定：admin ∨ incentive:manage 权限码 ∨ 本人记录
        if (!isAdmin && !incentiveService.canEdit()) {
            BizIncentiveRecord existing = incentiveService.getRecordById(id);
            if (existing == null || !existing.getUserId().equals(user.getUserId())) {
                throw new BusinessException(403, "无权编辑他人记录");
            }
        }
        incentiveService.updateRecord(id, record);
        return Result.ok();
    }

    /** 批量重算所有历史记录积分（按最新规则） */
    @PreAuthorize("hasAuthority('incentive:edit')")
    @PostMapping("/records/recalculate")
    public Result<java.util.Map<String, Object>> recalculateAll() {
        int changed = incentiveService.recalculateAllScores();
        return Result.ok(java.util.Map.of("changed", changed));
    }

    /** 同 updateRecord：归属校验在方法内（admin ∨ can_delete 迁移授权 ∨ 记录本人），去方法级硬码 */
    @DeleteMapping("/records/{id}")
    public Result<Void> deleteRecord(@PathVariable Long id) {
        SecurityUser user = SecurityUtils.getCurrentUser();
        boolean isAdmin = SecurityUtils.isAdmin();
        // 【2026-08-14 RBAC 收敛】授权判定：admin ∨ incentive:manage 权限码 ∨ 本人记录
        if (!isAdmin && !incentiveService.canDelete()) {
            // 变量名避开受限标识符 record（SonarLint S6213）
            BizIncentiveRecord incRecord = incentiveService.getRecordById(id);
            if (incRecord == null || !incRecord.getUserId().equals(user.getUserId())) {
                throw new BusinessException(403, "无权删除他人记录");
            }
        }
        incentiveService.deleteRecord(id);
        return Result.ok();
    }

    @PreAuthorize("hasAuthority('incentive:view')")
    @GetMapping("/score/customer/{customerId}")
    public Result<Integer> getScoreByCustomer(@PathVariable Long customerId) {
        return Result.ok(incentiveService.getScoreByCustomerId(customerId));
    }

    @PreAuthorize("hasAuthority('incentive:view')")
    @GetMapping("/scoreboard")
    public Result<List<Map<String, Object>>> scoreboard(@RequestParam(required = false) String period) {
        return Result.ok(incentiveService.scoreboard(period));
    }

    @PreAuthorize("hasAuthority('incentive:view')")
    @GetMapping("/ranking")
    public Result<List<Map<String, Object>>> ranking(
            @RequestParam(defaultValue = "personal") String type,
            @RequestParam(required = false) String period) {
        if ("dept".equals(type)) {
            return Result.ok(incentiveService.deptRanking(period));
        }
        return Result.ok(incentiveService.scoreboard(period));
    }

    /** 按客户分组查询激励记录（全员可见）— 支持多维度筛选 */
    @PreAuthorize("hasAuthority('incentive:view')")
    @GetMapping("/customer-records")
    public Result<List<Map<String, Object>>> customerRecords(
            @RequestParam(required = false) String industry,
            @RequestParam(required = false) Long mainCategoryId,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String salesperson,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String orgIds,
            @RequestParam(required = false) String devType,
            @RequestParam(required = false) String product,
            @RequestParam(required = false) String keyword) {
        return Result.ok(incentiveService.getCustomerRecords(industry, mainCategoryId, customerName, salesperson, startDate, endDate, orgIds, devType, product, keyword));
    }

    /** 激励统计表(全员口径): 按子类聚合文件数/总学分 + 周期总计 */
    @PreAuthorize("hasAuthority('incentive:view')")
    @GetMapping("/category-stats")
    public Result<Map<String, Object>> categoryStats(@RequestParam(required = false) String period) {
        return Result.ok(incentiveService.categoryStats(period));
    }

    /** 人员选择列表(公开): 用于筛选下拉框 */
    @PreAuthorize("hasAuthority('incentive:view')")
    @GetMapping("/users")
    public Result<List<Map<String, Object>>> userList() {
        return Result.ok(incentiveService.getUserList());
    }
}
