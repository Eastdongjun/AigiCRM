package com.ycmn.module.system.controller;

import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ycmn.common.Result;
import com.ycmn.module.system.entity.SysPermissionRule;
import com.ycmn.module.system.entity.SysRole;
import com.ycmn.module.system.service.SysRoleService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/system/roles")
@RequiredArgsConstructor
public class SysRoleController {

    private final SysRoleService roleService;

    // 【2026-09-02 权限对齐】人员管理页 onMounted 无条件加载角色下拉(loadRoles)，行政等
    // 持 system:user:view 但无 system:role:view 的账号进页即 403 弹「没有操作权限」。
    // 角色列表是"能看人员管理者必能选角色"的基础参考数据，与部门树 isAuthenticated 同思路，
    // 放宽为 role:view 或 user:view；角色增删改仍保持 role:add/edit/delete 不变。
    @GetMapping
    @PreAuthorize("hasAnyAuthority('system:role:view','system:user:view')")
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

    // ==================== 角色-权限规则绑定（规则引擎接通 2026-08-27）====================

    @GetMapping("/{roleId}/rules")
    @PreAuthorize("hasAuthority('system:role:view')")
    public Result<List<SysPermissionRule>> getRules(@PathVariable Long roleId) {
        return Result.ok(roleService.getRules(roleId));
    }

    @PutMapping("/{roleId}/rules")
    @PreAuthorize("hasAuthority('system:role:edit')")
    public Result<Void> updateRules(@PathVariable Long roleId, @RequestBody Map<String, List<Long>> body) {
        roleService.saveRules(roleId, body.get("ruleIds"));
        return Result.ok();
    }
}
