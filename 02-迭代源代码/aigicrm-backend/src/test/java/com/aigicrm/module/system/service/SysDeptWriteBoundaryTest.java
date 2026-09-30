package com.aigicrm.module.system.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.common.scope.GroupScopeUtil;
import com.aigicrm.module.system.entity.SysDept;
import com.aigicrm.module.system.mapper.SysDeptMapper;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.security.PermissionCacheService;
import com.aigicrm.security.SecurityUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 【P1 2026-09-29 复审修复】部门写操作集团边界回归钉。
 * 读路径已按集团收窄(N-06),但 create/update/delete 只看 system:dept:edit——
 * 持权限的非 admin 可改/删自己不可见或不该管的部门。修复口径与 SysPosition
 * 家族一致(REQ-005): 越权集团一律拒绝;未传 groupId 兜底当前集团(无集团 fail-closed)。
 */
class SysDeptWriteBoundaryTest {

    private final SysDeptMapper deptMapper = mock(SysDeptMapper.class);
    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final SysDeptService svc = new SysDeptService(
            deptMapper, userMapper, mock(PermissionCacheService.class), mock(JdbcTemplate.class));

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    /** 非admin·集团1 用户视角: canAccessGroup(1)=true, canAccessGroup(9002)=false */
    private static void asGroup1NonAdmin(MockedStatic<SecurityUtils> security) {
        security.when(SecurityUtils::isAdmin).thenReturn(false);
        security.when(SecurityUtils::getCurrentGroupIds).thenReturn(java.util.List.of(1L));
    }

    // ---------- create ----------

    @Test
    void create_crossGroupGroupId_rejected() {
        SysDept d = new SysDept();
        d.setDeptName("越权部门");
        d.setGroupId(9002L);
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            asGroup1NonAdmin(security);
            assertThrows(BusinessException.class, () -> svc.create(d), "非 admin 不可在别集团建部门");
        }
        verify(deptMapper, never()).insert(any(SysDept.class));
    }

    @Test
    void create_noGroupId_fallsBackToOwnGroup() {
        SysDept d = new SysDept();
        d.setDeptName("本集团部门");
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            asGroup1NonAdmin(security);
            svc.create(d);
        }
        org.junit.jupiter.api.Assertions.assertEquals(1L, d.getGroupId(), "未传 groupId 应兜底当前集团");
        verify(deptMapper).insert(d);
    }

    @Test
    void create_noGroupMembership_failClosed() {
        SysDept d = new SysDept();
        d.setDeptName("无集团账号建部门");
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::isAdmin).thenReturn(false);
            security.when(SecurityUtils::getCurrentGroupIds).thenReturn(java.util.List.of());
            assertThrows(BusinessException.class, () -> svc.create(d),
                    "无集团归属账号 fail-closed(P0-02 口径),不默认建到主集团");
        }
        verify(deptMapper, never()).insert(any(SysDept.class));
    }

    @Test
    void create_ownGroupOrAdmin_allowed() {
        SysDept own = new SysDept();
        own.setDeptName("本集团");
        own.setGroupId(1L);
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            asGroup1NonAdmin(security);
            assertDoesNotThrow(() -> svc.create(own));
        }
        verify(deptMapper).insert(own);
    }

    // ---------- update ----------

    @Test
    void update_targetInOtherGroup_rejected() {
        when(deptMapper.selectById(55L)).thenReturn(deptOf(55L, 9002L));
        SysDept d = new SysDept();
        d.setId(55L);
        d.setDeptName("改别集团部门");
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            asGroup1NonAdmin(security);
            assertThrows(BusinessException.class, () -> svc.update(d), "不可更新不可见/别集团部门");
        }
        verify(deptMapper, never()).updateById(any(SysDept.class));
    }

    @Test
    void update_moveToOtherGroup_rejected() {
        when(deptMapper.selectById(10L)).thenReturn(deptOf(10L, 1L));
        SysDept d = new SysDept();
        d.setId(10L);
        d.setGroupId(9002L);   // 把本集团部门改挂到别集团
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            asGroup1NonAdmin(security);
            assertThrows(BusinessException.class, () -> svc.update(d), "不可把部门迁移到别集团");
        }
        verify(deptMapper, never()).updateById(any(SysDept.class));
    }

    @Test
    void update_parentInOtherGroup_rejected() {
        when(deptMapper.selectById(10L)).thenReturn(deptOf(10L, 1L));
        when(deptMapper.selectById(9002L)).thenReturn(deptOf(9002L, 9002L));
        SysDept d = new SysDept();
        d.setId(10L);
        d.setParentId(9002L);  // 挂到别集团父节点(脏数据防线)
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            asGroup1NonAdmin(security);
            assertThrows(BusinessException.class, () -> svc.update(d), "父节点在别集团=跨集团迁移,拒");
        }
        verify(deptMapper, never()).updateById(any(SysDept.class));
    }

    @Test
    void update_ownGroupAllowed_adminAlwaysAllowed() {
        when(deptMapper.selectById(10L)).thenReturn(deptOf(10L, 1L));
        SysDept d = new SysDept();
        d.setId(10L);
        d.setDeptName("本集团改名");
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            asGroup1NonAdmin(security);
            assertDoesNotThrow(() -> svc.update(d));
        }
        SysDept any = new SysDept();
        any.setId(55L);
        any.setDeptName("admin 改任意");
        when(deptMapper.selectById(55L)).thenReturn(deptOf(55L, 9002L));
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::isAdmin).thenReturn(true);
            security.when(SecurityUtils::getCurrentGroupIds).thenReturn(null);
            assertDoesNotThrow(() -> svc.update(any));
        }
        verify(deptMapper, org.mockito.Mockito.times(2)).updateById(any(SysDept.class)); // 本集团1次+admin跨集团1次
    }

    // ---------- delete ----------

    @Test
    void delete_targetInOtherGroup_rejectedBeforeChildChecks() {
        when(deptMapper.selectById(55L)).thenReturn(deptOf(55L, 9002L));
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            asGroup1NonAdmin(security);
            assertThrows(BusinessException.class, () -> svc.delete(55L), "不可删除别集团部门");
        }
        verify(deptMapper, never()).deleteById(55L);
        verify(userMapper, never()).selectCount(any());
    }

    @Test
    void delete_ownGroup_stillRunsChildAndUserGuards() {
        when(deptMapper.selectById(10L)).thenReturn(deptOf(10L, 1L));
        when(deptMapper.selectCount(any())).thenReturn(2L);   // 有子部门
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            asGroup1NonAdmin(security);
            assertThrows(BusinessException.class, () -> svc.delete(10L),
                    "本集团删除仍走既有子部门守卫");
        }
        verify(deptMapper, never()).deleteById(10L);
    }

    private static SysDept deptOf(long id, long groupId) {
        SysDept d = new SysDept();
        d.setId(id);
        d.setGroupId(groupId);
        d.setStatus(1);
        d.setDeptName("部门" + id);
        return d;
    }
}
