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
import org.junit.jupiter.api.AfterEach;
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
            incentiveRecordMapper, userMapper, providerOf(ossClient), ossConfig, folderPermissionService,
            mock(com.aigicrm.security.SecurityAuditService.class),
            mock(org.springframework.jdbc.core.JdbcTemplate.class),
            mock(com.aigicrm.module.incentive.service.IncentiveService.class)); // BIZ-007 删佐证联动重算 // 2026-10-27 shareFile 集团边界校验新增依赖

    private java.nio.file.Path thumbCacheDir;

    @BeforeEach
    void setUp() throws Exception {
        // 绕过 @Value 注入：storageType 固定 local（与生产默认一致，使存储走真实本地落盘路径，替换/下载用例可验证文件内容），
        // uploadDir 指向临时目录，避免下载路径 Paths.get(null,...) 依赖异常吞掉的巧合
        ReflectionTestUtils.setField(svc, "storageType", "local");
        ReflectionTestUtils.setField(svc, "uploadDir", System.getProperty("java.io.tmpdir"));
        // 【性能 file-3】缩略图缓存目录隔离到本用例临时目录，避免跨用例/跨次运行串味
        thumbCacheDir = java.nio.file.Files.createTempDirectory("aigicrm-thumb-cache-test-");
        ReflectionTestUtils.setField(svc, "thumbnailCacheDir", thumbCacheDir.toString());
        // 纯 mock 环境初始化 Lambda 缓存，使 LambdaQueryWrapper.getSqlSegment() 可用
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), BizFile.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), BizFileShare.class);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (thumbCacheDir == null) return;
        try (java.util.stream.Stream<java.nio.file.Path> walk = java.nio.file.Files.walk(thumbCacheDir)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    java.nio.file.Files.deleteIfExists(p);
                } catch (Exception ignored) {
                    // 测试清理尽力而为
                }
            });
        }
    }

    /** 【性能 file-3】直接调用 writeThumbnail 的用例需要带 fileId/version/ossKey 的元数据构造缓存键 */
    private static BizFile thumbnailFile(long id, String fileName) {
        BizFile file = new BizFile();
        file.setId(id);
        file.setFileName(fileName);
        file.setVersion(1);
        file.setOssKey("test/" + id + "/" + fileName);
        return file;
    }

    /** 写一张测试图片（2×2 渐变，便于验证解码后尺寸/格式） */
    private static void writeTestImage(java.nio.file.Path path, String format, int width, int height) throws Exception {
        int type = "png".equals(format)
                ? java.awt.image.BufferedImage.TYPE_INT_ARGB
                : java.awt.image.BufferedImage.TYPE_INT_RGB;
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(width, height, type);
        java.awt.Graphics2D g = image.createGraphics();
        g.setPaint(new java.awt.GradientPaint(0, 0, java.awt.Color.RED, width, height, java.awt.Color.BLUE));
        g.fillRect(0, 0, width, height);
        g.dispose();
        javax.imageio.ImageIO.write(image, format, path.toFile());
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

    /** 【2026-11-06 用户令"全局检索"】文件列表关键字=文件名∨上传人∨所在文件夹, 双端同口径 */
    @Test
    void pageFiles_globalKeyword_matchesNameUploaderAndFolder() {
        when(folderMapper.selectList(any())).thenReturn(List.of());
        Page<BizFile> empty = new Page<>(1, 10);
        empty.setRecords(List.of());
        when(fileMapper.selectPage(any(), any())).thenReturn(empty);
        svc.pageFiles(1, 10, null, "public", "张三", 1L);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<BizFile>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(fileMapper).selectPage(any(), captor.capture());
        String sql = captor.getValue().getExpression().getSqlSegment();
        assertTrue(sql.contains("file_name LIKE"), "应保留文件名匹配");
        assertTrue(sql.contains("sys_user"), "应含上传人姓名子查询");
        assertTrue(sql.contains("real_name"), "应匹配上传人姓名列");
        assertTrue(sql.contains("biz_file_folder"), "应含所在文件夹子查询");
        // 【2026-11-06 生产500自纠】真实列名=folder_name(非name)——SQL段断言挡不住列名错, 生产真跑才暴露
        assertTrue(sql.contains("folder_name LIKE"), "文件夹子查询必须用真实列名folder_name");
    }

    @Test
    void sharedToMe_globalKeyword_alsoMatchesUploaderAndFolder() {
        BizFileShare share = new BizFileShare();
        share.setFileId(9L);
        share.setSharedToUserId(2L);
        when(fileShareMapper.selectList(any())).thenReturn(List.of(share));
        Page<BizFile> empty = new Page<>(1, 10);
        empty.setRecords(List.of());
        when(fileMapper.selectPage(any(), any())).thenReturn(empty);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(2L);
            svc.pageSharedToMe(1, 10, "李四");
        }
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<BizFile>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(fileMapper).selectPage(any(), captor.capture());
        String sql = captor.getValue().getExpression().getSqlSegment();
        assertTrue(sql.contains("file_name LIKE"), "应保留文件名匹配");
        assertTrue(sql.contains("sys_user") && sql.contains("real_name"), "应匹配上传人姓名");
        assertTrue(sql.contains("biz_file_folder"), "应匹配所在文件夹");
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
            when(folderPermissionService.canCreateFolder("private", 2L)).thenReturn(true);
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
        // 【2026-11-10 终版】mock 的 canCreateFolder 默认 false=无授权, 门槛拒绝
        BizFileFolder folder = new BizFileFolder();
        folder.setFolderType("public");
        folder.setParentId(0L);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(2L);
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            BusinessException ex = assertThrows(BusinessException.class, () -> svc.createFolder(folder));
            assertEquals("没有创建该类型文件夹的权限，请联系管理员授权", ex.getMessage());
            verify(folderMapper, never()).insert(any(BizFileFolder.class));
        }
    }

    @Test
    void createFolder_publicRoot_withGrant_passesAndSnapshotsOwner() {
        // 【2026-11-10 终版】授权含 FOLDER:create → 放行; owner=创建人(公共私人同), org=创建人组织快照
        BizFileFolder folder = new BizFileFolder();
        folder.setFolderType("public");
        folder.setParentId(0L);
        doAnswer(inv -> { inv.getArgument(0, BizFileFolder.class).setId(99L); return 1; })
                .when(folderMapper).insert(any(BizFileFolder.class));
        SysUser creator = new SysUser(); creator.setId(2L); creator.setOfficeId(5L);
        when(userMapper.selectById(2L)).thenReturn(creator);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(2L);
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            when(folderPermissionService.canCreateFolder("public", 2L)).thenReturn(true);
            svc.createFolder(folder);
            ArgumentCaptor<BizFileFolder> cap = ArgumentCaptor.forClass(BizFileFolder.class);
            verify(folderMapper).insert(cap.capture());
            assertEquals(2L, cap.getValue().getOwnerId(), "公共夹 owner=创建人(治存量 NULL)");
            assertEquals(5L, cap.getValue().getOrgId(), "org=创建人组织快照");
        }
    }

    @Test
    void createFolder_gateDenied_throws() {
        // 【2026-11-10 终版】canCreateFolder=false(无授权非admin) → 拒绝
        BizFileFolder folder = new BizFileFolder();
        folder.setFolderType("private");
        folder.setParentId(0L);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(2L);
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            when(folderPermissionService.canCreateFolder("private", 2L)).thenReturn(false);
            assertThrows(BusinessException.class, () -> svc.createFolder(folder));
            verify(folderMapper, never()).insert(any(BizFileFolder.class));
        }
    }

    @Test
    void createFolder_privateRoot_codeGateOnly() {
        // 【2026-11-10 终版】私人根=授权门槛同链, 授权通过即建
        BizFileFolder folder = new BizFileFolder();
        folder.setFolderType("private");
        folder.setParentId(0L);
        doAnswer(inv -> { inv.getArgument(0, BizFileFolder.class).setId(77L); return 1; })
                .when(folderMapper).insert(any(BizFileFolder.class));
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(2L);
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            when(folderPermissionService.canCreateFolder("private", 2L)).thenReturn(true);
            svc.createFolder(folder);
            verify(folderMapper).insert(folder);
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
            when(folderPermissionService.check(100L, FolderPermissionService.P_RENAME, 2L)).thenReturn(false);
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
            when(folderPermissionService.checkFile(9L, FolderPermissionService.P_REPLACE, 2L)).thenReturn(false);
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
            when(folderPermissionService.checkFile(9L, FolderPermissionService.P_REPLACE, 1L)).thenReturn(true);
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
            when(folderPermissionService.checkFile(9L, FolderPermissionService.P_REPLACE, 1L)).thenReturn(true);
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
        // I-1：ACL 授权角色重命名成功——【文件权限 v2】check(P_RENAME)=true 即放行，
        // 不再被遗留 checkFolderManagePermission（"只能管理自己的文件夹"）拦截
        BizFileFolder folder = new BizFileFolder();
        folder.setId(100L);
        folder.setFolderType("private");
        folder.setOwnerId(99L);
        when(folderMapper.selectById(100L)).thenReturn(folder);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(2L);
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            when(folderPermissionService.check(100L, FolderPermissionService.P_RENAME, 2L)).thenReturn(true);
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
            when(folderPermissionService.canCreateFolder("public", 2L)).thenReturn(true);
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
            assertEquals("没有创建该类型文件夹的权限，请联系管理员授权", ex.getMessage());
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
            when(folderPermissionService.checkFile(9L, FolderPermissionService.P_REPLACE, 1L)).thenReturn(true);
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
        Boolean ok = ReflectionTestUtils.invokeMethod(svc, "writeThumbnail", thumbnailFile(901L, "big.jpg"), f, resp);
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
        Boolean ok = ReflectionTestUtils.invokeMethod(svc, "writeThumbnail", thumbnailFile(902L, "big.png"), f, resp);
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
        Boolean ok = ReflectionTestUtils.invokeMethod(svc, "writeThumbnail", thumbnailFile(903L, "small.png"), f, resp);
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
        Boolean ok = ReflectionTestUtils.invokeMethod(svc, "writeThumbnail", thumbnailFile(904L, "doc.txt"), f, resp);
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
            when(folderPermissionService.checkFile(9L, FolderPermissionService.P_REPLACE, 1L)).thenReturn(true);
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
            when(folderPermissionService.checkFile(9L, FolderPermissionService.P_REPLACE, 1L)).thenReturn(true);
            // 走到存储写入层(即已通过佐证守卫)即算通过——本地落盘成功后 updateById 必被调用
            svc.replaceFile(9L, mf);
            verify(fileMapper).updateById(any(BizFile.class));
        }
    }

    // ====== 【性能 file-3 2026-10-18】缩略图字节缓存 / 激励可见集合缓存 ======

    @Test
    void download_thumbnail_afterOriginalDeleted_servesCacheButStillAuthorizes() throws Exception {
        java.nio.file.Path original = java.nio.file.Files.createTempFile(
                java.nio.file.Paths.get(System.getProperty("java.io.tmpdir")), "thumb-cache-src-", ".png");
        writeTestImage(original, "png", 800, 600);

        BizFile file = new BizFile();
        file.setId(910L);
        file.setFileName("cached.png");
        file.setFileScope("public");
        file.setUploaderId(1L);
        file.setOssKey(original.getFileName().toString());
        file.setVersion(1);
        file.setFileSize(java.nio.file.Files.size(original));
        when(fileMapper.selectById(910L)).thenReturn(file);

        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            m.when(SecurityUtils::isAdmin).thenReturn(false);

            MockHttpServletResponse first = new MockHttpServletResponse();
            svc.downloadById(910L, null, true, first);
            assertEquals(200, first.getStatus());
            assertEquals("image/png", first.getContentType());
            // 缩略图来自已授权内容，必须 private（不得被共享代理缓存）
            assertEquals("private, max-age=3600", first.getHeader("Cache-Control"));
            assertNotNull(first.getHeader("ETag"));
            assertTrue(first.getContentAsByteArray().length > 0);

            assertTrue(java.nio.file.Files.deleteIfExists(original), "测试前提: 原图已删除");

            MockHttpServletResponse second = new MockHttpServletResponse();
            svc.downloadById(910L, null, true, second);
            assertEquals(200, second.getStatus());
            assertEquals(first.getHeader("ETag"), second.getHeader("ETag"));
            assertArrayEquals(first.getContentAsByteArray(), second.getContentAsByteArray(),
                    "第二次应直接命中缓存字节，不再打开原图");
        }

        // 缓存不参与鉴权：两个请求仍各读一次元数据，canDownloadFile 在 writeThumbnail 之前照常执行
        verify(fileMapper, times(2)).selectById(910L);

        svc.evictThumbnailCache(910L);
        try (java.util.stream.Stream<java.nio.file.Path> stream = java.nio.file.Files.list(thumbCacheDir)) {
            assertFalse(stream.anyMatch(p -> p.getFileName().toString().startsWith("910-")),
                    "删除/替换清理应移除该 fileId 的缩略图缓存");
        }
    }

    @Test
    void download_thumbnail_cacheHit_doesNotBypassAuthorization() throws Exception {
        java.nio.file.Path original = java.nio.file.Files.createTempFile(
                java.nio.file.Paths.get(System.getProperty("java.io.tmpdir")), "thumb-auth-src-", ".jpg");
        writeTestImage(original, "jpg", 320, 240);

        BizFile file = new BizFile();
        file.setId(911L);
        file.setFileName("auth.jpg");
        file.setFileScope("private");
        file.setUploaderId(1L);
        file.setOssKey(original.getFileName().toString());
        file.setVersion(1);
        file.setFileSize(java.nio.file.Files.size(original));
        when(fileMapper.selectById(911L)).thenReturn(file);
        when(fileShareMapper.selectCount(any())).thenReturn(0L);

        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            MockHttpServletResponse uploader = new MockHttpServletResponse();
            svc.downloadById(911L, null, true, uploader);
            assertEquals(200, uploader.getStatus());
            assertTrue(uploader.getContentAsByteArray().length > 0);
        }

        // 缓存已存在，但另一无权限用户仍必须被 403 拦截，且不能返回缓存字节
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(2L);
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            MockHttpServletResponse denied = new MockHttpServletResponse();
            svc.downloadById(911L, null, true, denied);
            assertEquals(403, denied.getStatus());
            assertEquals(0, denied.getContentAsByteArray().length);
        }

        verify(fileShareMapper, atLeastOnce()).selectCount(any());
        svc.evictThumbnailCache(911L);
        java.nio.file.Files.deleteIfExists(original);
    }

    @Test
    void incentiveVisibility_twoFileRequests_fullLoadSharedRecordsOnce() {
        when(incentiveRecordMapper.selectSharedVisibilityFingerprint(2L)).thenReturn("2:10:1700000000");
        BizIncentiveRecord sharedA = new BizIncentiveRecord();
        sharedA.setUserId(3L);
        sharedA.setShared(1);
        sharedA.setFileIds(List.of(9L));
        BizIncentiveRecord sharedB = new BizIncentiveRecord();
        sharedB.setUserId(4L);
        sharedB.setShared(1);
        sharedB.setFileTrackIds(List.of(10L));
        when(incentiveRecordMapper.selectList(any())).thenReturn(List.of(sharedA, sharedB));
        when(fileShareMapper.selectCount(any())).thenReturn(0L);

        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            assertTrue(svc.canDownloadFile(anIncentiveFile(9L, 1L), 2L));
            assertTrue(svc.canDownloadFile(anIncentiveFile(10L, 1L), 2L));
        }

        // 两个不同文件共用一次全量加载；每个请求仍做一次轻量指纹查询
        verify(incentiveRecordMapper, times(1)).selectList(any());
        verify(incentiveRecordMapper, times(2)).selectSharedVisibilityFingerprint(2L);
    }

    @Test
    void incentiveVisibility_sharedZeroAndOwnRecords_notVisible() {
        when(incentiveRecordMapper.selectSharedVisibilityFingerprint(2L)).thenReturn("2:10:1700000000");
        BizIncentiveRecord notShared = new BizIncentiveRecord();
        notShared.setUserId(3L);
        notShared.setShared(0);
        notShared.setFileIds(List.of(9L));
        BizIncentiveRecord ownRecord = new BizIncentiveRecord();
        ownRecord.setUserId(2L);
        ownRecord.setShared(1);
        ownRecord.setFileIds(List.of(9L));
        when(incentiveRecordMapper.selectList(any())).thenReturn(List.of(notShared, ownRecord));
        when(fileShareMapper.selectCount(any())).thenReturn(0L);

        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            // 即使 SQL 层过滤失效、mapper 返回了 shared=0 或本人记录，加载侧二次过滤也必须不可见
            assertFalse(svc.canDownloadFile(anIncentiveFile(9L, 1L), 2L));
        }
        verify(incentiveRecordMapper).selectList(any());
    }

    @Test
    void incentiveVisibility_fingerprintChange_reloadsSetImmediately() {
        when(incentiveRecordMapper.selectSharedVisibilityFingerprint(2L))
                .thenReturn("2:10:1700000000", "3:11:1700000001");
        BizIncentiveRecord before = new BizIncentiveRecord();
        before.setUserId(3L);
        before.setShared(1);
        before.setFileIds(List.of(9L));
        BizIncentiveRecord after = new BizIncentiveRecord();
        after.setUserId(3L);
        after.setShared(1);
        after.setFileIds(List.of(10L));
        when(incentiveRecordMapper.selectList(any())).thenReturn(List.of(before), List.of(after));
        when(fileShareMapper.selectCount(any())).thenReturn(0L);

        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            assertTrue(svc.canDownloadFile(anIncentiveFile(9L, 1L), 2L));
            assertTrue(svc.canDownloadFile(anIncentiveFile(10L, 1L), 2L));
            // 指纹变化后旧文件立即不可见（撤销共享无需等 TTL）
            assertFalse(svc.canDownloadFile(anIncentiveFile(9L, 1L), 2L));
        }
        verify(incentiveRecordMapper, times(2)).selectList(any());
    }

    @Test
    void incentiveVisibility_ttlExpiry_forcesReloadEvenWithSameFingerprint() {
        java.util.concurrent.atomic.AtomicLong now = new java.util.concurrent.atomic.AtomicLong(1_000_000_000L);
        ReflectionTestUtils.setField(svc, "incentiveCacheClock", (java.util.function.LongSupplier) now::get);
        when(incentiveRecordMapper.selectSharedVisibilityFingerprint(2L)).thenReturn("2:10:1700000000");
        BizIncentiveRecord shared = new BizIncentiveRecord();
        shared.setUserId(3L);
        shared.setShared(1);
        shared.setFileIds(List.of(9L));
        when(incentiveRecordMapper.selectList(any())).thenReturn(List.of(shared));
        when(fileShareMapper.selectCount(any())).thenReturn(0L);

        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            assertTrue(svc.canDownloadFile(anIncentiveFile(9L, 1L), 2L));
            assertTrue(svc.canDownloadFile(anIncentiveFile(9L, 1L), 2L)); // TTL 内同指纹：复用集合
            now.addAndGet(java.util.concurrent.TimeUnit.SECONDS.toNanos(30) + 1); // 超过 30s：强制重载
            assertTrue(svc.canDownloadFile(anIncentiveFile(9L, 1L), 2L));
        }
        verify(incentiveRecordMapper, times(2)).selectList(any());
    }

    @Test
    void thumbnailCacheKey_invalidatedByVersionAndOriginalMtime() throws Exception {
        java.nio.file.Path f = java.nio.file.Files.createTempFile("thumb-key-", ".png");
        writeTestImage(f, "png", 20, 20);
        BizFile file = thumbnailFile(912L, "key.png");

        String baseKey = ReflectionTestUtils.invokeMethod(svc, "thumbnailCacheKey", file, f, true);
        assertNotNull(baseKey);

        file.setVersion(2);
        String versionKey = ReflectionTestUtils.invokeMethod(svc, "thumbnailCacheKey", file, f, true);
        assertNotEquals(baseKey, versionKey, "version 变化必须换 key");

        file.setVersion(1);
        long mtime = java.nio.file.Files.getLastModifiedTime(f).toMillis();
        java.nio.file.Files.setLastModifiedTime(f,
                java.nio.file.attribute.FileTime.fromMillis(mtime + 5_000L));
        String mtimeKey = ReflectionTestUtils.invokeMethod(svc, "thumbnailCacheKey", file, f, true);
        assertNotEquals(baseKey, mtimeKey, "原文件 mtime 变化必须换 key");

        java.nio.file.Files.deleteIfExists(f);
    }

    // ====== 【A7 2026-10-20 审计】replaceFile 事务化: afterCommit 删旧 + 失败补偿新媒体 =====
    // 注: @Transactional 的真实回滚需要 Spring 上下文(审计 F4 为 P2, 本仓库纯 mock 单测),
    // 此处以调用顺序/补偿行为锁定: ①旧文件删除只发生在 afterCommit 回调; ②失败路径 best-effort
    // 清理已落盘新媒体(防孤儿); ③重试按库内旧值重算配额差额, 不因上次失败漂移。

    /** 【A7】本组用例隔离的本地存储根(共享 tmpdir 下 private/1 有其它用例产物, 断言目录内容需隔离) */
    private java.nio.file.Path a7IsolatedRoot() throws Exception {
        java.nio.file.Path root = java.nio.file.Files.createTempDirectory("aigicrm-replace-tx-");
        ReflectionTestUtils.setField(svc, "uploadDir", root.toString());
        return root;
    }

    private java.nio.file.Path a7WriteOldFile(java.nio.file.Path root) throws Exception {
        java.nio.file.Path old = root.resolve("private/1/old.txt");
        java.nio.file.Files.createDirectories(old.getParent());
        java.nio.file.Files.writeString(old, "old-content");
        return old;
    }

    /** 活动事务同步下: 方法体内不得物理删旧文件, 只有 afterCommit 回调才删 */
    @Test
    void replaceFile_withActiveTxSync_oldFileDeletedOnlyAfterCommit() throws Exception {
        java.nio.file.Path root = a7IsolatedRoot();
        java.nio.file.Path oldFile = a7WriteOldFile(root);

        when(fileMapper.selectById(9L)).thenReturn(aReplaceableFile());
        MultipartFile mf = new MockMultipartFile("file", "new.txt", "text/plain",
                "new-content".getBytes(StandardCharsets.UTF_8));

        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
                m.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
                when(folderPermissionService.checkFile(9L, FolderPermissionService.P_REPLACE, 1L)).thenReturn(true);

                BizFile result = svc.replaceFile(9L, mf);

                // 事务尚未提交: 旧文件必须还在(回滚路径可完整还原), 新文件已落盘
                assertTrue(java.nio.file.Files.exists(oldFile),
                        "afterCommit 前不得物理删除旧文件(否则回滚后文件不可恢复)");
                assertTrue(java.nio.file.Files.exists(root.resolve(result.getOssKey())));
                var syncs = org.springframework.transaction.support.TransactionSynchronizationManager
                        .getSynchronizations();
                assertEquals(1, syncs.size(), "应恰好注册一个 afterCommit 删除回调");

                // 模拟提交: 回调触发旧文件物理删除
                syncs.forEach(org.springframework.transaction.support.TransactionSynchronization::afterCommit);
                assertFalse(java.nio.file.Files.exists(oldFile), "提交后旧文件应被物理删除");
            }
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
        // 配额差额在事务内(同一方法调用): 新 11B - 旧 10B = +1
        verify(userMapper).adjustStorageUsed(1L, 1L);
    }

    /** 配额调整失败(事务将回滚): 返回失败 + best-effort 清理新媒体, 旧文件保持原样 */
    @Test
    void replaceFile_quotaAdjustFailure_cleansNewMediaAndKeepsOldFile() throws Exception {
        java.nio.file.Path root = a7IsolatedRoot();
        java.nio.file.Path oldFile = a7WriteOldFile(root);

        when(fileMapper.selectById(9L)).thenReturn(aReplaceableFile());
        MultipartFile mf = new MockMultipartFile("file", "new.txt", "text/plain",
                "new-content".getBytes(StandardCharsets.UTF_8));
        when(userMapper.adjustStorageUsed(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong()))
                .thenThrow(new RuntimeException("quota adjust boom"));

        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            when(folderPermissionService.checkFile(9L, FolderPermissionService.P_REPLACE, 1L)).thenReturn(true);

            BusinessException ex = assertThrows(BusinessException.class, () -> svc.replaceFile(9L, mf));
            assertTrue(ex.getMessage().contains("文件替换失败"), "实际: " + ex.getMessage());
        }

        // 旧文件未动(事务回滚后台账仍指向它, 内容必须可下载)
        assertTrue(java.nio.file.Files.exists(oldFile), "失败路径不得删除旧文件");
        assertEquals("old-content", java.nio.file.Files.readString(oldFile));
        // 新媒体已被补偿清理: 目录内只剩 old.txt(无孤儿 new*.txt)
        try (java.util.stream.Stream<java.nio.file.Path> dir =
                     java.nio.file.Files.list(oldFile.getParent())) {
            var names = dir.map(p -> p.getFileName().toString()).toList();
            assertEquals(List.of("old.txt"), names, "失败后不应残留新媒体孤儿: " + names);
        }
    }

    /** 失败后重试成功: 配额差额按库内旧值重算(两次同为 +1), 终态旧删新在、无孤儿 */
    @Test
    void replaceFile_retryAfterQuotaFailure_quotaDeltaStable() throws Exception {
        java.nio.file.Path root = a7IsolatedRoot();
        java.nio.file.Path oldFile = a7WriteOldFile(root);

        // 每次调用返回全新旧台账(模拟真实 DB: 失败已回滚, 重试读到的仍是未替换的旧行)
        when(fileMapper.selectById(9L)).thenAnswer(inv -> aReplaceableFile());
        MultipartFile mf = new MockMultipartFile("file", "new.txt", "text/plain",
                "new-content".getBytes(StandardCharsets.UTF_8));
        when(userMapper.adjustStorageUsed(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong()))
                .thenThrow(new RuntimeException("first attempt fails"))
                .thenReturn(1); // 重试成功

        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            when(folderPermissionService.checkFile(9L, FolderPermissionService.P_REPLACE, 1L)).thenReturn(true);

            // 第一次: 配额失败 → 报错(事务语义下 updateById 一并回滚, 单测以异常传播锁定)
            assertThrows(BusinessException.class, () -> svc.replaceFile(9L, mf));
            // 第二次: 成功(库内旧值未变 → 差额同为 11-10=+1, 不漂移)
            BizFile result = svc.replaceFile(9L, mf);

            verify(userMapper, times(2)).adjustStorageUsed(1L, 1L);
            // 终态: 旧文件已删(无事务同步环境, afterCommit 退化为立即执行), 新文件在
            assertFalse(java.nio.file.Files.exists(oldFile), "成功后旧文件应删除");
            assertTrue(java.nio.file.Files.exists(root.resolve(result.getOssKey())));
            // 无孤儿: 目录内只有新文件一个条目
            try (java.util.stream.Stream<java.nio.file.Path> dir =
                         java.nio.file.Files.list(oldFile.getParent())) {
                assertEquals(1, dir.count(), "重试成功后目录内只应有新媒体, 无孤儿残留");
            }
        }
    }

    // ==================== 【T2 2026-11-11 审计整改】激励空间权限门 + 全 scope 配额 ====================

    @Test
    void upload_incentiveScope_withoutIncentiveAdd_throws403() {
        MultipartFile mf = mock(MultipartFile.class);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            m.when(() -> SecurityUtils.hasAuthority("incentive:add")).thenReturn(false);
            // 【T2】激励空间上传与 IncentiveController /records 同码门槛: 无码 403,
            // 且不进入存储/配额环节(防借上传端点向激励空间落盘)
            BusinessException ex = assertThrows(BusinessException.class, () -> svc.upload(mf, null, "incentive"));
            assertEquals(403, ex.getCode());
            verify(userMapper, never()).tryConsumeQuota(anyLong(), anyLong());
            verify(fileMapper, never()).insert(any(BizFile.class));
        }
    }

    @Test
    void upload_incentiveScope_withIncentiveAdd_consumesQuota() throws Exception {
        // 2×2 真图走 local 落盘路径(与既有用例同款), 验证激励上传同样扣配额
        java.nio.file.Path img = java.nio.file.Files.createTempFile("t2-inc-", ".png");
        writeTestImage(img, "png", 2, 2);
        byte[] bytes = java.nio.file.Files.readAllBytes(img);
        MultipartFile mf = new MockMultipartFile("f", "证据.png", "image/png",
                new ByteArrayInputStream(bytes));
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            m.when(() -> SecurityUtils.hasAuthority("incentive:add")).thenReturn(true);
            SysUser me = new SysUser();
            me.setOfficeId(10L);
            when(userMapper.selectById(1L)).thenReturn(me);
            when(userMapper.tryConsumeQuota(eq(1L), eq((long) bytes.length))).thenReturn(1);
            when(fileMapper.insert(any(BizFile.class))).thenReturn(1);

            BizFile saved = svc.upload(mf, null, "incentive");

            // 【T2】incentive scope 也必须过配额门(此前仅 private, 激励空间无限上传)
            verify(userMapper).tryConsumeQuota(eq(1L), eq((long) bytes.length));
            assertEquals("incentive", saved.getFileScope());
            java.nio.file.Files.deleteIfExists(img);
        }
    }

    @Test
    void upload_publicRoot_admin_alsoConsumesQuota() throws Exception {
        // 【T2】public 根目录仅 admin 可传, 但同样计入 admin 配额(审计 P1-2: 全 scope 配额)
        java.nio.file.Path img = java.nio.file.Files.createTempFile("t2-pub-", ".png");
        writeTestImage(img, "png", 2, 2);
        byte[] bytes = java.nio.file.Files.readAllBytes(img);
        MultipartFile mf = new MockMultipartFile("f", "公告.png", "image/png",
                new ByteArrayInputStream(bytes));
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            m.when(SecurityUtils::isAdmin).thenReturn(true);
            SysUser me = new SysUser();
            me.setOfficeId(10L);
            when(userMapper.selectById(1L)).thenReturn(me);
            when(userMapper.tryConsumeQuota(eq(1L), eq((long) bytes.length))).thenReturn(1);
            when(fileMapper.insert(any(BizFile.class))).thenReturn(1);

            svc.upload(mf, null, "public");

            verify(userMapper).tryConsumeQuota(eq(1L), eq((long) bytes.length));
            java.nio.file.Files.deleteIfExists(img);
        }
    }

    @Test
    void deleteFile_incentiveScope_returnsQuota() {
        // 【T2】占用/归还对称: incentive 文件删除归还上传人配额(存量已由 V2026.11.11.3 回溯对账)
        BizFile f = new BizFile();
        f.setId(77L);
        f.setFileScope("incentive");
        f.setUploaderId(5L);
        f.setFileSize(100L);
        f.setFileName("证据.jpg");
        when(fileMapper.selectById(77L)).thenReturn(f);
        when(fileMapper.deleteById(77L)).thenReturn(1);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(5L);
            m.when(SecurityUtils::getCurrentUsername).thenReturn("t");
            svc.deleteFile(77L);
            verify(userMapper).adjustStorageUsed(5L, -100L);
        }
    }

    @Test
    void deleteFile_unknownScope_doesNotReturnQuota() {
        // 【T2】白名单外历史脏 scope 行不还配额(防从未扣过的账面漂移)。
        // admin 才能删未知 scope(非 admin 走默认拒绝 403, 另有既有用例覆盖)——
        // 这里锁定 admin 删除成功路径上归还段按 quotaBearing 收敛
        BizFile f = new BizFile();
        f.setId(78L);
        f.setFileScope("legacy");
        f.setUploaderId(5L);
        f.setFileSize(100L);
        f.setFileName("旧物.jpg");
        when(fileMapper.selectById(78L)).thenReturn(f);
        when(fileMapper.deleteById(78L)).thenReturn(1);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            m.when(SecurityUtils::isAdmin).thenReturn(true);
            m.when(SecurityUtils::getCurrentUsername).thenReturn("t");
            svc.deleteFile(78L);
            verify(userMapper, never()).adjustStorageUsed(anyLong(), anyLong());
        }
    }
}
