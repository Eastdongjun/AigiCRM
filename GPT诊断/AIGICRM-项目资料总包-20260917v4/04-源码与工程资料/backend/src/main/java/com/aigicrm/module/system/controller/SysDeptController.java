package com.aigicrm.module.system.controller;

import com.aigicrm.common.Result;
import com.aigicrm.module.system.entity.SysDept;
import com.aigicrm.module.system.service.OrgAdminService;
import com.aigicrm.module.system.service.SysDeptService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system/depts")
@RequiredArgsConstructor
public class SysDeptController {

    private final SysDeptService deptService;
    private final OrgAdminService orgAdminService;

    // 【修复 FEAT-20260804-DICT-DEPT-READ】list/tree 是给普通业务表单(新增客户等页面)读
    // 部门树(大区/办事处下拉)用的，此前要求 system:dept:view——"系统管理"模块权限，销售员/行政等
    // 非管理岗角色未勾选时打开新增客户页即 403 弹"没有访问权限"。部门树是基础参考数据，与字典
    // (system:dict) 同口径，改为仅要求登录态；部门增删改仍保持 system:dept:edit 管理员权限不变。

    @GetMapping("/tree")
    @PreAuthorize("isAuthenticated()")
    public Result<List<SysDept>> tree() {
        return Result.ok(deptService.tree());
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Result<List<SysDept>> list() {
        return Result.ok(deptService.list());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:dept:edit')")
    public Result<Void> create(@RequestBody SysDept dept) {
        deptService.create(dept);
        return Result.ok();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:dept:edit')")
    public Result<Void> update(@PathVariable Long id, @RequestBody SysDept dept) {
        dept.setId(id);
        deptService.update(dept);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:dept:edit')")
    public Result<Void> delete(@PathVariable Long id) {
        deptService.delete(id);
        return Result.ok();
    }

    // ===== 组织管理员（原 /api/org/{orgId}/admins 迁入，SysOrgController 已删除） =====

    @GetMapping("/{deptId}/admins")
    @PreAuthorize("hasAuthority('system:dept:view')")
    public Result<List<Map<String, Object>>> getAdmins(@PathVariable Long deptId) {
        return Result.ok(orgAdminService.getOrgAdmins(deptId));
    }

    @PostMapping("/{deptId}/admins")
    @PreAuthorize("hasAuthority('system:dept:edit')")
    public Result<Void> addAdmin(@PathVariable Long deptId, @RequestParam Long userId) {
        orgAdminService.addOrgAdmin(deptId, userId);
        return Result.ok();
    }

    @DeleteMapping("/{deptId}/admins/{id}")
    @PreAuthorize("hasAuthority('system:dept:edit')")
    public Result<Void> removeAdmin(@PathVariable Long deptId, @PathVariable Long id) {
        orgAdminService.removeOrgAdmin(id);
        return Result.ok();
    }
}
