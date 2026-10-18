package com.aigicrm.module.system.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.module.system.entity.SysUser;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.security.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 【D1=A 2026-10-18】存储空间申请服务层契约:
 * - 管理员始终可申请(不受开关/名单限制); 非管理员需 enabled && 名单命中;
 * - 上限默认 100GB, 非法配置回退, 合法区间 [1GB,1TB];
 * - 只升不降; 执行侧锁行读取当前配额, 并发已变更时明确拒绝且不更新/不审计。
 */
@ExtendWith(MockitoExtension.class)
class StorageQuotaApplyServiceTest {

    @Mock SysUserMapper userMapper;
    @Mock SysConfigService configService;
    @Mock com.aigicrm.security.SecurityAuditService securityAudit;

    private SysUserService service;

    @BeforeEach
    void setUp() {
        service = new SysUserService(userMapper,
                mock(com.aigicrm.module.system.mapper.SysUserRoleMapper.class),
                mock(org.springframework.security.crypto.password.PasswordEncoder.class),
                configService,
                mock(com.aigicrm.module.customer.mapper.BizCustomerMapper.class),
                mock(com.aigicrm.module.customer.mapper.BizCustomerShareMapper.class),
                mock(org.springframework.jdbc.core.JdbcTemplate.class),
                mock(com.aigicrm.module.system.mapper.SysRoleMapper.class),
                mock(com.aigicrm.module.sea.service.SeaService.class),
                mock(com.aigicrm.module.system.mapper.SysDeptMapper.class),
                mock(com.aigicrm.module.system.mapper.SysUserOrgMapper.class),
                mock(com.aigicrm.security.PermissionCacheService.class),
                mock(com.aigicrm.security.JwtTokenProvider.class),
                mock(com.aigicrm.common.scope.OrgScopeResolver.class),
                securityAudit,
                mock(com.aigicrm.module.common.service.FormDraftService.class));
    }

    private SysUser user(Long id, long quota, Integer status, Integer deleted) {
        SysUser u = new SysUser();
        u.setId(id);
        u.setUsername("u" + id);
        u.setStorageQuota(quota);
        u.setStorageUsed(10L);
        u.setStatus(status);
        u.setDeleted(deleted);
        return u;
    }

    private void stubEnabled(boolean enabled) {
        when(configService.getBooleanValue(SysUserService.STORAGE_APPLY_ENABLED_KEY, true)).thenReturn(enabled);
    }

    private void stubUsers(String json) {
        when(configService.getValue(SysUserService.STORAGE_APPLY_USERS_KEY)).thenReturn(json);
    }

