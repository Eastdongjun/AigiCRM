package com.aigicrm.module.file.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.module.file.entity.BizFile;
import com.aigicrm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 【2026-11-17 阶段三】200MB 单文件分片上传：
 * init → 逐片上传（可断点/重复上传同一片）→ complete 合并，复用 FileService.upload 的
 * scope/文件夹权限/配额/审计同源；带 md5 时支持同人同 scope 秒传（返回已存在文件，不重复计配额）。
 */
// native-sql-audit: 上传会话按 uploader_id 归属校验; 秒传只查当前 uploader 的 biz_file; 单集团部署 tenant_id 恒 1
@Service
@RequiredArgsConstructor
public class FileChunkUploadService {
    // P0-5 batch A: duplicated string literals (Sonar java:S1192)
    private static final String LIT_PART = ".part";


    public static final long MAX_FILE_SIZE = 200L * 1024 * 1024;
    public static final int CHUNK_SIZE = 5 * 1024 * 1024;

    private final JdbcTemplate jdbc;
    private final FileService fileService;

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;
    /** 分片临时目录：默认系统临时目录（不要求 uploads 根可写）；多实例部署配置为共享 NAS。 */
    @Value("${file.chunk-dir:}")
    private String chunkDirConfig;
    @Value("${file.chunk.max-age-hours:24}")
    private int maxAgeHours;

    public Map<String, Object> init(String fileName, long fileSize, String md5, String scope, Long folderId) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (fileName == null || fileName.isBlank()) throw new BusinessException("文件名不能为空");
        if (fileName.length() > 255) throw new BusinessException("文件名过长");
        if (fileSize <= 0) throw new BusinessException("文件大小必须大于 0");
        if (fileSize > MAX_FILE_SIZE) throw new BusinessException("单文件上限 200MB");
        if (scope == null || !("public".equals(scope) || "private".equals(scope)
                || "incentive".equals(scope) || "fabushi".equals(scope))) {
            throw new BusinessException(403, "非法的文件范围");
        }
        String normalizedMd5 = md5 == null ? null : md5.trim().toLowerCase();
        if (normalizedMd5 != null && !normalizedMd5.matches("[0-9a-f]{32,64}")) {
            throw new BusinessException("md5 格式不正确");
        }
        // 秒传：同人同 scope/文件夹已有同 md5 活跃文件 → 直接返回（不重复扣配额）。
        if (normalizedMd5 != null) {
            List<Map<String, Object>> hit = jdbc.queryForList(
                    "SELECT id,file_name,file_size,file_scope,folder_id FROM biz_file " +
                            "WHERE md5=? AND uploader_id=? AND file_scope=? AND deleted=0 " +
                            "AND folder_id <=> ? ORDER BY id DESC LIMIT 1",
                    normalizedMd5, userId, scope, folderId);
            if (!hit.isEmpty()) {
                Map<String, Object> m = new LinkedHashMap<>(hit.get(0));
                m.put("instant", true);
                return m;
            }
        }
        String uploadId = UUID.randomUUID().toString();
        int totalChunks = (int) ((fileSize + CHUNK_SIZE - 1) / CHUNK_SIZE);
        jdbc.update("INSERT INTO biz_upload_session " +
                        "(upload_id,uploader_id,file_name,file_size,md5,file_scope,folder_id,total_chunks,received_chunks,status) " +
                        "VALUES(?,?,?,?,?,?,?,?,0,'uploading')",
                uploadId, userId, fileName, fileSize, normalizedMd5, scope, folderId, totalChunks);
        try {
            Files.createDirectories(chunkDir(uploadId));
        } catch (Exception e) {
            throw new BusinessException("创建上传会话失败: " + e.getMessage());
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("uploadId", uploadId);
        out.put("chunkSize", CHUNK_SIZE);
        out.put("totalChunks", totalChunks);
        out.put("instant", false);
        return out;
    }

    public Map<String, Object> uploadChunk(String uploadId, int index, MultipartFile chunk) {
        Map<String, Object> s = requireSession(uploadId);
        long userId = SecurityUtils.getCurrentUserId();
        if (((Number) s.get("uploader_id")).longValue() != userId) throw new BusinessException(403, "无权操作该上传会话");
        if (!"uploading".equals(s.get("status"))) throw new BusinessException("上传会话已结束");
        int total = ((Number) s.get("total_chunks")).intValue();
        if (index < 0 || index >= total) throw new BusinessException("分片序号越界");
        if (chunk == null || chunk.isEmpty()) throw new BusinessException("分片内容为空");
        if (chunk.getSize() > CHUNK_SIZE + 1024L * 1024) throw new BusinessException("分片过大");
        Path dir = chunkDir(uploadId);
        try {
            Files.createDirectories(dir);
            Files.copy(chunk.getInputStream(), dir.resolve(index + LIT_PART), StandardCopyOption.REPLACE_EXISTING);
            int received = countParts(dir);
            jdbc.update("UPDATE biz_upload_session SET received_chunks=? WHERE upload_id=?", received, uploadId);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("received", received);
            out.put("total", total);
            return out;
        } catch (Exception e) {
            throw new BusinessException("分片保存失败: " + e.getMessage());
        }
    }

