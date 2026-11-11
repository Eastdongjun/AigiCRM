package com.aigicrm.module.system.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.common.Result;
import com.aigicrm.module.customer.entity.BizCustomer;
import com.aigicrm.module.customer.mapper.BizCustomerMapper;
import com.aigicrm.module.file.entity.BizFile;
import com.aigicrm.module.file.entity.BizFileFolder;
import com.aigicrm.module.file.mapper.BizFileFolderMapper;
import com.aigicrm.module.file.mapper.BizFileMapper;
import com.aigicrm.module.file.service.FileService;
import com.aigicrm.module.system.controller.RecycleController;
import com.aigicrm.module.system.entity.SysDept;
import com.aigicrm.module.system.entity.SysLog;
import com.aigicrm.module.system.entity.SysUser;
import com.aigicrm.module.system.mapper.SysDeptMapper;
import com.aigicrm.module.system.mapper.SysLogMapper;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.security.PermissionCacheService;
import com.aigicrm.security.SecurityUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

    private final RecycleService controllerService = mock(RecycleService.class);
    private final RecycleController controller = new RecycleController(controllerService);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

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

    /** 锁定读返回指定行(恢复/彻底删除/清理共用的 SELECT ... FOR UPDATE) */
    @SuppressWarnings("unchecked")
    private void stubLockedFile(BizFile file) {
        doReturn(List.of(file)).when(jdbc).query(anyString(),
                org.mockito.ArgumentMatchers.<RowMapper<BizFile>>any(), eq(file.getId()));
    }

    private void stubLockedFileGone(Long id) {
        doReturn(List.of()).when(jdbc).query(anyString(),
                org.mockito.ArgumentMatchers.<RowMapper<BizFile>>any(), eq(id));
    }

    private void stubPurgeExpiredIds(List<Long> ids) {
        when(jdbc.queryForList(anyString(), eq(Long.class), any(Object[].class))).thenReturn(ids);
    }

    private void stubPurgeAllIds(List<Long> ids) {
        when(jdbc.queryForList(anyString(), eq(Long.class))).thenReturn(ids);
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
        stubLockedFile(deletedFile(11L, "public", null, 9L, 1024));
        when(fileService.contentExists(anyString())).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.restoreFile(11L));

        assertTrue(ex.getMessage().contains("物理清除"), "实际: " + ex.getMessage());
        verify(fileMapper, never()).restoreDeleted(anyLong());
    }

    @Test
    void restoreFile_lockedRowGone_rejected() {
        stubLockedFileGone(11L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.restoreFile(11L));

        assertTrue(ex.getMessage().contains("不在回收站"), "实际: " + ex.getMessage());
        verify(fileMapper, never()).restoreDeleted(anyLong());
    }

    @Test
    void restoreFile_folderMissing_restoresToRoot() {
        // 【审查 biz-2 2026-10-18】原文件夹已硬删且 ID 不复用: 恢复到根目录而非报错要求重建旧夹
        stubLockedFile(deletedFile(11L, "public", 7L, 9L, 1024));
        when(fileService.contentExists(anyString())).thenReturn(true);
        when(userMapper.tryConsumeQuota(9L, 1024L)).thenReturn(1); // 【T2】public 恢复同样重新占用配额
        when(fileFolderMapper.selectById(7L)).thenReturn(null);
        when(fileMapper.restoreDeletedToRoot(11L)).thenReturn(1);

        String message = service.restoreFile(11L);

        assertTrue(message.contains("根目录"), "恢复提示须说明已改挂根目录: " + message);
        verify(fileMapper).restoreDeletedToRoot(11L);
        verify(fileMapper, never()).restoreDeleted(anyLong());
        verify(logMapper).insert(any(SysLog.class));
    }

    /**
     * 【审查 data-3 2026-10-18】恢复的 TOCTOU 防线：先对 biz_file 行 FOR UPDATE，
     * 锁内做内容存在性检查，最后条件恢复；与清理路径锁同一行，清理失败保留台账（见下方用例）。
     */
    @Test
    void restoreFile_locksRowBeforeContentCheck_andConditionalUpdate() {
        BizFile file = deletedFile(11L, "public", null, 9L, 1024);
        stubLockedFile(file);
        when(fileService.contentExists(anyString())).thenReturn(true);
        when(userMapper.tryConsumeQuota(9L, 1024L)).thenReturn(1); // 【T2】public 恢复同样重新占用配额
        when(fileMapper.restoreDeleted(11L)).thenReturn(1);

        service.restoreFile(11L);

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(jdbc, fileService, fileMapper);
        order.verify(jdbc).query(contains("FOR UPDATE"), org.mockito.ArgumentMatchers.<RowMapper<BizFile>>any(), eq(11L));
        order.verify(fileService).contentExists("public/1/测试文件.pdf");
        order.verify(fileMapper).restoreDeleted(11L);
    }

    @Test
    void restoreFile_privateQuotaInsufficient_rejected() {
        stubLockedFile(deletedFile(11L, "private", null, 9L, 1024));
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
        stubLockedFile(deletedFile(11L, "public", null, 9L, 1024));
        when(fileService.contentExists(anyString())).thenReturn(true);
        when(userMapper.tryConsumeQuota(9L, 1024L)).thenReturn(1); // 【T2】public 恢复同样重新占用配额
        when(fileMapper.restoreDeleted(11L)).thenReturn(1);

        String message = service.restoreFile(11L);

        assertEquals("文件已恢复", message);
        verify(logMapper).insert(any(SysLog.class));
    }

    // ==================== 文件回收站清理 ====================

    @Test
    void purgeExpiredFiles_retentionDisabled_noop() {
        assertEquals(0, service.purgeExpiredFiles(0));
        RecycleService.PurgeFilesResult result = service.purgeExpiredFilesWithStats(-1);
        assertEquals(0, result.purged());
        assertEquals(0, result.skipped());
        assertEquals(0, result.failed());
        verify(jdbc, never()).queryForList(anyString(), eq(Long.class), any(Object[].class));
        verify(fileService, never()).deleteStoredContent(anyString());
    }

    @Test
    void purgeExpiredFiles_deletesContentAndRow() {
        stubPurgeExpiredIds(List.of(11L));
        stubLockedFile(deletedFile(11L, "public", null, 9L, 1024));
        when(fileService.contentExists("public/1/测试文件.pdf")).thenReturn(false);
        when(jdbc.update(eq("DELETE FROM biz_file WHERE id = ? AND deleted = 1"), eq(11L))).thenReturn(1);

        RecycleService.PurgeFilesResult result = service.purgeExpiredFilesWithStats(30);

        assertEquals(1, result.purged());
        assertEquals(0, result.skipped());
        assertEquals(0, result.failed());
        verify(fileService).deleteStoredContent("public/1/测试文件.pdf");
        verify(jdbc).update("DELETE FROM biz_file_share WHERE file_id = ?", 11L);
        verify(jdbc).update("DELETE FROM biz_file WHERE id = ? AND deleted = 1", 11L);
        verify(logMapper).insert(any(SysLog.class));
    }

    @Test
    void purgeAllDeletedFiles_clearsAll() {
        BizFile second = deletedFile(12L, "private", null, 9L, 2048);
        second.setOssKey("private/9/y.txt");
        stubPurgeAllIds(List.of(11L, 12L));
        stubLockedFile(deletedFile(11L, "public", null, 9L, 1024));
        stubLockedFile(second);
        when(fileService.contentExists(anyString())).thenReturn(false);
        when(jdbc.update(eq("DELETE FROM biz_file WHERE id = ? AND deleted = 1"), eq(11L))).thenReturn(1);
        when(jdbc.update(eq("DELETE FROM biz_file WHERE id = ? AND deleted = 1"), eq(12L))).thenReturn(1);

        RecycleService.PurgeFilesResult result = service.purgeAllDeletedFiles();

        assertEquals(2, result.purged());
        verify(fileService).deleteStoredContent("public/1/测试文件.pdf");
        verify(fileService).deleteStoredContent("private/9/y.txt");
        verify(jdbc).update("DELETE FROM biz_file_share WHERE file_id = ?", 11L);
        verify(jdbc).update("DELETE FROM biz_file_share WHERE file_id = ?", 12L);
        verify(logMapper).insert(any(SysLog.class));
    }

    @Test
    void purgeAllDeletedFiles_skipsIncentiveEvidence() {
        stubPurgeAllIds(List.of(11L));
        stubLockedFile(deletedFile(11L, "public", null, 9L, 1024));
        when(fileService.isIncentiveEvidence(11L)).thenReturn(true);

        RecycleService.PurgeFilesResult result = service.purgeAllDeletedFiles();

        assertEquals(0, result.purged());
        assertEquals(1, result.skipped());
        assertEquals(0, result.failed());
        verify(fileService, never()).deleteStoredContent(anyString());
        verify(jdbc, never()).update(eq("DELETE FROM biz_file_share WHERE file_id = ?"), eq(11L));
        verify(jdbc, never()).update(eq("DELETE FROM biz_file WHERE id = ? AND deleted = 1"), eq(11L));
    }

    @Test
    void purgeAllDeletedFiles_incentiveFallbackSkipsWhenMapperTenantBlind() {
        stubPurgeAllIds(List.of(11L));
        stubLockedFile(deletedFile(11L, "public", null, 9L, 1024));
        // 定时任务线程无会话: mapper 口径可能因租户插件恒空, JdbcTemplate 兜底仍应识别佐证
        when(fileService.isIncentiveEvidence(11L)).thenReturn(false);
        when(jdbc.queryForObject(contains("biz_incentive_record"), eq(Long.class), any(Object[].class)))
                .thenReturn(1L);

        RecycleService.PurgeFilesResult result = service.purgeAllDeletedFiles();

        assertEquals(0, result.purged());
        assertEquals(1, result.skipped());
        verify(fileService, never()).deleteStoredContent(anyString());
    }

    @Test
    void purgeAllDeletedFiles_duplicateOssKeyKeepsContent() {
        stubPurgeAllIds(List.of(11L));
        stubLockedFile(deletedFile(11L, "public", null, 9L, 1024));
        when(jdbc.queryForObject(contains("oss_key = ?"), eq(Long.class), any(Object[].class)))
                .thenReturn(1L);
        when(jdbc.update(eq("DELETE FROM biz_file WHERE id = ? AND deleted = 1"), eq(11L))).thenReturn(1);

        RecycleService.PurgeFilesResult result = service.purgeAllDeletedFiles();

        assertEquals(1, result.purged());
        assertEquals(0, result.failed());
        // 活跃行仍引用同一 oss_key: 只删已删台账, 不动物理内容
        verify(fileService, never()).deleteStoredContent(anyString());
        verify(jdbc).update("DELETE FROM biz_file_share WHERE file_id = ?", 11L);
        verify(jdbc).update("DELETE FROM biz_file WHERE id = ? AND deleted = 1", 11L);
    }

    @Test
    void purgeAllDeletedFiles_contentStillExists_keepsLedgerAndCountsFailed() {
        stubPurgeAllIds(List.of(11L));
        stubLockedFile(deletedFile(11L, "public", null, 9L, 1024));
        when(fileService.contentExists(anyString())).thenReturn(true);
        when(userMapper.tryConsumeQuota(9L, 1024L)).thenReturn(1); // 【T2】public 恢复同样重新占用配额

        RecycleService.PurgeFilesResult result = service.purgeAllDeletedFiles();

        assertEquals(0, result.purged());
        assertEquals(0, result.skipped());
        assertEquals(1, result.failed());
        verify(fileService).deleteStoredContent("public/1/测试文件.pdf");
        verify(jdbc, never()).update(eq("DELETE FROM biz_file_share WHERE file_id = ?"), eq(11L));
        verify(jdbc, never()).update(eq("DELETE FROM biz_file WHERE id = ? AND deleted = 1"), eq(11L));
    }

    @Test
    void purgeAllDeletedFiles_lockedRowRestoredConcurrently_skipped() {
        stubPurgeAllIds(List.of(11L));
        stubLockedFileGone(11L); // 锁内复核 deleted=1 已不成立(被并发恢复)

        RecycleService.PurgeFilesResult result = service.purgeAllDeletedFiles();

        assertEquals(0, result.purged());
        assertEquals(1, result.skipped());
        verify(fileService, never()).deleteStoredContent(anyString());
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
        assertTrue(detail.related().containsKey("联系人"));
        assertTrue(detail.related().containsKey("任务"));
    }

    @Test
    void deletedUserDetail_returnsRelatedCountsIncludingIncentiveRead() {
        when(userMapper.selectByIdIncludingDeleted(5L)).thenReturn(deletedUser(5L, "old5", 30L));
        stubNoRelated();

        RecycleService.DeletedUserDetail detail = service.deletedUserDetail(5L);

        assertTrue(detail.related().containsKey("激励已读"));
        assertTrue(detail.related().containsKey("文件目录"));
        assertTrue(detail.related().containsKey("文件分享"));
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
    void hardDeleteCustomer_taskLinked_rejected() {
        when(customerMapper.selectByIdIncludingDeleted(42L)).thenReturn(deletedCustomer(9L));
        stubNoRelated();
        when(jdbc.queryForObject(contains("biz_task"), eq(Long.class), any(Object[].class))).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.hardDeleteCustomer(42L));

        assertTrue(ex.getMessage().contains("任务 1"), "实际: " + ex.getMessage());
        verify(jdbc, never()).update(eq("DELETE FROM biz_customer WHERE id = ? AND deleted = 1"), any(Object[].class));
    }

    @Test
    void hardDeleteCustomer_clean_success_deletesContactsAndWritesAudit() {
        when(customerMapper.selectByIdIncludingDeleted(42L)).thenReturn(deletedCustomer(null));
        stubNoRelated();
        when(jdbc.update(eq("DELETE FROM biz_customer WHERE id = ? AND deleted = 1"), any(Object[].class))).thenReturn(1);

        String message = service.hardDeleteCustomer(42L);

        assertTrue(message.contains("彻底删除"));
        verify(jdbc).update("DELETE FROM biz_contact WHERE customer_id = ?", 42L);
        verify(jdbc).update(eq("DELETE FROM biz_customer WHERE id = ? AND deleted = 1"), any(Object[].class));
        verify(logMapper).insert(any(SysLog.class));
    }

    @Test
    void hardDeleteUser_withRefs_rejected() {
        when(userMapper.selectByIdIncludingDeleted(5L)).thenReturn(deletedUser(5L, "old5", 30L));
        stubNoRelated();
        when(jdbc.queryForObject(contains("FROM biz_file WHERE uploader_id"), eq(Long.class), any(Object[].class)))
                .thenReturn(2L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.hardDeleteUser(5L));

        assertTrue(ex.getMessage().contains("上传文件 2"), "实际: " + ex.getMessage());
        verify(jdbc, never()).update(eq("DELETE FROM sys_user WHERE id = ? AND deleted = 1"), any(Object[].class));
    }

    @Test
    void hardDeleteUser_folderLinked_rejected() {
        when(userMapper.selectByIdIncludingDeleted(5L)).thenReturn(deletedUser(5L, "old5", 30L));
        stubNoRelated();
        when(jdbc.queryForObject(contains("biz_file_folder"), eq(Long.class), any(Object[].class))).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.hardDeleteUser(5L));

        assertTrue(ex.getMessage().contains("文件目录 1"), "实际: " + ex.getMessage());
        verify(jdbc, never()).update(eq("DELETE FROM sys_user WHERE id = ? AND deleted = 1"), any(Object[].class));
    }

    @Test
    void hardDeleteUser_clean_success_deletesIncentiveReadAndEvictsCache() {
        when(userMapper.selectByIdIncludingDeleted(5L)).thenReturn(deletedUser(5L, "old5", 30L));
        stubNoRelated();
        when(jdbc.update(eq("DELETE FROM sys_user WHERE id = ? AND deleted = 1"), any(Object[].class))).thenReturn(1);

        String message = service.hardDeleteUser(5L);

        assertTrue(message.contains("彻底删除"));
        verify(jdbc).update("DELETE FROM biz_incentive_read WHERE user_id = ?", 5L);
        verify(permissionCacheService).evict(5L);
        verify(logMapper).insert(any(SysLog.class));
    }

    @Test
    void hardDeleteFile_referencedByIncentive_rejected() {
        stubLockedFile(deletedFile(11L, "public", null, 9L, 1024));
        when(fileService.isIncentiveEvidence(11L)).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.hardDeleteFile(11L));

        assertTrue(ex.getMessage().contains("激励记录"), "实际: " + ex.getMessage());
        verify(fileService, never()).deleteStoredContent(anyString());
        verify(jdbc, never()).update(eq("DELETE FROM biz_file WHERE id = ? AND deleted = 1"), eq(11L));
    }

    @Test
    void hardDeleteFile_success_removesContentSharesAndRow() {
        stubLockedFile(deletedFile(11L, "public", null, 9L, 1024));
        when(fileService.isIncentiveEvidence(11L)).thenReturn(false);
        when(fileService.contentExists("public/1/测试文件.pdf")).thenReturn(false);
        when(jdbc.update(eq("DELETE FROM biz_file WHERE id = ? AND deleted = 1"), eq(11L))).thenReturn(1);

        String message = service.hardDeleteFile(11L);

        assertTrue(message.contains("彻底删除"));
        verify(fileService).deleteStoredContent("public/1/测试文件.pdf");
        verify(jdbc).update("DELETE FROM biz_file_share WHERE file_id = ?", 11L);
        verify(jdbc).update("DELETE FROM biz_file WHERE id = ? AND deleted = 1", 11L);
        verify(logMapper).insert(any(SysLog.class));
    }

    @Test
    void hardDeleteFile_contentDeletionFailed_keepsLedger() {
        stubLockedFile(deletedFile(11L, "public", null, 9L, 1024));
        when(fileService.isIncentiveEvidence(11L)).thenReturn(false);
        when(fileService.contentExists("public/1/测试文件.pdf")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.hardDeleteFile(11L));

        assertTrue(ex.getMessage().contains("物理删除失败"), "实际: " + ex.getMessage());
        verify(jdbc, never()).update(eq("DELETE FROM biz_file_share WHERE file_id = ?"), eq(11L));
        verify(jdbc, never()).update(eq("DELETE FROM biz_file WHERE id = ? AND deleted = 1"), eq(11L));
    }

    @Test
    void hardDeleteFile_duplicateOssKeyOnlyDeletesLedger() {
        stubLockedFile(deletedFile(11L, "public", null, 9L, 1024));
        when(fileService.isIncentiveEvidence(11L)).thenReturn(false);
        when(jdbc.queryForObject(contains("oss_key = ?"), eq(Long.class), any(Object[].class)))
                .thenReturn(1L);
        when(jdbc.update(eq("DELETE FROM biz_file WHERE id = ? AND deleted = 1"), eq(11L))).thenReturn(1);

        service.hardDeleteFile(11L);

        verify(fileService, never()).deleteStoredContent(anyString());
        verify(jdbc).update("DELETE FROM biz_file_share WHERE file_id = ?", 11L);
        verify(jdbc).update("DELETE FROM biz_file WHERE id = ? AND deleted = 1", 11L);
    }

    // ==================== 控制器: 仅平台管理员 ====================

    private static void login(String... roles) {
        SecurityUser principal = new SecurityUser();
        principal.setUserId(1L);
        principal.setUsername("tester");
        principal.setRoleCodes(List.of(roles));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null));
    }

    @Test
    void controller_nonAdminEveryEndpointRejected403() {
        login("sales");
        MockHttpServletResponse response = new MockHttpServletResponse();
        List<Runnable> calls = List.of(
                () -> controller.deletedCustomers(1, 20, null),
                () -> controller.deletedCustomerDetail(1L),
                () -> controller.restoreCustomer(1L),
                () -> controller.hardDeleteCustomer(1L),
                () -> controller.deletedUsers(1, 20, null),
                () -> controller.deletedUserDetail(1L),
                () -> controller.restoreUser(1L),
                () -> controller.hardDeleteUser(1L),
                () -> controller.deletedFiles(1, 20, null),
                () -> controller.deletedFileDetail(1L),
                () -> controller.fileContent(1L, response),
                () -> controller.restoreFile(1L),
                () -> controller.hardDeleteFile(1L),
                () -> controller.purgeFiles());

        for (Runnable call : calls) {
            BusinessException ex = assertThrows(BusinessException.class, call::run);
            assertEquals(403, ex.getCode(), "非管理员必须 403");
            assertTrue(ex.getMessage().contains("平台管理员"), "实际: " + ex.getMessage());
        }
        verifyNoInteractions(controllerService);
    }

    @Test
    void controller_adminAllowed() {
        login("admin");
        when(controllerService.deletedCustomers(1, 20, null))
                .thenReturn(new RecycleService.PageResult<RecycleService.DeletedCustomerRow>(List.of(), 0));

        controller.deletedCustomers(1, 20, null);

        verify(controllerService).deletedCustomers(1, 20, null);
    }

    @Test
    void controller_purgeReturnsThreeCounts() {
        login("admin");
        when(controllerService.purgeAllDeletedFiles())
                .thenReturn(new RecycleService.PurgeFilesResult(2, 3, 1));

        Result<Map<String, Object>> result = controller.purgeFiles();

        assertEquals(200, result.getCode());
        assertEquals(2, result.getData().get("purged"));
        assertEquals(3, result.getData().get("skipped"));
        assertEquals(1, result.getData().get("failed"));
        assertTrue(String.valueOf(result.getData().get("message")).contains("清理 2 个"));
    }
}
