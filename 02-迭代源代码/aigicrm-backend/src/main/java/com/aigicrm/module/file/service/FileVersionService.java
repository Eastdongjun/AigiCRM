package com.aigicrm.module.file.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.module.file.entity.BizFile;
import com.aigicrm.module.file.entity.BizFileVersion;
import com.aigicrm.module.file.mapper.BizFileMapper;
import com.aigicrm.module.file.mapper.BizFileVersionMapper;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.module.system.service.SysConfigService;
import com.aigicrm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文件版本：列表、版本内容下载、回滚、按上限裁剪。
 *
 * <p>S5.3 从 {@link FileService} 抽出。回滚仍保留“现行内容先归档、再写回目标版本、
 * 配额差额原子调整”的原语义；佐证锁定走 {@link FileEvidenceGuard}，
 * 字节操作走 {@link FileStorageService}，缩略图失效走 {@link FileThumbnailService}。</p>
 */
@Service
@RequiredArgsConstructor
public class FileVersionService {

    private static final String LIT_X = "没有访问权限，请联系管理员授权";
    private static final String LIT_X_2 = "文件不存在";

    private final BizFileVersionMapper fileVersionMapper;
    private final BizFileMapper fileMapper;
    private final SysConfigService configService;
    private final JdbcTemplate jdbc;
    private final FolderPermissionService folderPermissionService;
    private final FileStorageService fileStorageService;
    private final FileThumbnailService fileThumbnailService;
    private final SysUserMapper userMapper;
    private final FileEvidenceGuard fileEvidenceGuard;

    /**
     * 【V2026.11.14.3 P1】版本上限裁剪: 保留最新 max-per-file 条, 超出旧版删内容+行(引用计数防误删)。
     * 止住"无限替换=无限磁盘"; 版本计入用户配额为第二版产品决策(本版不改配额口径)。
     */
    void pruneVersionsIfNeeded(Long fileId) {
        int max = resolveIntConfig("file.version.max-per-file", 20);
        if (max <= 0) return; // 0/负 = 不限制
        List<BizFileVersion> all = fileVersionMapper.listByFileId(fileId); // 新→旧
        if (all.size() <= max) return;
        BizFile main = fileMapper.selectById(fileId);
        String mainKey = main != null ? main.getOssKey() : null;
        for (int i = max; i < all.size(); i++) {
            BizFileVersion v = all.get(i);
            String key = v.getOssKey();
            boolean contentShared = key == null || key.isBlank()
                    || key.equals(mainKey)
                    || versionContentReferencedElsewhere(key, fileId, v.getId());
            // 行必须删: 否则共享 key 的旧版本会让 max-per-file 永远降不下来
            fileVersionMapper.deleteById(v.getId());
            if (!contentShared) deleteStoredContentAfterCommit(key);
        }
    }

    /** 版本字节是否仍被"当前主行之外"的活跃文件/其他版本引用(含同文件其他版本、其他文件版本)。 */
    private boolean versionContentReferencedElsewhere(String key, Long fileId, Long excludeVersionId) {
        Long active = jdbc.queryForObject(
                "SELECT COUNT(*) FROM biz_file WHERE oss_key = ? AND deleted = 0 AND id <> ?",
                Long.class, key, fileId);
        if (active != null && active > 0) return true;
        Long otherVersion = jdbc.queryForObject(
                "SELECT COUNT(*) FROM biz_file_version WHERE oss_key = ? AND id <> ?",
                Long.class, key, excludeVersionId);
        return otherVersion != null && otherVersion > 0;
    }

