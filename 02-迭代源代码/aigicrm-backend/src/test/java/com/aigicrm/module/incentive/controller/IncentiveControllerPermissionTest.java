package com.aigicrm.module.incentive.controller;

import com.aigicrm.common.BusinessException;
import com.aigicrm.common.Result;
import com.aigicrm.module.incentive.entity.BizIncentiveRecord;
import com.aigicrm.module.incentive.service.IncentiveService;
import com.aigicrm.security.SecurityUser;
import com.aigicrm.security.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PUT /records/{id} 归属校验（对齐 DELETE 模式）：
 * 非 admin 且无 incentive:manage 授权 且 记录非本人 → BusinessException(403) "无权编辑他人记录"
 * （2026-08-14 RBAC 收敛：403 由 GlobalExceptionHandler 映射为 HTTP 403）
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

    /** 带权限码集合的用户（模拟矩阵授予的 incentive:edit/delete 等） */
    private SecurityUser userWithPerm(Long userId, String... perms) {
        SecurityUser su = new SecurityUser();
        su.setUserId(userId);
        if (perms.length > 0) {
            su.setPermissions(new java.util.HashSet<>(java.util.Arrays.asList(perms)));
        }
        return su;
    }

    /** 普通销售员（无编辑授权）改他人记录 → 抛 BusinessException(403) */
    @Test
    void updateRejectsOthersRecordForPlainUser() {
        SecurityUser su = plainUser(2L);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUser).thenReturn(su);
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            when(incentiveService.canEdit()).thenReturn(false);
            when(incentiveService.getRecordById(79L)).thenReturn(recordOf(79L, 1L));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> controller.updateRecord(79L, new BizIncentiveRecord()));

            assertEquals(403, ex.getCode());
            assertEquals("无权编辑他人记录", ex.getMessage());
            verify(incentiveService, never()).updateRecord(79L, new BizIncentiveRecord());
        }
    }

    /** 记录不存在（无编辑授权）→ 抛 BusinessException(403)，不允许越权创建 */
    @Test
    void updateRejectsWhenRecordMissing() {
        SecurityUser su = plainUser(2L);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUser).thenReturn(su);
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            when(incentiveService.canEdit()).thenReturn(false);
            when(incentiveService.getRecordById(79L)).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> controller.updateRecord(79L, new BizIncentiveRecord()));

            assertEquals(403, ex.getCode());
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
            when(incentiveService.canEdit()).thenReturn(false);
            when(incentiveService.getRecordById(79L)).thenReturn(recordOf(79L, 2L));

            Result<Void> result = controller.updateRecord(79L, new BizIncentiveRecord());

            assertEquals(200, result.getCode());
            verify(incentiveService).updateRecord(79L, new BizIncentiveRecord());
        }
    }

    /** 有 incentive:manage 授权（M1 迁移绑定 admin+销售总监）→ 可改任意记录，跳过归属查询 */
    @Test
    void updateAllowsAnyRecordForUserWithEditPermission() {
        SecurityUser su = plainUser(2L);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUser).thenReturn(su);
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            when(incentiveService.canEdit()).thenReturn(true);

            Result<Void> result = controller.updateRecord(79L, new BizIncentiveRecord());

            assertEquals(200, result.getCode());
            verify(incentiveService).updateRecord(79L, new BizIncentiveRecord());
            verify(incentiveService, never()).getRecordById(79L);
        }
    }

    /** 矩阵 incentive:edit 码（无 incentive:manage）→ 不可改他人记录（分享制矩阵码不放行非本人编辑） */
    @Test
    void updateRejectsOtherRecordWithMatrixEditCodeOnly() {
        SecurityUser su = userWithPerm(2L, "incentive:edit");
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUser).thenReturn(su);
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            when(incentiveService.canEdit()).thenReturn(false);
            when(incentiveService.getRecordById(79L)).thenReturn(recordOf(79L, 1L));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> controller.updateRecord(79L, new BizIncentiveRecord()));

            assertEquals(403, ex.getCode());
            verify(incentiveService, never()).updateRecord(79L, new BizIncentiveRecord());
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
            verify(incentiveService, never()).canEdit();
        }
    }

    // ==================== C1：POST /records 权限码 ====================

    /** 上传新记录必须挂 incentive:add 独立权限码（设计 §3.2：上传语义从 edit 拆出） */
    @Test
    void addRecordRequiresIncentiveAddAuthority() throws Exception {
        Method m = IncentiveController.class.getMethod("addRecord", BizIncentiveRecord.class);
        PreAuthorize pa = m.getAnnotation(PreAuthorize.class);
        assertNotNull(pa, "POST /records 必须带 @PreAuthorize");
        assertTrue(pa.value().contains("incentive:add"),
                "上传权限码应为 incentive:add（销售员仅持 add 可上传），实际: " + pa.value());
    }

    /** 行为冒烟：有权限时走服务层（403 由 Spring Security @PreAuthorize 在集成层强制） */
    @Test
    void addRecordCallsService() {
        Result<Void> result = controller.addRecord(new BizIncentiveRecord());
        assertEquals(200, result.getCode());
        verify(incentiveService).addRecord(any(BizIncentiveRecord.class));
    }

    /** PC 与移动端共用阅读统计权限码，不能退回 system:config:view。 */
    @Test
    void readStatsRequiresDedicatedReadStatsAuthority() throws Exception {
        Method m = IncentiveController.class.getMethod("readStats", String.class);
        PreAuthorize pa = m.getAnnotation(PreAuthorize.class);
        assertNotNull(pa, "GET /read-stats 必须带 @PreAuthorize");
        assertEquals("hasAuthority('incentive:read-stats')", pa.value());
    }

    /** 标记已读接口是 PC/移动端共同入口，基础查看权限即可调用。 */
    @Test
    void markReadRequiresIncentiveViewAuthority() throws Exception {
        Method m = IncentiveController.class.getMethod("markRead", Long.class);
        PreAuthorize pa = m.getAnnotation(PreAuthorize.class);
        assertNotNull(pa, "POST /records/{id}/read 必须带 @PreAuthorize");
        assertEquals("hasAuthority('incentive:view')", pa.value());
    }

    // ==================== C2：PUT/DELETE 去方法级硬码 ====================

    /** PUT /records/{id} 方法级仅允许 incentive:view 底线：已卸 edit 码的销售员改本人记录靠方法内归属守卫放行 */
    @Test
    void updateRecordHasNoMethodLevelPreAuthorize() throws Exception {
        Method m = IncentiveController.class.getMethod("updateRecord", Long.class, BizIncentiveRecord.class);
        PreAuthorize pa = m.getAnnotation(PreAuthorize.class);
        assertNotNull(pa, "PUT 必须保留 incentive:view 底线注解（N-03 第二道防线）");
        assertEquals("hasAuthority('incentive:view')", pa.value(),
                "PUT 只允许 view 底线，归属仍由方法内守卫判定（isAdmin ∨ 专项授权 ∨ 记录本人）");
    }

    /** DELETE /records/{id} 同 PUT：仅 view 底线，方法内守卫 */
    @Test
    void deleteRecordHasNoMethodLevelPreAuthorize() throws Exception {
        Method m = IncentiveController.class.getMethod("deleteRecord", Long.class);
        PreAuthorize pa = m.getAnnotation(PreAuthorize.class);
        assertNotNull(pa, "DELETE 必须保留 incentive:view 底线注解（N-03 第二道防线）");
        assertEquals("hasAuthority('incentive:view')", pa.value(),
                "DELETE 只允许 view 底线，归属仍由方法内守卫判定（isAdmin ∨ 专项授权 ∨ 记录本人）");
    }

    /** 普通销售员（无 delete 码）删他人记录 → 抛 BusinessException(403) */
    @Test
    void deleteRejectsOthersRecordForPlainUser() {
        SecurityUser su = plainUser(2L);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUser).thenReturn(su);
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            when(incentiveService.canDelete()).thenReturn(false);
            when(incentiveService.getRecordById(79L)).thenReturn(recordOf(79L, 1L));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> controller.deleteRecord(79L));

            assertEquals(403, ex.getCode());
            assertEquals("无权删除他人记录", ex.getMessage());
            verify(incentiveService, never()).deleteRecord(79L);
        }
    }

    /** 普通销售员（无 delete 码）删本人记录 → 放行（本人恒可，设计 §6） */
    @Test
    void deleteAllowsOwnRecordForPlainUser() {
        SecurityUser su = plainUser(2L);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUser).thenReturn(su);
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            when(incentiveService.canDelete()).thenReturn(false);
            when(incentiveService.getRecordById(79L)).thenReturn(recordOf(79L, 2L));

            Result<Void> result = controller.deleteRecord(79L);

            assertEquals(200, result.getCode());
            verify(incentiveService).deleteRecord(79L);
        }
    }

    /** 矩阵 incentive:delete 码（无 incentive:manage）→ 不可删他人记录（分享制矩阵码不放行非本人删除） */
    @Test
    void deleteRejectsOtherRecordWithMatrixDeleteCodeOnly() {
        SecurityUser su = userWithPerm(2L, "incentive:delete");
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUser).thenReturn(su);
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            when(incentiveService.canDelete()).thenReturn(false);
            when(incentiveService.getRecordById(79L)).thenReturn(recordOf(79L, 1L));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> controller.deleteRecord(79L));

            assertEquals(403, ex.getCode());
            verify(incentiveService, never()).deleteRecord(79L);
        }
    }

    /** 有 incentive:manage 授权 → 可删任意记录，跳过归属查询 */
    @Test
    void deleteAllowsAnyRecordForUserWithDeletePermission() {
        SecurityUser su = plainUser(2L);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUser).thenReturn(su);
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            when(incentiveService.canDelete()).thenReturn(true);

            Result<Void> result = controller.deleteRecord(79L);

            assertEquals(200, result.getCode());
            verify(incentiveService).deleteRecord(79L);
            verify(incentiveService, never()).getRecordById(79L);
        }
    }
}
