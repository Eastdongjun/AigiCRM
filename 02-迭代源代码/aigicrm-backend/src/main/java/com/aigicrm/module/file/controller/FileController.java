package com.aigicrm.module.file.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.aigicrm.common.Result;
import com.aigicrm.module.file.entity.BizFile;
import com.aigicrm.module.file.entity.BizFileFolder;
import com.aigicrm.module.file.service.FileService;
import com.aigicrm.security.SecurityUtils;
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
    private final com.aigicrm.security.JwtTokenProvider jwtTokenProvider;
    /** 【D1=A 2026-10-18】存储空间申请资格/上限信息(复用用户服务配置读取) */
    private final com.aigicrm.module.system.service.SysUserService userService;
    /** 【D1=A 2026-10-18】在途 storage 单判定(与提交侧同一口径) */
    private final com.aigicrm.module.approval.service.ApprovalService approvalService;

    /** F2: 签发短时下载专用 token（1h，scope=download，仅下载/导出路径可用）——主 JWT 不再进 URL/日志。
     *  【2026-10-27 深度检查修复】dlToken 携带当前主 token 的 jti: 登出拉黑主 token 后, 派生下载 token 联动失效 */
    @GetMapping("/download-token")
    public Result<Map<String, Object>> downloadToken(jakarta.servlet.http.HttpServletRequest request) {
        Map<String, Object> data = new java.util.HashMap<>();
        String auth = request != null ? request.getHeader("Authorization") : null;
        String parentJti = null;
        if (auth != null && auth.startsWith("Bearer ")) {
            parentJti = jwtTokenProvider.getJtiFromToken(auth.substring(7));
        }
        data.put("token", jwtTokenProvider.generateDownloadToken(SecurityUtils.getCurrentUserId(), parentJti));
        return Result.ok(data);
    }

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

    @PreAuthorize("hasAuthority('file:upload')")
    @PostMapping("/upload")
    public Result<BizFile> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) Long folderId,
            @RequestParam(defaultValue = "private") String scope) {
        return Result.ok(fileService.upload(file, folderId, scope));
    }

    @PreAuthorize("hasAuthority('file:folder:manage')")
    @PostMapping("/folders")
    public Result<Void> createFolder(@RequestBody BizFileFolder folder) {
        fileService.createFolder(folder);
        return Result.ok();
    }

    @PreAuthorize("hasAuthority('file:folder:manage')")
    @PutMapping("/folders/{id}")
    public Result<Void> renameFolder(@PathVariable Long id, @RequestBody Map<String, String> body) {
        fileService.renameFolder(id, body.get("folderName"));
        return Result.ok();
    }

    @PreAuthorize("hasAuthority('file:folder:manage')")
    @DeleteMapping("/folders/{id}")
    public Result<Void> deleteFolder(@PathVariable Long id) {
        fileService.deleteFolder(id);
        return Result.ok();
    }

    @PreAuthorize("hasAuthority('file:delete')")
    @DeleteMapping("/{id}")
    public Result<Void> deleteFile(@PathVariable Long id) {
        fileService.deleteFile(id);
        return Result.ok();
    }

    /** 【文件权限体系 Task 6】覆盖替换：multipart file，版本+1（细粒度权限在 service 层 checkFile(P_EDIT)） */
    @PreAuthorize("hasAuthority('file:edit')")
    @PostMapping("/{id}/replace")
    public Result<BizFile> replaceFile(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return Result.ok(fileService.replaceFile(id, file));
    }

    @PreAuthorize("hasAuthority('file:view')")
    @GetMapping("/storage")
    public Result<Map<String, Object>> getStorageInfo() {
        return Result.ok(fileService.getStorageInfo(SecurityUtils.getCurrentUserId()));
    }

    /**
     * 【D1=A 2026-10-18】存储空间申请面板(PC/MP 契约):
     * used/quota(当前值)、maxQuota(生效上限 bytes)、canApply(资格)、reason(不可申请原因)、
     * enabled(开关)、allowDecrease(首版恒 false)。不新增权限码, 复用 file:view。
     */
    @PreAuthorize("hasAuthority('file:view')")
    @GetMapping("/storage/apply-info")
    public Result<Map<String, Object>> storageApplyInfo() {
        Long userId = SecurityUtils.getCurrentUserId();
        Map<String, Object> info = userService.storageApplyInfo(userId);
        // 【D1=A】提交后入口立即进入在途态，不能只等提交时后端拒绝；与 submit 共用同一口径。
        boolean pending = approvalService.hasPendingStorageApply(userId);
        info.put("pending", pending);
        if (pending) {
            info.put("canApply", false);
            info.put("reason", "您已有进行中的存储空间申请，请等待审批完成");
        }
        return Result.ok(info);
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

            Path uploadRoot = fileService.getLocalFilePath("").toAbsolutePath().normalize();
            // 【2026-08-15 安全审计 H3】先归一化再校验, 防 public/../private/... 前缀绕过。
            // 旧实现先判 rawKey 前缀后 normalize, 前缀检查可被路径段穿越绕过(当前靠 Tomcat URI 归一化兜底, 不可依赖)
            Path resolved = uploadRoot.resolve(rawKey).normalize();
            if (!resolved.startsWith(uploadRoot)) {
                return ResponseEntity.badRequest().build();
            }
            Path relative = uploadRoot.relativize(resolved);
            if (relative.getNameCount() == 0 || !"public".equals(relative.getName(0).toString())) {
                return ResponseEntity.status(403).build();
            }

            // 【P2 2026-09-30 复审修复·元数据校验】物理在 public/ 段还不足够:
            // 必须存在未删 biz_file 行且 fileScope=public。防"行已逻辑删/范围已改而物理残留"的匿名拉取。
            // 元数据不存在默认 404(不泄漏存在性);范围非 public 403。匿名语义不引入按登录用户的
            // canDownloadFile 判定, 只按行的公开性收口(登录下载走 /download/{id} 与 /download/{key})。
            String fileKey = relative.toString().replace('\\', '/');
            // 【P0 2026-10-17】匿名请求无租户上下文，必须走忽略租户拦截器的 public 专用查询，
            // 否则 selectOne 被改写为 tenant_id=NULL 恒空 → 公共下载全部 404。
            BizFile meta = fileService.getPublicFileByKey(fileKey);
            if (meta == null) {
                return ResponseEntity.notFound().build();
            }
            if (!"public".equals(meta.getFileScope())) {
                return ResponseEntity.status(403).build();
            }

            Resource resource = new UrlResource(resolved.toUri());
            if (resource.exists()) {
                String filename = meta.getFileName() != null ? meta.getFileName() : resolved.getFileName().toString();
                String encodedFilename = java.net.URLEncoder.encode(filename, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
                MediaType mediaType = MediaTypeFactory.getMediaType(filename).orElse(MediaType.APPLICATION_OCTET_STREAM);
                // 安全修复 2026-08-10: 与 FileService.dispositionFor 一致——SVG/HTML 等活性内容强制 attachment, 防匿名存储型XSS
                String disp = com.aigicrm.module.file.service.FileService.dispositionFor(filename);
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, disp + "; filename*=UTF-8''" + encodedFilename)
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
                // 【P2 2026-10-07 复审修复】disposition 与 /download/{id}、/download/public 同口径:
                // 纯图(不含SVG)/视频 inline 预览, 其余(SVG/HTML 等活性内容)强制 attachment,
                // 防历史/旁路落库文件在登录态直接执行(存储型 XSS)。
                String filename = resolved.getFileName().toString();
                String encodedFilename = java.net.URLEncoder.encode(filename, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
                MediaType mediaType = MediaTypeFactory.getMediaType(filename).orElse(MediaType.APPLICATION_OCTET_STREAM);
                String disp = com.aigicrm.module.file.service.FileService.dispositionFor(filename);
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, disp + "; filename*=UTF-8''" + encodedFilename)
                        .contentType(mediaType)
                        .body(resource);
            }

            // 本地不存在，OSS 文件 302（已过归属校验）
            // 【安全加固】重定向仅限 http:// 或 https:// 域（原 startsWith("http") 可被 httpx://evil 绕过）
            if (file.getOssUrl() != null
                    && (file.getOssUrl().startsWith("http://") || file.getOssUrl().startsWith("https://"))) {
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

    @PreAuthorize("hasAuthority('file:share')")
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
