package com.ycmn.module.system.controller;

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

    @GetMapping("/tree")
    public Result<List<SysMenu>> tree() {
        return Result.ok(menuService.tree());
    }

    @PostMapping
    public Result<Void> create(@RequestBody SysMenu menu) {
        menuService.create(menu);
        return Result.ok();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody SysMenu menu) {
        menu.setId(id);
        menuService.update(menu);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        menuService.delete(id);
        return Result.ok();
    }
}
