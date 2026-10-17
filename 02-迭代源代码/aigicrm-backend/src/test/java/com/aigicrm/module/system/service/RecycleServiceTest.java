package com.aigicrm.module.system.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.module.customer.entity.BizCustomer;
import com.aigicrm.module.customer.mapper.BizCustomerMapper;
import com.aigicrm.module.file.entity.BizFile;
import com.aigicrm.module.file.entity.BizFileFolder;
import com.aigicrm.module.file.mapper.BizFileFolderMapper;
import com.aigicrm.module.file.mapper.BizFileMapper;
import com.aigicrm.module.file.service.FileService;
import com.aigicrm.module.system.entity.SysDept;
import com.aigicrm.module.system.entity.SysLog;
import com.aigicrm.module.system.entity.SysUser;
import com.aigicrm.module.system.mapper.SysDeptMapper;
import com.aigicrm.module.system.mapper.SysLogMapper;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.security.PermissionCacheService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 【回收站 2026-10-18】已删除客户/人员/文件列表与恢复闸门 */
class RecycleServiceTest {

    private final BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final SysDeptMapper deptMapper = mock(SysDeptMapper.class);
    private final SysLogMapper logMapper = mock(SysLogMapper.class);
    private final BizFileMapper fileMapper = mock(BizFileMapper.class);
    private final BizFileFolderMapper fileFolderMapper = mock(BizFileFolderMapper.class);
    private final FileService fileService = mock(FileService.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PermissionCacheService permissionCacheService = mock(PermissionCacheService.class);
    private final RecycleService service = new RecycleService(
            customerMapper, userMapper, deptMapper, logMapper,
            fileMapper, fileFolderMapper, fileService, jdbc, permissionCacheService);

    private static BizCustomer deletedCustomer(Long ownerId) {
        BizCustomer c = new BizCustomer();
        c.setId(42L);
        c.setCustomerCode("SX-202607-999");
        c.setCustomerName("测试回收客户有限公司");
        c.setOwnerId(ownerId);
        c.setDeleted(1);
        return c;
    }

    private static SysUser deletedUser(Long id, String username, Long deptId) {
        SysUser u = new SysUser();
        u.setId(id);
        u.setUsername(username);
        u.setRealName("已删人员");
        u.setDeptId(deptId);
        u.setDeleted(1);
        u.setStatus(1);
        return u;
    }

    // ==================== 列表 ====================

    @Test
    void deletedCustomers_ownerNameFilledEvenIfOwnerDeleted() {
        when(customerMapper.selectDeletedPage(anyString(), eq(0), eq(20))).thenReturn(List.of(deletedCustomer(9L)));
        when(customerMapper.countDeleted(anyString())).thenReturn(1L);
        // selectNamesByIds 不过滤 deleted, 已删负责人也能回填姓名
        SysUser deletedOwner = deletedUser(9L, "s9", null);
        when(userMapper.selectNamesByIds(anyCollection())).thenReturn(List.of(deletedOwner));

        RecycleService.PageResult<RecycleService.DeletedCustomerRow> page =
                service.deletedCustomers(1, 20, "");

        assertEquals(1, page.total());
        assertEquals(1, page.records().size());
        assertEquals("已删人员", page.records().get(0).ownerName(), "已删负责人姓名仍应回填");
    }

    @Test
    void deletedUsers_deptNameFilled() {
        when(userMapper.selectDeletedPage(anyString(), eq(0), eq(20))).thenReturn(List.of(deletedUser(5L, "old5", 30L)));
        when(userMapper.countDeleted(anyString())).thenReturn(1L);
        SysDept dept = new SysDept();
        dept.setId(30L);
        dept.setDeptName("销售一部");
        when(deptMapper.selectBatchIds(anyCollection())).thenReturn(List.of(dept));

        RecycleService.PageResult<RecycleService.DeletedUserRow> page = service.deletedUsers(1, 20, "");

        assertEquals("销售一部", page.records().get(0).deptName());
    }

    // ==================== 客户恢复闸门 ====================

    @Test
    void restoreCustomer_success_writesAudit() {
        when(customerMapper.selectByIdIncludingDeleted(42L)).thenReturn(deletedCustomer(9L));
        when(customerMapper.countActiveByName(anyString())).thenReturn(0L);
        SysUser owner = deletedUser(9L, "s9", null);
        owner.setDeleted(0);
        when(userMapper.selectByIdIncludingDeleted(9L)).thenReturn(owner);
        when(customerMapper.countActivePoolRows(42L)).thenReturn(0L);
        when(customerMapper.restoreDeleted(42L)).thenReturn(1);

        String message = service.restoreCustomer(42L);

        assertTrue(message.contains("已恢复"));
        verify(customerMapper).restoreDeleted(42L);
        verify(logMapper).insert(any(SysLog.class));
    }

    @Test
    void restoreCustomer_duplicateActiveName_rejected() {
        when(customerMapper.selectByIdIncludingDeleted(42L)).thenReturn(deletedCustomer(9L));
        when(customerMapper.countActiveByName(anyString())).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.restoreCustomer(42L));

        assertTrue(ex.getMessage().contains("同名活跃客户"), "实际: " + ex.getMessage());
        verify(customerMapper, never()).restoreDeleted(anyLong());
    }

