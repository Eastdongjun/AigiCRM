package com.ycmn.module.system.controller;

import com.ycmn.common.Result;
import com.ycmn.module.system.entity.SysLegalEntity;
import com.ycmn.module.system.service.SysLegalEntityService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/system/legal-entities")
@RequiredArgsConstructor
public class SysLegalEntityController {

    private final SysLegalEntityService entityService;

    @GetMapping
    @PreAuthorize("hasAuthority('system:group:view')")
    public Result<List<SysLegalEntity>> list(@RequestParam(required = false) Long groupId) {
        if (groupId != null) {
            return Result.ok(entityService.listByGroupId(groupId));
        }
        return Result.ok(entityService.list());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:group:view')")
    public Result<SysLegalEntity> getById(@PathVariable Long id) {
        return Result.ok(entityService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:group:add')")
    public Result<Void> create(@RequestBody SysLegalEntity entity) {
        entityService.create(entity);
        return Result.ok();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:group:edit')")
    public Result<Void> update(@PathVariable Long id, @RequestBody SysLegalEntity entity) {
        entity.setId(id);
        entityService.update(entity);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:group:edit')")
    public Result<Void> delete(@PathVariable Long id) {
        entityService.delete(id);
        return Result.ok();
    }
}
