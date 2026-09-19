package com.aigicrm.module.system.controller;
import org.springframework.security.access.prepost.PreAuthorize;

import com.aigicrm.common.Result;
import com.aigicrm.module.system.entity.SysMenu;
import com.aigicrm.module.system.service.SysMenuService;
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
