package com.ycmn.module.file.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ycmn.common.Result;
import com.ycmn.module.file.entity.BizFile;
import com.ycmn.module.file.entity.BizFileFolder;
import com.ycmn.module.file.service.FileService;
import com.ycmn.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/file")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @GetMapping("/folders")
    public Result<List<BizFileFolder>> getFolderTree(
            @RequestParam(defaultValue = "public") String scope) {
        return Result.ok(fileService.getFolderTree(scope, SecurityUtils.getCurrentUserId()));
    }

    @GetMapping("/list")
    public Result<IPage<BizFile>> pageFiles(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long folderId,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String keyword) {
        return Result.ok(fileService.pageFiles(current, size, folderId, scope, keyword, SecurityUtils.getCurrentUserId()));
    }

    @PostMapping("/upload")
    public Result<BizFile> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) Long folderId,
            @RequestParam(defaultValue = "private") String scope) {
        return Result.ok(fileService.upload(file, folderId, scope));
    }

    @PostMapping("/folders")
    public Result<Void> createFolder(@RequestBody BizFileFolder folder) {
        fileService.createFolder(folder);
        return Result.ok();
    }

    @PutMapping("/folders/{id}")
    public Result<Void> renameFolder(@PathVariable Long id, @RequestBody Map<String, String> body) {
        fileService.renameFolder(id, body.get("folderName"));
        return Result.ok();
    }

    @DeleteMapping("/folders/{id}")
    public Result<Void> deleteFolder(@PathVariable Long id) {
        fileService.deleteFolder(id);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteFile(@PathVariable Long id) {
        fileService.deleteFile(id);
        return Result.ok();
    }

    @GetMapping("/storage")
    public Result<Map<String, Object>> getStorageInfo() {
        return Result.ok(fileService.getStorageInfo(SecurityUtils.getCurrentUserId()));
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<?> downloadById(@PathVariable Long id) {
        return fileService.downloadById(id);
    }

    @GetMapping("/download/**")
    public ResponseEntity<Resource> download(jakarta.servlet.http.HttpServletRequest request) {
        try {
            String fileKey = java.net.URLDecoder.decode(
                    request.getRequestURI().substring("/api/file/download/".length()),
                    java.nio.charset.StandardCharsets.UTF_8);

            // 优先尝试本地文件
            Path filePath = fileService.getLocalFilePath(fileKey);
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists()) {
                String filename = filePath.getFileName().toString();
                String encodedFilename = java.net.URLEncoder.encode(filename, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
                MediaType mediaType = MediaTypeFactory.getMediaType(filename).orElse(MediaType.APPLICATION_OCTET_STREAM);
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encodedFilename)
                        .contentType(mediaType)
                        .body(resource);
            }

            // 本地不存在，查数据库看是否有 OSS URL
            String ossUrl = fileService.getOssUrlByKey(fileKey);
            if (ossUrl != null && !ossUrl.isBlank()) {
                return ResponseEntity.status(302)
                        .header(HttpHeaders.LOCATION, ossUrl)
                        .build();
            }

            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ====== 文件分享 ======

    @PostMapping("/{id}/share")
    public Result<Void> shareFile(@PathVariable Long id, @RequestBody Map<String, List<Long>> body) {
        fileService.shareFile(id, body.getOrDefault("userIds", List.of()));
        return Result.ok();
    }

    @GetMapping("/shared-to-me")
    public Result<IPage<BizFile>> sharedToMe(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword) {
        return Result.ok(fileService.pageSharedToMe(current, size, keyword));
    }

    @GetMapping("/shared-to-me/count")
    public Result<Long> sharedToMeCount() {
        return Result.ok(fileService.countSharedToMe());
    }
}
