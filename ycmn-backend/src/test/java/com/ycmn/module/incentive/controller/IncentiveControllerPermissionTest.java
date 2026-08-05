package com.ycmn.module.incentive.controller;

import com.ycmn.common.Result;
import com.ycmn.module.incentive.entity.BizIncentiveRecord;
import com.ycmn.module.incentive.service.IncentiveService;
import com.ycmn.security.SecurityUser;
import com.ycmn.security.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PUT /records/{id} 归属校验（对齐 DELETE 模式）：
 * 非 admin 且无激励编辑授权 且 记录非本人 → 403 "无权编辑他人记录"
 */
@ExtendWith(MockitoExtension.class)
class IncentiveControllerPermissionTest {

    @Mock
    IncentiveService incentiveService;

    @InjectMocks
    IncentiveController controller;

    private SecurityUser plainUser(Long userId) {
        SecurityUser su = new SecurityUser();
        su.setUserId(userId);
        return su;
    }

    private BizIncentiveRecord recordOf(Long id, Long userId) {
        BizIncentiveRecord r = new BizIncentiveRecord();
        r.setId(id);
        r.setUserId(userId);
        return r;
    }

    /** 普通销售员（无编辑授权）改他人记录 → 403 */
    @Test
    void updateRejectsOthersRecordForPlainUser() {
        SecurityUser su = plainUser(2L);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUser).thenReturn(su);
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            when(incentiveService.canEdit(2L)).thenReturn(false);
            when(incentiveService.getRecordById(79L)).thenReturn(recordOf(79L, 1L));

            Result<Void> result = controller.updateRecord(79L, new BizIncentiveRecord());

            assertEquals(403, result.getCode());
            assertEquals("无权编辑他人记录", result.getMessage());
            verify(incentiveService, never()).updateRecord(79L, new BizIncentiveRecord());
        }
    }

    /** 记录不存在（无编辑授权）→ 403，不允许越权创建 */
    @Test
    void updateRejectsWhenRecordMissing() {
        SecurityUser su = plainUser(2L);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUser).thenReturn(su);
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            when(incentiveService.canEdit(2L)).thenReturn(false);
            when(incentiveService.getRecordById(79L)).thenReturn(null);

            Result<Void> result = controller.updateRecord(79L, new BizIncentiveRecord());

            assertEquals(403, result.getCode());
            verify(incentiveService, never()).updateRecord(79L, new BizIncentiveRecord());
        }
    }

    /** 普通销售员改本人记录 → 放行 */
    @Test
    void updateAllowsOwnRecordForPlainUser() {
        SecurityUser su = plainUser(2L);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUser).thenReturn(su);
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            when(incentiveService.canEdit(2L)).thenReturn(false);
            when(incentiveService.getRecordById(79L)).thenReturn(recordOf(79L, 2L));

            Result<Void> result = controller.updateRecord(79L, new BizIncentiveRecord());

            assertEquals(200, result.getCode());
            verify(incentiveService).updateRecord(79L, new BizIncentiveRecord());
        }
    }

    /** 有激励编辑授权（biz_incentive_permission can_edit=1）→ 可改任意记录，跳过归属查询 */
    @Test
    void updateAllowsAnyRecordForUserWithEditPermission() {
        SecurityUser su = plainUser(2L);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUser).thenReturn(su);
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            when(incentiveService.canEdit(2L)).thenReturn(true);

            Result<Void> result = controller.updateRecord(79L, new BizIncentiveRecord());

            assertEquals(200, result.getCode());
            verify(incentiveService).updateRecord(79L, new BizIncentiveRecord());
            verify(incentiveService, never()).getRecordById(79L);
        }
    }

    /** admin → 可改任意记录 */
    @Test
    void updateAllowsAnyRecordForAdmin() {
        SecurityUser su = plainUser(1L);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUser).thenReturn(su);
            m.when(SecurityUtils::isAdmin).thenReturn(true);

            Result<Void> result = controller.updateRecord(79L, new BizIncentiveRecord());

            assertEquals(200, result.getCode());
            verify(incentiveService).updateRecord(79L, new BizIncentiveRecord());
            verify(incentiveService, never()).getRecordById(79L);
            verify(incentiveService, never()).canEdit(2L);
        }
    }
}
