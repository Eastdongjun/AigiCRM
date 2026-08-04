package com.ycmn.module.file.service;

import com.aliyun.oss.OSS;
import com.ycmn.common.BusinessException;
import com.ycmn.config.OssConfig;
import com.ycmn.module.file.entity.BizFile;
import com.ycmn.module.file.entity.BizFileFolder;
import com.ycmn.module.file.mapper.BizFileFolderMapper;
import com.ycmn.module.file.mapper.BizFileMapper;
import com.ycmn.module.file.mapper.BizFileShareMapper;
import com.ycmn.module.incentive.mapper.BizIncentiveRecordMapper;
import com.ycmn.module.system.entity.SysUser;
import com.ycmn.module.system.mapper.SysUserMapper;
import com.ycmn.security.SecurityUtils;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 【文件权限体系 Task 4】FileService 权限校验回归测试：
 * 无权限 upload/createFolder/renameFolder/deleteFile → 403；下载开关关闭 → 403；
 * ACL 授权成功路径（mock FolderPermissionService 返回 true）。
 */
class FileServicePermissionTest {

    private final BizFileMapper fileMapper = mock(BizFileMapper.class);
    private final BizFileFolderMapper folderMapper = mock(BizFileFolderMapper.class);
    private final BizFileShareMapper fileShareMapper = mock(BizFileShareMapper.class);
    private final BizIncentiveRecordMapper incentiveRecordMapper = mock(BizIncentiveRecordMapper.class);
    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final OSS ossClient = mock(OSS.class);
    private final OssConfig ossConfig = mock(OssConfig.class);
    private final FolderPermissionService folderPermissionService = mock(FolderPermissionService.class);
    private final FileService svc = new FileService(fileMapper, folderMapper, fileShareMapper,
            incentiveRecordMapper, userMapper, ossClient, ossConfig, folderPermissionService);

    @BeforeEach
    void setUp() {
        // 绕过 @Value 注入，避免下载路径 Paths.get(null,...) 依赖异常吞掉的巧合
        ReflectionTestUtils.setField(svc, "uploadDir", System.getProperty("java.io.tmpdir"));
    }

    @Test
    void upload_withoutFolderUploadPermission_throws403() {
        MultipartFile mf = mock(MultipartFile.class);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            when(folderPermissionService.check(100L, FolderPermissionService.P_UPLOAD, 1L)).thenReturn(false);
            BusinessException ex = assertThrows(BusinessException.class, () -> svc.upload(mf, 100L, "private"));
            assertEquals(403, ex.getCode());
            verify(folderPermissionService).check(100L, FolderPermissionService.P_UPLOAD, 1L);
            verify(fileMapper, never()).insert(any());
        }
    }

    @Test
    void createFolder_withParentWithoutUploadPermission_throws403() {
        BizFileFolder folder = new BizFileFolder();
        folder.setFolderType("private");
        folder.setParentId(100L);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            when(folderPermissionService.check(100L, FolderPermissionService.P_UPLOAD, 1L)).thenReturn(false);
            BusinessException ex = assertThrows(BusinessException.class, () -> svc.createFolder(folder));
            assertEquals(403, ex.getCode());
            verify(folderMapper, never()).insert(any());
        }
    }

    @Test
    void renameFolder_withoutEditPermission_throws403() {
        BizFileFolder folder = new BizFileFolder();
        folder.setId(100L);
        folder.setFolderType("private");
        folder.setOwnerId(99L);
        when(folderMapper.selectById(100L)).thenReturn(folder);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(2L);
            when(folderPermissionService.check(100L, FolderPermissionService.P_EDIT, 2L)).thenReturn(false);
            BusinessException ex = assertThrows(BusinessException.class, () -> svc.renameFolder(100L, "新名字"));
            assertEquals(403, ex.getCode());
            verify(folderMapper, never()).updateById(any());
        }
    }

    @Test
    void deleteFile_inFolder_withoutDeletePermission_throws403() {
        BizFile file = new BizFile();
        file.setId(9L);
        file.setFolderId(100L);
        file.setUploaderId(1L);
        file.setFileScope("private");
        when(fileMapper.selectById(9L)).thenReturn(file);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(2L);
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            when(folderPermissionService.checkFile(9L, FolderPermissionService.P_DELETE, 2L)).thenReturn(false);
            BusinessException ex = assertThrows(BusinessException.class, () -> svc.deleteFile(9L));
            assertEquals(403, ex.getCode());
            verify(fileMapper, never()).deleteById(9L);
        }
    }

    @Test
    void download_inFolder_downloadableOff_returns403() throws Exception {
        BizFile file = new BizFile();
        file.setId(9L);
        file.setFolderId(100L);
        file.setUploaderId(1L);
        file.setFileScope("private");
        file.setOssKey("private/1/x.txt");
        BizFileFolder folder = new BizFileFolder();
        folder.setId(100L);
        folder.setDownloadable(0);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(fileMapper.selectById(9L)).thenReturn(file);
        when(folderMapper.selectById(100L)).thenReturn(folder);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            when(folderPermissionService.checkFile(9L, FolderPermissionService.P_DOWNLOAD, 1L)).thenReturn(true);
            svc.downloadById(9L, null, response);
            verify(response).setStatus(403);
        }
    }

    @Test
    void download_inFolder_downloadableOn_passesPermissionGates() throws Exception {
        BizFile file = new BizFile();
        file.setId(9L);
        file.setFolderId(100L);
        file.setUploaderId(1L);
        file.setFileScope("private");
        file.setOssKey("private/1/x.txt");
        BizFileFolder folder = new BizFileFolder();
        folder.setId(100L);
        folder.setDownloadable(1);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(fileMapper.selectById(9L)).thenReturn(file);
        when(folderMapper.selectById(100L)).thenReturn(folder);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            when(folderPermissionService.checkFile(9L, FolderPermissionService.P_DOWNLOAD, 1L)).thenReturn(true);
            svc.downloadById(9L, null, response);
            // 未在权限/开关处拦截：落到文件不存在 → 404，而不是 403
            verify(response).setStatus(404);
            verify(response, never()).setStatus(403);
        }
    }

    @Test
    void upload_aclGranted_uploadSucceeds() throws Exception {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setStorageUsed(0L);
        user.setStorageQuota(1024L * 1024 * 1024);
        when(userMapper.selectById(1L)).thenReturn(user);
        MultipartFile mf = new MockMultipartFile("file", "test.txt", "text/plain",
                new ByteArrayInputStream(new byte[0]));
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            when(folderPermissionService.check(100L, FolderPermissionService.P_UPLOAD, 1L)).thenReturn(true);
            BizFile result = svc.upload(mf, 100L, "private");
            assertNotNull(result);
            assertEquals("test.txt", result.getFileName());
            assertEquals(100L, result.getFolderId());
            assertEquals(1L, result.getUploaderId());
            verify(fileMapper).insert(any(BizFile.class));
        }
    }
}
