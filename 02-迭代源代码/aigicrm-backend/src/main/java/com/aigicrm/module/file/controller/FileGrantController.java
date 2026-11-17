package com.aigicrm.module.file.controller;

import com.aigicrm.common.Result;
import com.aigicrm.module.file.service.FileGrantService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 【2026-11-17 阶段二】biz_file_grant 统一授权管理面。
 * 权限：与文件例外授权共用 file:acl:manage；DENY 优先、支持继承/过期/组织范围。
 */
@RestController
@RequestMapping("/api/file/grants")
@RequiredArgsConstructor
public class FileGrantController {

    private final FileGrantService fileGrantService;

    @PreAuthorize("hasAuthority('file:acl:manage')")
    @GetMapping
    public Result<List<Map<String, Object>>> list(@RequestParam(required = false) String objectType,
                                                  @RequestParam(required = false) Long objectId) {
        return Result.ok(fileGrantService.list(objectType, objectId));
    }

    @PreAuthorize("hasAuthority('file:acl:manage')")
    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody FileGrantRequest body) {
        return Result.ok(fileGrantService.save(body.toCommand(null)));
    }

    @PreAuthorize("hasAuthority('file:acl:manage')")
    @PutMapping("/{id}")
    public Result<Map<String, Object>> update(@PathVariable Long id, @RequestBody FileGrantRequest body) {
        return Result.ok(fileGrantService.save(body.toCommand(id)));
    }

    @PreAuthorize("hasAuthority('file:acl:manage')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        fileGrantService.delete(id);
        return Result.ok();
    }

    /** 请求 DTO：controller 不持有 entity。 */
    public static class FileGrantRequest {
        public String subjectType;
        public Long subjectId;
        public String objectType;
        public Long objectId;
        public String action;
        public String effect;
        public Integer inherit;
        public Long scopeOrgId;
        public String scopeLevel;
        public java.time.LocalDateTime expireAt;

        public FileGrantService.Command toCommand(Long id) {
            return new FileGrantService.Command(id, subjectType, subjectId, objectType, objectId,
                    action, effect, inherit, scopeOrgId, scopeLevel, expireAt);
        }
    }
}
