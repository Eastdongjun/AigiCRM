package com.aigicrm.module.system.controller;

import com.aigicrm.common.Result;
import com.aigicrm.module.system.entity.SysRole;
import com.aigicrm.module.system.service.SysRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/system/roles")
@RequiredArgsConstructor
public class SysRoleController {

    private final SysRoleService roleService;

    @GetMapping
    @PreAuthorize("hasAuthority('system:role:view')")
    public Result<List<SysRole>> list() {
        return Result.ok(roleService.list());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:role:view')")
    public Result<SysRole> getById(@PathVariable Long id) {
        return Result.ok(roleService.getById(id));
    }

    @GetMapping("/{id}/orgs")
    @PreAuthorize("hasAuthority('system:role:view')")
    public Result<List<Long>> getOrgs(@PathVariable Long id) {
        com.aigicrm.module.system.entity.SysRole role = roleService.getById(id);
        if (role == null) {
            throw new com.aigicrm.common.BusinessException("角色不存在");
        }
        return Result.ok(role.getOrgIds());
    }

    @PutMapping("/{id}/orgs")
    @PreAuthorize("hasAuthority('system:role:edit')")
    public Result<Void> updateOrgs(@PathVariable Long id, @RequestBody List<Long> orgIds) {
        roleService.updateOrgs(id, orgIds);
        return Result.ok();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:role:add')")
    public Result<Void> create(@RequestBody SysRole role) {
        // 【六层验收 2026-11-08】空body 500 修复：空对象缺名/编码直达 DB NOT NULL 报 500，前置 400
        if (role == null || role.getRoleName() == null || role.getRoleName().isBlank()) {
            throw new com.aigicrm.common.BusinessException(400, "角色名称不能为空");
        }
        if (role.getRoleCode() == null || role.getRoleCode().isBlank()) {
            throw new com.aigicrm.common.BusinessException(400, "角色编码不能为空");
        }
        roleService.create(role);
        return Result.ok();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:role:edit')")
    public Result<Void> update(@PathVariable Long id, @RequestBody SysRole role) {
        role.setId(id);
        roleService.update(role);
        return Result.ok();
    }

    @PreAuthorize("hasAuthority('system:role:delete')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return Result.ok();
    }
}
