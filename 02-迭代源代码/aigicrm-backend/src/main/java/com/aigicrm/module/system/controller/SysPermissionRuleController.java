package com.aigicrm.module.system.controller;

import com.aigicrm.common.BusinessException;
import com.aigicrm.common.Result;
import com.aigicrm.module.system.entity.SysPermissionRule;
import com.aigicrm.module.system.service.SysPermissionRuleService;
import com.aigicrm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/system/rules")
@RequiredArgsConstructor
public class SysPermissionRuleController {

    private final SysPermissionRuleService ruleService;

    /**
     * 【11-08 修复清单6】权限规则写操作统一平台管理员闸门(参照 RecycleController 同款写法);
     * 与既有 system:rule:* 权限码叠加, 不放宽——权限规则影响全员数据范围口径, 非 admin 不得写。
     */
    private void requireAdmin() {
        if (!SecurityUtils.isAdmin()) {
            throw new BusinessException(403, "仅平台管理员可修改权限规则");
        }
    }

    @GetMapping
    @PreAuthorize("hasAuthority('system:rule:view')")
    public Result<List<SysPermissionRule>> list() {
        return Result.ok(ruleService.list());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:rule:add')")
    public Result<Void> create(@RequestBody SysPermissionRule rule) {
        // 【六层验收 2026-11-08】空body 500 修复：空对象缺名/编码/实体/类型直达 DB NOT NULL 报 500，前置 400
        if (rule == null || rule.getName() == null || rule.getName().isBlank()) {
            throw new BusinessException(400, "规则名称不能为空");
        }
        if (rule.getCode() == null || rule.getCode().isBlank()) {
            throw new BusinessException(400, "规则编码不能为空");
        }
        if (rule.getTargetEntity() == null || rule.getTargetEntity().isBlank()) {
            throw new BusinessException(400, "目标实体不能为空");
        }
        if (rule.getRuleType() == null || rule.getRuleType().isBlank()) {
            throw new BusinessException(400, "规则类型不能为空");
        }
        requireAdmin();
        ruleService.create(rule);
        return Result.ok();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:rule:edit')")
    public Result<Void> update(@PathVariable Long id, @RequestBody SysPermissionRule rule) {
        requireAdmin();
        rule.setId(id);
        ruleService.update(rule);
        return Result.ok();
    }

    @PreAuthorize("hasAuthority('system:rule:delete')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        requireAdmin();
        ruleService.delete(id);
        return Result.ok();
    }
}