    /** 事务内登记 afterCommit 删除字节; 无事务(直调/单测)时立即删除。 */
    private void deleteStoredContentAfterCommit(String key) {
        if (key == null || key.isBlank()) return;
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            fileStorageService.deleteStoredContent(key);
                        }
                    });
        } else {
            fileStorageService.deleteStoredContent(key);
        }
    }

    private int resolveIntConfig(String key, int def) {
        try {
            String v = configService.getValue(key);
            return (v == null || v.isBlank()) ? def : Integer.parseInt(v.trim());
        } catch (Exception e) {
            return def;
        }
    }

    /** 版本列表(新→旧)。查看权限=checkFile view; 当前主行不在列表内(它就是"现行版")。 */
    public List<FileService.VersionRow> listVersions(Long fileId) {
        if (fileMapper.selectById(fileId) == null) throw new BusinessException(LIT_X_2);
        Long userId = SecurityUtils.getCurrentUserId();
        if (!folderPermissionService.checkFile(fileId, FolderPermissionService.P_VIEW, userId)) {
            throw new BusinessException(403, LIT_X);
        }
        return fileVersionMapper.listByFileId(fileId).stream().map(FileService.VersionRow::of).toList();
    }

    /** 版本内容下载(字节来自归档行, 与回收站详情下载同一存储通道)。 */
    public void downloadVersion(Long fileId, Integer versionNo,
                                jakarta.servlet.http.HttpServletResponse response) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (!folderPermissionService.checkFile(fileId, FolderPermissionService.P_DOWNLOAD, userId)) {
            throw new BusinessException(403, LIT_X);
        }
        BizFileVersion v = fileVersionMapper.byVersionNo(fileId, versionNo);
        if (v == null) throw new BusinessException("版本不存在");
        if (!fileStorageService.contentExists(v.getOssKey())) {
            throw new BusinessException("该版本内容已过保留期清除");
        }
        fileStorageService.writeStoredContent(v.getOssKey(),
                v.getFileName() != null ? v.getFileName() : "version-" + versionNo, v.getFileType(), response);
    }

    /**
     * 回滚到指定版本: 当前主行内容先归档(回滚前的样子不丢), 再把目标版本内容写回主行
     * (version+1, 配额差额原子调整)。佐证文件禁回滚(与 replace 同一锁定裁决)。
     */
    @Transactional
    public BizFile rollbackToVersion(Long fileId, Integer versionNo) {
        BizFile file = fileMapper.selectById(fileId);
        if (file == null) throw new BusinessException(LIT_X_2);
        Long userId = SecurityUtils.getCurrentUserId();
        if (!folderPermissionService.canRollbackVersion(file, userId)) {
            throw new BusinessException(403, LIT_X);
        }
        fileEvidenceGuard.assertNotIncentiveEvidence(fileId);
        BizFileVersion v = fileVersionMapper.byVersionNo(fileId, versionNo);
        if (v == null) throw new BusinessException("版本不存在");
        if (!fileStorageService.contentExists(v.getOssKey())) {
            throw new BusinessException("该版本内容已过保留期清除，无法回滚");
        }
        int curVersion = file.getVersion() == null ? 1 : file.getVersion();
        // ① 回滚前的现行内容归档(数据不丢; 与 replace 归档同构)
        BizFileVersion cur = new BizFileVersion();
        cur.setTenantId(1L);
        cur.setFileId(fileId);
        cur.setVersionNo(curVersion);
        cur.setOssKey(file.getOssKey());
        cur.setOssUrl(file.getOssUrl());
        cur.setFileSize(file.getFileSize());
        cur.setFileType(file.getFileType());
        cur.setFileName(file.getFileName());
        cur.setUploadedBy(userId);
        fileVersionMapper.insert(cur);
        // ② 目标版本写回主行(存储对象沿用归档行的 key——它自归档起一直存活)
        long oldSize = file.getFileSize() != null ? file.getFileSize() : 0L;
        file.setOssKey(v.getOssKey());
        file.setOssUrl(v.getOssUrl());
        file.setFileSize(v.getFileSize());
        file.setFileType(v.getFileType());
        file.setVersion(curVersion + 1);
        file.setReplaceBy(userId);
        file.setReplacedAt(LocalDateTime.now());
        fileMapper.updateById(file);
        fileThumbnailService.evictThumbnailCache(fileId);
        // 【V2026.11.14.3 P1】回滚归档后同样按上限裁剪
        pruneVersionsIfNeeded(fileId);
        if (FileService.quotaBearing(file.getFileScope()) && file.getUploaderId() != null) {
            long newSize = v.getFileSize() != null ? v.getFileSize() : 0L;
            userMapper.adjustStorageUsed(file.getUploaderId(), newSize - oldSize);
        }
        return file;
    }
}
