package com.ycmn.module.approval.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ycmn.common.Result;
import com.ycmn.common.annotation.YcmnLog;
import com.ycmn.module.approval.entity.BizApproval;
import com.ycmn.module.approval.service.ApprovalService;
import com.ycmn.security.SecurityUtils;
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
            @RequestParam(required = false) Long deptId) {
        return Result.ok(approvalService.page(current, size, tab, type, SecurityUtils.getCurrentUserId(), deptId));
    }

    @PreAuthorize("hasAuthority('approval:view')")
    @PostMapping("/submit")
    public Result<Void> submit(@RequestBody Map<String, String> params) {
        String type = params.get("type") != null ? params.get("type") : params.get("approvalType");
        if (type == null || type.isBlank()) {
            return Result.error(400, "审批类型不能为空");
        }
        approvalService.submit(
                type,
                params.get("customerId") != null ? Long.valueOf(params.get("customerId")) : null,
                params.get("actionAfterApprove"),
                params.get("extraData")
        );
        return Result.ok();
    }

    @PreAuthorize("hasAuthority('approval:approve')")
    @PostMapping("/{id}/approve")
    @YcmnLog(module = "审批", action = "审批通过", targetType = "approval")
    public Result<Void> approve(@PathVariable Long id, @RequestBody(required = false) Map<String, String> params) {
        approvalService.approve(id, params != null ? params.get("comment") : null);
        return Result.ok();
    }

    @PreAuthorize("hasAuthority('approval:reject')")
    @PostMapping("/{id}/reject")
    @YcmnLog(module = "审批", action = "审批驳回", targetType = "approval")
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
