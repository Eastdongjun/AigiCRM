package com.ycmn.module.system.controller;

import com.ycmn.common.Result;
import com.ycmn.module.system.entity.SysDept;
import com.ycmn.module.system.service.SysDeptService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/system/depts")
@RequiredArgsConstructor
public class SysDeptController {

    private final SysDeptService deptService;

    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('system:dept:view')")
    public Result<List<SysDept>> tree() {
        return Result.ok(deptService.tree());
    }

    @GetMapping
    @PreAuthorize("hasAuthority('system:dept:view')")
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
}