    @Test
    void applyInfo_adminAlwaysCanApply_evenWhenDisabledAndNotListed() {
        SysUser admin = user(1L, 100L, 1, 0);
        when(userMapper.selectById(1L)).thenReturn(admin);
        stubEnabled(false);
        try (MockedStatic<SecurityUtils> sec = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::isAdmin).thenReturn(true);

            Map<String, Object> info = service.storageApplyInfo(1L);

            assertEquals(10L, info.get("used"));
            assertEquals(100L, info.get("quota"));
            assertEquals(107374182400L, info.get("maxQuota"), "缺失配置回退 100GB");
            assertEquals(true, info.get("canApply"));
            assertNull(info.get("reason"));
            assertEquals(false, info.get("enabled"));
            assertEquals(false, info.get("allowDecrease"));
            assertTrue(info.containsKey("reason"), "不可申请原因字段必须存在(可 null)");
        }
    }

    @Test
    void applyInfo_nonAdminDisabled_cannotApply() {
        when(userMapper.selectById(7L)).thenReturn(user(7L, 100L, 1, 0));
        stubEnabled(false);
        try (MockedStatic<SecurityUtils> sec = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::isAdmin).thenReturn(false);

            Map<String, Object> info = service.storageApplyInfo(7L);

            assertEquals(false, info.get("canApply"));
            assertTrue(String.valueOf(info.get("reason")).contains("未开放"), "原因: " + info.get("reason"));
        }
    }

    @Test
    void applyInfo_nonAdminAuthorizedUser_canApply() {
        when(userMapper.selectById(7L)).thenReturn(user(7L, 100L, 1, 0));
        stubEnabled(true);
        stubUsers("[7,8]");
        try (MockedStatic<SecurityUtils> sec = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::isAdmin).thenReturn(false);

            Map<String, Object> info = service.storageApplyInfo(7L);

            assertEquals(true, info.get("canApply"));
            assertNull(info.get("reason"));
        }
    }

    @Test
    void applyInfo_nonAdminNotAuthorized_cannotApply() {
        when(userMapper.selectById(7L)).thenReturn(user(7L, 100L, 1, 0));
        stubEnabled(true);
        stubUsers("[8,9]");
        try (MockedStatic<SecurityUtils> sec = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::isAdmin).thenReturn(false);

            Map<String, Object> info = service.storageApplyInfo(7L);

            assertEquals(false, info.get("canApply"));
            assertTrue(String.valueOf(info.get("reason")).contains("授权名单"), "原因: " + info.get("reason"));
        }
    }

    @Test
    void applyInfo_usersJsonWithNumericStrings_stillMatches() {
        when(userMapper.selectById(7L)).thenReturn(user(7L, 100L, 1, 0));
        stubEnabled(true);
        stubUsers("[\"7\",\"8\"]");
        try (MockedStatic<SecurityUtils> sec = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::isAdmin).thenReturn(false);
            assertEquals(true, service.storageApplyInfo(7L).get("canApply"));
        }
    }

    @Test
    void applyInfo_dirtyUsersJson_failClosed() {
        when(userMapper.selectById(7L)).thenReturn(user(7L, 100L, 1, 0));
        stubEnabled(true);
        stubUsers("not-a-json-array");
        try (MockedStatic<SecurityUtils> sec = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::isAdmin).thenReturn(false);
            assertEquals(false, service.storageApplyInfo(7L).get("canApply"));
        }
    }

    @Test
    void applyInfo_invalidMaxQuota_fallsBackTo100GB() {
        when(userMapper.selectById(7L)).thenReturn(user(7L, 100L, 1, 0));
        when(configService.getValue(SysUserService.STORAGE_MAX_QUOTA_KEY)).thenReturn("abc");
        assertEquals(107374182400L, service.storageApplyInfo(7L).get("maxQuota"));
    }

    @Test
    void applyInfo_maxQuotaAbove1TB_clampedTo1TB() {
        when(userMapper.selectById(7L)).thenReturn(user(7L, 100L, 1, 0));
        when(configService.getValue(SysUserService.STORAGE_MAX_QUOTA_KEY)).thenReturn("2199023255552"); // 2TB
        assertEquals(1099511627776L, service.storageApplyInfo(7L).get("maxQuota"));
    }

    @Test
    void applyInfo_maxQuotaBelow1GB_clampedTo1GB() {
        when(userMapper.selectById(7L)).thenReturn(user(7L, 100L, 1, 0));
        when(configService.getValue(SysUserService.STORAGE_MAX_QUOTA_KEY)).thenReturn("1024");
        assertEquals(1073741824L, service.storageApplyInfo(7L).get("maxQuota"));
    }

    @Test
    void validate_newQuotaNotGreaterThanCurrent_rejected() {
        when(userMapper.selectById(7L)).thenReturn(user(7L, 200L, 1, 0));
        stubEnabled(true);
        stubUsers("[7]");
        try (MockedStatic<SecurityUtils> sec = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::isAdmin).thenReturn(false);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.validateStorageQuotaRequest(7L, 200L));
            assertTrue(ex.getMessage().contains("只能调高"));
        }
    }

    @Test
    void validate_newQuotaAboveMax_rejected() {
        when(userMapper.selectById(7L)).thenReturn(user(7L, 100L, 1, 0));
        stubEnabled(true);
        stubUsers("[7]");
        when(configService.getValue(SysUserService.STORAGE_MAX_QUOTA_KEY)).thenReturn("1000");
        try (MockedStatic<SecurityUtils> sec = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::isAdmin).thenReturn(false);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.validateStorageQuotaRequest(7L, 1073741825L));
            assertTrue(ex.getMessage().contains("超过系统上限"), "生效上限被钳制到 1GB, 1GB+1 超限: " + ex.getMessage());
        }
    }

    @Test
    void validate_nonAdminNotAuthorized_403() {
        when(userMapper.selectById(7L)).thenReturn(user(7L, 100L, 1, 0));
        stubEnabled(true);
        stubUsers("[]");
        try (MockedStatic<SecurityUtils> sec = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::isAdmin).thenReturn(false);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.validateStorageQuotaRequest(7L, 200L));
            assertEquals(403, ex.getCode());
        }
    }

    @Test
    void validate_nonAdminDisabled_403() {
        when(userMapper.selectById(7L)).thenReturn(user(7L, 100L, 1, 0));
        stubEnabled(false);
        try (MockedStatic<SecurityUtils> sec = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::isAdmin).thenReturn(false);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.validateStorageQuotaRequest(7L, 200L));
            assertEquals(403, ex.getCode());
            assertTrue(ex.getMessage().contains("未开放"));
        }
    }

    @Test
    void validate_invalidMaxConfig_fallsBack100GBAndRejectsAboveIt() {
        when(userMapper.selectById(7L)).thenReturn(user(7L, 100L, 1, 0));
        stubEnabled(true);
        stubUsers("[7]");
        when(configService.getValue(SysUserService.STORAGE_MAX_QUOTA_KEY)).thenReturn("not-a-number");
        try (MockedStatic<SecurityUtils> sec = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::isAdmin).thenReturn(false);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.validateStorageQuotaRequest(7L, 107374182401L));
            assertTrue(ex.getMessage().contains("超过系统上限"), ex.getMessage());
        }
    }

    @Test
    void validate_adminBypassesEnabledSwitch() {
        when(userMapper.selectById(1L)).thenReturn(user(1L, 100L, 1, 0));
        stubEnabled(false);
        try (MockedStatic<SecurityUtils> sec = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::isAdmin).thenReturn(true);

            SysUser current = service.validateStorageQuotaRequest(1L, 200L);
            assertEquals(100L, current.getStorageQuota());
        }
    }

    @Test
    void validate_disabledAccount_rejected() {
        when(userMapper.selectById(7L)).thenReturn(user(7L, 100L, 0, 0));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validateStorageQuotaRequest(7L, 200L));
        assertTrue(ex.getMessage().contains("停用"));
    }

    @Test
    void reason_trimmedAndLengthValidated() {
        assertEquals("扩容 100GB", service.validateStorageApplyReason("  扩容 100GB  "));
        assertThrows(BusinessException.class, () -> service.validateStorageApplyReason("   "));
        assertThrows(BusinessException.class, () -> service.validateStorageApplyReason("x".repeat(201)));
    }

    @Test
    void apply_locksRowReadsCurrent_updatesQuota_andAuditsOldNew() {
        when(userMapper.selectByIdForUpdate(7L)).thenReturn(user(7L, 100L, 1, 0));

        service.applyStorageQuota(7L, 200L, 55L);

        verify(userMapper).selectByIdForUpdate(7L);
        verify(userMapper).updateById(org.mockito.ArgumentMatchers.argThat(
                (SysUser u) -> u != null && u.getId().equals(7L) && u.getStorageQuota().equals(200L)));
        verify(securityAudit).storageQuotaChanged(7L, 100L, 200L, 55L);
    }

    @Test
    void apply_concurrentQuotaAlreadyChanged_rejectedWithoutUpdateOrAudit() {
        // 提交时 old=100/new=200; 审批执行前另一端已调到 300 → 锁内当前 300 > 200, 拒绝
        when(userMapper.selectByIdForUpdate(7L)).thenReturn(user(7L, 300L, 1, 0));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.applyStorageQuota(7L, 200L, 55L));

        assertTrue(ex.getMessage().contains("不高于当前配额"), ex.getMessage());
        verify(userMapper, never()).updateById((SysUser) any());
        verify(securityAudit, never()).storageQuotaChanged(any(), any(), any(), any());
    }

    @Test
    void apply_deletedApplicant_rejected() {
        when(userMapper.selectByIdForUpdate(7L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.applyStorageQuota(7L, 200L, 55L));
        assertTrue(ex.getMessage().contains("不存在"));
        verify(userMapper, never()).updateById((SysUser) any());
        assertFalse(ex.getCode() == 403);
    }

    @Test
    void apply_newQuotaAboveMax_rejectedUnderLock() {
        when(userMapper.selectByIdForUpdate(7L)).thenReturn(user(7L, 100L, 1, 0));
        // 默认上限 100GB; 申请 200GB 超限
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.applyStorageQuota(7L, 200L * 1024 * 1024 * 1024, 55L));
        assertTrue(ex.getMessage().contains("超过当前系统上限"), ex.getMessage());
        verify(userMapper, never()).updateById((SysUser) any());
        verify(securityAudit, never()).storageQuotaChanged(any(), any(), any(), any());
    }

    @Test
    void apply_missingUser_rejectedBeforeAnyWrite() {
        when(userMapper.selectByIdForUpdate(eq(404L))).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.applyStorageQuota(404L, 200L, 55L));
        verify(userMapper, never()).updateById((SysUser) any());
    }

    @Test
    void applyInfo_currentQuotaAtMax_hidesApplyEntry() {
        when(userMapper.selectById(7L)).thenReturn(user(7L, SysUserService.DEFAULT_STORAGE_MAX_QUOTA, 1, 0));
        try (MockedStatic<SecurityUtils> sec = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::isAdmin).thenReturn(true);
            Map<String, Object> info = service.storageApplyInfo(7L);
            assertEquals(false, info.get("canApply"));
            assertTrue(String.valueOf(info.get("reason")).contains("上限"), "原因: " + info.get("reason"));
        }
    }
}
