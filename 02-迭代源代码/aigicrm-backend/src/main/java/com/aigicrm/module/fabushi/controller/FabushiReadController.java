package com.aigicrm.module.fabushi.controller;

import com.aigicrm.common.Result;
import com.aigicrm.module.fabushi.service.FabushiPostService;
import com.aigicrm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 法布施已读标记端点(一期 2026-11-15): 浏览行为幂等 upsert, fabushi:view 门。 */
@RestController
@RequestMapping("/api/fabushi/reads")
@RequiredArgsConstructor
public class FabushiReadController {

    private final FabushiPostService postService;

    /** 标记已读(postId; 幂等, 重复标记刷新 read_at)。 */
    @PostMapping
    @PreAuthorize("hasAuthority('fabushi:view')")
    public Result<Void> markRead(@RequestBody Map<String, Long> body) {
        postService.markRead(body == null ? null : body.get("postId"), SecurityUtils.getCurrentUserId());
        return Result.ok();
    }
}
