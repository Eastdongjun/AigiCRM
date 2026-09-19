package com.aigicrm.module.system.controller;

import com.aigicrm.common.Result;
import com.aigicrm.module.system.entity.SysPermissionRule;
import com.aigicrm.module.system.service.SysPermissionRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/system/rules")
@RequiredArgsConstructor
public class SysPermissionRuleController {

    private final SysPermissionRuleService ruleService;

    @GetMapping
    @PreAuthorize("hasAuthority('system:rule:view')")
    public Result<List<SysPermissionRule>> list() {
        return Result.ok(ruleService.list());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:rule:add')")
    public Result<Void> create(@RequestBody SysPermissionRule rule) {
        ruleService.create(rule);
        return Result.ok();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:rule:edit')")
    public Result<Void> update(@PathVariable Long id, @RequestBody SysPermissionRule rule) {
        rule.setId(id);
        ruleService.update(rule);
        return Result.ok();
    }

    @PreAuthorize("hasAuthority('system:rule:delete')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        ruleService.delete(id);
        return Result.ok();
    }
}
