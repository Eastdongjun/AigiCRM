package com.ycmn.module.system.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisMapperBuilderAssistant;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ycmn.common.BusinessException;
import com.ycmn.module.system.entity.SysRole;
import com.ycmn.module.system.mapper.SysMenuMapper;
import com.ycmn.module.system.mapper.SysRoleMapper;
import com.ycmn.module.system.mapper.SysRoleMenuMapper;
import com.ycmn.module.system.mapper.SysRoleOrgMapper;
import com.ycmn.security.SecurityUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SysRoleService 单元测试（纯 mock，无 Spring 上下文）。
 * 覆盖三类行为：
 * - updateOrgs：custom 数据范围的组织集合保存（清空重建，幂等）；
 * - list：集团边界过滤（非 admin 仅本集团 / admin 全量 / 空集团退主集团 1，
 *   仿 SysUserService.applyGroupScope 口径，断言 LambdaQueryWrapper 条件）；
 * - CRUD 集团校验（REQ-005 残余收口）：getById 越权集团→null（防 id 枚举）、
 *   create 越权集团→BusinessException 且未传 groupId 时兜底当前集团（admin 兜底主集团 1）、
 *   update 目标记录/新归属越权→拒绝、delete 越权→拒绝；admin 恒放行。
 */
class SysRoleServiceTest {

