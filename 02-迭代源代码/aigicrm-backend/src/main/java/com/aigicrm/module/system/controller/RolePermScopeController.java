package com.aigicrm.module.system.controller;

import com.aigicrm.common.BusinessException;
import com.aigicrm.common.Result;
import com.aigicrm.module.system.service.RolePermScopeService;
import com.aigicrm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 角色-模块-动作 数据范围配置(权限体系重构 2026-08-10)。
 * 每个权限 = 功能(谁能做, 走角色菜单矩阵) × 范围(对谁的数据, 走本接口)。
 */
@RestController
@RequestMapping("/api/system/perm-scopes")
@RequiredArgsConstructor
public class RolePermScopeController {

    private final RolePermScopeService rolePermScopeService;
    /** 【WP5】保存回执(epoch/受影响人数) */
    private final com.aigicrm.common.auth.PermEpochService permEpochService;
    /** 【WP8】双轨对账 */
    private final com.aigicrm.module.system.service.PermReconcileService reconcileService;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    @PreAuthorize("hasAuthority('system:config:view')")
    @GetMapping("/{roleCode}")
    public Result<List<Map<String, Object>>> getScopes(@PathVariable String roleCode) {
        return Result.ok(rolePermScopeService.getRoleScopes(roleCode));
    }

    @PreAuthorize("hasAuthority('system:config:edit')")
    @PutMapping("/{roleCode}")
    public Result<java.util.Map<String, Object>> updateScopes(@PathVariable String roleCode,
                                     @RequestBody List<Map<String, Object>> configs) {
        requireAdmin();
        rolePermScopeService.updateRoleScopes(roleCode, configs);
        // 【WP5 2026-11-13】保存授权返回 {epoch, affectedUsers}(规格 WP5; 前端可提示生效人数)
        return Result.ok(saveAck());
    }

    // ==================== 【批③ 正交配置层】主体三级通用端点 ====================

    /** 岗位级范围配置(assigneeType=POSITION, key=岗位ID) */
    @PreAuthorize("hasAuthority('system:config:view')")
    @GetMapping("/assignee/{type}/{key}")
    public Result<List<Map<String, Object>>> getAssigneeScopes(@PathVariable String type, @PathVariable String key) {
        return Result.ok(rolePermScopeService.getAssigneeScopes(type, key));
    }

    /** 岗位级/用户级范围配置保存(assigneeType=POSITION/USER; 角色级走 /{roleCode}) */
    @PreAuthorize("hasAuthority('system:config:edit')")
    @PutMapping("/assignee/{type}/{key}")
    public Result<java.util.Map<String, Object>> updateAssigneeScopes(@PathVariable String type, @PathVariable String key,
                                             @RequestBody List<Map<String, Object>> configs) {
        requireAdmin();
        if (!com.aigicrm.common.scope.ScopeConfig.ASSIGNEE_POSITION.equals(type)
                && !com.aigicrm.common.scope.ScopeConfig.ASSIGNEE_USER.equals(type)) {
            throw new BusinessException("仅支持岗位(POSITION)/用户(USER)级配置, 角色级请走 /{roleCode}");
        }
        rolePermScopeService.updateScopes(type, key, configs);
        return Result.ok(saveAck());
    }

    /** 【WP6 2026-11-13】影响分析(规格 WP6): 保存前计算受影响用户——主体绑定的在职用户数+角色清单 */
    @PreAuthorize("hasAuthority('system:config:view')")
    @GetMapping("/impact/{type}/{key}")
    public Result<java.util.Map<String, Object>> impact(@PathVariable String type, @PathVariable String key) {
        requireAdmin();
        long affected;
        String scopeDesc;
        switch (type) {
            case "ROLE" -> {
                Long cnt = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM sys_user_role ur JOIN sys_user u ON u.id = ur.user_id " +
                        "JOIN sys_role r ON r.id = ur.role_id WHERE r.role_code = ? AND u.deleted = 0 AND u.status = 1",
                        Long.class, key);
                affected = cnt != null ? cnt : 0;
                scopeDesc = "角色 " + key;
            }
            case "POSITION" -> {
                Long cnt = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM sys_user_position up JOIN sys_user u ON u.id = up.user_id " +
                        "WHERE up.position_id = ? AND u.deleted = 0 AND u.status = 1",
                        Long.class, Long.valueOf(key));
                affected = cnt != null ? cnt : 0;
                scopeDesc = "岗位 #" + key;
            }
            case "USER" -> {
                affected = 1;
                scopeDesc = "用户 #" + key;
            }
            default -> { throw new BusinessException("未知主体类型: " + type); }
        }
        return Result.ok(java.util.Map.of(
                "affectedUsers", affected,
                "scopeDesc", scopeDesc,
                "confirmThreshold", 20)); // 超阈值前端须二次确认(规格 WP6 影响分析)
    }

    /** 【WP8 对账 2026-11-13】抽样双轨对比(admin): 旧/新轨 (customer,view) 可见集 0 差异=切流门槛 */
    @PreAuthorize("hasAuthority('system:config:view')")
    @GetMapping("/reconcile")
    public Result<java.util.Map<String, Object>> reconcile(
            @RequestParam(defaultValue = "50") int sample,
            @RequestParam(required = false) Long userId) {
        requireAdmin();
        return Result.ok(reconcileService.reconcile(sample, userId));
    }

    /** 【WP5 2026-11-13】保存回执: 新版本号 + 受影响用户数(绑定该主体的在职用户) */
    private java.util.Map<String, Object> saveAck() {
        long epoch = permEpochService.current();
        Long affected = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_user u WHERE u.deleted = 0 AND u.status = 1 AND EXISTS (" +
                "SELECT 1 FROM sys_user_role ur JOIN sys_role r ON r.id = ur.role_id WHERE ur.user_id = u.id)",
                Long.class);
        return java.util.Map.of("epoch", epoch, "affectedUsers", affected != null ? affected : 0L);
    }

    private void requireAdmin() {
        // 【修复 2026-08-10 P1-03】范围配置=数据权限放大,必须超管本人才能写——
        // 否则 system:config:edit 落到非超管角色时可将自己角色 customer edit/delete 配成 all 提权。
        if (!SecurityUtils.isAdmin()) {
            throw new BusinessException("仅系统管理员可配置数据范围");
        }
    }
}
