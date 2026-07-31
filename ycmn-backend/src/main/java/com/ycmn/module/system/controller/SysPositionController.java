package com.ycmn.module.system.controller;

import com.ycmn.common.Result;
import com.ycmn.module.system.entity.SysPermissionRule;
import com.ycmn.module.system.entity.SysPosition;
import com.ycmn.module.system.service.SysPositionService;
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

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:position:view')")
    public Result<SysPosition> getById(@PathVariable Long id) {
        return Result.ok(positionService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:position:add')")
    public Result<Void> create(@RequestBody SysPosition position) {
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
    @PreAuthorize("hasAuthority('system:position:edit')")
    public Result<Void> updateRules(@PathVariable Long positionId, @RequestBody Map<String, List<Long>> body) {
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
    @PreAuthorize("hasAuthority('system:user:edit')")
    public Result<Void> assignUserPositions(@PathVariable Long userId, @RequestBody Map<String, List<Long>> body) {
        positionService.assignUserPositions(userId, body.get("positionIds"));
        return Result.ok();
    }
}