    public BizFile complete(String uploadId) {
        Map<String, Object> s = requireSession(uploadId);
        long userId = SecurityUtils.getCurrentUserId();
        if (((Number) s.get("uploader_id")).longValue() != userId) throw new BusinessException(403, "无权操作该上传会话");
        if ("completed".equals(s.get("status"))) {
            Long fileId = s.get("file_id") == null ? null : ((Number) s.get("file_id")).longValue();
            return fileId == null ? null : fileService.findById(fileId);
        }
        int total = ((Number) s.get("total_chunks")).intValue();
        long size = ((Number) s.get("file_size")).longValue();
        Path dir = chunkDir(uploadId);
        try {
            if (countParts(dir) != total) throw new BusinessException("分片不完整，请继续上传");
            String md5 = (String) s.get("md5");
            Path merged = mergeParts(dir, total);
            verifyMerged(merged, size, md5);
            String scope = (String) s.get("file_scope");
            Long folderId = s.get("folder_id") == null ? null : ((Number) s.get("folder_id")).longValue();
            PathMultipartFile mp = new PathMultipartFile(merged, (String) s.get("file_name"), Files.size(merged));
            BizFile file = fileService.upload(mp, folderId, scope);
            if (md5 != null && !md5.isBlank()) {
                fileService.updateMd5(file.getId(), md5);
                file.setMd5(md5);
            }
            jdbc.update("UPDATE biz_upload_session SET status='completed', file_id=?, received_chunks=? WHERE upload_id=?",
                    file.getId(), total, uploadId);
            deleteRecursively(dir);
            return file;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("合并上传失败: " + e.getMessage());
        }
    }

    /** 按序号合并分片（S3776 拆分，语义与原内联逻辑一致）。 */
    private Path mergeParts(Path dir, int total) throws java.io.IOException {
        Path merged = Files.createTempFile(dir, "merged-", ".tmp");
        try (OutputStream out = Files.newOutputStream(merged)) {
            for (int i = 0; i < total; i++) {
                Path part = dir.resolve(i + LIT_PART);
                if (!Files.isRegularFile(part)) throw new BusinessException("缺少分片 " + i);
                Files.copy(part, out);
            }
        }
        return merged;
    }

    /** 合并后大小/MD5 校验；失败删除临时文件并由调用方转业务异常（S3776 拆分）。 */
    private void verifyMerged(Path merged, long size, String md5) throws java.io.IOException, java.security.NoSuchAlgorithmException {
        if (Files.size(merged) != size) {
            Files.deleteIfExists(merged);
            throw new BusinessException("合并后大小不一致，请重新上传");
        }
        if (md5 != null && !md5.isBlank()) {
            String actual = md5(merged);
            if (!md5.equalsIgnoreCase(actual)) {
                Files.deleteIfExists(merged);
                throw new BusinessException("文件校验失败（md5 不一致），请重新上传");
            }
        }
    }

    /** 【2026-11-17 P2-6】每小时清理超过 max-age-hours 的未完成分片会话与临时目录。 */
    @org.springframework.scheduling.annotation.Scheduled(
            fixedDelayString = "${file.chunk.cleanup-ms:3600000}")
    public void cleanupExpiredSessions() {
        try {
            List<String> ids = jdbc.queryForList(
                    "SELECT upload_id FROM biz_upload_session WHERE status='uploading' " +
                            "AND updated_at < DATE_SUB(NOW(), INTERVAL ? HOUR) LIMIT 200",
                    String.class, maxAgeHours);
            for (String id : ids) {
                deleteRecursively(chunkDir(id));
                jdbc.update("UPDATE biz_upload_session SET status='expired' WHERE upload_id=? AND status='uploading'", id);
            }
        } catch (Exception ignored) {
            // 清理失败不阻断上传主链，下一轮重试。
        }
    }

    private Map<String, Object> requireSession(String uploadId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM biz_upload_session WHERE upload_id=?", uploadId);
        if (rows.isEmpty()) throw new BusinessException("上传会话不存在");
        return rows.get(0);
    }

    private Path chunkDir(String uploadId) {
        if (uploadId == null || !uploadId.matches("[0-9a-fA-F-]{36}")) throw new BusinessException("上传会话非法");
        Path root = (chunkDirConfig == null || chunkDirConfig.isBlank()
                ? Paths.get(System.getProperty("java.io.tmpdir"), "aigicrm-chunks")
                : Paths.get(chunkDirConfig)).toAbsolutePath().normalize();
        Path dir = root.resolve(uploadId).normalize();
        if (!dir.startsWith(root)) throw new BusinessException("上传会话非法");
        return dir;
    }

    private int countParts(Path dir) throws Exception {
        if (!Files.isDirectory(dir)) return 0;
        try (var stream = Files.list(dir)) {
            return (int) stream.filter(p -> p.getFileName().toString().endsWith(LIT_PART)).count();
        }
    }

    private String md5(Path file) throws java.io.IOException, java.security.NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) md.update(buf, 0, n);
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private void deleteRecursively(Path dir) {
        try (var stream = Files.walk(dir)) {
            stream.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (Exception ignored) { }
            });
        } catch (Exception ignored) { }
    }

    /** 避免把 200MB 读入堆：MultipartFile 直接包临时文件，FileService 内部按流拷贝。 */
    private static final class PathMultipartFile implements MultipartFile {
        private final Path path;
        private final String name;
        private final long size;

        private PathMultipartFile(Path path, String name, long size) {
            this.path = path;
            this.name = name;
            this.size = size;
        }

        @Override public String getName() { return "file"; }
        @Override public String getOriginalFilename() { return name; }
        @Override public String getContentType() { return null; }
        @Override public boolean isEmpty() { return size == 0; }
        @Override public long getSize() { return size; }
        @Override public byte[] getBytes() throws java.io.IOException { return Files.readAllBytes(path); }
        @Override public InputStream getInputStream() throws java.io.IOException { return Files.newInputStream(path); }
        @Override public void transferTo(java.io.File dest) throws java.io.IOException {
            Files.copy(path, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
