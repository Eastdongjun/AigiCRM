package com.aigicrm.module.fabushi.controller;

import com.aigicrm.common.Result;
import com.aigicrm.module.fabushi.service.FabushiReadingService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** 二期阅读链：书架/进度/记录/提醒设置，全部本人维度，入口码 fabushi:view。 */
@RestController
@RequestMapping("/api/fabushi")
@RequiredArgsConstructor
public class FabushiReadingController {
    private final FabushiReadingService readingService;

    /** 【D7 2026-11-23】我的觉醒三板块个人数据聚合（当日/本月/累计）。 */
    @GetMapping("/awaken/status")
    @PreAuthorize("hasAuthority('fabushi:view')")
    public Result<Map<String, Object>> awakenStatus() {
        return Result.ok(readingService.awakenStatus());
    }

    @GetMapping("/shelf")
    @PreAuthorize("hasAuthority('fabushi:view')")
    public Result<Map<String, Object>> shelf() { return Result.ok(readingService.shelfData()); }

    @PostMapping("/shelf")
    @PreAuthorize("hasAuthority('fabushi:view')")
    public Result<Void> addShelf(@RequestBody(required = false) Map<String, Object> body) {
        readingService.addShelf(body == null || body.get("bookId") == null
                ? null : Long.valueOf(String.valueOf(body.get("bookId"))), body);
        return Result.ok();
    }

    @PutMapping("/shelf/{id}")
    @PreAuthorize("hasAuthority('fabushi:view')")
    public Result<Void> updateShelf(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        readingService.updateShelf(id, body); return Result.ok();
    }

    @DeleteMapping("/shelf/{id}")
    @PreAuthorize("hasAuthority('fabushi:view')")
    public Result<Void> removeShelf(@PathVariable Long id) { readingService.removeShelf(id); return Result.ok(); }

    /** 【D20】批量置顶/取消置顶/移出/加入分组/手动排序。 */
    @PostMapping("/shelf/batch")
    @PreAuthorize("hasAuthority('fabushi:view')")
    public Result<Void> batchShelf(@RequestBody Map<String, Object> body) {
        readingService.batchShelf(ids(body.get("ids")), body);
        return Result.ok();
    }

    /** 【D20】分组列表=10 大类型自动组（共享模板）+ 用户自建组。 */
    @GetMapping("/shelf/groups")
    @PreAuthorize("hasAuthority('fabushi:view')")
    public Result<List<Map<String, Object>>> groups() {
        return Result.ok(readingService.groups());
    }

    @PostMapping("/shelf/groups")
    @PreAuthorize("hasAuthority('fabushi:view')")
    public Result<Map<String, Object>> createGroup(@RequestBody Map<String, Object> body) {
        return Result.ok(readingService.createGroup(body == null ? null : string(body.get("name"))));
    }

    @PutMapping("/shelf/groups/{id}")
    @PreAuthorize("hasAuthority('fabushi:view')")
    public Result<Void> renameGroup(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        readingService.renameGroup(id, body == null ? null : string(body.get("name")));
        return Result.ok();
    }

    /** 删自建组只解绑书架，不动阅读进度/上传内容。 */
    @DeleteMapping("/shelf/groups/{id}")
    @PreAuthorize("hasAuthority('fabushi:view')")
    public Result<Void> deleteGroup(@PathVariable Long id) {
        readingService.deleteGroup(id); return Result.ok();
    }

    @GetMapping("/progress/{bookId}")
    @PreAuthorize("hasAuthority('fabushi:view')")
    public Result<Map<String, Object>> progress(@PathVariable Long bookId) { return Result.ok(readingService.progress(bookId)); }

    @PutMapping("/progress/{bookId}")
    @PreAuthorize("hasAuthority('fabushi:view')")
    public Result<Void> saveProgress(@PathVariable Long bookId, @RequestBody Map<String, Object> body) {
        readingService.saveProgress(bookId, body); return Result.ok();
    }

    @GetMapping("/records")
    @PreAuthorize("hasAuthority('fabushi:view')")
    public Result<Map<String, Object>> records(@RequestParam(required = false) String month) { return Result.ok(readingService.records(month)); }

    @GetMapping("/settings")
    @PreAuthorize("hasAuthority('fabushi:view')")
    public Result<Map<String, Object>> settings() { return Result.ok(readingService.settings()); }

    @PutMapping("/settings")
    @PreAuthorize("hasAuthority('fabushi:view')")
    public Result<Void> saveSettings(@RequestBody Map<String, Object> body) { readingService.saveSettings(body); return Result.ok(); }

    private static String string(Object raw) {
        return raw == null ? null : String.valueOf(raw);
    }

    private static List<Long> ids(Object raw) {
        if (!(raw instanceof Collection<?> items)) return List.of();
        return items.stream().map(item -> Long.valueOf(String.valueOf(item))).toList();
    }
}
