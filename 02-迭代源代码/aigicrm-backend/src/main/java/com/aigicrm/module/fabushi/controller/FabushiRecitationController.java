package com.aigicrm.module.fabushi.controller;

import com.aigicrm.common.Result;
import com.aigicrm.module.fabushi.service.FabushiRecitationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 法布施复诵端点（D14 2026-11-23）：发布复诵=create（音频须挂 fabushi 空间、≤60s），查看=view。
 * 【ArchUnit controller_must_not_depend_on_entity】body/返回全走 Map。
 */
@RestController
@RequestMapping("/api/fabushi/posts/{id}/recitations")
@RequiredArgsConstructor
public class FabushiRecitationController {

    private final FabushiRecitationService recitationService;

    /** 复诵(fabushi:create；同人同帖可多次)。 */
    @PostMapping
    @PreAuthorize("hasAuthority('fabushi:create')")
    public Result<Map<String, Object>> create(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return Result.ok(recitationService.createData(id, body));
    }

    /** 复诵列表(fabushi:view)。 */
    @GetMapping
    @PreAuthorize("hasAuthority('fabushi:view')")
    public Result<List<Map<String, Object>>> list(@PathVariable Long id) {
        return Result.ok(recitationService.listData(id));
    }
}
