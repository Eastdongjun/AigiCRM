package com.aigicrm.module.fabushi.controller;

import com.aigicrm.common.Result;
import com.aigicrm.module.fabushi.service.FabushiActivityService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/fabushi/activities")
@RequiredArgsConstructor
public class FabushiActivityController {
    private final FabushiActivityService activityService;

    @GetMapping
    @PreAuthorize("hasAuthority('fabushi:view')")
    public Result<Map<String, Object>> list() { return Result.ok(activityService.list()); }

    @PostMapping("/{id}/join")
    @PreAuthorize("hasAuthority('fabushi:view')")
    public Result<Void> join(@PathVariable Long id) { activityService.join(id); return Result.ok(); }

    @PostMapping
    @PreAuthorize("hasAuthority('fabushi:activity:manage')")
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) { return Result.ok(activityService.create(body)); }

    @PutMapping("/{id}/end")
    @PreAuthorize("hasAuthority('fabushi:activity:manage')")
    public Result<Void> end(@PathVariable Long id) { activityService.end(id); return Result.ok(); }
}
