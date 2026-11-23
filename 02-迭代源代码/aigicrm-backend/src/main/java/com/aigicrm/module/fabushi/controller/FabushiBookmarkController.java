package com.aigicrm.module.fabushi.controller;

import com.aigicrm.common.BusinessException;
import com.aigicrm.common.Result;
import com.aigicrm.module.fabushi.service.FabushiBookmarkService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 【D19/D24 切片 7】阅读器书签端点：按书列表 / 同页幂等标记 / 本人删除。
 * 复用 fabushi:view，不新增权限码（D19 后端仅轻量承载）。
 */
@RestController
@RequestMapping("/api/fabushi/bookmarks")
@RequiredArgsConstructor
public class FabushiBookmarkController {
    private final FabushiBookmarkService bookmarkService;

    @GetMapping
    @PreAuthorize("hasAuthority('fabushi:view')")
    public Result<List<Map<String, Object>>> list(@RequestParam Long bookId) {
        return Result.ok(bookmarkService.list(bookId));
    }

    /** 【PROTO-AUDIT-20261123 P9/D23】我的书摘：跨书本人书签（带书名）。 */
    @GetMapping("/mine")
    @PreAuthorize("hasAuthority('fabushi:view')")
    public Result<List<Map<String, Object>>> mine() {
        return Result.ok(bookmarkService.listMine());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('fabushi:view')")
    public Result<Map<String, Object>> add(@RequestBody Map<String, Object> body) {
        return Result.ok(bookmarkService.add(parseBookId(body), body));
    }

    /**
     * 【CODE-REVIEW-20261123 R1-02】body 里的 bookId 必须安全解析。
     * 原写法 `Long.valueOf(String.valueOf(...))` 在 {"bookId":"abc"} 时抛 NumberFormatException，
     * 被 GlobalExceptionHandler 兜底成 500「系统异常」；参数错误应为 400。
     */
    private static Long parseBookId(Map<String, Object> body) {
        Object raw = body == null ? null : body.get("bookId");
        if (raw == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(400, "书目ID格式不正确");
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('fabushi:view')")
    public Result<Void> remove(@PathVariable Long id) {
        bookmarkService.remove(id);
        return Result.ok();
    }
}
