package com.aigicrm.module.file.service;

import com.aliyun.oss.OSS;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.aigicrm.common.BusinessException;
import com.aigicrm.config.OssConfig;
import com.aigicrm.module.file.entity.BizFile;
import com.aigicrm.module.file.entity.BizFileFolder;
import com.aigicrm.module.file.entity.BizFileShare;
import com.aigicrm.module.file.mapper.BizFileFolderMapper;
import com.aigicrm.module.file.mapper.BizFileMapper;
import com.aigicrm.module.file.mapper.BizFileShareMapper;
import com.aigicrm.module.file.mapper.BizFolderAclMapper;
import com.aigicrm.module.incentive.entity.BizIncentiveRecord;
import com.aigicrm.module.incentive.mapper.BizIncentiveRecordMapper;
import com.aigicrm.module.system.entity.SysUser;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.security.SecurityUtils;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
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
    private final BizFolderAclMapper folderAclMapper = mock(BizFolderAclMapper.class);
    private final FileService svc = new FileService(fileMapper, folderMapper, fileShareMapper, folderAclMapper,
            incentiveRecordMapper, userMapper, providerOf(ossClient), ossConfig, folderPermissionService);

    @BeforeEach
    void setUp() {
        // 绕过 @Value 注入：storageType 固定 local（与生产默认一致，使存储走真实本地落盘路径，替换/下载用例可验证文件内容），
        // uploadDir 指向临时目录，避免下载路径 Paths.get(null,...) 依赖异常吞掉的巧合
        ReflectionTestUtils.setField(svc, "storageType", "local");
        ReflectionTestUtils.setField(svc, "uploadDir", System.getProperty("java.io.tmpdir"));
        // 纯 mock 环境初始化 Lambda 缓存，使 LambdaQueryWrapper.getSqlSegment() 可用
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), BizFile.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), BizFileShare.class);
    }

    private static ObjectProvider<OSS> providerOf(OSS client) {
        return new ObjectProvider<>() {
            @Override public OSS getObject(Object... args) { return client; }
            @Override public OSS getIfAvailable() { return client; }
            @Override public OSS getIfUnique() { return client; }
            @Override public OSS getIfAvailable(java.util.function.Supplier<OSS> supplier) { return client; }
            @Override public OSS getIfUnique(java.util.function.Supplier<OSS> supplier) { return client; }
        };
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
            verify(fileMapper, never()).insert(any(BizFile.class));
        }
    }

    @Test
    void sharedToMe_filtersByRecipientAndExcludesDeletedFiles() {
        BizFileShare share = new BizFileShare();
        share.setFileId(9L);
        share.setSharedToUserId(2L);
        when(fileShareMapper.selectList(any())).thenReturn(List.of(share));
        BizFile visible = new BizFile();
        visible.setId(9L);
        visible.setDeleted(0);
        visible.setFileName("共享方案.pdf");
        Page<BizFile> page = new Page<>(1, 10);
        page.setTotal(1);
        page.setRecords(List.of(visible));
        when(fileMapper.selectPage(any(), any())).thenReturn(page);

        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(2L);
            IPage<BizFile> result = svc.pageSharedToMe(1, 10, null);
            assertEquals(1L, result.getTotal());
            assertEquals("共享方案.pdf", result.getRecords().get(0).getFileName());
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Wrapper<BizFile>> captor = ArgumentCaptor.forClass(Wrapper.class);
            verify(fileMapper).selectPage(any(), captor.capture());
            assertTrue(captor.getValue().getExpression().getSqlSegment().contains("deleted"));
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
            verify(folderMapper, never()).insert(any(BizFileFolder.class));
        }
    }

    @Test
    void createFolder_rootParentIdZero_private_nonAdmin_succeeds() {
        // 【体检 H3 2026-10-12】parentId=0 是根级哨兵(DDL 默认值, buildFolderTree 以 0 为根):
        // PC(selectedFolderId||0) 与 MP(根级传 0) 均传 0, 此前被当真实父夹校验 check(0)→403,
        // 非 admin 建根级私人夹恒败。归一化后 0=根级, 不得触发父夹权限校验
        BizFileFolder folder = new BizFileFolder();
        folder.setFolderType("private");
        folder.setParentId(0L);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(2L);
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            svc.createFolder(folder);
            verify(folderPermissionService, never()).check(org.mockito.ArgumentMatchers.anyLong(),
                    org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong());
            verify(folderMapper).insert(folder);
            // 归一化为 null 后 insert 由 MP 省略 null 字段落列默认 0, 与存量根夹哨兵一致
            assertNull(folder.getParentId());
        }
    }

    @Test
    void createFolder_rootParentIdZero_public_nonAdmin_keepsAdminGate() {
        // 【体检 H3 2026-10-12】归一化不放宽根级公共夹管理员门槛(与 parentId=null 同规则)
        BizFileFolder folder = new BizFileFolder();
        folder.setFolderType("public");
        folder.setParentId(0L);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(2L);
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            BusinessException ex = assertThrows(BusinessException.class, () -> svc.createFolder(folder));
            assertEquals("公共文件夹只有管理员才能创建", ex.getMessage());
            verify(folderMapper, never()).insert(any(BizFileFolder.class));
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
            verify(folderMapper, never()).updateById(any(BizFileFolder.class));
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
    void download_inFolder_downloadableOff_returns403() {
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
            svc.downloadById(9L, null, false, response);
            verify(response).setStatus(403);
        }
    }

    @Test
    void download_inFolder_downloadableOn_passesPermissionGates() {
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
            svc.downloadById(9L, null, false, response);
            // 未在权限/开关处拦截：落到文件不存在 → 404，而不是 403
            verify(response).setStatus(404);
            verify(response, never()).setStatus(403);
        }
    }

    @Test
    void upload_aclGranted_uploadSucceeds() throws Exception {
        // 【2026-10-09 配额原子化】上传配额改条件扣减(WHERE used+size<=quota), 桩随新交互
        when(userMapper.tryConsumeQuota(eq(1L), anyLong())).thenReturn(1);
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
    void download_sharedRecipient_allowedWhenSwitchOn() {
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
            svc.downloadById(9L, null, false, response);
            verify(response).setStatus(404);
            verify(response, never()).setStatus(403);
        }
    }

    @Test
    void download_admin_bypassesDownloadableSwitch() {
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
            svc.downloadById(9L, null, false, response);
            verify(response).setStatus(404);
            verify(response, never()).setStatus(403);
        }
    }

    @Test
    void download_managerOfOwner_allowedWhenSwitchOn() {
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
            svc.downloadById(9L, null, false, response);
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
        IPage<BizFile> result = svc.pageFiles(1, 10, 100L, "private", null, 2L);
        assertEquals(1, result.getTotal());
        assertEquals(9L, result.getRecords().get(0).getUploaderId());
        verify(fileMapper).selectPage(any(), any());
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

    // ====== Task 6：覆盖替换接口 ======

    private BizFile aReplaceableFile() {
        BizFile file = new BizFile();
        file.setId(9L);
        file.setFolderId(100L);
        file.setUploaderId(1L);
        file.setFileScope("private");
        file.setOssKey("private/1/old.txt");
        file.setFileName("old.txt");
        file.setFileSize(10L);
        file.setVersion(1);
        return file;
    }

    @Test
    void replaceFile_withoutEditPermission_throws403() {
        when(fileMapper.selectById(9L)).thenReturn(aReplaceableFile());
        MultipartFile mf = new MockMultipartFile("file", "new.txt", "text/plain", new byte[25]);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(2L);
            when(folderPermissionService.checkFile(9L, FolderPermissionService.P_EDIT, 2L)).thenReturn(false);
            BusinessException ex = assertThrows(BusinessException.class, () -> svc.replaceFile(9L, mf));
            assertEquals(403, ex.getCode());
            verify(fileMapper, never()).updateById(any(BizFile.class));
            verify(userMapper, never()).updateById(any(SysUser.class));
        }
    }

    @Test
    void replaceFile_fileNotFound_throws() {
        when(fileMapper.selectById(9L)).thenReturn(null);
        MultipartFile mf = new MockMultipartFile("file", "new.txt", "text/plain", new byte[25]);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            BusinessException ex = assertThrows(BusinessException.class, () -> svc.replaceFile(9L, mf));
            assertEquals("文件不存在", ex.getMessage());
            verify(folderPermissionService, never()).checkFile(any(), any(), any());
            verify(fileMapper, never()).updateById(any(BizFile.class));
        }
    }

    @Test
    void replaceFile_success_versionIncrementsAndStorageDelta() {
        when(fileMapper.selectById(9L)).thenReturn(aReplaceableFile());
        MultipartFile mf = new MockMultipartFile("file", "new.txt", "text/plain", new byte[25]);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            when(folderPermissionService.checkFile(9L, FolderPermissionService.P_EDIT, 1L)).thenReturn(true);
            BizFile result = svc.replaceFile(9L, mf);
            // 版本+1 / replace_by / replaced_at / file_size / oss_key 更新
            assertEquals(2, result.getVersion());
            assertEquals(1L, result.getReplaceBy());
            assertNotNull(result.getReplacedAt());
            assertEquals(25L, result.getFileSize());
            assertNotEquals("private/1/old.txt", result.getOssKey());
            assertTrue(result.getOssKey().startsWith("private/1/"));
            ArgumentCaptor<BizFile> fileCap = ArgumentCaptor.forClass(BizFile.class);
            verify(fileMapper).updateById(fileCap.capture());
            assertNotEquals("private/1/old.txt", fileCap.getValue().getOssKey());
            assertTrue(fileCap.getValue().getOssKey().startsWith("private/1/"));
            // 【2026-10-09 配额原子化】存储差额改 DB 端原子增减: delta = 25 - 10 = +15
            verify(userMapper).adjustStorageUsed(eq(1L), eq(15L));
            verify(userMapper, never()).updateById(any(SysUser.class));
        }
    }

    @Test
    void replaceFile_afterReplace_downloadReturnsNewContent() throws Exception {
        when(fileMapper.selectById(9L)).thenReturn(aReplaceableFile());
        BizFileFolder folder = new BizFileFolder();
        folder.setId(100L);
        folder.setDownloadable(1);
        when(folderMapper.selectById(100L)).thenReturn(folder);
        String newContent = "new content v2";
        MultipartFile mf = new MockMultipartFile("file", "new.txt", "text/plain",
                newContent.getBytes(StandardCharsets.UTF_8));
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            when(folderPermissionService.checkFile(9L, FolderPermissionService.P_EDIT, 1L)).thenReturn(true);
            svc.replaceFile(9L, mf);
            // 替换后同一 fileId 下载返回新内容（走更新后的 ossKey）
            MockHttpServletResponse response = new MockHttpServletResponse();
            svc.downloadById(9L, null, false, response);
            assertEquals(200, response.getStatus());
            assertEquals(newContent, response.getContentAsString());
        }
    }

    // ====== 最终修复轮（I-1 文件夹管理接入 ACL 判定 / M-1 删除级联 / M-2② ossUrl 修正）======

    @Test
    void renameFolder_aclGranted_succeeds() {
        // I-1：ACL 授权角色重命名成功——check(P_EDIT)=true 即放行，
        // 不再被遗留 checkFolderManagePermission（"只能管理自己的文件夹"）拦截
        BizFileFolder folder = new BizFileFolder();
        folder.setId(100L);
        folder.setFolderType("private");
        folder.setOwnerId(99L);
        when(folderMapper.selectById(100L)).thenReturn(folder);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(2L);
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            when(folderPermissionService.check(100L, FolderPermissionService.P_EDIT, 2L)).thenReturn(true);
            svc.renameFolder(100L, "新名字");
            ArgumentCaptor<BizFileFolder> cap = ArgumentCaptor.forClass(BizFileFolder.class);
            verify(folderMapper).updateById(cap.capture());
            assertEquals("新名字", cap.getValue().getFolderName());
        }
    }

    @Test
    void deleteFolder_aclGranted_succeedsAndCascadesAclDeletion() {
        // I-1 + M-1：ACL 授权角色删除成功（不再被遗留门槛拦截），
        // 且同步删除该文件夹的 biz_folder_acl 记录（设计 §8 删除级联）
        BizFileFolder folder = new BizFileFolder();
        folder.setId(100L);
        folder.setFolderType("private");
        folder.setOwnerId(99L);
        when(folderMapper.selectById(100L)).thenReturn(folder);
        when(fileMapper.selectCount(any())).thenReturn(0L);
        when(folderMapper.selectCount(any())).thenReturn(0L);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(2L);
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            when(folderPermissionService.check(100L, FolderPermissionService.P_DELETE, 2L)).thenReturn(true);
            svc.deleteFolder(100L);
            verify(folderMapper).deleteById(100L);
            verify(folderAclMapper).delete(any());
        }
    }

    @Test
    void deleteFolder_managerChainUser_deletesDownstreamPrivateFolder() {
        // I-1：上级链用户删除下级私有文件夹成功（check 由 isManagerChain 放行，模拟上级对下级的判定）
        BizFileFolder folder = new BizFileFolder();
        folder.setId(100L);
        folder.setFolderType("private");
        folder.setOwnerId(9L);
        when(folderMapper.selectById(100L)).thenReturn(folder);
        when(fileMapper.selectCount(any())).thenReturn(0L);
        when(folderMapper.selectCount(any())).thenReturn(0L);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(2L);
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            when(folderPermissionService.check(100L, FolderPermissionService.P_DELETE, 2L)).thenReturn(true);
            svc.deleteFolder(100L);
            verify(folderMapper).deleteById(100L);
        }
    }

    @Test
    void createFolder_publicSubfolder_aclGranted_succeeds() {
        // I-1：非管理员在已授权（ACL upload）公共父夹下创建公共子文件夹成功
        BizFileFolder folder = new BizFileFolder();
        folder.setFolderType("public");
        folder.setParentId(100L);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(2L);
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            when(folderPermissionService.check(100L, FolderPermissionService.P_UPLOAD, 2L)).thenReturn(true);
            svc.createFolder(folder);
            verify(folderMapper).insert(folder);
        }
    }

    @Test
    void createFolder_publicRoot_nonAdmin_throws403() {
        // I-1：根级公共文件夹（无 parentId）仍保留管理员门槛
        BizFileFolder folder = new BizFileFolder();
        folder.setFolderType("public");
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            BusinessException ex = assertThrows(BusinessException.class, () -> svc.createFolder(folder));
            assertEquals("公共文件夹只有管理员才能创建", ex.getMessage());
            verify(folderMapper, never()).insert(any(BizFileFolder.class));
        }
    }

    @Test
    void replaceFile_withoutOriginalFilename_ossUrlStillUpdated() {
        // M-2②：originalFilename==null 时 ossUrl 也更新为新 key 的 URL（不保留指向已删旧文件的旧值）
        when(fileMapper.selectById(9L)).thenReturn(aReplaceableFile());
        SysUser user = new SysUser();
        user.setId(1L);
        user.setStorageUsed(100L);
        user.setStorageQuota(1024L * 1024L);
        when(userMapper.selectById(1L)).thenReturn(user);
        MultipartFile mf = new MockMultipartFile("file", (String) null, "text/plain", new byte[25]);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            when(folderPermissionService.checkFile(9L, FolderPermissionService.P_EDIT, 1L)).thenReturn(true);
            BizFile result = svc.replaceFile(9L, mf);
            assertNotNull(result.getOssUrl());
            assertTrue(result.getOssUrl().contains(result.getOssKey()), "ossUrl 应指向新 key: " + result.getOssUrl());
        }
    }

    // ====== 激励下载放开：shared=1 激励记录中的文件"列表可见即可下载"（与 pageFiles incentive 分支同规则）======

    private BizFile anIncentiveFile(Long id, Long uploaderId) {
        BizFile file = new BizFile();
        file.setId(id);
        file.setFolderId(null);
        file.setUploaderId(uploaderId);
        file.setFileScope("incentive");
        file.setOssKey("incentive/" + uploaderId + "/x.txt");
        return file;
    }

    @Test
    void canDownloadFile_incentiveInSharedRecord_visibleToOthers() {
        // 激励文件在 shared=1 的他人记录 file_ids 中 → 非上传者可下载（销售总监下载下属激励材料）
        // 变量名避开受限标识符 record（SonarLint S6213）
        BizIncentiveRecord incRecord = new BizIncentiveRecord();
        incRecord.setUserId(3L);
        incRecord.setShared(1);
        incRecord.setFileIds(List.of(9L));
        when(incentiveRecordMapper.selectList(any())).thenReturn(List.of(incRecord));
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            assertTrue(svc.canDownloadFile(anIncentiveFile(9L, 1L), 2L));
        }
    }

    @Test
    void canDownloadFile_incentiveInSharedRecord_trackSlotVisible() {
        // 激励文件出现在他人共享记录的 file_track_ids 槽位中 → 同样可见可下载（五个槽位任一命中）
        BizIncentiveRecord incRecord = new BizIncentiveRecord();
        incRecord.setUserId(3L);
        incRecord.setShared(1);
        incRecord.setFileTrackIds(List.of(9L));
        when(incentiveRecordMapper.selectList(any())).thenReturn(List.of(incRecord));
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            assertTrue(svc.canDownloadFile(anIncentiveFile(9L, 1L), 2L));
        }
    }

    @Test
    void download_incentiveNotInSharedRecord_returns403() {
        // 激励文件不在任何 shared 记录中（且非本人/被分享/管理员）→ 非上传者下载 403
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(fileMapper.selectById(9L)).thenReturn(anIncentiveFile(9L, 1L));
        when(incentiveRecordMapper.selectList(any())).thenReturn(List.of());
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(2L);
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            svc.downloadById(9L, null, false, response);
            verify(response).setStatus(403);
        }
    }

    @Test
    void canDownloadFile_incentiveOwnUpload_stillAllowed() {
        // 本人上传的激励文件仍可下载（canAccessFile 路径不回归，不触发 shared 记录查询）
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            assertTrue(svc.canDownloadFile(anIncentiveFile(9L, 1L), 1L));
            verify(incentiveRecordMapper, never()).selectList(any());
        }
    }

    // ===== 安全 2026-08-08：上传路径穿越防护 =====

    @Test
    void sanitizeFilename_blocksPathTraversal() {
        // Unix 绝对/相对路径 → 只保留文件名
        assertEquals("passwd", FileService.sanitizeFilename("../../etc/passwd"));
        assertEquals("passwd", FileService.sanitizeFilename("/etc/passwd"));
        // Windows 路径分隔符 → 只保留最后一段
        assertEquals("x.exe", FileService.sanitizeFilename("C:\\..\\..\\evil\\x.exe"));
        // 纯 .. 序列 → 兜底 unnamed
        assertEquals("unnamed", FileService.sanitizeFilename("../../.."));
        // 含 .. 的混合文件名 → 移除 .. 但保留文件名
        assertEquals("a1b.pdf", FileService.sanitizeFilename("a..1b.pdf"));
        // 正常文件名不受影响
        assertEquals("报告.pdf", FileService.sanitizeFilename("报告.pdf"));
        // null → 兜底
        assertEquals("unnamed", FileService.sanitizeFilename(null));
    }

    // ====== 上传类型断言负向(GPT三轮 FILE-P1-008): 无扩展名必须拒绝, 对齐 javadoc 声明 ======
    @org.junit.jupiter.api.Test
    void uploadType_noExtension_rejected() {
        org.junit.jupiter.api.Assertions.assertThrows(com.aigicrm.common.BusinessException.class,
                () -> FileService.assertAllowedUploadType("unnamed", "application/octet-stream"));
        org.junit.jupiter.api.Assertions.assertThrows(com.aigicrm.common.BusinessException.class,
                () -> FileService.assertAllowedUploadType("报告", null));
        // 白名单内正常类型仍放行(不抛)
        FileService.assertAllowedUploadType("报告.pdf", "application/pdf");
        // 白名单外有扩展名仍拒绝
        org.junit.jupiter.api.Assertions.assertThrows(com.aigicrm.common.BusinessException.class,
                () -> FileService.assertAllowedUploadType("evil.exe", "application/x-msdownload"));
    }

    // ====== 缩略图子采样解码回归（2026-08-09 生产 OOM 复盘）======
    // 原实现 ImageIO.read() 整图解码 5MB+ JPEG（~96MB/张），激励查询页首屏并发生成时撑爆 -Xmx512m 堆 → 500。
    // 修复改为 ImageReader 子采样解码（只解码 ~2×目标像素）。以下用例锁死大图/PNG透明/小图/非图片各分支行为。
    @Test
    void writeThumbnail_largeJpeg_subsamplingReturns300px() throws Exception {
        java.nio.file.Path f = java.nio.file.Files.createTempFile("thumb-big", ".jpg");
        java.awt.image.BufferedImage big = new java.awt.image.BufferedImage(4000, 3000, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = big.createGraphics();
        g.setPaint(new java.awt.GradientPaint(0, 0, java.awt.Color.RED, 4000, 3000, java.awt.Color.BLUE));
        g.fillRect(0, 0, 4000, 3000);
        g.dispose();
        javax.imageio.ImageIO.write(big, "jpg", f.toFile());

        MockHttpServletResponse resp = new MockHttpServletResponse();
        Boolean ok = ReflectionTestUtils.invokeMethod(svc, "writeThumbnail", f, "big.jpg", resp);
        assertNotNull(ok);
        assertTrue(ok);
        assertEquals("image/jpeg", resp.getContentType());
        java.awt.image.BufferedImage thumb = javax.imageio.ImageIO.read(new ByteArrayInputStream(resp.getContentAsByteArray()));
        assertTrue(thumb.getWidth() <= 300 && thumb.getHeight() <= 300);
        assertEquals(4000.0 / 3000.0, (double) thumb.getWidth() / thumb.getHeight(), 0.05);
        java.nio.file.Files.deleteIfExists(f);
    }

    @Test
    void writeThumbnail_largePng_preservesTransparencyFormat() throws Exception {
        java.nio.file.Path f = java.nio.file.Files.createTempFile("thumb-big", ".png");
        java.awt.image.BufferedImage big = new java.awt.image.BufferedImage(3500, 3500, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = big.createGraphics();
        g.setColor(new java.awt.Color(0, 0, 0, 0)); // 全透明背景
        g.fillRect(0, 0, 3500, 3500);
        g.dispose();
        javax.imageio.ImageIO.write(big, "png", f.toFile());

        MockHttpServletResponse resp = new MockHttpServletResponse();
        Boolean ok = ReflectionTestUtils.invokeMethod(svc, "writeThumbnail", f, "big.png", resp);
        assertNotNull(ok);
        assertTrue(ok);
        assertEquals("image/png", resp.getContentType());
        java.awt.image.BufferedImage thumb = javax.imageio.ImageIO.read(new ByteArrayInputStream(resp.getContentAsByteArray()));
        assertTrue(thumb.getWidth() <= 300 && thumb.getHeight() <= 300);
        assertTrue(thumb.getColorModel().hasAlpha());
        java.nio.file.Files.deleteIfExists(f);
    }

    /** 【文件回收站 2026-10-18】删除只做逻辑删, 物理内容必须保留供回收站恢复 */
    @Test
    void deleteFile_keepsContentForRecycle() throws Exception {
        java.nio.file.Path content = java.nio.file.Paths.get(
                System.getProperty("java.io.tmpdir"), "recycle-keep.txt");
        java.nio.file.Files.writeString(content, "keep-for-recycle");

        BizFile file = new BizFile();
        file.setId(9L);
        file.setFileScope("public");
        file.setOssKey("recycle-keep.txt");
        file.setFileName("recycle-keep.txt");
        when(fileMapper.selectById(9L)).thenReturn(file);

        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(true);
            m.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            svc.deleteFile(9L);
        }

        assertTrue(java.nio.file.Files.exists(content), "删除只进回收站，物理内容应保留");
        verify(fileMapper).deleteById(9L);
        java.nio.file.Files.deleteIfExists(content);
    }

    @Test
    void writeThumbnail_smallImage_passesThrough() throws Exception {
        java.nio.file.Path f = java.nio.file.Files.createTempFile("thumb-small", ".png");
        java.awt.image.BufferedImage small = new java.awt.image.BufferedImage(120, 80, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = small.createGraphics();
        g.setColor(java.awt.Color.RED);
        g.fillRect(0, 0, 120, 80);
        g.dispose();
        javax.imageio.ImageIO.write(small, "png", f.toFile());

        MockHttpServletResponse resp = new MockHttpServletResponse();
        Boolean ok = ReflectionTestUtils.invokeMethod(svc, "writeThumbnail", f, "small.png", resp);
        assertNotNull(ok);
        assertTrue(ok);
        java.awt.image.BufferedImage thumb = javax.imageio.ImageIO.read(new ByteArrayInputStream(resp.getContentAsByteArray()));
        assertEquals(120, thumb.getWidth());
        assertEquals(80, thumb.getHeight());
        java.nio.file.Files.deleteIfExists(f);
    }

    @Test
    void writeThumbnail_nonImage_returnsFalse() throws Exception {
        java.nio.file.Path f = java.nio.file.Files.createTempFile("doc", ".txt");
        java.nio.file.Files.writeString(f, "not an image");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        Boolean ok = ReflectionTestUtils.invokeMethod(svc, "writeThumbnail", f, "doc.txt", resp);
        assertNotNull(ok);
        assertFalse(ok);
        java.nio.file.Files.deleteIfExists(f);
    }

    /** 【决策5 2026-10-18 用户裁决·佐证锁定】被任一激励记录槽位引用的文件=计分佐证,禁替换(防先传A得分再换B) */
    @Test
    void replaceFile_incentiveEvidenceFile_rejected() {
        when(fileMapper.selectById(9L)).thenReturn(aReplaceableFile());
        MultipartFile mf = new MockMultipartFile("file", "new.txt", "text/plain", new byte[25]);
        BizIncentiveRecord evidence = new BizIncentiveRecord();
        evidence.setFilePreIds(java.util.List.of(9L)); // 文件9挂在"安装前"槽
        when(incentiveRecordMapper.selectList(any())).thenReturn(java.util.List.of(evidence));
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            when(folderPermissionService.checkFile(9L, FolderPermissionService.P_EDIT, 1L)).thenReturn(true);
            BusinessException ex = assertThrows(BusinessException.class, () -> svc.replaceFile(9L, mf));
            assertTrue(ex.getMessage().contains("激励计分佐证"), "实际: " + ex.getMessage());
            verify(fileMapper, never()).updateById(any(BizFile.class));
        }
    }

    /** 佐证锁定不误伤: 无激励记录引用的普通文件照常可替换(存量成功用例的守卫回归) */
    @Test
    void replaceFile_nonEvidenceFile_guardSilent() {
        when(fileMapper.selectById(9L)).thenReturn(aReplaceableFile());
        MultipartFile mf = new MockMultipartFile("file", "new.txt", "text/plain", new byte[25]);
        when(incentiveRecordMapper.selectList(any())).thenReturn(java.util.List.of());
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            when(folderPermissionService.checkFile(9L, FolderPermissionService.P_EDIT, 1L)).thenReturn(true);
            // 走到存储写入层(即已通过佐证守卫)即算通过——本地落盘成功后 updateById 必被调用
            svc.replaceFile(9L, mf);
            verify(fileMapper).updateById(any(BizFile.class));
        }
    }
}