    @Test
    void restoreCustomer_ownerDeleted_rejected() {
        when(customerMapper.selectByIdIncludingDeleted(42L)).thenReturn(deletedCustomer(9L));
        when(customerMapper.countActiveByName(anyString())).thenReturn(0L);
        when(userMapper.selectByIdIncludingDeleted(9L)).thenReturn(deletedUser(9L, "s9", null)); // deleted=1

        BusinessException ex = assertThrows(BusinessException.class, () -> service.restoreCustomer(42L));

        assertTrue(ex.getMessage().contains("原负责人已被删除"), "实际: " + ex.getMessage());
        verify(customerMapper, never()).restoreDeleted(anyLong());
    }

    @Test
    void restoreCustomer_poolResidual_rejected() {
        when(customerMapper.selectByIdIncludingDeleted(42L)).thenReturn(deletedCustomer(null));
        when(customerMapper.countActiveByName(anyString())).thenReturn(0L);
        when(customerMapper.countActivePoolRows(42L)).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.restoreCustomer(42L));

        assertTrue(ex.getMessage().contains("公海池"), "实际: " + ex.getMessage());
        verify(customerMapper, never()).restoreDeleted(anyLong());
    }

    @Test
    void restoreCustomer_notInRecycle_rejected() {
        BizCustomer alive = deletedCustomer(null);
        alive.setDeleted(0);
        when(customerMapper.selectByIdIncludingDeleted(42L)).thenReturn(alive);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.restoreCustomer(42L));

        assertTrue(ex.getMessage().contains("不在回收站"), "实际: " + ex.getMessage());
        verify(customerMapper, never()).restoreDeleted(anyLong());
    }

    // ==================== 人员恢复闸门 ====================

    @Test
    void restoreUser_success_bumpsPermVersionAndEvictsCache() {
        when(userMapper.selectByIdIncludingDeleted(5L)).thenReturn(deletedUser(5L, "old5", 30L));
        when(userMapper.countActiveByUsername("old5")).thenReturn(0L);
        SysDept dept = new SysDept();
        dept.setId(30L);
        when(deptMapper.selectById(30L)).thenReturn(dept);
        when(userMapper.restoreDeleted(5L)).thenReturn(1);

        String message = service.restoreUser(5L);

        assertTrue(message.contains("重新分配角色"), "实际: " + message);
        verify(jdbc).update(anyString(), eq(5L));
        verify(permissionCacheService).evict(5L);
        verify(logMapper).insert(any(SysLog.class));
    }

    @Test
    void restoreUser_usernameTaken_rejected() {
        when(userMapper.selectByIdIncludingDeleted(5L)).thenReturn(deletedUser(5L, "old5", 30L));
        when(userMapper.countActiveByUsername("old5")).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.restoreUser(5L));

        assertTrue(ex.getMessage().contains("已被活跃用户占用"), "实际: " + ex.getMessage());
        verify(userMapper, never()).restoreDeleted(anyLong());
    }

    @Test
    void restoreUser_deptMissing_rejected() {
        when(userMapper.selectByIdIncludingDeleted(5L)).thenReturn(deletedUser(5L, "old5", 30L));
        when(userMapper.countActiveByUsername("old5")).thenReturn(0L);
        when(deptMapper.selectById(30L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.restoreUser(5L));

        assertTrue(ex.getMessage().contains("原部门已不存在"), "实际: " + ex.getMessage());
        verify(userMapper, never()).restoreDeleted(anyLong());
    }

    // ==================== 已删除文件 ====================

    private static BizFile deletedFile(Long id, String scope, Long folderId, Long uploaderId, long size) {
        BizFile f = new BizFile();
        f.setId(id);
        f.setFileName("测试文件.pdf");
        f.setFileSize(size);
        f.setFileScope(scope);
        f.setFolderId(folderId);
        f.setUploaderId(uploaderId);
        f.setDeleted(1);
        f.setOssKey("public/1/测试文件.pdf");
        return f;
    }

    @Test
    void deletedFiles_namesAndContentFlagFilled() {
        when(fileMapper.selectDeletedPage(anyString(), eq(0), eq(20)))
                .thenReturn(List.of(deletedFile(11L, "public", 7L, 9L, 1024)));
        when(fileMapper.countDeleted(anyString())).thenReturn(1L);
        SysUser uploader = deletedUser(9L, "admin", null);
        uploader.setDeleted(0);
        uploader.setRealName("管理员");
        when(userMapper.selectNamesByIds(anyCollection())).thenReturn(List.of(uploader));
        BizFileFolder folder = new BizFileFolder();
        folder.setId(7L);
        folder.setFolderName("公共资料");
        when(fileFolderMapper.selectBatchIds(anyCollection())).thenReturn(List.of(folder));
        when(fileService.contentExists(anyString())).thenReturn(true);

        RecycleService.PageResult<RecycleService.DeletedFileRow> page = service.deletedFiles(1, 20, "");

        assertEquals(1, page.total());
        assertEquals("管理员", page.records().get(0).uploaderName());
        assertEquals("公共资料", page.records().get(0).folderName());
        assertTrue(page.records().get(0).contentAvailable());
    }

    @Test
    void restoreFile_contentPhysicallyDeleted_rejected() {
        when(fileMapper.selectByIdIncludingDeleted(11L)).thenReturn(deletedFile(11L, "public", null, 9L, 1024));
        when(fileService.contentExists(anyString())).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.restoreFile(11L));

        assertTrue(ex.getMessage().contains("物理清除"), "实际: " + ex.getMessage());
        verify(fileMapper, never()).restoreDeleted(anyLong());
    }

    @Test
    void restoreFile_folderMissing_rejected() {
        when(fileMapper.selectByIdIncludingDeleted(11L)).thenReturn(deletedFile(11L, "public", 7L, 9L, 1024));
        when(fileService.contentExists(anyString())).thenReturn(true);
        when(fileFolderMapper.selectById(7L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.restoreFile(11L));

        assertTrue(ex.getMessage().contains("原文件夹已删除"), "实际: " + ex.getMessage());
        verify(fileMapper, never()).restoreDeleted(anyLong());
    }

    @Test
    void restoreFile_privateQuotaInsufficient_rejected() {
        when(fileMapper.selectByIdIncludingDeleted(11L)).thenReturn(deletedFile(11L, "private", null, 9L, 1024));
        when(fileService.contentExists(anyString())).thenReturn(true);
        SysUser uploader = deletedUser(9L, "u9", null);
        uploader.setDeleted(0);
        when(userMapper.selectByIdIncludingDeleted(9L)).thenReturn(uploader);
        when(userMapper.tryConsumeQuota(9L, 1024L)).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.restoreFile(11L));

        assertTrue(ex.getMessage().contains("配额不足"), "实际: " + ex.getMessage());
        verify(fileMapper, never()).restoreDeleted(anyLong());
    }

    @Test
    void restoreFile_success_writesAudit() {
        when(fileMapper.selectByIdIncludingDeleted(11L)).thenReturn(deletedFile(11L, "public", null, 9L, 1024));
        when(fileService.contentExists(anyString())).thenReturn(true);
        when(fileMapper.restoreDeleted(11L)).thenReturn(1);

        String message = service.restoreFile(11L);

        assertEquals("文件已恢复", message);
        verify(logMapper).insert(any(SysLog.class));
    }

    // ==================== 文件回收站清理 ====================

    private static Map<String, Object> fileRow(Long id, String ossKey) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", id);
        row.put("oss_key", ossKey);
        return row;
    }

    @Test
    void purgeExpiredFiles_retentionDisabled_noop() {
        assertEquals(0, service.purgeExpiredFiles(0));
        verify(jdbc, never()).queryForList(anyString());
        verify(fileService, never()).deleteStoredContent(anyString());
    }

    @Test
    void purgeExpiredFiles_deletesContentAndRow() {
        when(jdbc.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(fileRow(11L, "public/1/x.pdf")));
        when(jdbc.update(anyString(), eq(11L))).thenReturn(1);

        int purged = service.purgeExpiredFiles(30);

        assertEquals(1, purged);
        verify(fileService).deleteStoredContent("public/1/x.pdf");
        verify(jdbc).update(anyString(), eq(11L));
        verify(logMapper).insert(any(SysLog.class));
    }

    @Test
    void purgeAllDeletedFiles_clearsAll() {
        when(jdbc.queryForList(anyString()))
                .thenReturn(List.of(fileRow(11L, "public/1/x.pdf"), fileRow(12L, "private/9/y.txt")));
        when(jdbc.update(anyString(), eq(11L))).thenReturn(1);
        when(jdbc.update(anyString(), eq(12L))).thenReturn(1);

        int purged = service.purgeAllDeletedFiles();

        assertEquals(2, purged);
        verify(fileService).deleteStoredContent("public/1/x.pdf");
        verify(fileService).deleteStoredContent("private/9/y.txt");
        verify(logMapper).insert(any(SysLog.class));
    }

    // ==================== 详情与彻底删除 ====================

    private void stubNoRelated() {
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
    }

    @Test
    void deletedCustomerDetail_returnsOwnerAndRelatedCounts() {
        when(customerMapper.selectByIdIncludingDeleted(42L)).thenReturn(deletedCustomer(9L));
        SysUser owner = deletedUser(9L, "s9", null);
        owner.setDeleted(0);
        owner.setRealName("原负责人");
        when(userMapper.selectNamesByIds(anyCollection())).thenReturn(List.of(owner));
        stubNoRelated();

        RecycleService.DeletedCustomerDetail detail = service.deletedCustomerDetail(42L);

        assertEquals("原负责人", detail.ownerName());
        assertTrue(detail.related().containsKey("发票"));
        assertEquals(0L, detail.related().get("发票"));
    }

    @Test
    void hardDeleteCustomer_withBusinessRefs_rejected() {
        when(customerMapper.selectByIdIncludingDeleted(42L)).thenReturn(deletedCustomer(9L));
        stubNoRelated();
        when(jdbc.queryForObject(contains("biz_invoice"), eq(Long.class), any(Object[].class))).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.hardDeleteCustomer(42L));

        assertTrue(ex.getMessage().contains("发票 1"), "实际: " + ex.getMessage());
        verify(jdbc, never()).update(eq("DELETE FROM biz_customer WHERE id = ? AND deleted = 1"), any(Object[].class));
    }

    @Test
    void hardDeleteCustomer_clean_success_writesAudit() {
        when(customerMapper.selectByIdIncludingDeleted(42L)).thenReturn(deletedCustomer(null));
        stubNoRelated();
        when(jdbc.update(eq("DELETE FROM biz_customer WHERE id = ? AND deleted = 1"), any(Object[].class))).thenReturn(1);

        String message = service.hardDeleteCustomer(42L);

        assertTrue(message.contains("彻底删除"));
        verify(jdbc).update(eq("DELETE FROM biz_customer WHERE id = ? AND deleted = 1"), any(Object[].class));
        verify(logMapper).insert(any(SysLog.class));
    }

    @Test
    void hardDeleteUser_withRefs_rejected() {
        when(userMapper.selectByIdIncludingDeleted(5L)).thenReturn(deletedUser(5L, "old5", 30L));
        stubNoRelated();
        when(jdbc.queryForObject(contains("biz_file"), eq(Long.class), any(Object[].class))).thenReturn(2L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.hardDeleteUser(5L));

        assertTrue(ex.getMessage().contains("上传文件 2"), "实际: " + ex.getMessage());
        verify(jdbc, never()).update(eq("DELETE FROM sys_user WHERE id = ? AND deleted = 1"), any(Object[].class));
    }

    @Test
    void hardDeleteUser_clean_success_evictsCache() {
        when(userMapper.selectByIdIncludingDeleted(5L)).thenReturn(deletedUser(5L, "old5", 30L));
        stubNoRelated();
        when(jdbc.update(eq("DELETE FROM sys_user WHERE id = ? AND deleted = 1"), any(Object[].class))).thenReturn(1);

        String message = service.hardDeleteUser(5L);

        assertTrue(message.contains("彻底删除"));
        verify(permissionCacheService).evict(5L);
        verify(logMapper).insert(any(SysLog.class));
    }

    @Test
    void hardDeleteFile_referencedByIncentive_rejected() {
        when(fileMapper.selectByIdIncludingDeleted(11L)).thenReturn(deletedFile(11L, "public", null, 9L, 1024));
        when(fileService.isIncentiveEvidence(11L)).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.hardDeleteFile(11L));

        assertTrue(ex.getMessage().contains("激励记录"), "实际: " + ex.getMessage());
        verify(fileService, never()).deleteStoredContent(anyString());
    }

    @Test
    void hardDeleteFile_success_removesContentAndRow() {
        when(fileMapper.selectByIdIncludingDeleted(11L)).thenReturn(deletedFile(11L, "public", null, 9L, 1024));
        when(fileService.isIncentiveEvidence(11L)).thenReturn(false);
        when(jdbc.update(contains("DELETE FROM biz_file"), any(Object[].class))).thenReturn(1);

        String message = service.hardDeleteFile(11L);

        assertTrue(message.contains("彻底删除"));
        verify(fileService).deleteStoredContent("public/1/测试文件.pdf");
        verify(logMapper).insert(any(SysLog.class));
    }
}
