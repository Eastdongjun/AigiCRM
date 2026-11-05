package com.aigicrm.module.file.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.module.file.entity.BizFile;
import com.aigicrm.module.file.mapper.BizFileFolderMapper;
import com.aigicrm.module.file.mapper.BizFileMapper;
import com.aigicrm.module.file.mapper.BizFileShareMapper;
import com.aigicrm.module.system.entity.SysUser;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.module.system.service.SysConfigService;
import com.aigicrm.common.scope.OrgScopeResolver;
import com.aigicrm.common.engine.CustomerAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 【P2 2026-10-09 复审末项】文件配额并发一致性回归钉。
 * 旧实现三路径(上传/删除/替换)配额更新 = selectById→内存加减→updateById,
 * 并发上传互相覆盖 storageUsed(丢失更新);配额检查与扣减两步间竞态可超配额。
 * 修复: 上传=条件扣减(WHERE used+size<=quota, 参数化)原子守卫;
 *       增减=SET used=GREATEST(0, used+delta) 数据库端原子。
 */
class FileQuotaConcurrencyTest {

    @TempDir
    Path root;

    private final BizFileMapper fileMapper = mock(BizFileMapper.class);
    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final com.aigicrm.module.file.service.FolderPermissionService folderPermissionService =
            mock(com.aigicrm.module.file.service.FolderPermissionService.class);
    private FileService svc;

    @BeforeEach
    void setUp() {
        var provider = new org.springframework.beans.factory.ObjectProvider<com.aliyun.oss.OSS>() {
            @Override public com.aliyun.oss.OSS getObject(Object... args) { return null; }
            @Override public com.aliyun.oss.OSS getObject() { return null; }
            @Override public com.aliyun.oss.OSS getIfAvailable() { return null; }
            @Override public com.aliyun.oss.OSS getIfUnique() { return null; }
        };
        svc = new FileService(fileMapper, mock(BizFileFolderMapper.class), mock(BizFileShareMapper.class),
                mock(com.aigicrm.module.file.mapper.BizFolderAclMapper.class),
                mock(com.aigicrm.module.incentive.mapper.BizIncentiveRecordMapper.class),
                userMapper, provider, null /* ossConfig 本地模式不触 */, folderPermissionService,
                mock(org.springframework.jdbc.core.JdbcTemplate.class)); // 2026-10-27 shareFile 集团边界校验新增依赖
        ReflectionTestUtils.setField(svc, "storageType", "local");
        ReflectionTestUtils.setField(svc, "uploadDir", root.toString());
        when(folderPermissionService.check(anyLong(), org.mockito.ArgumentMatchers.<String>any(), anyLong())).thenReturn(true);
    }

    private BizFile uploadAs(MockMultipartFile mf, Long folderId, Long userId) throws java.io.IOException {
        try (var m = org.mockito.Mockito.mockStatic(com.aigicrm.security.SecurityUtils.class)) {
            m.when(com.aigicrm.security.SecurityUtils::getCurrentUserId).thenReturn(userId);
            m.when(com.aigicrm.security.SecurityUtils::isAdmin).thenReturn(false);
            return svc.upload(mf, folderId, "private");
        }
    }

    private MockMultipartFile file(long size) throws java.io.IOException {
        return new MockMultipartFile("file", "a.pdf", "application/pdf",
                new ByteArrayInputStream(new byte[(int) size]));
    }

    @Test
    void upload_quotaGuard_atomicConditionalConsume() throws java.io.IOException {
        // 条件扣减命中 1 行 = 配额内, 上传成功; 钉住走 tryConsumeQuota 而非读改写
        when(userMapper.tryConsumeQuota(7L, 100L)).thenReturn(1);
        BizFile f = uploadAs(file(100L), null, 7L);
        assertNotNull(f);
        verify(userMapper).tryConsumeQuota(7L, 100L);
        verify(userMapper, never()).updateById(any(SysUser.class));
    }

    @Test
    void upload_quotaExceeded_conditionalConsumeReturnsZero_rejected() throws java.io.IOException {
        // WHERE used+size<=quota 不命中 → 0 行 → 拒绝(并发下两请求同过旧读检查也只有一个能扣成功)
        when(userMapper.tryConsumeQuota(7L, 100L)).thenReturn(0);
        SysUser u = new SysUser();
        u.setId(7L);
        u.setStorageUsed(999999L);
        u.setStorageQuota(1000000L);
        when(userMapper.selectById(7L)).thenReturn(u);
        BusinessException ex = assertThrows(BusinessException.class, () -> uploadAs(file(100L), null, 7L));
        assertEquals(true, ex.getMessage().contains("存储空间不足"), ex.getMessage());
        verify(fileMapper, never()).insert(any(BizFile.class));
    }

