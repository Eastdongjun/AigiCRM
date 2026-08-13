package com.ycmn.module.system.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisMapperBuilderAssistant;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ycmn.common.BusinessException;
import com.ycmn.module.system.entity.SysDept;
import com.ycmn.module.system.entity.SysRole;
import com.ycmn.module.system.entity.SysRoleMenu;
import com.ycmn.module.system.mapper.SysDeptMapper;
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


    /** 显式构造以便捕获 roleMapper 的查询条件（集团边界断言） */
    private static SysRoleService newServiceWith(SysRoleMapper roleMapper) {
        return new SysRoleService(roleMapper,
                mock(SysRoleMenuMapper.class),
                mock(SysMenuMapper.class),
                mock(SysRoleOrgMapper.class),
                mock(SysDeptMapper.class),
                mock(com.ycmn.security.PermissionCacheService.class),
                mock(com.ycmn.module.system.service.RolePermScopeService.class),
                mock(com.ycmn.module.system.mapper.SysUserRoleMapper.class),
                mock(org.springframework.jdbc.core.JdbcTemplate.class));
    }

    /** 显式构造以便同时 stub roleMapper(集团归属)与校验 orgMapper(未触发写) */
    private static SysRoleService newServiceWith(SysRoleMapper roleMapper, SysRoleOrgMapper orgMapper) {
        return new SysRoleService(roleMapper,
                mock(SysRoleMenuMapper.class),
                mock(SysMenuMapper.class),
                orgMapper,
                mock(SysDeptMapper.class),
                mock(com.ycmn.security.PermissionCacheService.class),
                mock(com.ycmn.module.system.service.RolePermScopeService.class),
                mock(com.ycmn.module.system.mapper.SysUserRoleMapper.class),
                mock(org.springframework.jdbc.core.JdbcTemplate.class));
    }

    /** 显式构造以便 stub deptMapper(org 有效集团 + 子树集团一致性校验) */
    private static SysRoleService newServiceWith(SysRoleMapper roleMapper, SysRoleOrgMapper orgMapper, SysDeptMapper deptMapper) {
        return new SysRoleService(roleMapper,
                mock(SysRoleMenuMapper.class),
                mock(SysMenuMapper.class),
                orgMapper,
                deptMapper,
                mock(com.ycmn.security.PermissionCacheService.class),
                mock(com.ycmn.module.system.service.RolePermScopeService.class),
                mock(com.ycmn.module.system.mapper.SysUserRoleMapper.class),
                mock(org.springframework.jdbc.core.JdbcTemplate.class));
    }

    /** stub 组织存在 + 有效集团 + 子树显式集团(subGroups 为空=子树同集团无跨集团节点) */
    private static void stubOrg(SysDeptMapper deptMapper, Long orgId, Long effectiveGroup, List<Long> subGroups) {
        SysDept dept = new SysDept();
        dept.setId(orgId);
        when(deptMapper.selectById(orgId)).thenReturn(dept);
        when(deptMapper.selectEffectiveGroupId(orgId)).thenReturn(effectiveGroup);
        when(deptMapper.selectDescendantGroupIds(orgId)).thenReturn(subGroups);
    }

    @Test
    void updateOrgs_customScope_savesSelectedOrgs() {
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        SysRoleOrgMapper orgMapper = mock(SysRoleOrgMapper.class);
        SysDeptMapper deptMapper = mock(SysDeptMapper.class);
        when(roleMapper.selectById(5L)).thenReturn(roleOf(5L, 1L)); // 目标角色存在且集团 1
        stubOrg(deptMapper, 10L, 1L, List.of());
        stubOrg(deptMapper, 20L, 1L, List.of());
        SysRoleService service = newServiceWith(roleMapper, orgMapper, deptMapper);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentGroupIds).thenReturn(List.of(1L));

            service.updateOrgs(5L, List.of(10L, 20L));

            verify(orgMapper).deleteByRoleId(5L);
            verify(orgMapper, times(2)).insert(any());
        }
    }

    @Test
    void updateOrgs_emptyList_clearsOnly() {
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        SysRoleOrgMapper orgMapper = mock(SysRoleOrgMapper.class);
        when(roleMapper.selectById(5L)).thenReturn(roleOf(5L, 1L));
        SysRoleService service = newServiceWith(roleMapper, orgMapper);

        service.updateOrgs(5L, List.of());

        verify(orgMapper).deleteByRoleId(5L);
        verify(orgMapper, never()).insert(any());
    }

    @Test
    void updateOrgs_nullList_clearsOnly() {
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        SysRoleOrgMapper orgMapper = mock(SysRoleOrgMapper.class);
        when(roleMapper.selectById(5L)).thenReturn(roleOf(5L, 1L));
        SysRoleService service = newServiceWith(roleMapper, orgMapper);

        service.updateOrgs(5L, null);

        verify(orgMapper).deleteByRoleId(5L);
        verify(orgMapper, never()).insert(any());
    }

    @Test
    void updateOrgs_unknownRoleRejected() {
        // 未知 roleId(角色不存在) → 拒绝, 收口孤儿 role_org 写
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        SysRoleOrgMapper orgMapper = mock(SysRoleOrgMapper.class);
        SysDeptMapper deptMapper = mock(SysDeptMapper.class);
        when(roleMapper.selectById(99L)).thenReturn(null); // 角色不存在
        SysRoleService service = newServiceWith(roleMapper, orgMapper, deptMapper);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentGroupIds).thenReturn(List.of(1L));

            assertThrows(BusinessException.class, () -> service.updateOrgs(99L, List.of(10L)),
                    "未知 roleId 不应可写 role_org");
            verify(orgMapper, never()).deleteByRoleId(any());
            verify(orgMapper, never()).insert(any());
        }
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
            @SuppressWarnings("unchecked")

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
            @SuppressWarnings("unchecked")

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
            @SuppressWarnings("unchecked")

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
                mock(SysRoleMenuMapper.class), menuMapper, mock(SysRoleOrgMapper.class),
                mock(SysDeptMapper.class),
                mock(com.ycmn.security.PermissionCacheService.class),
                mock(com.ycmn.module.system.service.RolePermScopeService.class),
                mock(com.ycmn.module.system.mapper.SysUserRoleMapper.class),
                mock(org.springframework.jdbc.core.JdbcTemplate.class));
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
    void update_unknownRoleRejected() {
        // 【2026-08-12 收口】update 对不存在角色 → 拒绝, 收口孤儿 role_org 写(与 updateOrgs 对齐)
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        SysRoleOrgMapper orgMapper = mock(SysRoleOrgMapper.class);
        SysDeptMapper deptMapper = mock(SysDeptMapper.class);
        when(roleMapper.selectById(99L)).thenReturn(null); // 角色不存在
        stubOrg(deptMapper, 10L, 1L, List.of()); // org 10 有效集团 1(若未收口, 会写入孤儿 role_org)
        SysRoleService service = newServiceWith(roleMapper, orgMapper, deptMapper);
        SysRole payload = roleOf(99L, null);
        payload.setDataScope("custom");
        payload.setOrgIds(List.of(10L));
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentGroupIds).thenReturn(List.of(1L));
            m.when(SecurityUtils::getCurrentDataScope).thenReturn("all"); // 放行 validateGrantableRole, 暴露孤儿写路径

            assertThrows(BusinessException.class, () -> service.update(payload),
                    "未知 roleId 不应可写 role_org");
            verify(roleMapper, never()).updateById(any());
            verify(orgMapper, never()).insert(any());
        }
    }

    @Test
    void update_sameGroupRoleAllowed() {
        // 同集团合法角色更新正向测试: 未知 roleId 收口不误伤, updateById + role_org 写入正常
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        SysRoleOrgMapper orgMapper = mock(SysRoleOrgMapper.class);
        SysDeptMapper deptMapper = mock(SysDeptMapper.class);
        when(roleMapper.selectById(5L)).thenReturn(roleOf(5L, 1L));
        stubOrg(deptMapper, 10L, 1L, List.of());
        SysRoleService service = newServiceWith(roleMapper, orgMapper, deptMapper);
        SysRole payload = roleOf(5L, null);
        payload.setRoleName("改名");
        payload.setDataScope("custom");
        payload.setOrgIds(List.of(10L));
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentGroupIds).thenReturn(List.of(1L));
            m.when(SecurityUtils::getCurrentDataScope).thenReturn("all");

            service.update(payload);

            verify(roleMapper).updateById(payload);
            verify(orgMapper).deleteByRoleId(5L);
            verify(orgMapper, times(1)).insert(any());
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

    @Test
    void delete_unknownRole_rejected() {
        // 不存在角色 → 拒绝(不再静默成功), admin 同样拦截(存在性校验与权限无关)
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        SysRoleService service = newServiceWith(roleMapper);
        when(roleMapper.selectById(99L)).thenReturn(null);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(true);

            assertThrows(BusinessException.class, () -> service.delete(99L),
                    "删除不存在的角色不应静默成功");
            verify(roleMapper, never()).deleteById(99L);
        }
    }

    // ---- 【P2 2026-08-11】updateOrgs 集团边界校验（对齐 update/delete；此前缺失, 持 system:role:edit
    //      非超管可对任意集团角色写 role_org 扩大 custom 数据范围, 且 sys_role_org 不在租户白名单, 跨集团生效） ----

    @Test
    void updateOrgs_otherGroupRoleRejected() {
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        SysRoleOrgMapper orgMapper = mock(SysRoleOrgMapper.class);
        SysRoleService service = newServiceWith(roleMapper, orgMapper);
        when(roleMapper.selectById(5L)).thenReturn(roleOf(5L, 2L));
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentGroupIds).thenReturn(List.of(1L));

            assertThrows(BusinessException.class, () -> service.updateOrgs(5L, List.of(10L, 20L)),
                    "非超管不应可写其他集团角色的组织集");
            verify(orgMapper, never()).deleteByRoleId(any());
            verify(orgMapper, never()).insert(any());
        }
    }

    @Test
    void updateOrgs_sameGroupRoleAllowed() {
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        SysRoleOrgMapper orgMapper = mock(SysRoleOrgMapper.class);
        SysDeptMapper deptMapper = mock(SysDeptMapper.class);
        // 子树含集团 1 的显式节点(同集团) → 放行
        stubOrg(deptMapper, 10L, 1L, List.of(1L));
        stubOrg(deptMapper, 20L, 1L, List.of(1L));
        SysRoleService service = newServiceWith(roleMapper, orgMapper, deptMapper);
        when(roleMapper.selectById(5L)).thenReturn(roleOf(5L, 1L));
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentGroupIds).thenReturn(List.of(1L));

            service.updateOrgs(5L, List.of(10L, 20L));

            verify(orgMapper).deleteByRoleId(5L);
            verify(orgMapper, times(2)).insert(any());
        }
    }

    @Test
    void updateOrgs_adminAnyGroupRoleAllowed() {
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        SysRoleOrgMapper orgMapper = mock(SysRoleOrgMapper.class);
        SysRoleService service = newServiceWith(roleMapper, orgMapper);
        when(roleMapper.selectById(5L)).thenReturn(roleOf(5L, 2L));
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(true);

            service.updateOrgs(5L, List.of(10L, 20L));

            verify(orgMapper).deleteByRoleId(5L);
            verify(orgMapper, times(2)).insert(any());
        }
    }

    // ---- 【P2(c) 2026-08-11】saveRoleOrgs 组织集团归属校验(共享 create/update/updateOrgs 入口) ----
    // 此前对 orgIds 零校验: 同集团非超管可把其他集团 org 挂到本集团 custom 角色 role_org,
    // sys_dept 不在租户白名单 + OrgScopeResolver 装载子树 → 跨集团数据范围扩大真实生效。

    @Test
    void updateOrgs_otherGroupOrgRejected() {
        // 目标角色同集团, 但 org 有效集团=2 → 拒绝(fail-closed), 不写 role_org
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        SysRoleOrgMapper orgMapper = mock(SysRoleOrgMapper.class);
        SysDeptMapper deptMapper = mock(SysDeptMapper.class);
        stubOrg(deptMapper, 20L, 2L, List.of()); // org 20 有效集团 2
        SysRoleService service = newServiceWith(roleMapper, orgMapper, deptMapper);
        when(roleMapper.selectById(5L)).thenReturn(roleOf(5L, 1L));
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentGroupIds).thenReturn(List.of(1L));

            assertThrows(BusinessException.class, () -> service.updateOrgs(5L, List.of(20L)),
                    "同集团角色也不应可挂其他集团组织");
            // saveRoleOrgs 首行无条件清空(设计), 事务回滚; 核心断言=不写入任何 role_org 行
            verify(orgMapper, never()).insert(any());
        }
    }

    @Test
    void updateOrgs_unknownOrgRejected() {
        // org 在 sys_dept 不存在(已删除/伪造) → 拒绝, 不写孤儿 role_org
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        SysRoleOrgMapper orgMapper = mock(SysRoleOrgMapper.class);
        SysDeptMapper deptMapper = mock(SysDeptMapper.class);
        when(deptMapper.selectById(99L)).thenReturn(null); // 查不到
        SysRoleService service = newServiceWith(roleMapper, orgMapper, deptMapper);
        when(roleMapper.selectById(5L)).thenReturn(roleOf(5L, 1L));
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentGroupIds).thenReturn(List.of(1L));

            assertThrows(BusinessException.class, () -> service.updateOrgs(5L, List.of(99L)),
                    "查不到的 org 不应被写入");
            // saveRoleOrgs 首行无条件清空(设计), 事务回滚; 核心断言=不写入任何 role_org 行
            verify(orgMapper, never()).insert(any());
        }
    }

    @Test
    void updateOrgs_subtreeCrossGroupRejected() {
        // P2(c) 子树旁路: 锚点 org 有效集团 1(可访问), 但其子树含集团 2 节点 → 拒绝
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        SysRoleOrgMapper orgMapper = mock(SysRoleOrgMapper.class);
        SysDeptMapper deptMapper = mock(SysDeptMapper.class);
        stubOrg(deptMapper, 20L, 1L, List.of(2L)); // 子树含集团 2 显式节点
        SysRoleService service = newServiceWith(roleMapper, orgMapper, deptMapper);
        when(roleMapper.selectById(5L)).thenReturn(roleOf(5L, 1L));
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentGroupIds).thenReturn(List.of(1L));

            assertThrows(BusinessException.class, () -> service.updateOrgs(5L, List.of(20L)),
                    "includeChildren=1 展开的子树不得含跨集团节点");
            verify(orgMapper, never()).insert(any());
        }
    }

    @Test
    void create_crossGroupOrgRejected() {
        // create 入口共享 saveRoleOrgs: 非超管(调用者 all 范围)建本集团 custom 角色挂其他集团 org → 拒绝
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        SysRoleOrgMapper orgMapper = mock(SysRoleOrgMapper.class);
        SysDeptMapper deptMapper = mock(SysDeptMapper.class);
        stubOrg(deptMapper, 20L, 2L, List.of()); // org 20 有效集团 2
        SysRoleService service = newServiceWith(roleMapper, orgMapper, deptMapper);
        SysRole role = roleOf(null, null);
        role.setRoleCode("r-cross");
        role.setRoleName("跨集团角色");
        role.setDataScope("custom");
        role.setOrgIds(List.of(20L));
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentGroupIds).thenReturn(List.of(1L));
            m.when(SecurityUtils::getCurrentDataScope).thenReturn("all"); // 通过 validateGrantableRole 抵达 saveRoleOrgs

            assertThrows(BusinessException.class, () -> service.create(role));
            // create 事务回滚后不残留任何 role_org 行(核心断言)
            verify(orgMapper, never()).insert(any());
        }
    }

    @Test
    void updateOrgs_multiGroupRoleOrgMismatchRejected() {
        // P2(c)-r1: 可访问多集团的高权用户不得把其他集团 org 挂到目标角色(org 有效集团必须==角色集团)
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        SysRoleOrgMapper orgMapper = mock(SysRoleOrgMapper.class);
        SysDeptMapper deptMapper = mock(SysDeptMapper.class);
        stubOrg(deptMapper, 20L, 2L, List.of()); // org 20 有效集团 2
        SysRoleService service = newServiceWith(roleMapper, orgMapper, deptMapper);
        when(roleMapper.selectById(5L)).thenReturn(roleOf(5L, 1L)); // 目标角色集团 1
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentGroupIds).thenReturn(List.of(1L, 2L)); // 调用者可访问双集团

            assertThrows(BusinessException.class, () -> service.updateOrgs(5L, List.of(20L)),
                    "org 有效集团必须与目标角色集团一致");
            verify(orgMapper, never()).insert(any());
        }
    }

    @Test
    void updateOrgs_nullOrgRejected() {
        // P2(c)-r3: orgIds 含 null → 拒绝(避免写 role_org(org_id=NULL) 脏行)
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        SysRoleOrgMapper orgMapper = mock(SysRoleOrgMapper.class);
        SysDeptMapper deptMapper = mock(SysDeptMapper.class);
        SysRoleService service = newServiceWith(roleMapper, orgMapper, deptMapper);
        when(roleMapper.selectById(5L)).thenReturn(roleOf(5L, 1L));
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentGroupIds).thenReturn(List.of(1L));

            assertThrows(BusinessException.class, () -> service.updateOrgs(5L, java.util.Arrays.asList((Long) null)),
                    "orgIds 不应含 null");
            verify(orgMapper, never()).insert(any());
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

    // ===== 安全 2026-08-12：祖先闭包只补 menu_type=1 目录行，不注入带权限码的 type=2 父菜单（P0 提权旁路） =====

    /**
     * 【P0 2026-08-12 对齐审计修复方向2】闭包只补 menu_type=1 目录行(permission 空, 无 RBAC)，
     * 不再补带真实权限码的 menu_type=2 父菜单——父菜单须由用户显式勾选。
     * 非超管提交"只含子菜单"的请求时，type=2 父菜单权限码不得经闭包静默入库。
     * 本测试构造 13(type=2 子菜单, customer:detail:view) -> 11(type=2 父菜单, customer:view) -> 10(type=1 目录)：
     * 断言 role_menu 只写 {13, 10}——目录行 10 补入保证侧边栏锚点，父菜单 11 不注入。
     */
    @Test
    void create_ancestorClosureOnlyAddsDirRows_notParentMenu() {
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        SysMenuMapper menuMapper = mock(SysMenuMapper.class);
        SysRoleMenuMapper roleMenuMapper = mock(SysRoleMenuMapper.class);
        SysRoleService service = new SysRoleService(
                roleMapper,
                roleMenuMapper,
                menuMapper,
                mock(SysRoleOrgMapper.class),
                mock(SysDeptMapper.class),
                mock(com.ycmn.security.PermissionCacheService.class),
                mock(com.ycmn.module.system.service.RolePermScopeService.class),
                mock(com.ycmn.module.system.mapper.SysUserRoleMapper.class),
                mock(org.springframework.jdbc.core.JdbcTemplate.class));

        // 菜单树: 13(客户详情,type=2) -> 11(客户管理,type=2, customer:view) -> 10(业务核心目录,type=1)
        com.ycmn.module.system.entity.SysMenu m13 = new com.ycmn.module.system.entity.SysMenu();
        m13.setId(13L); m13.setParentId(11L); m13.setMenuType(2); m13.setPermission("customer:detail:view");
        com.ycmn.module.system.entity.SysMenu m11 = new com.ycmn.module.system.entity.SysMenu();
        m11.setId(11L); m11.setParentId(10L); m11.setMenuType(2); m11.setPermission("customer:view");
        com.ycmn.module.system.entity.SysMenu m10 = new com.ycmn.module.system.entity.SysMenu();
        m10.setId(10L); m10.setParentId(0L); m10.setMenuType(1);
        when(menuMapper.selectById(13L)).thenReturn(m13);
        when(menuMapper.selectById(11L)).thenReturn(m11);
        when(menuMapper.selectById(10L)).thenReturn(m10);
        when(menuMapper.selectBatchIds(any())).thenReturn(List.of(m13)); // validateGrantableRole 校验提交集合

        SysRole role = new SysRole();
        role.setRoleCode("sales");
        role.setDataScope("self");
        role.setGroupId(1L);
        role.setMenuIds(List.of(13L)); // 只提交子菜单(调用者持有 customer:detail:view)

        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentDataScope).thenReturn("self");
            m.when(SecurityUtils::getCurrentGroupIds).thenReturn(List.of(1L));
            com.ycmn.security.SecurityUser caller = new com.ycmn.security.SecurityUser();
            caller.setPermissions(new java.util.HashSet<>(java.util.Set.of("customer:detail:view")));
            m.when(SecurityUtils::getCurrentUser).thenReturn(caller);

            service.create(role); // 不抛异常：闭包只补目录行，type=2 父权限不被注入，无越权可拦

            // 闭包展开后 role_menu 应只写 {13(提交), 10(目录行)}，type=2 父菜单 11 不得入库
            ArgumentCaptor<SysRoleMenu> captor = ArgumentCaptor.forClass(SysRoleMenu.class);
            verify(roleMenuMapper, times(2)).insert(captor.capture());
            List<Long> written = captor.getAllValues().stream()
                    .map(SysRoleMenu::getMenuId).sorted().toList();
            assertEquals(java.util.List.of(10L, 13L), written,
                    "闭包应只补 menu_type=1 目录行(10)，不得注入 type=2 父菜单(11)");
        }
    }

    /** 非超管持有父权限时，闭包也【不】补入 type=2 父菜单——父菜单须显式勾选，杜绝静默授权 */
    @Test
    void create_ancestorClosureOnlyAddsDirRows_evenWhenCallerHoldsParentPerm() {
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        SysMenuMapper menuMapper = mock(SysMenuMapper.class);
        SysRoleMenuMapper roleMenuMapper = mock(SysRoleMenuMapper.class);
        SysRoleService service = new SysRoleService(
                roleMapper,
                roleMenuMapper,
                menuMapper,
                mock(SysRoleOrgMapper.class),
                mock(SysDeptMapper.class),
                mock(com.ycmn.security.PermissionCacheService.class),
                mock(com.ycmn.module.system.service.RolePermScopeService.class),
                mock(com.ycmn.module.system.mapper.SysUserRoleMapper.class),
                mock(org.springframework.jdbc.core.JdbcTemplate.class));

        com.ycmn.module.system.entity.SysMenu m13 = new com.ycmn.module.system.entity.SysMenu();
        m13.setId(13L); m13.setParentId(11L); m13.setMenuType(2); m13.setPermission("customer:detail:view");
        com.ycmn.module.system.entity.SysMenu m11 = new com.ycmn.module.system.entity.SysMenu();
        m11.setId(11L); m11.setParentId(10L); m11.setMenuType(2); m11.setPermission("customer:view");
        com.ycmn.module.system.entity.SysMenu m10 = new com.ycmn.module.system.entity.SysMenu();
        m10.setId(10L); m10.setParentId(0L); m10.setMenuType(1);
        when(menuMapper.selectById(13L)).thenReturn(m13);
        when(menuMapper.selectById(11L)).thenReturn(m11);
        when(menuMapper.selectById(10L)).thenReturn(m10);
        when(menuMapper.selectBatchIds(any())).thenReturn(List.of(m13));

        SysRole role = new SysRole();
        role.setRoleCode("sales");
        role.setDataScope("self");
        role.setGroupId(1L);
        role.setMenuIds(List.of(13L));

        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentDataScope).thenReturn("self");
            m.when(SecurityUtils::getCurrentGroupIds).thenReturn(List.of(1L));
            // 调用者持有子+父权限，父权限在子集内 —— 但契约是"父菜单不静默注入"
            com.ycmn.security.SecurityUser caller = new com.ycmn.security.SecurityUser();
            caller.setPermissions(new java.util.HashSet<>(java.util.Set.of("customer:detail:view", "customer:view")));
            m.when(SecurityUtils::getCurrentUser).thenReturn(caller);

            service.create(role); // 不应抛异常
            verify(roleMapper).insert(role);

            ArgumentCaptor<SysRoleMenu> captor = ArgumentCaptor.forClass(SysRoleMenu.class);
            verify(roleMenuMapper, times(2)).insert(captor.capture());
            List<Long> written = captor.getAllValues().stream()
                    .map(SysRoleMenu::getMenuId).sorted().toList();
            assertEquals(java.util.List.of(10L, 13L), written,
                    "即使调用者持有父权限，闭包也只补 type=1 目录行(10)，不注入 type=2 父菜单(11)");
        }
    }

    /** 超管创建角色时闭包同样只补 type=1 目录行，不注入 type=2 父菜单（契约与调用者权限无关） */
    @Test
    void create_ancestorClosureOnlyAddsDirRows_forAdmin() {
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        SysMenuMapper menuMapper = mock(SysMenuMapper.class);
        SysRoleMenuMapper roleMenuMapper = mock(SysRoleMenuMapper.class);
        SysRoleService service = new SysRoleService(
                roleMapper,
                roleMenuMapper,
                menuMapper,
                mock(SysRoleOrgMapper.class),
                mock(SysDeptMapper.class),
                mock(com.ycmn.security.PermissionCacheService.class),
                mock(com.ycmn.module.system.service.RolePermScopeService.class),
                mock(com.ycmn.module.system.mapper.SysUserRoleMapper.class),
                mock(org.springframework.jdbc.core.JdbcTemplate.class));

        com.ycmn.module.system.entity.SysMenu m13 = new com.ycmn.module.system.entity.SysMenu();
        m13.setId(13L); m13.setParentId(11L); m13.setMenuType(2); m13.setPermission("customer:detail:view");
        com.ycmn.module.system.entity.SysMenu m11 = new com.ycmn.module.system.entity.SysMenu();
        m11.setId(11L); m11.setParentId(10L); m11.setMenuType(2); m11.setPermission("customer:view");
        com.ycmn.module.system.entity.SysMenu m10 = new com.ycmn.module.system.entity.SysMenu();
        m10.setId(10L); m10.setParentId(0L); m10.setMenuType(1);
        when(menuMapper.selectById(13L)).thenReturn(m13);
        when(menuMapper.selectById(11L)).thenReturn(m11);
        when(menuMapper.selectById(10L)).thenReturn(m10);
        when(menuMapper.selectBatchIds(any())).thenReturn(List.of(m13));

        SysRole role = new SysRole();
        role.setRoleCode("sales");
        role.setDataScope("self");
        role.setGroupId(1L);
        role.setMenuIds(List.of(13L));

        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(true);
            service.create(role); // admin 不拦截
            verify(roleMapper).insert(role);

            ArgumentCaptor<SysRoleMenu> captor = ArgumentCaptor.forClass(SysRoleMenu.class);
            verify(roleMenuMapper, times(2)).insert(captor.capture());
            List<Long> written = captor.getAllValues().stream()
                    .map(SysRoleMenu::getMenuId).sorted().toList();
            assertEquals(java.util.List.of(10L, 13L), written,
                    "admin 闭包同样只补 type=1 目录行(10)，不注入 type=2 父菜单(11)");
        }
    }
}
