package com.ycmn.module.system.controller;

import com.ycmn.common.Result;
import com.ycmn.module.system.entity.SysRole;
import com.ycmn.module.system.service.SysRoleService;
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
        com.ycmn.module.system.entity.SysRole role = roleService.getById(id);
        if (role == null) {
            throw new com.ycmn.common.BusinessException("角色不存在");
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
