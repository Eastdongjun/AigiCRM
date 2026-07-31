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
        for (SysConfig c : all) {
            if ("system.name".equals(c.getConfigKey()) || "system.logo".equals(c.getConfigKey())) {
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
                Path filePath = Paths.get(uploadDir, fileKey);
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
