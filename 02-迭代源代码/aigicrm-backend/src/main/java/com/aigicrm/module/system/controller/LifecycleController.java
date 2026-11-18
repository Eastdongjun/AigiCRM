package com.aigicrm.module.system.controller;

import com.aigicrm.common.Result;
import com.aigicrm.module.system.service.LifecycleService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 【2026-11-17 阶段三】离职交接 / 组织移动（人员跨部门）一级审批。
 * 创建/查询/回滚需人员管理码；审批/驳回仅需登录，服务层校验“配置的审批人或管理员”。
 */
@RestController
@RequestMapping("/api/system/lifecycle")
@RequiredArgsConstructor
public class LifecycleController {

    private final LifecycleService lifecycleService;

    @PreAuthorize("hasAuthority('system:user:edit')")
    @PostMapping("/handover")
    public Result<Map<String, Object>> handover(@RequestBody HandoverRequest body) {
        return Result.ok(lifecycleService.createHandover(
                body.fromUserId, body.toUserId, body.reason, Boolean.TRUE.equals(body.copyRoles),
                Boolean.TRUE.equals(body.transferIncentives), Boolean.TRUE.equals(body.transferReports)));
    }

    @PreAuthorize("hasAuthority('system:user:edit')")
    @PostMapping("/org-move")
    public Result<Map<String, Object>> orgMove(@RequestBody OrgMoveRequest body) {
        return Result.ok(lifecycleService.createOrgMove(body.userId, body.toDeptId, body.reason));
    }

    @PreAuthorize("hasAnyAuthority('system:user:edit','approval:view')")
    @GetMapping("/requests")
    public Result<List<Map<String, Object>>> list(@RequestParam(required = false) String type,
                                                  @RequestParam(required = false) String status) {
        return Result.ok(lifecycleService.list(type, status));
    }

    @PreAuthorize("hasAnyAuthority('system:user:edit','approval:view')")
    @GetMapping("/requests/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.ok(lifecycleService.detail(id));
    }

    /** 【A4 拍板 #4/#5】管理员转交在途单审批人（仅 pending；功能码之外服务层再收口 admin）。 */
    @PreAuthorize("hasAuthority('system:user:edit')")
    @PostMapping("/requests/{id}/transfer-approver")
    public Result<Map<String, Object>> transferApprover(@PathVariable Long id,
                                                        @RequestBody TransferApproverRequest body) {
        return Result.ok(lifecycleService.transferApprover(id, body.newApproverId, body.reason));
    }

    /** 转交请求 DTO。 */
    public static class TransferApproverRequest {
        public Long newApproverId;
        public String reason;
    }

    @PreAuthorize("hasAnyAuthority('system:user:edit','approval:view')")
    @PostMapping("/requests/{id}/approve")
    public Result<Map<String, Object>> approve(@PathVariable Long id,
                                               @RequestBody(required = false) ApproveRequest body) {
        return Result.ok(lifecycleService.approve(id, body == null ? null : body.comment));
    }

    @PreAuthorize("hasAnyAuthority('system:user:edit','approval:view')")
    @PostMapping("/requests/{id}/reject")
    public Result<Map<String, Object>> reject(@PathVariable Long id,
                                              @RequestBody(required = false) RejectRequest body) {
        return Result.ok(lifecycleService.reject(id, body == null ? null : body.reason));
    }

    @PreAuthorize("hasAuthority('system:user:edit')")
    @PostMapping("/requests/{id}/rollback")
    public Result<Map<String, Object>> rollback(@PathVariable Long id) {
        return Result.ok(lifecycleService.rollback(id));
    }

    public static class HandoverRequest {
        public Long fromUserId;
        public Long toUserId;
        public String reason;
        public Boolean copyRoles;
        public Boolean transferIncentives;
        public Boolean transferReports;
    }

    public static class OrgMoveRequest {
        public Long userId;
        public Long toDeptId;
        public String reason;
    }

    public static class ApproveRequest {
        public String comment;
    }

    public static class RejectRequest {
        public String reason;
    }
}
