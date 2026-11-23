package com.aigicrm.module.fabushi.controller;

import com.aigicrm.common.Result;
import com.aigicrm.module.fabushi.service.FabushiActivityService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 【D22 2026-11-22 裁决】共读活动（组队读书）双端入口已下线，本控制器**冻结不再投入**。
 * 保留接口仅为：存量活动数据可查、旧客户端请求不 500、避免权限中心/矩阵联动改动。
 * 不新增能力、不修 UI；表与权限码保留（删表需单独审批）。
 *
 * @deprecated D22：组队读书已下线，请勿在新功能中依赖本控制器。
 */
@Deprecated(since = "2026-11-22", forRemoval = false)
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
