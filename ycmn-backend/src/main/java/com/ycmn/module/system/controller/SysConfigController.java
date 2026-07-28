package com.ycmn.module.system.controller;

import com.ycmn.common.Result;
import com.ycmn.module.file.service.FileService;
import com.ycmn.module.system.entity.SysConfig;
import com.ycmn.module.system.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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
    @PreAuthorize("hasAuthority('system:config:view')")
    public Result<Void> batchUpdate(@RequestBody List<SysConfig> configs) {
        configService.batchUpdate(configs);
        return Result.ok();
    }

    /**
     * 公开接口：获取系统名称和Logo（登录页等未认证页面使用）
     */
    @GetMapping("/public")
    public Result<Map<String, String>> publicConfig() {
        List<SysConfig> all = configService.list();
        Map<String, String> result = new HashMap<>();
        for (SysConfig c : all) {
            if ("system.name".equals(c.getConfigKey()) || "system.logo".equals(c.getConfigKey())) {
                result.put(c.getConfigKey(), c.getConfigValue());
            }
        }
        return Result.ok(result);
    }

    /**
     * 公开接口：获取系统Logo图片文件
     */
    @GetMapping("/logo")
    public ResponseEntity<Resource> logo() {
        try {
            String logoUrl = configService.getValue("system.logo");
            if (logoUrl == null || logoUrl.isBlank()) {
                return ResponseEntity.notFound().build();
            }
            // 本地文件: /api/file/download/xxx → 提取路径读取文件
            if (logoUrl.startsWith("/api/file/download/")) {
                String fileKey = logoUrl.substring("/api/file/download/".length());
                Path filePath = Paths.get(uploadDir, fileKey);
                Resource resource = new UrlResource(filePath.toUri());
                if (resource.exists()) {
                    return ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_TYPE, "image/png")
                            .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                            .body(resource);
                }
                // 本地不存在，尝试查数据库获取 OSS URL
                String ossUrl = fileService.getOssUrlByKey(fileKey);
                if (ossUrl != null) {
                    return ResponseEntity.status(302)
                            .header(HttpHeaders.LOCATION, ossUrl)
                            .build();
                }
                return ResponseEntity.notFound().build();
            }
            // OSS URL: 重定向
            return ResponseEntity.status(302)
                    .header(HttpHeaders.LOCATION, logoUrl)
                    .build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}
