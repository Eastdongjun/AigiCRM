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

    @PreAuthorize("hasAuthority('system:config:view')")
    @GetMapping("/{roleCode}")
    public Result<List<Map<String, Object>>> getScopes(@PathVariable String roleCode) {
        return Result.ok(rolePermScopeService.getRoleScopes(roleCode));
    }

    @PreAuthorize("hasAuthority('system:config:edit')")
    @PutMapping("/{roleCode}")
    public Result<Void> updateScopes(@PathVariable String roleCode,
                                     @RequestBody List<Map<String, Object>> configs) {
        requireAdmin();
        rolePermScopeService.updateRoleScopes(roleCode, configs);
        return Result.ok();
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
    public Result<Void> updateAssigneeScopes(@PathVariable String type, @PathVariable String key,
                                             @RequestBody List<Map<String, Object>> configs) {
        requireAdmin();
        if (!com.aigicrm.common.scope.ScopeConfig.ASSIGNEE_POSITION.equals(type)
                && !com.aigicrm.common.scope.ScopeConfig.ASSIGNEE_USER.equals(type)) {
            throw new BusinessException("仅支持岗位(POSITION)/用户(USER)级配置, 角色级请走 /{roleCode}");
        }
        rolePermScopeService.updateScopes(type, key, configs);
        return Result.ok();
    }

    private void requireAdmin() {
        // 【修复 2026-08-10 P1-03】范围配置=数据权限放大,必须超管本人才能写——
        // 否则 system:config:edit 落到非超管角色时可将自己角色 customer edit/delete 配成 all 提权。
        if (!SecurityUtils.isAdmin()) {
            throw new BusinessException("仅系统管理员可配置数据范围");
        }
    }
}
