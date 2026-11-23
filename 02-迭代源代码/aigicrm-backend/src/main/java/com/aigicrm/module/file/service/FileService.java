package com.aigicrm.module.file.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.aigicrm.common.BusinessException;
import com.aigicrm.module.file.entity.BizFile;
import com.aigicrm.module.file.entity.BizFileFolder;
import com.aigicrm.module.file.entity.BizFileVersion;
import com.aigicrm.module.file.mapper.BizFileMapper;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class FileService {
    // P0-5 batch A: duplicated string literals (Sonar java:S1192)
    private static final String LIT_FABUSHI = "fabushi";
    private static final String LIT_INCENTIVE = "incentive";
    private static final String LIT_HTTPS = "https://";
    private static final String LIT_PUBLIC = "public";
    private static final String LIT_PRIVATE = "private";
    private static final String LIT_X = "没有访问权限，请联系管理员授权";
    private static final String LIT_X_2 = "文件不存在";

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FileService.class);

    private final BizFileMapper fileMapper;
    /** 【V2026.11.14.1 版本历史】replace 归档仍在本类（版本读写/回滚在 FileVersionService） */
    private final com.aigicrm.module.file.mapper.BizFileVersionMapper fileVersionMapper;
    private final SysUserMapper userMapper;
    private final FolderPermissionService folderPermissionService;
    /** S5.3 查询/可见性/访问判定独立 bean；本类保留公共委托。 */
    private final FileQueryService fileQueryService;
    /** S5.3 分享语义独立 bean；本类保留公共委托，控制器/测试调用方不变。 */
    private final FileShareService fileShareService;
    /** S5.3 文件夹写侧独立 bean；本类保留公共委托。 */
    private final FileFolderService fileFolderService;
    /** S5.3 字节存储独立 bean；本类保留公共委托。 */
    private final FileStorageService fileStorageService;
    /** S5.3 缩略图生成/缓存独立 bean；本类保留公共委托。 */
    private final FileThumbnailService fileThumbnailService;
    /** S5.3 激励佐证判定独立 bean；替换/回滚/回收站共用。 */
    private final FileEvidenceGuard fileEvidenceGuard;
    /** S5.3 版本列表/下载/回滚/裁剪独立 bean；本类保留公共委托。 */
    private final FileVersionService fileVersionService;
    /** S5.3 删除/改名独立 bean；本类保留公共委托。 */
    private final FileMutationService fileMutationService;
    /** S5.3 批量下载/zip 独立 bean；本类保留公共委托。 */
    private final FileBatchDownloadService fileBatchDownloadService;

    /** 【2026-11-17 阶段三】视频 5 秒预览：ffmpeg 路径/开关（默认开，失败回退 503，不泄露原文件）。 */
    @Value("${file.video-preview-enabled:true}")
    private boolean videoPreviewEnabled;
    @Value("${file.ffmpeg-path:ffmpeg}")
    private String ffmpegPath;

    // ====== 查询/文件夹树/可见性（S5.3 语义已移到 FileQueryService，本类保留公共委托） ======

    public List<BizFileFolder> getFolderTree(String scope, Long userId) {
        return fileQueryService.getFolderTree(scope, userId);
    }

    public IPage<BizFile> pageFiles(int current, int size, Long folderId, String scope, String keyword, Long userId) {
        return fileQueryService.pageFiles(current, size, folderId, scope, keyword, userId);
    }

    // ====== 上传/字节存储（S5.3 语义已移到 FileStorageService，本类保留公共委托） ======

    public BizFile upload(MultipartFile file, Long folderId, String scope) {
        return fileStorageService.upload(file, folderId, scope);
    }

    /** 【2026-11-17 阶段三】分片上传完成回读/回填 md5。 */
    public BizFile findById(Long id) {
        return id == null ? null : fileMapper.selectById(id);
    }

    public void updateMd5(Long id, String md5) {
        if (id == null || md5 == null) return;
        fileMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<BizFile>()
                .eq(BizFile::getId, id).set(BizFile::getMd5, md5));
    }

    /**
     * 【T2 2026-11-11 审计整改】占配额的文件范围判定: 与 upload 的 scope 白名单对称
     * (public/private/incentive)。删除归还/替换差额/回收站恢复共用, 保证占用与归还账面对称;
     * 历史脏 scope 行(白名单外)不还不扣, 防账面漂移。
     */
    public static boolean quotaBearing(String scope) {
        // 【法布施一期 2026-11-15】fabushi 计个人配额(用户已裁决"计入现有个人配额"), 与 scope 门禁对称
        return LIT_PUBLIC.equals(scope) || LIT_PRIVATE.equals(scope) || LIT_INCENTIVE.equals(scope)
                || LIT_FABUSHI.equals(scope);
    }

    // ====== 文件夹写侧（S5.3 语义已移到 FileFolderService，本类保留公共委托） ======

    public void createFolder(BizFileFolder folder) {
        fileFolderService.createFolder(folder);
    }

    public void renameFolder(Long folderId, String newName) {
        fileFolderService.renameFolder(folderId, newName);
    }

    public void deleteFolder(Long folderId) {
        fileFolderService.deleteFolder(folderId);
    }

    // ====== 删除/改名（S5.3 语义已移到 FileMutationService，本类保留公共委托） ======

    public void deleteFile(Long fileId) {
        fileMutationService.deleteFile(fileId);
    }

    public void renameFile(Long fileId, String fileName) {
        fileMutationService.renameFile(fileId, fileName);
    }

    /**
     * 【文件权限体系 Task 6】覆盖替换：新文件先存后删旧（避免先删旧后存储失败导致文件丢失）。
     * 更新 oss_key/oss_url/file_size/file_type/version+1/replace_by/replaced_at；
     * 私人文件按差额调整配额（storageUsed = 原已用 - 旧size + 新size，最小 0）。
     * 【2026-11-09 文件权限 v2】动作细分: 替换走独立动作 replace(从 edit 伞拆出, 全局码仍 file:edit),
     * 判定链见 FolderPermissionService.checkFile(存量 ACL edit 授权读时展开继续有效)。
     * 【A7 2026-10-20 审计·事务化】旧实现 updateById→删旧文件→调配额三步无事务: 配额调整失败时
     * 接口报"替换失败"但台账已提交、旧文件可能已物理删除, 用户重试会再次覆盖并少扣配额。
     * 现口径: ①方法加 @Transactional, 台账更新与配额差额同事务提交/回滚;
     * ②旧文件物理删除挪到 afterCommit——回滚时台账仍指向旧 key, 事务内先删旧文件会造成
     * "返回失败但文件已丢"(不可逆); deleteStorage 自身吞异常(:540-555), 只能作 best-effort;
     * ③新媒体(uploadStorage 在事务前段已落盘)在失败路径上 best-effort 清理, 防止回滚后留孤儿。
     */
    @org.springframework.transaction.annotation.Transactional
    public BizFile replaceFile(Long fileId, MultipartFile newFile) {
        BizFile file = fileMapper.selectById(fileId);
        if (file == null) throw new BusinessException(LIT_X_2);
        if (LIT_FABUSHI.equals(file.getFileScope())) {
            throw new BusinessException(403, "法布施文件只支持查看/下载/删除，不支持替换");
        }
        Long userId = SecurityUtils.getCurrentUserId();
        if (!folderPermissionService.checkFile(fileId, FolderPermissionService.P_REPLACE, userId)) {
            throw new BusinessException(403, LIT_X);
        }
        // 【决策5 2026-10-18 用户裁决·佐证锁定】激励计分佐证禁替换:佐证文件(安装前三槽/跟踪槽/旧单槽)
        // 是计分依据(安装前三槽齐全即得分),同 ID 换内容会无声替换计分证据(先传A得分再换B)。
        // 需更新佐证走激励模块"删除后重传"(删除触发重算,分数随新证据重出)。记录表为百级量,全量扫可接受。
        fileEvidenceGuard.assertNotIncentiveEvidence(fileId);
        String scope = file.getFileScope() != null ? file.getFileScope() : LIT_PRIVATE;
        String newKey = null;
        try {
            // 先存新文件（沿用原文件的 scope/uploaderId 目录，保证 key 下载路由语义一致）
            // 【GPT三轮 FILE-P1-008】无扩展名拒绝后, replace 的 null 文件名场景改用旧文件扩展兜底
            // (类型继承自已过白名单的旧文件, 治理语义不破), 保持 M-2② 的 ossUrl 更新行为可测
            newKey = fileStorageService.uploadStorage(newFile, scope, file.getUploaderId(), FileNames.extOf(file.getFileName()));
            fileStorageService.scanStored(newKey);
            String oldKey = file.getOssKey();
            long oldSize = file.getFileSize() != null ? file.getFileSize() : 0L;
            // 【V2026.11.14.1 版本历史】主行覆盖前先快照旧版本(旧 key/type/size/version):
            // 归档行与主行更新同事务, 台账与归档原子一致; 旧字节不再物理删除(下方 deleteStorage 拔除)。
            BizFileVersion archived = new BizFileVersion();
            archived.setTenantId(1L);
            archived.setFileId(fileId);
            archived.setVersionNo(file.getVersion() == null ? 1 : file.getVersion());
            archived.setOssKey(oldKey);
            archived.setOssUrl(fileStorageService.fileUrl(oldKey));
            archived.setFileSize(oldSize);
            archived.setFileType(file.getFileType());
            archived.setFileName(file.getFileName());
            archived.setUploadedBy(userId);
            fileVersionMapper.insert(archived);
            // ossUrl 与 upload 的 URL 方案一致：本地走 key 下载路由，OSS 走桶地址（保证替换后两种模式均可下载）
            String newOssUrl = fileStorageService.fileUrl(newKey);
            file.setOssKey(newKey);
            // 【安全修复】ossUrl 无条件更新为新 key 的 URL：旧对象已随 deleteStorage 删除，
            // 保留旧 URL 会指向已删除文件（M-2②：originalFilename==null 时同样更新）
            file.setOssUrl(newOssUrl);
            file.setFileSize(newFile.getSize());
            file.setFileType(newFile.getContentType());
            file.setVersion((file.getVersion() == null ? 0 : file.getVersion()) + 1);
            file.setReplaceBy(userId);
            file.setReplacedAt(LocalDateTime.now());
            fileMapper.updateById(file);
            // 【V2026.11.14.3 P1】归档后按上限裁剪最旧版本(默认 20, 配置可调)
            pruneVersionsIfNeeded(fileId);
            // 【性能 file-3】版本/ossKey 已变化，旧版本缩略图不再命中新 key；主动清掉避免磁盘残留
            // (缓存清理幂等: 回滚场景只是多一次 miss, 无损)
            evictThumbnailCache(fileId);
            // 【P2 2026-10-09 配额并发修复】差额调整改数据库端原子(GREATEST 守护最小 0)——
            // 【A7】与 updateById 同事务: 任一失败整体回滚, 配额与台账不再漂移
            // 【T2 2026-11-11】差额调整覆盖全 scope(与扣减对称)
            if (quotaBearing(scope) && file.getUploaderId() != null) {
                userMapper.adjustStorageUsed(file.getUploaderId(), newFile.getSize() - oldSize);
            }
            // 【V2026.11.14.1 版本历史】旧字节不再物理删除——已归档 biz_file_version, 供版本下载/回滚。
            // 原 afterCommit deleteStorage(oldKey) 拔除; 旧对象生命周期归回收站保留期任务管理。
            return file;
        } catch (BusinessException e) {
            cleanupOrphanMedia(newKey);
            throw e;
        } catch (Exception e) {
            cleanupOrphanMedia(newKey);
            throw new BusinessException("文件替换失败: " + e.getMessage());
        }
    }

    /**
     * 【A7】替换失败补偿: uploadStorage 已落盘的新媒体在事务回滚后成为孤儿(台账仍指向旧 key),
     * best-effort 物理清理; deleteStorage 自身吞异常, 清理失败仅留孤儿文件(无数据正确性影响)。
     * newKey 为 null 表示尚未写入新媒体(权限/佐证/白名单拦截), 无需清理。
     */
    private void cleanupOrphanMedia(String newKey) {
        if (newKey == null || newKey.isEmpty()) return;
        try {
            fileStorageService.deleteStorage(newKey);
        } catch (Exception e) {
            log.warn("替换失败后的新媒体清理异常 fileKey={}", newKey, e);
        }
    }

    /** 【回收站 2026-10-18】文件是否被任一激励记录五槽引用；判定已移到 FileEvidenceGuard。 */
    public boolean isIncentiveEvidence(Long fileId) {
        return fileEvidenceGuard.isIncentiveEvidence(fileId);
    }

    // ====== 字节存储（S5.3 语义已移到 FileStorageService，本类保留公共委托） ======

    public Path getLocalFilePath(String fileKey) {
        return fileStorageService.getLocalFilePath(fileKey);
    }

    public void deleteStoredContent(String fileKey) {
        fileStorageService.deleteStoredContent(fileKey);
    }

    public void writeStoredContent(String fileKey, String fileName, String contentType,
                                   jakarta.servlet.http.HttpServletResponse response) {
        fileStorageService.writeStoredContent(fileKey, fileName, contentType, response);
    }

    void writeStoredContentTo(String fileKey, java.io.OutputStream out) throws java.io.IOException {
        fileStorageService.writeStoredContentTo(fileKey, out);
    }

    // ==================== 【V2026.11.14.1 版本历史】(用户拍板 2026-11-14) ====================

    /** 版本列表行(对外 DTO, 不暴露 ossKey/ossUrl——存储路由面不外泄) */
    public record VersionRow(Long id, Integer versionNo, Long fileSize, String fileType,
                             String fileName, Long uploadedBy, LocalDateTime createdAt) {
        static VersionRow of(BizFileVersion v) {
            return new VersionRow(v.getId(), v.getVersionNo(), v.getFileSize(), v.getFileType(),
                    v.getFileName(), v.getUploadedBy(), v.getCreatedAt());
        }
    }

    // ====== 版本（S5.3 语义已移到 FileVersionService，本类保留公共委托） ======

    void pruneVersionsIfNeeded(Long fileId) {
        fileVersionService.pruneVersionsIfNeeded(fileId);
    }

    public List<VersionRow> listVersions(Long fileId) {
        return fileVersionService.listVersions(fileId);
    }

    public void downloadVersion(Long fileId, Integer versionNo,
                                jakarta.servlet.http.HttpServletResponse response) {
        fileVersionService.downloadVersion(fileId, versionNo, response);
    }

    public BizFile rollbackToVersion(Long fileId, Integer versionNo) {
        return fileVersionService.rollbackToVersion(fileId, versionNo);
    }

    // ====== 批量下载（S5.3 语义已移到 FileBatchDownloadService，本类保留公共委托） ======

    public void batchDownload(List<Long> fileIds, jakarta.servlet.http.HttpServletResponse response) {
        fileBatchDownloadService.batchDownload(fileIds, response);
    }

    /**
     * 【回收站 2026-10-18】文件内容是否仍在存储中。
     * 删除文件时 deleteStorage 会物理清除字节, 仅保留元数据软删行; 只有内容仍存在
     * (删除失败残留/OSS 版本保留等) 时才允许恢复元数据, 否则恢复出来也是坏链接。
     * fail-closed: 检查异常一律按不可恢复处理。
     */
    public boolean contentExists(String fileKey) {
        try {
            return fileStorageService.contentExists(fileKey);
        } catch (Exception e) {
            log.warn("文件内容存在性检查失败 fileKey={}", fileKey, e);
            return false;
        }
    }

    /** 安全内联判定: 仅纯图片(不含 SVG——可内嵌脚本执行, 存存储型XSS)、视频与 PDF 可 inline, 其余一律 attachment。
     *  2026-08-10 安全修复: 原先 svg 也在 inline 名单, 配合任意上传可被匿名/分享 URL 直接执行窃取 localStorage JWT。
     *  2026-10-17 PDF 加入 inline: 浏览器内建 PDF 阅读器需 inline+Range 才能边下边看(前端阅读器直链流式,
     *  23.5MB PDF 首屏从"全量下载完"降到秒级); PDF 阅读器在浏览器内沙箱渲染, 无 SVG/HTML 的脚本执行面。
     *  SVG/HTML 等活性内容仍强制 attachment。 */
    private static final java.util.regex.Pattern SAFE_INLINE_EXT =
            java.util.regex.Pattern.compile("(?i).*\\.(jpg|jpeg|png|gif|webp|bmp|mp4|webm|pdf)$");

    public static String dispositionFor(String filename) {
        return filename != null && SAFE_INLINE_EXT.matcher(filename).matches() ? "inline" : "attachment";
    }

    /** 【2026-11-17 阶段三】视频 5 秒预览（ffmpeg 转码缓存）；非视频返回 415。 */
    public void writeVideoPreview(Long fileId, jakarta.servlet.http.HttpServletResponse response) {
        BizFile file = fileMapper.selectById(fileId);
        Long userId = SecurityUtils.getCurrentUserId();
        Path source = resolveVideoSource(file, userId, response);
        if (source == null) return;
        try {
            Path cached = generateCachedPreview(source, file);
            if (cached == null) { response.setStatus(503); return; }
            response.setStatus(200);
            response.setContentType("video/mp4");
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "inline");
            response.setContentLengthLong(Files.size(cached));
            try (InputStream in = Files.newInputStream(cached)) {
                in.transferTo(response.getOutputStream());
                response.getOutputStream().flush();
            }
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("视频预览生成失败 fileId={}: {}", fileId, e.getMessage());
            response.setStatus(503);
        }
    }

    private Path resolveVideoSource(BizFile file, Long userId, jakarta.servlet.http.HttpServletResponse response) {
        if (file == null) { response.setStatus(404); return null; }
        if (!canDownloadFile(file, userId)) { response.setStatus(403); return null; }
        String name = file.getFileName() == null ? "" : file.getFileName().toLowerCase(java.util.Locale.ROOT);
        if (!name.matches(".*\\.(mp4|webm|mov|avi|mkv|m4v)$")) { response.setStatus(415); return null; }
        if (!videoPreviewEnabled) { response.setStatus(503); return null; }
        if (file.getOssKey() == null) { response.setStatus(404); return null; }
        Path source = fileStorageService.getLocalFilePath(file.getOssKey());
        if (Files.isRegularFile(source)) return source;
        if (file.getOssUrl() != null && file.getOssUrl().startsWith(LIT_HTTPS)) {
            response.setStatus(302);
            response.setHeader(HttpHeaders.LOCATION, file.getOssUrl());
            return null;
        }
        response.setStatus(404);
        return null;
    }

    private Path generateCachedPreview(Path source, BizFile file) throws java.io.IOException, InterruptedException {
        Path cacheRoot = fileThumbnailService.thumbnailCacheRoot();
        Files.createDirectories(cacheRoot);
        String key = "preview-" + file.getId() + "-" + (file.getVersion() == null ? 1 : file.getVersion())
                + "-" + Integer.toHexString(Objects.hash(file.getOssKey(), file.getFileSize(),
                Files.getLastModifiedTime(source).toMillis())) + ".mp4";
        Path cached = cacheRoot.resolve(key);
        if (Files.isRegularFile(cached)) return cached;
        Path tmp = Files.createTempFile(cacheRoot, "preview-", ".tmp");
        Process p = new ProcessBuilder(ffmpegPath, "-y", "-ss", "0", "-i", source.toString(),
                "-t", "5", "-an", "-vf", "scale='min(1280,iw)':-2",
                "-c:v", "libx264", "-preset", "veryfast", "-movflags", "+faststart",
                tmp.toString()).redirectErrorStream(true).start();
        boolean done = p.waitFor(60, java.util.concurrent.TimeUnit.SECONDS);
        if (!done) p.destroyForcibly();
        if (!done || p.exitValue() != 0 || Files.size(tmp) == 0) {
            Files.deleteIfExists(tmp);
            return null;
        }
        Files.move(tmp, cached, StandardCopyOption.REPLACE_EXISTING);
        return cached;
    }

    public void downloadById(Long fileId, String range, boolean thumbnail, jakarta.servlet.http.HttpServletResponse response) {
        BizFile file = fileMapper.selectById(fileId);
        if (file == null) {
            response.setStatus(404);
            return;
        }

        // 归属校验：只有上传者、被分享者、管理员可以下载
        Long userId = SecurityUtils.getCurrentUserId();
        if (!canDownloadFile(file, userId)) {
            response.setStatus(403);
            return;
        }
        try {
            Path filePath = fileStorageService.getLocalFilePath(file.getOssKey());
            // 【缩略图 2026-08-09】列表预览场景：图片返回 ≤300px 缩略图，避免激励查询等页首屏全尺寸下载
            // 【性能 file-3 2026-10-18】授权（上方 canDownloadFile）每次请求都先执行；命中字节缓存后不再读原图
            // 非图片/读取失败 → 回退完整下载
            if (thumbnail && writeThumbnail(file, filePath, response)) return;
            if (serveLocalFile(file, filePath, range, response)) return;
            if (redirectToOss(file, response)) return;
        } catch (Exception e) {
            // S4: 下载写响应异常不得静默吞成 404——记录日志便于排查
            log.error("文件下载失败 fileId={}", fileId, e);
        }
        response.setStatus(404);
    }

    /** 本地文件存在则写完整响应并返回 true；不存在返回 false（调用方回退 OSS 302）。 */
    private boolean serveLocalFile(BizFile file, Path filePath, String range,
                                   jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        Resource resource = new UrlResource(filePath.toUri());
        if (!resource.exists()) return false;
        String filename = file.getFileName();
        String encoded = java.net.URLEncoder.encode(filename, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
        MediaType mt = MediaTypeFactory.getMediaType(filename).orElse(MediaType.APPLICATION_OCTET_STREAM);
        String disp = dispositionFor(filename);

        long fileSize = resource.contentLength();
        RangeSpan span = parseRange(range, fileSize);
        if (span.invalid()) {
            response.setStatus(416);
            response.setHeader("Content-Range", "bytes */" + fileSize);
            return true;
        }
        long length = span.end() - span.start() + 1;

        response.setStatus(span.ranged() ? 206 : 200);
        response.setHeader("Accept-Ranges", "bytes");
        if (span.ranged()) {
            response.setHeader("Content-Range", "bytes " + span.start() + "-" + span.end() + "/" + fileSize);
        }
        response.setContentType(mt.toString());
        response.setContentLengthLong(length);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, disp + "; filename*=UTF-8''" + encoded);

        streamResource(resource, span.start(), length, response);
        return true;
    }

    /** 解析 Range 头；非 bytes= 视为整段。invalid=true 表示越界（应回 416）。 */
    private static RangeSpan parseRange(String range, long fileSize) {
        long start = 0;
        long end = fileSize - 1;
        boolean ranged = range != null && range.startsWith("bytes=");
        if (!ranged) return new RangeSpan(false, start, end, false);
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("bytes=(\\d*)-(\\d*)").matcher(range);
        if (m.find()) {
            if (!m.group(1).isEmpty()) start = Long.parseLong(m.group(1));
            if (!m.group(2).isEmpty()) end = Math.min(Long.parseLong(m.group(2)), fileSize - 1);
        }
        boolean invalid = start > end || start >= fileSize;
        return new RangeSpan(true, start, end, invalid);
    }

    private static void streamResource(Resource resource, long start, long length,
                                       jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        try (java.io.InputStream is = resource.getInputStream()) {
            is.skipNBytes(start);
            java.io.OutputStream out = response.getOutputStream();
            byte[] buf = new byte[8192];
            long remaining = length;
            int n;
            while (remaining > 0 && (n = is.read(buf, 0, (int) Math.min(buf.length, remaining))) != -1) {
                out.write(buf, 0, n);
                remaining -= n;
            }
            out.flush();
        }
    }

    /** OSS 直链可 302 时写响应并返回 true；否则 false 由调用方落 404。 */
    private static boolean redirectToOss(BizFile file, jakarta.servlet.http.HttpServletResponse response) {
        // 【2026-10-27 深度检查修复】OSS 302 与 FileController 路由白名单同口径:
        // startsWith("http") 可被 httpx:// 绕过, 收紧为 https:// (OSS 地址均 https)
        if (file.getOssUrl() != null && file.getOssUrl().startsWith(LIT_HTTPS)) {
            response.setStatus(302);
            response.setHeader(HttpHeaders.LOCATION, file.getOssUrl());
            return true;
        }
        return false;
    }

    /** Range 解析结果：ranged=客户端传了 bytes=；invalid=越界需 416。 */
    private record RangeSpan(boolean ranged, long start, long end, boolean invalid) {}

    // ====== 缩略图（S5.3 语义已移到 FileThumbnailService，本类保留调用点） ======

    private boolean writeThumbnail(BizFile file, Path filePath,
                                   jakarta.servlet.http.HttpServletResponse response) {
        return fileThumbnailService.writeThumbnail(file, filePath, response);
    }

    void evictThumbnailCache(Long fileId) {
        fileThumbnailService.evictThumbnailCache(fileId);
    }

    // ====== 文件查询/访问判定（S5.3 语义已移到 FileQueryService，本类保留公共委托） ======

    public BizFile getFileByKey(String fileKey) {
        return fileQueryService.getFileByKey(fileKey);
    }

    public BizFile getPublicFileByKey(String fileKey) {
        return fileQueryService.getPublicFileByKey(fileKey);
    }

    public String getOssUrlByKey(String fileKey) {
        return fileQueryService.getOssUrlByKey(fileKey);
    }

    public Map<String, Object> getStorageInfo(Long userId) {
        return fileQueryService.getStorageInfo(userId);
    }

    public boolean canAccessFile(BizFile file, Long userId) {
        return fileQueryService.canAccessFile(file, userId);
    }

    public boolean canDownloadFile(BizFile file, Long userId) {
        return fileQueryService.canDownloadFile(file, userId);
    }

    // ====== 文件分享（S5.3 语义已移到 FileShareService，本类保留公共委托） ======

    public void shareFile(Long fileId, List<Long> userIds) {
        fileShareService.shareFile(fileId, userIds);
    }

    public IPage<BizFile> pageSharedToMe(int current, int size, String keyword) {
        return fileShareService.pageSharedToMe(current, size, keyword);
    }

    public long countSharedToMe() {
        return fileShareService.countSharedToMe();
    }
}
