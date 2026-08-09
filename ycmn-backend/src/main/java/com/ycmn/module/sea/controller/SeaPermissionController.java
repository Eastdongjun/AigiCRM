package com.ycmn.module.sea.controller;

import com.ycmn.common.BusinessException;
import com.ycmn.common.Result;
import com.ycmn.security.SecurityUtils;
import com.ycmn.module.sea.service.SeaPermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 公海专项权限（2026-08-10 按组织架构层级勾选）。
 * view/edit/delete/claim 四项独立，每项授权给组织节点集+人员集。
 */
@RestController
@RequestMapping("/api/sea/permission")
@RequiredArgsConstructor
public class SeaPermissionController {

    private static final List<String> PERM_TYPES = List.of("view", "edit", "delete", "claim");

    private final JdbcTemplate jdbc;
    private final SeaPermissionService seaPermissionService;

    /** 权限配置列表（四行 + 已选组织/人员名） */
    @PreAuthorize("hasAuthority('system:config:view')")
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (String type : PERM_TYPES) {
            Map<String, Object> m = new HashMap<>();
            m.put("permType", type);
            m.put("label", label(type));
            try {
                Map<String, Object> row = jdbc.queryForMap(
                        "SELECT dept_ids, user_ids FROM biz_sea_permission WHERE perm_type=?", type);
                String deptIds = row.get("dept_ids") != null ? String.valueOf(row.get("dept_ids")) : "";
                String userIds = row.get("user_ids") != null ? String.valueOf(row.get("user_ids")) : "";
                m.put("deptIds", deptIds);
                m.put("userIds", userIds);
                m.put("deptNames", resolveDeptNames(deptIds));
                m.put("userNames", resolveUserNames(userIds));
            } catch (Exception e) {
                m.put("deptIds", "");
                m.put("userIds", "");
                m.put("deptNames", "");
                m.put("userNames", "");
            }
            list.add(m);
        }
        return Result.ok(list);
    }

    /** 授权：给某 permType 勾选组织节点集 + 人员集（全量覆盖该类型） */
    @PreAuthorize("hasAuthority('system:config:edit')")
    @PostMapping("/grant")
    public Result<Void> grant(@RequestBody Map<String, Object> body) {
        // 【安全】授予公海权限=授予公海可见/操作，仅超管可操作（防非超管持 system:config:edit 局部提权）
        if (!SecurityUtils.isAdmin()) throw new BusinessException("仅系统管理员可配置公海权限");
        String permType = str(body.get("permType"));
        if (!PERM_TYPES.contains(permType)) throw new BusinessException("无效的权限类型: " + permType);
        String deptIds = joinIds(body.get("deptIds"));
        String userIds = joinIds(body.get("userIds"));
        jdbc.update("INSERT INTO biz_sea_permission (perm_type, dept_ids, user_ids) VALUES (?,?,?) " +
                    "ON DUPLICATE KEY UPDATE dept_ids=VALUES(dept_ids), user_ids=VALUES(user_ids)",
                permType, deptIds, userIds);
        return Result.ok();
    }

    /** 清空某权限类型配置（回收全部授权） */
    @PreAuthorize("hasAuthority('system:config:edit')")
    @DeleteMapping("/{permType}")
    public Result<Void> revoke(@PathVariable String permType) {
        if (!SecurityUtils.isAdmin()) throw new BusinessException("仅系统管理员可配置公海权限");
        if (!PERM_TYPES.contains(permType)) throw new BusinessException("无效的权限类型: " + permType);
        jdbc.update("UPDATE biz_sea_permission SET dept_ids=NULL, user_ids=NULL WHERE perm_type=?", permType);
        return Result.ok();
    }

    /** 当前用户公海权限（登录即可，供前端按钮/页面判定） */
    @GetMapping("/check")
    public Result<Map<String, Boolean>> check() {
        Long userId = SecurityUtils.getCurrentUserId();
        Map<String, Boolean> r = new HashMap<>();
        r.put("canView", seaPermissionService.hasPerm("view", userId));
        r.put("canEdit", seaPermissionService.hasPerm("edit", userId));
        r.put("canDelete", seaPermissionService.hasPerm("delete", userId));
        r.put("canClaim", seaPermissionService.hasPerm("claim", userId));
        return Result.ok(r);
    }

    private String label(String type) {
        return switch (type) {
            case "view" -> "查看公海";
            case "edit" -> "编辑公海";
            case "delete" -> "删除公海";
            case "claim" -> "捞取公海";
            default -> type;
        };
    }

    private String resolveDeptNames(String deptIdsCsv) {
        if (deptIdsCsv == null || deptIdsCsv.isBlank()) return "";
        List<String> names = new ArrayList<>();
        for (String id : deptIdsCsv.split(",")) {
            if (id.isBlank()) continue;
            try {
                var dept = jdbc.queryForMap("SELECT dept_name FROM sys_dept WHERE id=?", Long.parseLong(id.trim()));
                names.add(String.valueOf(dept.get("dept_name")));
            } catch (Exception ignored) {}
        }
        return String.join(", ", names);
    }

    private String resolveUserNames(String userIdsCsv) {
        if (userIdsCsv == null || userIdsCsv.isBlank()) return "";
        List<String> names = new ArrayList<>();
        for (String id : userIdsCsv.split(",")) {
            if (id.isBlank()) continue;
            try {
                var u = jdbc.queryForMap("SELECT real_name FROM sys_user WHERE id=?", Long.parseLong(id.trim()));
                names.add(String.valueOf(u.get("real_name")));
            } catch (Exception ignored) {}
        }
        return String.join(", ", names);
    }

    private String joinIds(Object v) {
        if (v == null) return null;
        if (v instanceof List<?> list) {
            List<String> ids = new ArrayList<>();
            for (Object o : list) if (o != null) ids.add(o.toString());
            return ids.isEmpty() ? null : String.join(",", ids);
        }
        return String.valueOf(v);
    }

    private String str(Object v) {
        return v != null ? String.valueOf(v) : null;
    }
}
