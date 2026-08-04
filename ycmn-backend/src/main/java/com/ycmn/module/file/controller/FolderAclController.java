package com.ycmn.module.file.controller;

import com.ycmn.common.Result;
import com.ycmn.module.file.service.FolderAclService;
import com.ycmn.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/file/folders")
@RequiredArgsConstructor
public class FolderAclController {
    private final FolderAclService aclService;

    @GetMapping("/{id}/acl")
    @PreAuthorize("isAuthenticated()")
    public Result<List<Map<String, Object>>> listAcl(@PathVariable Long id) { return Result.ok(aclService.listAcl(id)); }

    @PostMapping("/{id}/acl")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> saveAcl(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        aclService.saveAcl(id, Long.valueOf(body.get("roleId").toString()),
                ((List<?>) body.get("perms")).stream().map(String::valueOf).collect(Collectors.toList()));
        return Result.ok();
    }

    @DeleteMapping("/{id}/acl/{roleId}")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> removeAcl(@PathVariable Long id, @PathVariable Long roleId) { aclService.removeAcl(id, roleId); return Result.ok(); }

    @PutMapping("/{id}/download")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> setDownloadable(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        aclService.setDownloadable(id, body.get("downloadable")); return Result.ok();
    }

    @GetMapping("/{id}/permissions")
    @PreAuthorize("isAuthenticated()")
    public Result<Map<String, Object>> permissions(@PathVariable Long id) {
        return Result.ok(aclService.permissions(id, SecurityUtils.getCurrentUserId()));
    }
}
