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
}
