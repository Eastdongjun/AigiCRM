package com.aigicrm.module.file.controller;

import com.aigicrm.common.Result;
import com.aigicrm.module.file.service.FileService;
import com.aigicrm.module.system.service.SysUserService;
import com.aigicrm.module.approval.service.ApprovalService;
import com.aigicrm.security.JwtTokenProvider;
import com.aigicrm.security.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 【D1=A 2026-10-18】GET /api/file/storage/apply-info 契约:
 * 透传 SysUserService.storageApplyInfo 的 7 个字段, 复用 file:view(见 PermissionMatrixTest)。
 */
class FileStorageApplyInfoTest {

    private final FileService fileService = mock(FileService.class);
    private final SysUserService userService = mock(SysUserService.class);
    private final ApprovalService approvalService = mock(ApprovalService.class);
    private FileController controller;

    @BeforeEach
    void setUp() {
        controller = new FileController(fileService, mock(JwtTokenProvider.class), userService, approvalService);
    }

    @Test
    void storageApplyInfo_returnsContractFieldsForCurrentUser() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("used", 10L);
        info.put("quota", 100L);
        info.put("maxQuota", 107374182400L);
        info.put("canApply", true);
        info.put("reason", null);
        info.put("enabled", true);
        info.put("allowDecrease", false);
        when(userService.storageApplyInfo(7L)).thenReturn(info);
        when(approvalService.hasPendingStorageApply(7L)).thenReturn(false);

        try (MockedStatic<SecurityUtils> sec = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::getCurrentUserId).thenReturn(7L);

            Result<Map<String, Object>> result = controller.storageApplyInfo();

            assertEquals(200, result.getCode());
            assertEquals(8, result.getData().size(), "契约 7 项 + pending");
            assertEquals(10L, result.getData().get("used"));
            assertEquals(100L, result.getData().get("quota"));
            assertEquals(107374182400L, result.getData().get("maxQuota"));
            assertEquals(true, result.getData().get("canApply"));
            assertNull(result.getData().get("reason"));
            assertEquals(true, result.getData().get("enabled"));
            assertEquals(false, result.getData().get("allowDecrease"));
            assertEquals(false, result.getData().get("pending"));
            assertTrue(result.getData().containsKey("reason"));
        }
        verify(userService).storageApplyInfo(7L);
        verify(approvalService).hasPendingStorageApply(7L);
    }

    @Test
    void storageApplyInfo_pendingRequest_disablesEntry() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("canApply", true);
        info.put("reason", null);
        when(userService.storageApplyInfo(7L)).thenReturn(info);
        when(approvalService.hasPendingStorageApply(7L)).thenReturn(true);

        try (MockedStatic<SecurityUtils> sec = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::getCurrentUserId).thenReturn(7L);
            Result<Map<String, Object>> result = controller.storageApplyInfo();
            assertEquals(false, result.getData().get("canApply"));
            assertEquals(true, result.getData().get("pending"));
            assertTrue(String.valueOf(result.getData().get("reason")).contains("进行中的存储空间申请"));
        }
    }
}
