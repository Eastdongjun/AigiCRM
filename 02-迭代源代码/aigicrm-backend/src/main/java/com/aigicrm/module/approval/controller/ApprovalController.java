package com.aigicrm.module.approval.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.aigicrm.common.Result;
import com.aigicrm.common.annotation.AigicrmLog;
import com.aigicrm.module.approval.entity.BizApproval;
import com.aigicrm.module.approval.service.ApprovalService;
import com.aigicrm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/approval")
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalService approvalService;

    @PreAuthorize("hasAuthority('approval:view')")
    @GetMapping("/list")
    public Result<IPage<BizApproval>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "15") int size,
            @RequestParam(defaultValue = "all") String tab,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long deptId) {
        return Result.ok(approvalService.page(current, size, tab, type, keyword, SecurityUtils.getCurrentUserId(), deptId));
    }

    @PreAuthorize("hasAuthority('approval:view')")
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.ok(approvalService.detail(id, SecurityUtils.getCurrentUserId()));
    }

    @PreAuthorize("hasAuthority('approval:view')")
    @PostMapping("/submit")
    public Result<Void> submit(@RequestBody Map<String, String> params) {
        String type = firstPresent(params, "type", "approvalType");
        if (type == null || type.isBlank()) {
            return Result.error(400, "审批类型不能为空");
        }
        Long customerId = parseLongOrNull(params.get("customerId"));
        approvalService.submit(
                type,
                customerId,
                params.get("actionAfterApprove"),
                params.get("extraData")
        );
        return Result.ok();
    }

    private String firstPresent(Map<String, String> params, String primaryKey, String fallbackKey) {
        String primaryValue = params.get(primaryKey);
        return primaryValue != null ? primaryValue : params.get(fallbackKey);
    }

    private Long parseLongOrNull(String value) {
        return value == null ? null : Long.valueOf(value);
    }

    /** 【议题二.1 裁决】管理员手动转交审批节点(服务层二次校验 isAdmin) */
    @PreAuthorize("hasAuthority('approval:view')")
    @PutMapping("/{id}/nodes/{nodeId}/transfer")
    public Result<Void> transferNode(@PathVariable Long id, @PathVariable Long nodeId,
                                     @RequestBody Map<String, Long> body) {
        approvalService.transferNode(id, nodeId, body.get("newApproverId"));
        return Result.ok();
    }

    @PreAuthorize("hasAuthority('approval:approve')")
    @PostMapping("/{id}/approve")
    @AigicrmLog(module = "审批", action = "审批通过", targetType = "approval")
    public Result<Void> approve(@PathVariable Long id, @RequestBody(required = false) Map<String, String> params) {
        // 【延期B 2026-10-17】审批人可调延期天数:extendDays 可选,仅 extend 单生效(服务端校验类型+范围)
        Integer extendDays = null;
        if (params != null && params.get("extendDays") != null && !params.get("extendDays").isBlank()) {
            try {
                extendDays = Integer.valueOf(params.get("extendDays").trim());
            } catch (NumberFormatException e) {
                throw new com.aigicrm.common.BusinessException("延期天数必须是整数");
            }
        }
        approvalService.approve(id, params != null ? params.get("comment") : null, extendDays);
        return Result.ok();
    }

    @PreAuthorize("hasAuthority('approval:reject')")
    @PostMapping("/{id}/reject")
    @AigicrmLog(module = "审批", action = "审批驳回", targetType = "approval")
    public Result<Void> reject(@PathVariable Long id, @RequestBody(required = false) Map<String, String> params) {
        approvalService.reject(id, params != null ? params.get("comment") : null);
        return Result.ok();
    }

    @PreAuthorize("hasAuthority('approval:view')")
    @GetMapping("/statistics")
    public Result<Map<String, Long>> statistics() {
        return Result.ok(approvalService.statistics(SecurityUtils.getCurrentUserId()));
    }
}
