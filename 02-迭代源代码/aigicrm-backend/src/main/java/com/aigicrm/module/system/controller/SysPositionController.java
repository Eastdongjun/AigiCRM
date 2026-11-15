package com.aigicrm.module.system.controller;

import com.aigicrm.common.BusinessException;
import com.aigicrm.common.Result;
import com.aigicrm.module.system.entity.SysPermissionRule;
import com.aigicrm.module.system.entity.SysPosition;
import com.aigicrm.module.system.service.SysPositionService;
import com.aigicrm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system/positions")
@RequiredArgsConstructor
public class SysPositionController {

    private final SysPositionService positionService;

    @GetMapping
    @PreAuthorize("hasAuthority('system:position:view')")
    public Result<List<SysPosition>> list() {
        return Result.ok(positionService.list());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:position:add')")
    public Result<Void> create(@RequestBody SysPosition position) {
        // 【六层验收 2026-11-08】空body 500 修复：空对象缺名/编码直达 DB NOT NULL 报 500，前置 400
        if (position == null || position.getName() == null || position.getName().isBlank()) {
            throw new BusinessException(400, "岗位名称不能为空");
        }
        if (position.getCode() == null || position.getCode().isBlank()) {
            throw new BusinessException(400, "岗位编码不能为空");
        }
        positionService.create(position);
        return Result.ok();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:position:edit')")
    public Result<Void> update(@PathVariable Long id, @RequestBody SysPosition position) {
        position.setId(id);
        positionService.update(position);
        return Result.ok();
    }

    @PreAuthorize("hasAuthority('system:position:delete')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        positionService.delete(id);
        return Result.ok();
    }

    // ==================== 岗位-规则绑定 ====================

    @GetMapping("/{positionId}/rules")
    @PreAuthorize("hasAuthority('system:position:view')")
    public Result<List<SysPermissionRule>> getRules(@PathVariable Long positionId) {
        return Result.ok(positionService.getRules(positionId));
    }

    @PutMapping("/{positionId}/rules")
    @PreAuthorize("hasAuthority('system:position:rule:edit')")
    public Result<Void> updateRules(@PathVariable Long positionId, @RequestBody Map<String, List<Long>> body) {
        // 岗位规则=权限定义本身；由 system:position:rule:edit 显式授权，不再用 isAdmin 隐式放行。
        positionService.saveRules(positionId, body.get("ruleIds"));
        return Result.ok();
    }

    // ==================== 用户-岗位分配 ====================

    @GetMapping("/users/{userId}")
    @PreAuthorize("hasAnyAuthority('system:position:view','system:user:view')")
    public Result<List<SysPosition>> getUserPositions(@PathVariable Long userId) {
        return Result.ok(positionService.getUserPositions(userId));
    }

    @PutMapping("/users/{userId}")
    @PreAuthorize("hasAnyAuthority('system:user:edit','system:user:assign')")
    public Result<Void> assignUserPositions(@PathVariable Long userId, @RequestBody Map<String, List<Long>> body) {
        // 【安全 2026-08-08 提权防护】岗位携带权限规则视为特权：仅超管可指派。
        // 防"先给岗位配宽松规则、再指派给自己"的提权路径（规则在登录时并入 RuleResult 生效）。
        List<Long> positionIds = body.get("positionIds");
        if (positionIds != null && !positionIds.isEmpty() && !SecurityUtils.hasAuthority("system:user:assign")) {
            boolean hasRulePosition = positionIds.stream()
                    .anyMatch(id -> !positionService.getRules(id).isEmpty());
            if (hasRulePosition) throw new BusinessException(403, "无权指派携带权限规则的岗位");
        }
        positionService.assignUserPositions(userId, positionIds);
        return Result.ok();
    }
}
