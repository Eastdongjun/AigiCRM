package com.ycmn.module.system.controller;
import org.springframework.security.access.prepost.PreAuthorize;

import com.ycmn.common.Result;
import com.ycmn.module.system.entity.SysMenu;
import com.ycmn.module.system.service.SysMenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/system/menus")
@RequiredArgsConstructor
public class SysMenuController {

    private final SysMenuService menuService;

    @PreAuthorize("hasAuthority('system:menu:view')")
    @GetMapping("/tree")
    public Result<List<SysMenu>> tree() {
        return Result.ok(menuService.tree());
    }

    @PreAuthorize("hasAuthority('system:menu:edit')")
    @PostMapping
    public Result<Void> create(@RequestBody SysMenu menu) {
        menuService.create(menu);
        return Result.ok();
    }

    @PreAuthorize("hasAuthority('system:menu:edit')")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody SysMenu menu) {
        menu.setId(id);
        menuService.update(menu);
        return Result.ok();
    }

    @PreAuthorize("hasAuthority('system:menu:edit')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        menuService.delete(id);
        return Result.ok();
    }
}
