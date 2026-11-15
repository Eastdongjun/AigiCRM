package com.aigicrm.module.fabushi.controller;

import com.aigicrm.common.Result;
import com.aigicrm.module.fabushi.service.FabushiSectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/fabushi/books/{bookId}/sections")
@RequiredArgsConstructor
public class FabushiSectionController {
    private final FabushiSectionService sectionService;

    @GetMapping
    @PreAuthorize("hasAuthority('fabushi:view')")
    public Result<Map<String, Object>> tree(@PathVariable Long bookId) { return Result.ok(sectionService.tree(bookId)); }

    @PostMapping
    @PreAuthorize("hasAuthority('fabushi:book:manage')")
    public Result<Map<String, Object>> create(@PathVariable Long bookId, @RequestBody Map<String, Object> body) { return Result.ok(sectionService.create(bookId, body)); }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('fabushi:book:manage')")
    public Result<Void> update(@PathVariable Long bookId, @PathVariable Long id, @RequestBody Map<String, Object> body) { sectionService.update(bookId, id, body); return Result.ok(); }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('fabushi:book:manage')")
    public Result<Void> delete(@PathVariable Long bookId, @PathVariable Long id) { sectionService.delete(bookId, id); return Result.ok(); }
}
