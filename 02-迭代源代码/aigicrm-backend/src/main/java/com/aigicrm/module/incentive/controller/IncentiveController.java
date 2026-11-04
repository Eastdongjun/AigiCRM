package com.aigicrm.module.incentive.controller;

import com.aigicrm.common.BusinessException;
import com.aigicrm.common.Result;
import com.aigicrm.module.incentive.entity.BizIncentiveCategory;
import com.aigicrm.module.incentive.entity.BizIncentiveRecord;
import com.aigicrm.module.incentive.service.IncentiveService;
import com.aigicrm.security.SecurityUser;
import com.aigicrm.security.SecurityUtils;
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
     * 更新记录（追加/删除文件时调用，自动重算学分）。
     * 设计 §6：归属校验全部交给方法内守卫（isAdmin ∨ can_edit 迁移授权 ∨ 记录本人）——
     * 去掉方法级 @PreAuthorize 硬码，已卸 edit 码的销售员仍可改本人记录（本人恒可）。
     * 登录保护由 SecurityConfig anyRequest().authenticated() 兜底。
     */
    // 【PR-01 N-03 2026-09-18】方法级注解做底线（本人恒可语义保留在方法内守卫），
    // 此前完全无注解，仅靠方法内单一守卫。
    @PreAuthorize("hasAuthority('incentive:view')")
    @PutMapping("/records/{id}")
    public Result<Void> updateRecord(@PathVariable Long id, @RequestBody BizIncentiveRecord record) {
        assertCanEditRecord(id);
        incentiveService.updateRecord(id, record);
        return Result.ok();
    }

    /**
     * 【用户裁决 2026-10-10】编辑历史文件时可移除单个已有文件（上传错了需要修改）。
     * 服务端原子完成"槽位数组剔除该文件ID→重算学分→盖修改人章"；物理文件保留只解除引用。
     * 权限同 updateRecord：admin ∨ incentive:manage 授权 ∨ 本人记录。
     */
    @PreAuthorize("hasAuthority('incentive:view')")
    @PostMapping("/records/{id}/remove-file")
    public Result<Void> removeFile(@PathVariable Long id, @RequestBody RemoveFileBody body) {
        assertCanEditRecord(id);
        incentiveService.removeFile(id, body.slot(), body.fileId());
        return Result.ok();
    }

    /** remove-file 请求体：slot ∈ file/pre/mid/post/track */
    public record RemoveFileBody(String slot, Long fileId) {}

    /**
     * 【2026-08-14 RBAC 收敛】编辑归属判定：admin ∨ incentive:manage 授权 ∨ 本人记录。
     * 【工作流复检修复 2026-10-10】updateRecord/removeFile 原为两段复制粘贴守卫，单边改动会
     * 造成两接口权限口径漂移——收敛为单一方法，两接口共用同一判定。
     */
    private void assertCanEditRecord(Long id) {
        SecurityUser user = SecurityUtils.getCurrentUser();
        if (SecurityUtils.isAdmin() || incentiveService.canEdit()) return;
        BizIncentiveRecord existing = incentiveService.getRecordById(id);
        if (existing == null || existing.getUserId() == null || !existing.getUserId().equals(user.getUserId())) {
            throw new BusinessException(403, "无权编辑他人记录");
        }
    }


    /** 批量重算所有历史记录学分（按最新规则） */
    @PreAuthorize("hasAuthority('incentive:edit')")
    @PostMapping("/records/recalculate")
    public Result<java.util.Map<String, Object>> recalculateAll() {
        int changed = incentiveService.recalculateAllScores();
        return Result.ok(java.util.Map.of("changed", changed));
    }

    /** 同 updateRecord：归属校验在方法内（admin ∨ can_delete 迁移授权 ∨ 记录本人），去方法级硬码 */
    // 【PR-01 N-03】incentive:view 底线注解，归属仍由方法内守卫判定。
    @PreAuthorize("hasAuthority('incentive:view')")
    @DeleteMapping("/records/{id}")
    public Result<Void> deleteRecord(@PathVariable Long id) {
        SecurityUser user = SecurityUtils.getCurrentUser();
        boolean isAdmin = SecurityUtils.isAdmin();
        // 【2026-08-14 RBAC 收敛】授权判定：admin ∨ incentive:manage 权限码 ∨ 本人记录
        if (!isAdmin && !incentiveService.canDelete()) {
            // 变量名避开受限标识符 record（SonarLint S6213）
            BizIncentiveRecord incRecord = incentiveService.getRecordById(id);
            if (incRecord == null || incRecord.getUserId() == null || !incRecord.getUserId().equals(user.getUserId())) {
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

    // 【2026-11-04 用户令"逐功能建码"】排行数据面: 激励排行查看∨激励统计查看（统计页 PC 与排行页 MP 双端共用）
    @PreAuthorize("hasAnyAuthority('incentive:stats:view','incentive:rank:view')")
    @GetMapping("/scoreboard")
    public Result<List<Map<String, Object>>> scoreboard(@RequestParam(required = false) String period) {
        return Result.ok(incentiveService.scoreboard(period));
    }

    // 【2026-11-04 用户令"逐功能建码"】排行数据面: 激励排行查看∨激励统计查看（部门排行, 双端共用）
    @PreAuthorize("hasAnyAuthority('incentive:stats:view','incentive:rank:view')")
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
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String orgIds,
            @RequestParam(required = false) String devType,
            @RequestParam(required = false) String product,
            @RequestParam(required = false) String keyword,
            // 【性能 2026-10-27】recordId 定位: 小程序拨盘详情页只拉该记录所属客户的一组数据,
            // 替代"全量下载所有客户记录再客户端线性查找"(生产实测全量 544KB/TTFB 0.9s)
            @RequestParam(required = false) Long recordId) {
        return Result.ok(incentiveService.getCustomerRecords(industry, mainCategoryId, customerName, salesperson, startDate, endDate, period, orgIds, devType, product, keyword, recordId));
    }

    @PreAuthorize("hasAuthority('incentive:view')")
    @PostMapping("/records/{id}/read")
    public Result<Void> markRead(@PathVariable Long id) {
        incentiveService.markRead(id);
        return Result.ok();
    }

    @PreAuthorize("hasAuthority('incentive:read-stats')")
    @GetMapping("/read-stats")
    public Result<List<Map<String, Object>>> readStats(@RequestParam(required = false) String period) {
        return Result.ok(incentiveService.readStats(period));
    }

    /**
     * 本月激励查询阅读排行榜（2026-09-13 新增页面数据源）
     *
     * 【需求】系统设置下新增「本月激励查询阅读排行榜」页；权限码 incentive:read-ranking:view
     *         默认只绑给系统管理员角色，管理员在角色管理里勾选后其它角色才可见（菜单与接口同码）
     * 【参数】period 形如 2026-09，缺省=当月
     */
    @PreAuthorize("hasAuthority('incentive:read-ranking:view')")
    @GetMapping("/read-ranking")
    public Result<Map<String, Object>> readRanking(@RequestParam(required = false) String period) {
        return Result.ok(incentiveService.readRanking(period));
    }

    /** 激励统计表(全员口径): 按子类聚合文件数/总学分 + 周期总计 */
    // 【2026-11-04 用户令"逐功能建码"】统计数据面: 激励统计查看（激励统计页·统计页签/PC 激励统计菜单）
    @PreAuthorize("hasAuthority('incentive:stats:view')")
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
