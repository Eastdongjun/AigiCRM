package com.aigicrm.module.system.controller;

import com.aigicrm.common.Result;
import com.aigicrm.module.system.entity.SysGroup;
import com.aigicrm.module.system.service.SysGroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/system/groups")
@RequiredArgsConstructor
public class SysGroupController {

    private final SysGroupService groupService;

    @GetMapping
    @PreAuthorize("hasAuthority('system:group:view')")
    public Result<List<SysGroup>> list() {
        return Result.ok(groupService.list());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:group:add')")
    public Result<Void> create(@RequestBody SysGroup group) {
        // 【六层验收 2026-11-08】空body 500 修复：空对象缺名/编码直达 DB NOT NULL 报 500，前置 400
        if (group == null || group.getGroupName() == null || group.getGroupName().isBlank()) {
            throw new com.aigicrm.common.BusinessException(400, "集团名称不能为空");
        }
        if (group.getGroupCode() == null || group.getGroupCode().isBlank()) {
            throw new com.aigicrm.common.BusinessException(400, "集团编码不能为空");
        }
        groupService.create(group);
        return Result.ok();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:group:edit')")
    public Result<Void> update(@PathVariable Long id, @RequestBody SysGroup group) {
        group.setId(id);
        groupService.update(group);
        return Result.ok();
    }

    @PreAuthorize("hasAuthority('system:group:delete')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        groupService.delete(id);
        return Result.ok();
    }
}
