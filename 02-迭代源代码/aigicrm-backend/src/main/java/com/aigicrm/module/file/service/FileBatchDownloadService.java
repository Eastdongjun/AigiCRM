package com.aigicrm.module.file.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.module.file.entity.BizFile;
import com.aigicrm.module.file.mapper.BizFileMapper;
import com.aigicrm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量下载（zip 流式打包）。
 *
 * <p>S5.3 从 {@link FileService} 抽出。逐个过下载权限与内容存在性，
 * 无权限/缺内容的静默剔除；同名条目加序号；全被剔除返回 400。</p>
 */
@Service
@RequiredArgsConstructor
public class FileBatchDownloadService {

    private final BizFileMapper fileMapper;
    private final FolderPermissionService folderPermissionService;
    private final FileStorageService fileStorageService;

    public void batchDownload(List<Long> fileIds, jakarta.servlet.http.HttpServletResponse response) {
        FileBatchSupport.requireBatchLimit(fileIds);
        List<BizFile> allowed = collectDownloadable(fileIds, SecurityUtils.getCurrentUserId());
        if (allowed.isEmpty()) throw new BusinessException("无可下载文件（不存在或无下载权限）");
        try {
            writeZip(allowed, response);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(FileBatchDownloadService.class)
                    .warn("批量下载打包失败: {}", e.getMessage());
            throw new BusinessException("批量下载失败，请重试");
        }
    }

    private List<BizFile> collectDownloadable(List<Long> fileIds, Long userId) {
        List<BizFile> allowed = new ArrayList<>();
        for (Long id : fileIds == null ? List.<Long>of() : fileIds) {
            BizFile f = fileMapper.selectById(id);
            if (isDownloadable(f, id, userId)) allowed.add(f);
        }
        return allowed;
    }

    /** 存在 + 有下载权限 + 内容仍在存储；三者任一不满足即静默剔除。 */
    private boolean isDownloadable(BizFile f, Long id, Long userId) {
        return f != null
                && folderPermissionService.checkFile(id, FolderPermissionService.P_DOWNLOAD, userId)
                && fileStorageService.contentExists(f.getOssKey());
    }

    private void writeZip(List<BizFile> allowed, jakarta.servlet.http.HttpServletResponse response)
            throws java.io.IOException {
        response.setContentType("application/zip");
        String zipName = "files-" + java.time.LocalDate.now() + ".zip";
        String encoded = java.net.URLEncoder.encode(zipName, java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);
        java.util.Set<String> used = new java.util.HashSet<>();
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(response.getOutputStream())) {
            for (BizFile f : allowed) {
                // 【V2026.11.14.3 P2】条目名清洗: 路径穿越(..)/分隔符/控制字符 → '_'(zip slip 防线)
                String unique = uniqueEntryName(sanitizeZipEntryName(f.getFileName(), f.getId()), used);
                zos.putNextEntry(new java.util.zip.ZipEntry(unique));
                fileStorageService.writeStoredContentTo(f.getOssKey(), zos);
                zos.closeEntry();
            }
        }
    }

    /** 【V2026.11.14.3 P2】zip 条目名清洗: 路径穿越(..)/分隔符/控制字符 → '_'; 空名回退 file-{id}。 */
    private String sanitizeZipEntryName(String name, Long fallbackId) {
        String n = name == null || name.isBlank() ? ("file-" + fallbackId) : name;
        n = n.replaceAll("\\p{Cntrl}", "_").replace("..", "_").replace("/", "_").replace("\\", "_").trim();
        return n.isBlank() ? ("file-" + fallbackId) : n;
    }

    private String uniqueEntryName(String base, java.util.Set<String> used) {
        String unique = base;
        int i = 1;
        while (!used.add(unique)) {
            unique = base.replaceFirst("(\\.[^.]+)?$",
                    base.matches(".*\\.[^.]+") ? "-" + i + "$1" : "-" + i);
            i++;
        }
        return unique;
    }
}