    /** 纯 mock 单测无 MyBatis 启动,手动注册 TableInfo 供 LambdaQueryWrapper 解析列名 */
    @BeforeAll
    static void initTableInfo() {
        MybatisMapperBuilderAssistant assistant =
                new MybatisMapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SysRole.class);
    }

    private SysRoleService newService(SysRoleOrgMapper orgMapper) {
        return new SysRoleService(
                mock(SysRoleMapper.class),
                mock(SysRoleMenuMapper.class),
                mock(SysMenuMapper.class),
                orgMapper);
    }

    /** 显式构造以便捕获 roleMapper 的查询条件（集团边界断言） */
    private static SysRoleService newServiceWith(SysRoleMapper roleMapper) {
        return new SysRoleService(roleMapper,
                mock(SysRoleMenuMapper.class),
                mock(SysMenuMapper.class),
                mock(SysRoleOrgMapper.class));
    }

    @Test
    void updateOrgs_customScope_savesSelectedOrgs() {
        SysRoleOrgMapper orgMapper = mock(SysRoleOrgMapper.class);
        SysRoleService service = newService(orgMapper);

        service.updateOrgs(5L, List.of(10L, 20L));

        verify(orgMapper).deleteByRoleId(5L);
        verify(orgMapper, times(2)).insert(any());
    }

    @Test
    void updateOrgs_emptyList_clearsOnly() {
        SysRoleOrgMapper orgMapper = mock(SysRoleOrgMapper.class);
        SysRoleService service = newService(orgMapper);

        service.updateOrgs(5L, List.of());

        verify(orgMapper).deleteByRoleId(5L);
        verify(orgMapper, never()).insert(any());
    }

    @Test
    void updateOrgs_nullList_clearsOnly() {
        SysRoleOrgMapper orgMapper = mock(SysRoleOrgMapper.class);
        SysRoleService service = newService(orgMapper);

        service.updateOrgs(5L, null);

        verify(orgMapper).deleteByRoleId(5L);
        verify(orgMapper, never()).insert(any());
    }

    // ---- 集团边界（仿 SysUserService.applyGroupScope 口径：非 admin 仅本集团；空退主集团 1） ----

    @Test
    void list_nonAdminFiltersByGroupIds() {
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        SysRoleService service = newServiceWith(roleMapper);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentGroupIds).thenReturn(List.of(1L));

            service.list();

            ArgumentCaptor<LambdaQueryWrapper<SysRole>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
            verify(roleMapper).selectList(captor.capture());
            String expr = captor.getValue().getSqlSegment();
            assertTrue(expr.contains("group_id"), "非 admin 应附加 group_id 过滤，实际: " + expr);
            assertTrue(captor.getValue().getParamNameValuePairs().containsValue(1L),
                    "应限定到当前集团(1)，实际: " + captor.getValue().getParamNameValuePairs());
        }
    }

    @Test
    void list_adminSeesAllGroups() {
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        SysRoleService service = newServiceWith(roleMapper);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(true);

            service.list();

            ArgumentCaptor<LambdaQueryWrapper<SysRole>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
            verify(roleMapper).selectList(captor.capture());
            String expr = captor.getValue().getSqlSegment();
            assertFalse(expr.contains("group_id"), "admin 不应附加集团过滤，实际: " + expr);
        }
    }

    @Test
    void list_emptyGroupIdsFallsBackToMainGroup() {
        // 取不到集团归属（无岗位/无组织）时退主集团 1（fail-closed，与 MybatisPlusConfig.resolveTenantId 同口径）
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        SysRoleService service = newServiceWith(roleMapper);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentGroupIds).thenReturn(List.of());

            service.list();

            ArgumentCaptor<LambdaQueryWrapper<SysRole>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
            verify(roleMapper).selectList(captor.capture());
            String expr = captor.getValue().getSqlSegment();
            assertTrue(expr.contains("group_id"), "集团为空也应过滤到主集团，实际: " + expr);
            assertTrue(captor.getValue().getParamNameValuePairs().containsValue(1L),
                    "应退回主集团 1，实际: " + captor.getValue().getParamNameValuePairs());
        }
    }

    // ---- 单条读写集团校验（REQ-005 残余收口：getById 越权→null；create/update/delete 越权→拒绝） ----
    // 校验统一走 GroupScopeUtil.canAccessGroup：admin 恒放行；非 admin 仅允许本集团（空集合退主集团 1）。
    // 每个拒绝用例都断言底层 mapper 未被调用，证明拦截发生在写入之前（fail-closed）。

    private static SysRole roleOf(Long id, Long groupId) {
        SysRole r = new SysRole();
        r.setId(id);
        r.setGroupId(groupId);
        return r;
    }

    @Test
    void getById_otherGroupRole_returnsNull() {
        // 非 admin 读其他集团角色 → null（与列表同口径，防 id 枚举）
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        SysRoleService service = newServiceWith(roleMapper);
        when(roleMapper.selectById(5L)).thenReturn(roleOf(5L, 2L));
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentGroupIds).thenReturn(List.of(1L));

            assertNull(service.getById(5L));
        }
    }

    @Test
    void getById_sameGroupRole_returnsRoleWithMenus() {
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        SysMenuMapper menuMapper = mock(SysMenuMapper.class);
        SysRoleService service = new SysRoleService(roleMapper,
                mock(SysRoleMenuMapper.class), menuMapper, mock(SysRoleOrgMapper.class));
        when(roleMapper.selectById(5L)).thenReturn(roleOf(5L, 1L));
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentGroupIds).thenReturn(List.of(1L));

            assertNotNull(service.getById(5L));
            verify(menuMapper).selectMenuIdsByRoleId(5L);
        }
    }

    @Test
    void getById_adminSeesAnyGroup() {
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        SysRoleService service = newServiceWith(roleMapper);
        when(roleMapper.selectById(5L)).thenReturn(roleOf(5L, 2L));
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(true);

            assertNotNull(service.getById(5L));
        }
    }

    @Test
    void create_otherGroupRejected() {
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        SysRoleService service = newServiceWith(roleMapper);
        SysRole role = roleOf(null, 2L);
        role.setRoleCode("r2");
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentGroupIds).thenReturn(List.of(1L));

            assertThrows(BusinessException.class, () -> service.create(role));
            verify(roleMapper, never()).insert(any());
        }
    }

    @Test
    void create_noGroupId_defaultsToCurrentGroup() {
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        SysRoleService service = newServiceWith(roleMapper);
        SysRole role = new SysRole();
        role.setRoleCode("r9");
        role.setRoleName("测试角色");
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentGroupIds).thenReturn(List.of(1L));

            service.create(role);

            ArgumentCaptor<SysRole> captor = ArgumentCaptor.forClass(SysRole.class);
            verify(roleMapper).insert(captor.capture());
            assertEquals(1L, captor.getValue().getGroupId(), "未传 groupId 应默认当前用户集团");
        }
    }

    @Test
    void create_admin_noGroupId_defaultsToMainGroup() {
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        SysRoleService service = newServiceWith(roleMapper);
        SysRole role = new SysRole();
        role.setRoleCode("r9");
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(true);

            service.create(role);

            ArgumentCaptor<SysRole> captor = ArgumentCaptor.forClass(SysRole.class);
            verify(roleMapper).insert(captor.capture());
            assertEquals(1L, captor.getValue().getGroupId(), "admin 未传 groupId 应默认主集团 1");
        }
    }

    @Test
    void update_otherGroupRoleRejected() {
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        SysRoleService service = newServiceWith(roleMapper);
        when(roleMapper.selectById(5L)).thenReturn(roleOf(5L, 2L));
        SysRole payload = roleOf(5L, null);
        payload.setRoleName("改名");
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentGroupIds).thenReturn(List.of(1L));

            assertThrows(BusinessException.class, () -> service.update(payload));
            verify(roleMapper, never()).updateById(any());
        }
    }

    @Test
    void update_groupIdMoveToOtherGroupRejected() {
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        SysRoleService service = newServiceWith(roleMapper);
        when(roleMapper.selectById(5L)).thenReturn(roleOf(5L, 1L));
        SysRole payload = roleOf(5L, 2L);
        payload.setRoleName("改名");
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentGroupIds).thenReturn(List.of(1L));

            assertThrows(BusinessException.class, () -> service.update(payload));
            verify(roleMapper, never()).updateById(any());
        }
    }

    @Test
    void delete_otherGroupRoleRejected() {
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        SysRoleService service = newServiceWith(roleMapper);
        when(roleMapper.selectById(5L)).thenReturn(roleOf(5L, 2L));
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentGroupIds).thenReturn(List.of(1L));

            assertThrows(BusinessException.class, () -> service.delete(5L));
            verify(roleMapper, never()).deleteById(5L);
        }
    }

    @Test
    void delete_sameGroupRoleAllowed() {
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        SysRoleService service = newServiceWith(roleMapper);
        when(roleMapper.selectById(5L)).thenReturn(roleOf(5L, 1L));
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentGroupIds).thenReturn(List.of(1L));

            service.delete(5L);

            verify(roleMapper).deleteById(5L);
        }
    }

    // ===== 安全 2026-08-08：提权防护（角色授予不得超过调用者自身权限） =====

    /** 非超管创建 admin 角色 → 拒绝 */
    @Test
    void create_rejectsAdminRoleForNonAdmin() {
        SysRole role = new SysRole();
        role.setRoleCode("admin");
        role.setDataScope("all");
        SysRoleService service = newServiceWith(mock(SysRoleMapper.class));
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            assertThrows(BusinessException.class, () -> service.create(role),
                    "非超管不可创建系统管理员角色");
        }
    }

    /** 非超管创建 dataScope 宽于自身(self) 的角色 → 拒绝 */
    @Test
    void create_rejectsBroaderScopeForNonAdmin() {
        SysRole role = new SysRole();
        role.setRoleCode("sales_manager");
        role.setDataScope("all");
        SysRoleService service = newServiceWith(mock(SysRoleMapper.class));
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentDataScope).thenReturn("self");
            assertThrows(BusinessException.class, () -> service.create(role),
                    "非超管不可创建比自身数据范围更宽的角色");
        }
    }

    /** 超管可创建 admin 角色（validateGrantableRole 直接放行） */
    @Test
    void create_allowsAdminRoleForAdmin() {
        SysRole role = new SysRole();
        role.setRoleCode("admin");
        role.setDataScope("all");
        SysRoleService service = newServiceWith(mock(SysRoleMapper.class));
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(true);
            role.setGroupId(1L); // 跳过 visibleGroupIds 默认分支
            service.create(role); // 不应抛异常
        }
    }
}
