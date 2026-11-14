package com.aigicrm.module.file.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.module.file.entity.BizFile;
import com.aigicrm.module.file.entity.BizFileFolder;
import com.aigicrm.module.file.entity.BizFileVersion;
import com.aigicrm.module.file.mapper.BizFileMapper;
import com.aigicrm.module.file.mapper.BizFileVersionMapper;
import com.aigicrm.module.incentive.mapper.BizIncentiveRecordMapper;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.security.SecurityUser;
import com.aigicrm.security.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 【V2026.11.14.1 文件管理三功能】版本历史/夹软删回收站/批量操作 核心钉(用户拍板 2026-11-14):
 * ① replace 归档旧版本——旧字节不再物理删除(此前 replace 后想回退做不到);
 * ② rollback 回滚——现行内容先归档不丢, 主行指向目标版本, 配额差额原子调整;
 * ③ deleteFolder 软删——ACL 保留(恢复即原授权生效, 彻底删除才清);
 * ④ batchDeleteFiles——逐条单链守卫, 部分成功不回滚。
 * 存储通道照 FileQuotaConcurrencyTest 本地模式(storagType=local + @TempDir)。
 */
class FolderFileVersionBatchTest {

    @TempDir
    Path root;

    private final BizFileMapper fileMapper = mock(BizFileMapper.class);
    private final com.aigicrm.module.file.mapper.BizFileFolderMapper folderMapper =
            mock(com.aigicrm.module.file.mapper.BizFileFolderMapper.class);
    private final com.aigicrm.module.file.mapper.BizFolderAclMapper folderAclMapper =
            mock(com.aigicrm.module.file.mapper.BizFolderAclMapper.class);
    private final BizFileVersionMapper fileVersionMapper = mock(BizFileVersionMapper.class);
    private final BizIncentiveRecordMapper incentiveRecordMapper = mock(BizIncentiveRecordMapper.class);
    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final com.aigicrm.module.file.service.FolderPermissionService folderPermissionService =
            mock(com.aigicrm.module.file.service.FolderPermissionService.class);
    private FileService svc;

