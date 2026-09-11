package com.aigicrm.module.system.controller;

import com.aigicrm.common.Result;
import com.aigicrm.module.file.service.FileService;
import com.aigicrm.module.system.entity.SysConfig;
import com.aigicrm.module.system.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system/configs")
@RequiredArgsConstructor
public class SysConfigController {

    private final SysConfigService configService;
    private final FileService fileService;

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    @GetMapping
    @PreAuthorize("hasAuthority('system:config:view')")
    public Result<List<SysConfig>> list() {
        return Result.ok(configService.list());
    }

    @PutMapping
    @PreAuthorize("hasAuthority('system:config:edit')")
    public Result<Void> batchUpdate(@RequestBody List<SysConfig> configs) {
        configService.batchUpdate(configs);
        return Result.ok();
    }

    @GetMapping("/public")
    public Result<Map<String, String>> publicConfig() {
        List<SysConfig> all = configService.list();
        Map<String, String> result = new HashMap<>();
        result.put("system.name", "AIGICRM");
        result.put("system.subtitle", "市场资源管理系统");
        // 匿名可读的公开配置:登录页品牌 + 客户保护/提醒/调整/开票超期(前端列表展示与状态判断依赖)
        java.util.Set<String> publicKeys = java.util.Set.of(
                "system.name", "system.logo", "system.subtitle",
                "customer.protection.days", "customer.warning.days", "customer.adjust.days",
                "invoice.overdue.days");
        for (SysConfig c : all) {
            if (publicKeys.contains(c.getConfigKey())) {
                result.put(c.getConfigKey(), c.getConfigValue());
            }
        }
        return Result.ok(result);
    }

    @GetMapping("/logo")
    public ResponseEntity<?> logo() {
        try {
            String logoUrl = configService.getValue("system.logo");
            if (logoUrl == null || logoUrl.isBlank()) {
                return transparentSvg();
            }
            if (logoUrl.startsWith("/api/file/download/")) {
                String fileKey = logoUrl.substring("/api/file/download/".length());
                // 安全(REV-20260802-01): 只允许 public/ 前缀 + 路径包含校验, 防止路径穿越与私密文件匿名泄露
                if (!fileKey.startsWith("public/")) {
                    return transparentSvg();
                }
                Path uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
                Path filePath = uploadRoot.resolve(fileKey).normalize();
                if (!filePath.startsWith(uploadRoot)) {
                    return transparentSvg();
                }
                Resource resource = new UrlResource(filePath.toUri());
                if (resource.exists()) {
                    return ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_TYPE, "image/png")
                            .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                            .body(resource);
                }
                String ossUrl = fileService.getOssUrlByKey(fileKey);
                if (ossUrl != null) {
                    return ResponseEntity.status(302)
                            .header(HttpHeaders.LOCATION, ossUrl)
                            .build();
                }
                return transparentSvg();
            }
            return ResponseEntity.status(302)
                    .header(HttpHeaders.LOCATION, logoUrl)
                    .build();
        } catch (Exception e) {
            return transparentSvg();
        }
    }

    private ResponseEntity<byte[]> transparentSvg() {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"1\" height=\"1\"/>".getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "image/svg+xml")
                .body(svg);
    }
}
