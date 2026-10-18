package com.aigicrm.module.system.controller;

import com.aigicrm.common.BusinessException;
import com.aigicrm.common.Result;
import com.aigicrm.module.file.service.FileService;
import com.aigicrm.module.system.entity.SysConfig;
import com.aigicrm.module.system.service.SysConfigService;
import com.aigicrm.security.SecurityUtils;
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

    /** 系统 Logo 唯一允许的保存形式：本系统公共文件下载地址 */
    private static final String PUBLIC_DOWNLOAD_PREFIX = "/api/file/download/public/";

    @GetMapping
    @PreAuthorize("hasAuthority('system:config:view')")
    public Result<List<SysConfig>> list() {
        return Result.ok(configService.list());
    }

    @PutMapping
    @PreAuthorize("hasAuthority('system:config:edit')")
    public Result<Void> batchUpdate(@RequestBody List<SysConfig> configs) {
        List<SysConfig> safeConfigs = configs == null ? List.of() : configs;
        validateLogoConfigs(safeConfigs);
        configService.batchUpdate(safeConfigs);
        return Result.ok();
    }

    /**
     * 【P0 2026-10-17 Logo 安全】system.logo 是匿名可读品牌资源，只能指向本系统 public 下载路由：
     * 1) 仅平台 admin 可修改（与范围配置等高风险配置同口径）；
     * 2) 必须以前缀 /api/file/download/public/ 开头，禁止 .. 穿越；
     * 3) normalize 后相对路径首段必须仍是 public（防 public/../private 前缀绕过）。
     */
    private void validateLogoConfigs(List<SysConfig> configs) {
        // 前端"保存全部配置"会把未修改的 system.logo 一并回传：只对真实变更做校验/鉴权，
        // 否则非 admin 保存其他设置也会被误拒。
        String currentLogo = configService.getValue("system.logo");
        List<String> changedLogoValues = configs.stream()
                .filter(c -> c != null && "system.logo".equals(c.getConfigKey()))
                .map(SysConfig::getConfigValue)
                .filter(value -> !java.util.Objects.equals(currentLogo, value))
                .toList();
        if (changedLogoValues.isEmpty()) return;
        if (!SecurityUtils.isAdmin()) {
            throw new BusinessException(403, "仅系统管理员可修改系统 Logo");
        }
        for (String value : changedLogoValues) {
            validateLogoValue(value);
        }
    }

    private void validateLogoValue(String value) {
        if (value == null || value.isBlank()) return; // 空值=清除 Logo
        if (!value.startsWith(PUBLIC_DOWNLOAD_PREFIX) || value.contains("..")) {
            throw new BusinessException(400, "Logo 仅支持 /api/file/download/public/ 开头的公共文件地址");
        }
        Path uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path resolved = uploadRoot.resolve(value.substring("/api/file/download/".length())).normalize();
        if (!resolved.startsWith(uploadRoot)) {
            throw new BusinessException(400, "Logo 地址必须指向 public 目录下的文件");
        }
        Path relative = uploadRoot.relativize(resolved);
        if (relative.getNameCount() == 0 || !"public".equals(relative.getName(0).toString())) {
            throw new BusinessException(400, "Logo 地址必须指向 public 目录下的文件");
        }
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
                // 【P0 2026-10-17 路径穿越】归一化后必须重新校验相对路径首段为 public：
                // 旧实现只在前缀检查后 normalize，"public/../private/x" 可绕过并匿名读取私密文件。
                Path relative = uploadRoot.relativize(filePath);
                if (relative.getNameCount() == 0 || !"public".equals(relative.getName(0).toString())) {
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
