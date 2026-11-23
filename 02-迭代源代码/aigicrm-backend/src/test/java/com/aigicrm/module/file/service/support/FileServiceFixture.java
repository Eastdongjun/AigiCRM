package com.aigicrm.module.file.service.support;

import com.aigicrm.config.OssConfig;
import com.aigicrm.module.file.mapper.BizFileFolderMapper;
import com.aigicrm.module.file.mapper.BizFileMapper;
import com.aigicrm.module.file.mapper.BizFileShareMapper;
import com.aigicrm.module.file.mapper.BizFileVersionMapper;
import com.aigicrm.module.file.service.FileEvidenceGuard;
import com.aigicrm.module.file.service.FileFolderService;
import com.aigicrm.module.file.service.FileService;
import com.aigicrm.module.file.service.FileShareService;
import com.aigicrm.module.file.service.FileStorageService;
import com.aigicrm.module.file.service.FileThumbnailService;
import com.aigicrm.module.file.service.FileVersionService;
import com.aigicrm.module.file.service.FolderPermissionService;
import com.aigicrm.module.incentive.mapper.BizIncentiveRecordMapper;
import com.aigicrm.module.incentive.service.IncentiveService;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.module.system.service.SysConfigService;
import com.aigicrm.security.SecurityAuditService;
import com.aliyun.oss.OSS;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * FileService 测试装配单一入口。S5.3 拆分期间只改本文件，测试调用点不动。
 */
public final class FileServiceFixture {

    private FileServiceFixture() {
    }

    public static FileService create(
            BizFileMapper fileMapper,
            BizFileFolderMapper folderMapper,
            BizFileShareMapper fileShareMapper,
            BizFileVersionMapper fileVersionMapper,
            SysConfigService configService,
            BizIncentiveRecordMapper incentiveRecordMapper,
            SysUserMapper userMapper,
            ObjectProvider<OSS> ossClientProvider,
            OssConfig ossConfig,
            FolderPermissionService folderPermissionService,
            SecurityAuditService securityAudit,
            JdbcTemplate jdbc,
            IncentiveService incentiveService) {
        FileShareService fileShareService = new FileShareService(fileMapper, fileShareMapper, userMapper,
                folderPermissionService, jdbc);
        FileFolderService fileFolderService = new FileFolderService(folderMapper, fileMapper, userMapper,
                folderPermissionService);
        FileStorageService fileStorageService = new FileStorageService(fileMapper, userMapper,
                folderPermissionService, fileFolderService, ossClientProvider, ossConfig, null);
        FileThumbnailService fileThumbnailService = new FileThumbnailService();
        FileEvidenceGuard fileEvidenceGuard = new FileEvidenceGuard(incentiveRecordMapper);
        FileVersionService fileVersionService = new FileVersionService(fileVersionMapper, fileMapper, configService,
                jdbc, folderPermissionService, fileStorageService, fileThumbnailService, userMapper,
                fileEvidenceGuard);
        return new FileService(fileMapper, folderMapper, fileShareMapper, fileVersionMapper,
                incentiveRecordMapper, userMapper, folderPermissionService, securityAudit, jdbc, incentiveService,
                fileShareService, fileFolderService, fileStorageService, fileThumbnailService,
                fileEvidenceGuard, fileVersionService);
    }

    /** 测试需要改 storageType/uploadDir 等 @Value 字段时，拿到真实的存储 bean。 */
    public static FileStorageService storageOf(FileService service) {
        return (FileStorageService) org.springframework.test.util.ReflectionTestUtils
                .getField(service, "fileStorageService");
    }

    /** 测试需要隔离缩略图缓存目录时，拿到真实的缩略图 bean。 */
    public static FileThumbnailService thumbnailOf(FileService service) {
        return (FileThumbnailService) org.springframework.test.util.ReflectionTestUtils
                .getField(service, "fileThumbnailService");
    }
}
