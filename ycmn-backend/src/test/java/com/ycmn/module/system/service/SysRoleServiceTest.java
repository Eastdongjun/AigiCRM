package com.ycmn.module.system.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisMapperBuilderAssistant;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * SysRoleService 单元测试（纯 mock，无 Spring 上下文）。
 * 覆盖两类行为：
 * - updateOrgs：custom 数据范围的组织集合保存（清空重建，幂等）；
 * - list：集团边界过滤（非 admin 仅本集团 / admin 全量 / 空集团退主集团 1，
 *   仿 SysUserService.applyGroupScope 口径，断言 LambdaQueryWrapper 条件）。
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
}