    @Test
    void upload_storageFailure_quotaCompensated() throws java.io.IOException {
        // 扣减成功后存储写失败 → 补偿归还(delta 负数, GREATEST 守护不越界)
        when(userMapper.tryConsumeQuota(7L, 50L)).thenReturn(1);
        when(fileMapper.insert(any(BizFile.class))).thenThrow(new RuntimeException("db down"));
        assertThrows(BusinessException.class, () -> uploadAs(file(50L), null, 7L));
        verify(userMapper).adjustStorageUsed(eq(7L), eq(-50L));
    }

    @Test
    void delete_releaseQuota_atomicAdjust() {
        BizFile f = new BizFile();
        f.setId(1L);
        f.setOssKey("private/7/x.bin");
        f.setFileSize(80L);
        f.setFileScope("private");
        f.setUploaderId(7L);
        when(fileMapper.selectById(1L)).thenReturn(f);
        when(fileMapper.deleteById(1L)).thenReturn(1);
        when(folderPermissionService.checkFile(eq(1L), org.mockito.ArgumentMatchers.<String>any(), anyLong())).thenReturn(true);
        try (var m = org.mockito.Mockito.mockStatic(com.aigicrm.security.SecurityUtils.class)) {
            m.when(com.aigicrm.security.SecurityUtils::getCurrentUserId).thenReturn(7L);
            m.when(com.aigicrm.security.SecurityUtils::isAdmin).thenReturn(false);
            svc.deleteFile(1L);
        }
        verify(userMapper).adjustStorageUsed(eq(7L), eq(-80L));
        verify(userMapper, never()).updateById(any(SysUser.class));
    }

    /**
     * 【审查 backend-5 2026-10-18】重复删除只退款一次：第二次调用台账行已逻辑删
     * (selectById 被 @TableLogic 过滤为 null)，直接幂等返回，不再归还配额。
     */
    @Test
    void delete_repeatedCall_refundsOnlyOnce() {
        BizFile f = new BizFile();
        f.setId(1L);
        f.setOssKey("private/7/x.bin");
        f.setFileSize(80L);
        f.setFileScope("private");
        f.setUploaderId(7L);
        when(fileMapper.selectById(1L)).thenReturn(f, (BizFile) null);
        when(fileMapper.deleteById(1L)).thenReturn(1);
        when(folderPermissionService.checkFile(eq(1L), org.mockito.ArgumentMatchers.<String>any(), anyLong())).thenReturn(true);
        try (var m = org.mockito.Mockito.mockStatic(com.aigicrm.security.SecurityUtils.class)) {
            m.when(com.aigicrm.security.SecurityUtils::getCurrentUserId).thenReturn(7L);
            m.when(com.aigicrm.security.SecurityUtils::isAdmin).thenReturn(false);
            svc.deleteFile(1L);
            svc.deleteFile(1L);
        }
        verify(fileMapper, org.mockito.Mockito.times(1)).deleteById(1L);
        verify(userMapper, org.mockito.Mockito.times(1)).adjustStorageUsed(eq(7L), eq(-80L));
    }

    /**
     * 【审查 backend-5 2026-10-18】并发删除：双方都读到活跃行，但只有一方逻辑删影响行数=1；
     * 败者 affected=0 不得归还配额。
     */
    @Test
    void delete_concurrentAffectedZero_noRefund() {
        BizFile f = new BizFile();
        f.setId(1L);
        f.setFileSize(80L);
        f.setFileScope("private");
        f.setUploaderId(7L);
        when(fileMapper.selectById(1L)).thenReturn(f);
        when(fileMapper.deleteById(1L)).thenReturn(0); // 他人已删
        when(folderPermissionService.checkFile(eq(1L), org.mockito.ArgumentMatchers.<String>any(), anyLong())).thenReturn(true);
        try (var m = org.mockito.Mockito.mockStatic(com.aigicrm.security.SecurityUtils.class)) {
            m.when(com.aigicrm.security.SecurityUtils::getCurrentUserId).thenReturn(7L);
            m.when(com.aigicrm.security.SecurityUtils::isAdmin).thenReturn(false);
            svc.deleteFile(1L);
        }
        verify(userMapper, never()).adjustStorageUsed(anyLong(), org.mockito.ArgumentMatchers.anyLong());
    }

    // 【2026-11-05 用户令"替换文件功能删除"】replace_deltaQuota_atomicAdjust 随 replaceFile 退役
}
