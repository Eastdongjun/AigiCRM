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
import org.springframework.security.access.prepost.PreAuthorize;
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

    @PreAuthorize("hasAuthority('file:view')")
    @GetMapping("/folders")
    public Result<List<BizFileFolder>> getFolderTree(
            @RequestParam(defaultValue = "public") String scope) {
        return Result.ok(fileService.getFolderTree(scope, SecurityUtils.getCurrentUserId()));
    }

    @PreAuthorize("hasAuthority('file:view')")
    @GetMapping("/list")
    public Result<IPage<BizFile>> pageFiles(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long folderId,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String keyword) {
        return Result.ok(fileService.pageFiles(current, size, folderId, scope, keyword, SecurityUtils.getCurrentUserId()));
    }

    @PreAuthorize("hasAuthority('file:view')")
    @PostMapping("/upload")
    public Result<BizFile> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) Long folderId,
            @RequestParam(defaultValue = "private") String scope) {
        return Result.ok(fileService.upload(file, folderId, scope));
    }

    @PreAuthorize("hasAuthority('file:view')")
    @PostMapping("/folders")
    public Result<Void> createFolder(@RequestBody BizFileFolder folder) {
        fileService.createFolder(folder);
        return Result.ok();
    }

    @PreAuthorize("hasAuthority('file:view')")
    @PutMapping("/folders/{id}")
    public Result<Void> renameFolder(@PathVariable Long id, @RequestBody Map<String, String> body) {
        fileService.renameFolder(id, body.get("folderName"));
        return Result.ok();
    }

    @PreAuthorize("hasAuthority('file:view')")
    @DeleteMapping("/folders/{id}")
    public Result<Void> deleteFolder(@PathVariable Long id) {
        fileService.deleteFolder(id);
        return Result.ok();
    }

    @PreAuthorize("hasAuthority('file:view')")
    @DeleteMapping("/{id}")
    public Result<Void> deleteFile(@PathVariable Long id) {
        fileService.deleteFile(id);
        return Result.ok();
    }

    /** 【文件权限体系 Task 6】覆盖替换：multipart file，版本+1（细粒度权限在 service 层 checkFile(P_EDIT)） */
    @PreAuthorize("hasAuthority('file:view')")
    @PostMapping("/{id}/replace")
    public Result<BizFile> replaceFile(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return Result.ok(fileService.replaceFile(id, file));
    }

    @PreAuthorize("hasAuthority('file:view')")
    @GetMapping("/storage")
    public Result<Map<String, Object>> getStorageInfo() {
        return Result.ok(fileService.getStorageInfo(SecurityUtils.getCurrentUserId()));
    }

    @PreAuthorize("hasAuthority('file:view')")
    @GetMapping("/download/{id}")
    public void downloadById(@PathVariable Long id,
                             @RequestHeader(value = "Range", required = false) String range,
                             @RequestParam(value = "thumbnail", required = false, defaultValue = "false") boolean thumbnail,
                             jakarta.servlet.http.HttpServletResponse response) {
        fileService.downloadById(id, range, thumbnail, response);
    }

    /** 公开文件下载 — 无需登录，仅限 public 范围 */
    @GetMapping("/download/public/**")
    public ResponseEntity<Resource> downloadPublic(jakarta.servlet.http.HttpServletRequest request) {
        try {
            String rawKey = java.net.URLDecoder.decode(
                    request.getRequestURI().substring("/api/file/download/".length()),
                    java.nio.charset.StandardCharsets.UTF_8);

            // 只允许 public/ 前缀的文件
            if (!rawKey.startsWith("public/")) {
                return ResponseEntity.status(403).build();
            }

            Path uploadRoot = fileService.getLocalFilePath("").toAbsolutePath().normalize();
            Path resolved = uploadRoot.resolve(rawKey).normalize();
            if (!resolved.startsWith(uploadRoot)) {
                return ResponseEntity.badRequest().build();
            }

            Resource resource = new UrlResource(resolved.toUri());
            if (resource.exists()) {
                String filename = resolved.getFileName().toString();
                String encodedFilename = java.net.URLEncoder.encode(filename, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
                MediaType mediaType = MediaTypeFactory.getMediaType(filename).orElse(MediaType.APPLICATION_OCTET_STREAM);
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encodedFilename)
                        .contentType(mediaType).body(resource);
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PreAuthorize("hasAuthority('file:view')")
    @GetMapping("/download/**")
    public ResponseEntity<Resource> download(jakarta.servlet.http.HttpServletRequest request) {
        try {
            String rawKey = java.net.URLDecoder.decode(
                    request.getRequestURI().substring("/api/file/download/".length()),
                    java.nio.charset.StandardCharsets.UTF_8);

            // 路径穿越防护：normalize 后验证必须在 uploadDir 内
            Path uploadRoot = fileService.getLocalFilePath("").toAbsolutePath().normalize();
            Path resolved = uploadRoot.resolve(rawKey).normalize();
            if (!resolved.startsWith(uploadRoot)) {
                return ResponseEntity.badRequest().build();
            }

            // 归属校验：按 oss_key 解析文件记录，与 /download/{id} 同权
            // （canDownloadFile = canAccessFile(上传者/被分享者/管理员/公共文件) 或夹内文件文件夹下载权限，
            //   非管理员还受夹内文件下载开关约束；biz_file 查不到的文件保持现状不阻断）
            BizFile file = fileService.getFileByKey(rawKey);
            if (file == null) {
                return ResponseEntity.notFound().build();
            }
            if (!fileService.canDownloadFile(file, SecurityUtils.getCurrentUserId())) {
                return ResponseEntity.status(403).build();
            }

            // 优先尝试本地文件
            Resource resource = new UrlResource(resolved.toUri());
            if (resource.exists()) {
                String filename = resolved.getFileName().toString();
                String encodedFilename = java.net.URLEncoder.encode(filename, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
                MediaType mediaType = MediaTypeFactory.getMediaType(filename).orElse(MediaType.APPLICATION_OCTET_STREAM);
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encodedFilename)
                        .contentType(mediaType)
                        .body(resource);
            }

            // 本地不存在，OSS 文件 302（已过归属校验）
            if (file.getOssUrl() != null && file.getOssUrl().startsWith("http")) {
                return ResponseEntity.status(302)
                        .header(HttpHeaders.LOCATION, file.getOssUrl())
                        .build();
            }

            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ====== 文件分享 ======

    @PreAuthorize("hasAuthority('file:view')")
    @PostMapping("/{id}/share")
    public Result<Void> shareFile(@PathVariable Long id, @RequestBody Map<String, List<Long>> body) {
        fileService.shareFile(id, body.getOrDefault("userIds", List.of()));
        return Result.ok();
    }

    @PreAuthorize("hasAuthority('file:view')")
    @GetMapping("/shared-to-me")
    public Result<IPage<BizFile>> sharedToMe(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword) {
        return Result.ok(fileService.pageSharedToMe(current, size, keyword));
    }

    @PreAuthorize("hasAuthority('file:view')")
    @GetMapping("/shared-to-me/count")
    public Result<Long> sharedToMeCount() {
        return Result.ok(fileService.countSharedToMe());
    }
}
