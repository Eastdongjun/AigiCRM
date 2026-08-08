package com.ycmn.module.incentive.controller;

import com.ycmn.common.Result;
import com.ycmn.common.BusinessException;
import com.ycmn.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/incentive/permission")
@RequiredArgsConstructor
public class IncentivePermissionController {

    private final JdbcTemplate jdbc;

    @PreAuthorize("hasAuthority('system:config:view')")
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list() {
        List<Map<String, Object>> list = jdbc.queryForList(
            "SELECT p.id, p.user_id, p.can_edit, p.can_delete, p.can_view_all, p.can_view_shared, u.real_name AS realName, u.username, " +
            "COALESCE(d.org_name,'—') AS officeName, COALESCE(r.role_name,'—') AS roleName " +
            "FROM biz_incentive_permission p JOIN sys_user u ON p.user_id = u.id " +
            "LEFT JOIN sys_org d ON u.office_id = d.id " +
            "LEFT JOIN sys_user_role ur ON u.id = ur.user_id " +
            "LEFT JOIN sys_role r ON ur.role_id = r.id ORDER BY p.id");
        return Result.ok(list);
    }

    @PreAuthorize("hasAuthority('system:config:edit')")
    @PostMapping("/grant")
    public Result<Void> grant(@RequestBody Map<String, Object> body) {
        // 【安全 2026-08-08】授予激励专项权限=授予"看全公司激励记录"，仅超管可操作
        // （防 system:config:edit 落到非超管角色后形成局部提权）
        if (!SecurityUtils.isAdmin()) throw new BusinessException("仅系统管理员可授予激励权限");
        int canViewAll = toInt(body.get("canViewAll"));
        int canViewShared = toInt(body.get("canViewShared")); // 查看已分享默认开(未传=1)
        int canEdit = toInt(body.get("canEdit"));
        int canDelete = toInt(body.get("canDelete"));
        // 批量授权：userIds 数组优先；兼容单用户 userId（向后兼容）
        java.util.List<Long> userIds = new java.util.ArrayList<>();
        Object uids = body.get("userIds");
        if (uids instanceof java.util.List && !((java.util.List<?>) uids).isEmpty()) {
            for (Object o : (java.util.List<?>) uids) {
                if (o != null) userIds.add(Long.valueOf(o.toString()));
            }
        } else if (body.get("userId") != null) {
            userIds.add(Long.valueOf(body.get("userId").toString()));
        }
        if (userIds.isEmpty()) throw new BusinessException("请选择要授权的人员");
        for (Long userId : userIds) {
            jdbc.update("INSERT INTO biz_incentive_permission (user_id, can_view_all, can_view_shared, can_edit, can_delete) VALUES (?,?,?,?,?) " +
                        "ON DUPLICATE KEY UPDATE can_view_all=?, can_view_shared=?, can_edit=?, can_delete=?",
                        userId, canViewAll, canViewShared, canEdit, canDelete, canViewAll, canViewShared, canEdit, canDelete);
        }
        return Result.ok();
    }

    private int toInt(Object v) {
        if (v instanceof Boolean) return (Boolean) v ? 1 : 0;
        if (v instanceof Number) return ((Number) v).intValue();
        return 1;
    }

    @PreAuthorize("hasAuthority('system:config:edit')")
    @DeleteMapping("/{id}")
    public Result<Void> revoke(@PathVariable Long id) {
        // 【安全 2026-08-08】撤销激励权限同 grant，仅超管可操作
        if (!SecurityUtils.isAdmin()) throw new BusinessException("仅系统管理员可撤销激励权限");
        jdbc.update("DELETE FROM biz_incentive_permission WHERE id=?", id);
        return Result.ok();
    }

    @GetMapping("/check")
    public Result<Map<String, Boolean>> check() {
        Long userId = SecurityUtils.getCurrentUserId();
        Map<String, Object> row = null;
        try {
            row = jdbc.queryForMap("SELECT can_view_all, can_view_shared, can_edit, can_delete FROM biz_incentive_permission WHERE user_id=?", userId);
        } catch (Exception e) {}
        Map<String, Boolean> result = new HashMap<>();
        result.put("canViewAll", row != null && ((Number)row.get("can_view_all")).intValue() == 1);
        result.put("canViewShared", row == null || ((Number)row.get("can_view_shared")).intValue() == 1);
        result.put("canEdit", row != null && ((Number)row.get("can_edit")).intValue() == 1);
        result.put("canDelete", row != null && ((Number)row.get("can_delete")).intValue() == 1);
        return Result.ok(result);
    }
}
