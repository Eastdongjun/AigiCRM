package com.ycmn.module.file.service;

import com.aliyun.oss.OSS;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
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
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.util.List;

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
        // 纯 mock 环境初始化 Lambda 缓存，使 LambdaQueryWrapper.getSqlSegment() 可用
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), BizFile.class);
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

    // ====== 修复轮（I-1/I-4/I-7）：下载语义 ======

    @Test
    void download_sharedRecipient_allowedWhenSwitchOn() throws Exception {
        // I-1：B 被 A 分享夹内文件，开关允许时 B 可下载（不被 checkFile 拦截）
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
        when(fileShareMapper.selectCount(any())).thenReturn(1L); // B 是被分享者
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(2L);
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            svc.downloadById(9L, null, response);
            verify(response).setStatus(404);
            verify(response, never()).setStatus(403);
        }
    }

    @Test
    void download_admin_bypassesDownloadableSwitch() throws Exception {
        // I-4b：管理员不受下载开关约束
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
            m.when(SecurityUtils::isAdmin).thenReturn(true);
            svc.downloadById(9L, null, response);
            verify(response).setStatus(404);
            verify(response, never()).setStatus(403);
        }
    }

    @Test
    void download_managerOfOwner_allowedWhenSwitchOn() throws Exception {
        // 上级链用户：canAccessFile 不通过（非上传者/非被分享），checkFile 走文件夹权限放行
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
        when(fileShareMapper.selectCount(any())).thenReturn(0L);
        when(folderPermissionService.checkFile(9L, FolderPermissionService.P_DOWNLOAD, 2L)).thenReturn(true);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(2L);
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            svc.downloadById(9L, null, response);
            verify(response).setStatus(404);
            verify(response, never()).setStatus(403);
        }
    }

    // ====== 修复轮（I-2/I-5/I-7）：列表可见性过滤 ======

    @Test
    void pageFiles_privateFolder_visibleToManager_returnsFiles() {
        // I-2：上级打开下级私有文件夹，列表不再强制 uploaderId=本人
        BizFileFolder folder = new BizFileFolder();
        folder.setId(100L);
        folder.setOwnerId(9L);
        when(folderMapper.selectList(any())).thenReturn(List.of(folder));
        when(folderPermissionService.isManagerChain(2L, 9L)).thenReturn(true);
        BizFile f = new BizFile();
        f.setId(1L);
        f.setFolderId(100L);
        f.setFileScope("private");
        f.setUploaderId(9L);
        Page<BizFile> p = new Page<>(1, 10, 1);
        p.setRecords(List.of(f));
        when(fileMapper.selectPage(any(), any())).thenReturn(p);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            IPage<BizFile> result = svc.pageFiles(1, 10, 100L, "private", null, 2L);
            assertEquals(1, result.getTotal());
            assertEquals(9L, result.getRecords().get(0).getUploaderId());
            verify(fileMapper).selectPage(any(), any());
        }
    }

    @Test
    void pageFiles_folderNotVisible_returnsEmptyPage() {
        // 非公共 scope 过滤生效：不可见文件夹 → 空页，不查文件
        BizFileFolder folder = new BizFileFolder();
        folder.setId(100L);
        folder.setOwnerId(9L);
        when(folderMapper.selectList(any())).thenReturn(List.of(folder));
        when(folderPermissionService.isManagerChain(2L, 9L)).thenReturn(false);
        when(folderPermissionService.permsFor(100L, 2L)).thenReturn(List.of());
        IPage<BizFile> result = svc.pageFiles(1, 10, 100L, "private", null, 2L);
        assertEquals(0, result.getTotal());
        verify(fileMapper, never()).selectPage(any(), any());
    }

    @Test
    void pageFiles_unknownScope_cannotEnumerateFolderFiles() {
        // I-5：scope 为空/未知时候选集为空，夹内文件一律不可见，封堵枚举
        when(folderMapper.selectList(any())).thenReturn(List.of());
        IPage<BizFile> result = svc.pageFiles(1, 10, 100L, null, null, 2L);
        assertEquals(0, result.getTotal());
        verify(fileMapper, never()).selectPage(any(), any());
    }

    @Test
    void pageFiles_privateFolderless_onlyOwnFiles() {
        // 私有 scope 无文件夹文件仍需本人上传（isNull(folder_id) AND uploader_id=本人）
        when(folderMapper.selectList(any())).thenReturn(List.of());
        Page<BizFile> p = new Page<>(1, 10, 0);
        when(fileMapper.selectPage(any(), any())).thenReturn(p);
        svc.pageFiles(1, 10, null, "private", null, 2L);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<BizFile>> cap = ArgumentCaptor.forClass(Wrapper.class);
        verify(fileMapper).selectPage(any(), cap.capture());
        String segment = cap.getValue().getSqlSegment();
        assertTrue(segment.contains("uploader_id"), "应约束本人上传: " + segment);
        assertTrue(segment.contains("folder_id IS NULL"), "应限定无文件夹文件: " + segment);
    }

    // ====== 修复轮 2/5（ossKey 下载路由 /download/** 与 downloadById 同权）======

    private BizFile aFolderFile(Long id, Long folderId, Long uploaderId) {
        BizFile file = new BizFile();
        file.setId(id);
        file.setFolderId(folderId);
        file.setUploaderId(uploaderId);
        file.setFileScope("private");
        file.setOssKey("private/" + uploaderId + "/x.txt");
        return file;
    }

    @Test
    void canDownloadFile_switchOff_deniedForNonAdmin() {
        // key 路由同权：非管理员 + 夹内文件开关关 → 拒绝
        BizFile file = aFolderFile(9L, 100L, 1L);
        BizFileFolder folder = new BizFileFolder();
        folder.setId(100L);
        folder.setDownloadable(0);
        when(folderMapper.selectById(100L)).thenReturn(folder);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            assertFalse(svc.canDownloadFile(file, 1L));
        }
    }

    @Test
    void canDownloadFile_switchOn_allowedForUploader() {
        BizFile file = aFolderFile(9L, 100L, 1L);
        BizFileFolder folder = new BizFileFolder();
        folder.setId(100L);
        folder.setDownloadable(1);
        when(folderMapper.selectById(100L)).thenReturn(folder);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            assertTrue(svc.canDownloadFile(file, 1L));
        }
    }

    @Test
    void canDownloadFile_shareeAllowedWhenSwitchOn() {
        // 被分享者：canAccessFile 通过，开关开 → 放行
        BizFile file = aFolderFile(9L, 100L, 1L);
        BizFileFolder folder = new BizFileFolder();
        folder.setId(100L);
        folder.setDownloadable(1);
        when(folderMapper.selectById(100L)).thenReturn(folder);
        when(fileShareMapper.selectCount(any())).thenReturn(1L);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            assertTrue(svc.canDownloadFile(file, 2L));
        }
    }

    @Test
    void canDownloadFile_adminBypassesSwitch() {
        BizFile file = aFolderFile(9L, 100L, 1L);
        BizFileFolder folder = new BizFileFolder();
        folder.setId(100L);
        folder.setDownloadable(0);
        when(folderMapper.selectById(100L)).thenReturn(folder);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(true);
            assertTrue(svc.canDownloadFile(file, 1L));
        }
    }

    @Test
    void canDownloadFile_managerChainViaCheckFile() {
        // 上级链用户：canAccessFile 不通过，checkFile 走文件夹权限放行（开关开）
        BizFile file = aFolderFile(9L, 100L, 1L);
        BizFileFolder folder = new BizFileFolder();
        folder.setId(100L);
        folder.setDownloadable(1);
        when(folderMapper.selectById(100L)).thenReturn(folder);
        when(fileShareMapper.selectCount(any())).thenReturn(0L);
        when(folderPermissionService.checkFile(9L, FolderPermissionService.P_DOWNLOAD, 2L)).thenReturn(true);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            assertTrue(svc.canDownloadFile(file, 2L));
        }
    }

    @Test
    void canDownloadFile_noPermission_denied() {
        BizFile file = aFolderFile(9L, 100L, 1L);
        when(fileShareMapper.selectCount(any())).thenReturn(0L);
        when(folderPermissionService.checkFile(9L, FolderPermissionService.P_DOWNLOAD, 2L)).thenReturn(false);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            assertFalse(svc.canDownloadFile(file, 2L));
        }
    }
}
