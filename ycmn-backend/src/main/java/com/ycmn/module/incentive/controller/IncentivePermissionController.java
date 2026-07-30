package com.ycmn.module.incentive.controller;

import com.ycmn.common.Result;
import com.ycmn.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/incentive/permission")
@RequiredArgsConstructor
public class IncentivePermissionController {

    private final JdbcTemplate jdbc;

    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list() {
        List<Map<String, Object>> list = jdbc.queryForList(
            "SELECT p.id, p.user_id, p.can_edit, p.can_delete, u.real_name AS realName, u.username, " +
            "COALESCE(d.dept_name,'—') AS officeName, COALESCE(r.role_name,'—') AS roleName " +
            "FROM biz_incentive_permission p JOIN sys_user u ON p.user_id = u.id " +
            "LEFT JOIN sys_dept d ON u.office_id = d.id " +
            "LEFT JOIN sys_user_role ur ON u.id = ur.user_id " +
            "LEFT JOIN sys_role r ON ur.role_id = r.id ORDER BY p.id");
        return Result.ok(list);
    }

    @PostMapping("/grant")
    public Result<Void> grant(@RequestBody Map<String, Object> body) {
        Long userId = Long.valueOf(body.get("userId").toString());
        int canEdit = toInt(body.get("canEdit"));
        int canDelete = toInt(body.get("canDelete"));
        jdbc.update("INSERT INTO biz_incentive_permission (user_id, can_edit, can_delete) VALUES (?,?,?) " +
                    "ON DUPLICATE KEY UPDATE can_edit=?, can_delete=?",
                    userId, canEdit, canDelete, canEdit, canDelete);
        return Result.ok();
    }

    private int toInt(Object v) {
        if (v instanceof Boolean) return (Boolean) v ? 1 : 0;
        if (v instanceof Number) return ((Number) v).intValue();
        return 1;
    }

    @DeleteMapping("/{id}")
    public Result<Void> revoke(@PathVariable Long id) {
        jdbc.update("DELETE FROM biz_incentive_permission WHERE id=?", id);
        return Result.ok();
    }

    @GetMapping("/check")
    public Result<Map<String, Boolean>> check() {
        Long userId = SecurityUtils.getCurrentUserId();
        Map<String, Object> row = null;
        try {
            row = jdbc.queryForMap("SELECT can_edit, can_delete FROM biz_incentive_permission WHERE user_id=?", userId);
        } catch (Exception e) {}
        Map<String, Boolean> result = new HashMap<>();
        result.put("canEdit", row != null && ((Number)row.get("can_edit")).intValue() == 1);
        result.put("canDelete", row != null && ((Number)row.get("can_delete")).intValue() == 1);
        return Result.ok(result);
    }
}
