package com.ycmn.module.incentive.controller;

import com.ycmn.common.Result;
import com.ycmn.common.BusinessException;
import com.ycmn.security.PermissionCacheService;
import com.ycmn.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 激励权限面板（2026-08-14 RBAC 收敛）：
 * 由"用户直绑 biz_incentive_permission"改为"角色绑定"——canViewAll → 绑定「激励-全量查看」角色，
 * canViewShared → 绑定「激励-分享可见」角色（role_code 由 M1 迁移 SQL 插入，本类不负责建角色）。
 * 授权/撤销后 evict 权限缓存（PermissionCacheService）保证即时生效。
 */
@RestController
@RequestMapping("/api/incentive/permission")
@RequiredArgsConstructor
public class IncentivePermissionController {

    private static final String ROLE_CODE_VIEW_ALL = "incentive_view_all";
    private static final String ROLE_CODE_VIEW_SHARED = "incentive_view_shared";

    private final JdbcTemplate jdbc;
    private final PermissionCacheService permissionCacheService;

    @PreAuthorize("hasAuthority('system:config:view')")
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list() {
        // 【2026-08-14 RBAC 收敛】面板列表 = 绑定「激励-全量查看」/「激励-分享可见」角色的用户。
        // biz_incentive_permission 已废弃，不再有记录主键；id 即 userId（旧前端 /revoke 传 id 兼容为 userId）。
        List<Map<String, Object>> list = jdbc.queryForList(
            "SELECT u.id AS id, u.id AS userId, u.username, u.real_name AS realName, " +
            "MAX(CASE WHEN r.role_code = ? THEN 1 ELSE 0 END) AS canViewAll, " +
            "MAX(CASE WHEN r.role_code = ? THEN 1 ELSE 0 END) AS canViewShared " +
            "FROM sys_user u " +
            "JOIN sys_user_role ur ON ur.user_id = u.id " +
            "JOIN sys_role r ON r.id = ur.role_id " +
            "WHERE r.role_code IN (?,?) " +
            "GROUP BY u.id, u.username, u.real_name ORDER BY u.id",
            ROLE_CODE_VIEW_ALL, ROLE_CODE_VIEW_SHARED, ROLE_CODE_VIEW_ALL, ROLE_CODE_VIEW_SHARED);
        return Result.ok(list);
    }

    @PreAuthorize("hasAuthority('system:config:edit')")
    @PostMapping("/grant")
    public Result<Void> grant(@RequestBody Map<String, Object> body) {
        // 【安全 2026-08-08】授予激励权限=授予"看全公司激励记录"能力，仅超管可操作
        // （防 system:config:edit 落到非超管角色后形成局部提权）
        if (!SecurityUtils.isAdmin()) throw new BusinessException("仅系统管理员可授予激励权限");
        // 【2026-08-14 RBAC 收敛】勾选映射为角色绑定：canViewAll=1 → 绑「激励-全量查看」；canViewShared=1 → 绑「激励-分享可见」
        Long viewAllRoleId = roleIdByCode(ROLE_CODE_VIEW_ALL);
        Long viewSharedRoleId = roleIdByCode(ROLE_CODE_VIEW_SHARED);
        int canViewAll = toInt(body.get("canViewAll"));
        int canViewShared = toInt(body.get("canViewShared")); // 查看已分享默认开(未传=1)
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
            bindRole(userId, viewAllRoleId, canViewAll == 1);
            bindRole(userId, viewSharedRoleId, canViewShared == 1);
            permissionCacheService.evict(userId);
            // 【2026-08-14 缓存自愈】与 evict 双保险：Redis 写失败时版本比对自愈
            jdbc.update("UPDATE sys_user SET perm_version = perm_version + 1 WHERE id=?", userId);
        }
        return Result.ok();
    }

    /** 绑定/解绑单个角色（sys_user_role 有 UNIQUE(user_id,role_id)，重复绑定幂等） */
    private void bindRole(Long userId, Long roleId, boolean bind) {
        if (bind) {
            jdbc.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (?,?) " +
                        "ON DUPLICATE KEY UPDATE role_id=role_id", userId, roleId);
        } else {
            jdbc.update("DELETE FROM sys_user_role WHERE user_id=? AND role_id=?", userId, roleId);
        }
    }

    /** 按 role_code 查角色 id；角色未初始化（M1 迁移未执行）时抛业务异常 */
    private Long roleIdByCode(String roleCode) {
        try {
            return jdbc.queryForObject("SELECT id FROM sys_role WHERE role_code=? LIMIT 1", Long.class, roleCode);
        } catch (EmptyResultDataAccessException e) {
            throw new BusinessException("角色未初始化，请先执行 M1 迁移");
        }
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
        // 【2026-08-14 RBAC 收敛】撤销 = 仅解绑「激励-全量查看」，保留全员默认的「激励-分享可见」——
        // 与前端文案一致："回到基础可见范围：只能看到已分享的记录与本人上传内容"（评审 P1-3 修复）
        // 旧前端传 id=biz_incentive_permission 记录主键，现在无记录表，直接当 userId 用
        Long userId = id;
        Long viewAllRoleId = roleIdByCode(ROLE_CODE_VIEW_ALL);
        jdbc.update("DELETE FROM sys_user_role WHERE user_id=? AND role_id=?", userId, viewAllRoleId);
        permissionCacheService.evict(userId);
        // 【2026-08-14 缓存自愈】与 evict 双保险
        jdbc.update("UPDATE sys_user SET perm_version = perm_version + 1 WHERE id=?", userId);
        return Result.ok();
    }

    @GetMapping("/check")
    public Result<Map<String, Boolean>> check() {
        // 【2026-08-14 RBAC 收敛】与 IncentiveService 判定保持一致，全部走权限码，不再直查 biz_incentive_permission
        Map<String, Boolean> result = new HashMap<>();
        result.put("canViewAll", SecurityUtils.isAdmin() || SecurityUtils.hasAuthority("incentive:view-all"));
        result.put("canViewShared", SecurityUtils.hasAuthority("incentive:view-shared"));
        result.put("canEdit", SecurityUtils.isAdmin() || SecurityUtils.hasAuthority("incentive:manage"));
        result.put("canDelete", SecurityUtils.isAdmin() || SecurityUtils.hasAuthority("incentive:manage"));
        return Result.ok(result);
    }
}