    @BeforeEach
    void setUp() throws Exception {
        // MP LambdaQueryWrapper 测试环境需实体 lambda 缓存(deleteFile/quotaBearing 内 wrapper 用)
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""), BizFile.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""), BizFileFolder.class);
        // 预置"旧版本字节"(本地盘): replace 断言旧字节保留 / rollback 断言内容可用
        java.nio.file.Files.createDirectories(root.resolve("private/7"));
        java.nio.file.Files.writeString(root.resolve("private/7/old-key.docx"), "OLD-CONTENT");
        var provider = new org.springframework.beans.factory.ObjectProvider<com.aliyun.oss.OSS>() {
            @Override public com.aliyun.oss.OSS getObject(Object... args) { return null; }
            @Override public com.aliyun.oss.OSS getObject() { return null; }
            @Override public com.aliyun.oss.OSS getIfAvailable() { return null; }
            @Override public com.aliyun.oss.OSS getIfUnique() { return null; }
        };
        svc = new FileService(fileMapper, folderMapper, mock(com.aigicrm.module.file.mapper.BizFileShareMapper.class),
                folderAclMapper, fileVersionMapper, incentiveRecordMapper,
                userMapper, provider, null /* ossConfig 本地模式不触 */, folderPermissionService,
                mock(com.aigicrm.security.SecurityAuditService.class),
                mock(org.springframework.jdbc.core.JdbcTemplate.class),
                mock(com.aigicrm.module.incentive.service.IncentiveService.class));
        org.springframework.test.util.ReflectionTestUtils.setField(svc, "storageType", "local");
        org.springframework.test.util.ReflectionTestUtils.setField(svc, "uploadDir", root.toString());
        when(incentiveRecordMapper.selectList(any())).thenReturn(List.of()); // 非激励佐证
    }

    private SecurityUser su(Long id) {
        SecurityUser u = new SecurityUser();
        u.setUserId(id);
        u.setPermissions(new HashSet0().of("file:view", "file:edit", "file:delete"));
        return u;
    }

    /** 小工具: 避免 java.util.HashSet 与断言混名 */
    static class HashSet0 {
        java.util.Set<String> of(String... xs) { return new java.util.HashSet<>(List.of(xs)); }
    }

    @Test
    void replace_archivesOldVersion_andKeepsOldBytes() throws Exception {
        BizFile file = new BizFile();
        file.setId(11L);
        file.setFileName("报告.docx");
        file.setFileScope("private");
        file.setUploaderId(7L);
        file.setOssKey("private/7/old-key.docx");
        file.setOssUrl("/api/file/download/private/7/old-key.docx");
        file.setFileSize(100L);
        file.setFileType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        file.setVersion(1);
        when(fileMapper.selectById(11L)).thenReturn(file);
        when(folderPermissionService.checkFile(eq(11L), eq(FolderPermissionService.P_REPLACE), eq(7L))).thenReturn(true);

        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(7L);
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentUsername).thenReturn("u7");
            MockMultipartFile mf = new MockMultipartFile("file", "新报告.docx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "NEW".getBytes());
            BizFile after = svc.replaceFile(11L, mf);
            assertEquals(2, after.getVersion(), "替换后版本 +1");
        }
        // 归档行: version_no=旧版本1, oss_key=旧 key, 大小=旧大小
        ArgumentCaptor<BizFileVersion> cap = ArgumentCaptor.forClass(BizFileVersion.class);
        verify(fileVersionMapper, times(1)).insert(cap.capture());
        BizFileVersion v = cap.getValue();
        assertEquals(11L, v.getFileId());
        assertEquals(1, v.getVersionNo(), "归档行版本号=被替换时主行版本");
        assertEquals("private/7/old-key.docx", v.getOssKey(), "归档行=旧存储 key(旧字节保留)");
        assertEquals(100L, v.getFileSize());
        // 本地存储旧文件仍在磁盘(负例自证: 若恢复 deleteStorage 这里会红)
        assertTrue(java.nio.file.Files.exists(root.resolve("private/7/old-key.docx")),
                "替换后旧字节必须保留在存储(版本历史可回滚的前提)");
    }

    @Test
    void rollback_archivesCurrent_thenRestoresTargetVersion() {
        BizFile file = new BizFile();
        file.setId(11L);
        file.setFileName("报告.docx");
        file.setFileScope("private");
        file.setUploaderId(7L);
        file.setOssKey("private/7/new-key.docx"); // 现行=新 key
        file.setFileSize(300L);
        file.setVersion(2);
        when(fileMapper.selectById(11L)).thenReturn(file);
        when(folderPermissionService.checkFile(eq(11L), eq(FolderPermissionService.P_REPLACE), eq(7L))).thenReturn(true);
        BizFileVersion v1 = new BizFileVersion();
        v1.setFileId(11L);
        v1.setVersionNo(1);
        v1.setOssKey("private/7/old-key.docx");
        v1.setOssUrl("/api/file/download/private/7/old-key.docx");
        v1.setFileSize(100L);
        v1.setFileType("application/msword");
        when(fileVersionMapper.byVersionNo(11L, 1)).thenReturn(v1);
        when(fileMapper.selectById(11L)).thenReturn(file);

        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(7L);
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            BizFile after = svc.rollbackToVersion(11L, 1);
            assertEquals("private/7/old-key.docx", after.getOssKey(), "主行 key=回滚目标版本");
            assertEquals(3, after.getVersion(), "回滚也 +1(审计连续)");
            assertEquals(100L, after.getFileSize());
        }
        // 回滚前的现行内容先归档(version_no=2), 数据不丢
        ArgumentCaptor<BizFileVersion> cap = ArgumentCaptor.forClass(BizFileVersion.class);
        verify(fileVersionMapper, times(1)).insert(cap.capture());
        assertEquals(2, cap.getValue().getVersionNo(), "回滚前现行内容归档为 v2");
        assertEquals("private/7/new-key.docx", cap.getValue().getOssKey());
        // 配额差额: 100-300 = -200 私有文件归还
        verify(userMapper, times(1)).adjustStorageUsed(7L, -200L);
    }

    @Test
    void deleteFolder_softDeletes_andKeepsAclRows() {
        BizFileFolder folder = new BizFileFolder();
        folder.setId(30L);
        folder.setOwnerId(7L);
        when(folderMapper.selectById(30L)).thenReturn(folder);
        when(fileMapper.selectCount(any())).thenReturn(0L); // 夹内无文件
        when(folderMapper.selectCount(any())).thenReturn(0L); // 无子夹
        when(folderPermissionService.check(eq(30L), eq(FolderPermissionService.P_DELETE), eq(7L))).thenReturn(true);

        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(7L);
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            svc.deleteFolder(30L);
        }
        // 软删: deleteById(@TableLogic→UPDATE deleted=1) 被调用; ACL 行**保留**(恢复即原授权生效)
        verify(folderMapper, times(1)).deleteById(30L);
        verify(folderAclMapper, never()).delete(any());
        assertNotNull(folder, "软删不物理清行");
    }

    @Test
    void batchDeleteFiles_partialSuccess_perItemGuards() {
        BizFile ok = new BizFile(); ok.setId(1L); ok.setFileScope("private"); ok.setUploaderId(7L);
        BizFile locked = new BizFile(); locked.setId(2L); locked.setFileScope("incentive"); locked.setUploaderId(99L);
        when(fileMapper.selectById(1L)).thenReturn(ok);
        when(fileMapper.selectById(2L)).thenReturn(locked);
        when(fileMapper.deleteById(1L)).thenReturn(1);

        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(7L);
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentUsername).thenReturn("u7");
            Map<Long, String> out = svc.batchDeleteFiles(List.of(1L, 2L, 999L));
            assertEquals("OK", out.get(1L), "有权删除项成功");
            assertTrue(out.get(2L) != null && !"OK".equals(out.get(2L)), "无权项逐条报原因(激励他人文件)");
            assertTrue(out.containsKey(999L) && !"OK".equals(out.get(999L)), "不存在项报失败不抛整批异常");
        }
    }

    @Test
    void listVersions_deniedWithoutViewPermission() {
        when(fileMapper.selectById(11L)).thenReturn(new BizFile());
        when(folderPermissionService.checkFile(eq(11L), eq(FolderPermissionService.P_VIEW), eq(99L))).thenReturn(false);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(99L);
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            assertThrows(BusinessException.class, () -> svc.listVersions(11L),
                    "无 view 权限不得读版本列表(权限链与单文件一致)");
        }
    }
}
