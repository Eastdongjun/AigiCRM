package com.aigicrm.module.system.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.module.common.service.FormDraftService;
import com.aigicrm.module.customer.entity.BizCustomer;
import com.aigicrm.module.customer.mapper.BizCustomerMapper;
import com.aigicrm.module.customer.mapper.BizCustomerShareMapper;
import com.aigicrm.module.sea.service.SeaService;
import com.aigicrm.module.system.entity.SysUser;
import com.aigicrm.module.system.mapper.SysDeptMapper;
import com.aigicrm.module.system.mapper.SysRoleMapper;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.module.system.mapper.SysUserRoleMapper;
import com.aigicrm.module.system.mapper.SysUserOrgMapper;
import com.aigicrm.security.JwtTokenProvider;
import com.aigicrm.security.PermissionCacheService;
import com.aigicrm.security.SecurityAuditService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 【P2-2 2026-10-18】deleteUser 副作用顺序：审计事件(USER_ADMIN user_delete)与草稿清理
 * 原本发生在存在性/权限校验之前——被拒的删除也会落一条"已删除"审计并清空该用户草稿
 * （事务兜底只回滚 DB 写，audit 的 event() 非事务）。校验必须前置，拒绝路径零副作用。
 */
class SysUserDeleteOrderTest {

    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final SysUserRoleMapper userRoleMapper = mock(SysUserRoleMapper.class);
    private final BizCustomerMapper customerMapper = mock(BizCustomerMapper.class);
    private final BizCustomerShareMapper shareMapper = mock(BizCustomerShareMapper.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final SecurityAuditService securityAudit = mock(SecurityAuditService.class);
    private final FormDraftService formDraftService = mock(FormDraftService.class);
    private final PermissionCacheService permissionCache = mock(PermissionCacheService.class);

    private SysUserService newService() {
        return new SysUserService(userMapper, userRoleMapper,
                mock(PasswordEncoder.class), mock(com.aigicrm.module.system.service.SysConfigService.class),
                customerMapper, shareMapper, jdbc, mock(SysRoleMapper.class),
                mock(SeaService.class), mock(SysDeptMapper.class), mock(SysUserOrgMapper.class),
                permissionCache, mock(JwtTokenProvider.class),
                mock(com.aigicrm.common.scope.OrgScopeResolver.class),
                securityAudit, formDraftService);
    }

    @Test
    void missingUserRejectsWithoutSideEffects() {
        when(userMapper.selectById(99L)).thenReturn(null);
        SysUserService service = newService();

        try (MockedStatic<com.aigicrm.security.SecurityUtils> m = mockStatic(com.aigicrm.security.SecurityUtils.class)) {
            m.when(com.aigicrm.security.SecurityUtils::isAdmin).thenReturn(true);
            assertThrows(BusinessException.class, () -> service.deleteUser(99L), "用户不存在应被拒");
        }
        verify(securityAudit, never()).userAdminEvent(anyString(), anyLong(), any());
        verify(formDraftService, never()).purgeByUser(anyLong());
    }

    @Test
    void adminTargetRejectsWithoutSideEffects() {
        SysUser target = new SysUser();
        target.setId(1L);
        when(userMapper.selectById(1L)).thenReturn(target);
        // 非-admin 操作者 + 目标持 admin 角色 → hasAdminRole 经 jdbc 计数为 1
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(1L))).thenReturn(1);
        SysUserService service = newService();

        try (MockedStatic<com.aigicrm.security.SecurityUtils> m = mockStatic(com.aigicrm.security.SecurityUtils.class)) {
            m.when(com.aigicrm.security.SecurityUtils::isAdmin).thenReturn(false);
            assertThrows(BusinessException.class, () -> service.deleteUser(1L), "非 admin 删 admin 账号应被拒");
        }
        verify(securityAudit, never()).userAdminEvent(anyString(), anyLong(), any());
        verify(formDraftService, never()).purgeByUser(anyLong());
    }

    @Test
    void successfulDeleteStillAuditsAndPurges() {
        // 防过度旋转：校验前置后，成功路径的审计与草稿清理必须原样保留
        SysUser target = new SysUser();
        target.setId(7L);
        when(userMapper.selectById(7L)).thenReturn(target);
        when(customerMapper.selectList(any())).thenReturn(List.<BizCustomer>of());
        SysUserService service = newService();

        try (MockedStatic<com.aigicrm.security.SecurityUtils> m = mockStatic(com.aigicrm.security.SecurityUtils.class)) {
            m.when(com.aigicrm.security.SecurityUtils::isAdmin).thenReturn(true);
            service.deleteUser(7L);
        }
        verify(securityAudit, times(1)).userAdminEvent(eq("user_delete"), eq(7L), any());
        verify(formDraftService, times(1)).purgeByUser(7L);
    }
}
