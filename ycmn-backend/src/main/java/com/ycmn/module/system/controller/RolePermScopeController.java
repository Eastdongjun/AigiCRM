package com.ycmn.module.system.controller;

import com.ycmn.common.Result;
import com.ycmn.module.system.service.RolePermScopeService;
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
        rolePermScopeService.updateRoleScopes(roleCode, configs);
        return Result.ok();
    }
}
