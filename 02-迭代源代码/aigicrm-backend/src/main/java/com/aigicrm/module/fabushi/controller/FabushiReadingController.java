package com.aigicrm.module.fabushi.controller;

import com.aigicrm.common.Result;
import com.aigicrm.module.fabushi.service.FabushiReadingService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 二期阅读链：书架/进度/记录/提醒设置，全部本人维度，入口码 fabushi:view。 */
@RestController
@RequestMapping("/api/fabushi")
@RequiredArgsConstructor
public class FabushiReadingController {
    private final FabushiReadingService readingService;

    @GetMapping("/shelf")
    @PreAuthorize("hasAuthority('fabushi:view')")
    public Result<Map<String, Object>> shelf() { return Result.ok(readingService.shelfData()); }

    @PostMapping("/shelf")
    @PreAuthorize("hasAuthority('fabushi:view')")
    public Result<Void> addShelf(@RequestBody Map<String, Object> body) {
        readingService.addShelf(body == null || body.get("bookId") == null ? null : Long.valueOf(String.valueOf(body.get("bookId"))));
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
}
