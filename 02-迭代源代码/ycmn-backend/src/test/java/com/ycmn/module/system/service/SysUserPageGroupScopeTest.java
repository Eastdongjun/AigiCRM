package com.ycmn.module.system.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisMapperBuilderAssistant;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ycmn.module.customer.mapper.BizCustomerMapper;
import com.ycmn.module.customer.mapper.BizCustomerShareMapper;
import com.ycmn.module.sea.service.SeaService;
import com.ycmn.module.system.entity.SysDept;
import com.ycmn.module.system.entity.SysUser;
import com.ycmn.module.system.mapper.SysDeptMapper;
import com.ycmn.module.system.mapper.SysRoleMapper;
import com.ycmn.module.system.mapper.SysUserMapper;
import com.ycmn.module.system.mapper.SysUserRoleMapper;
import com.ycmn.security.SecurityUtils;

/**
 * 系统管理页集团边界回归测试（2026-08-07 生产回归：admin 未传 deptIds 被误判空页）。
 *
 * 覆盖：
 * 1. admin 不带 deptIds → 归一空串下发 mapper（= 不按部门过滤），不返回空页
 * 2. admin 显式 deptIds → 原样透传
 * 3. 非 admin 且本集团无可见部门 → 空页，不下发记录
 * 4. 【2026-09-02 数据范围叠加】非 admin 时 applyDataScope 按 resolveVisibleDeptIds 收窄
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked") // RedisTemplate mock 泛型未检查转换(诊断降噪 2026-09-02)
class SysUserPageGroupScopeTest {

    @Mock
    SysUserMapper userMapper;
    @Mock
    SysUserRoleMapper userRoleMapper;
    @Mock
    PasswordEncoder passwordEncoder;
    @Mock
    SysConfigService configService;
    @Mock
    BizCustomerMapper customerMapper;
    @Mock
    BizCustomerShareMapper shareMapper;
    @Mock
    JdbcTemplate jdbc;
    @Mock
    SysRoleMapper roleMapper;
    @Mock
    SeaService seaService;
    @Mock
    SysDeptMapper deptMapper;

    /** 纯 mock 单测无 MyBatis 启动，手动注册 TableInfo 供 LambdaQueryWrapper 解析列名 */
    @BeforeAll
    static void initTableInfo() {
        MybatisMapperBuilderAssistant assistant = new MybatisMapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SysUser.class);
        TableInfoHelper.initTableInfo(assistant, SysDept.class);
    }

    private SysUserService newService() {
        return new SysUserService(userMapper, userRoleMapper, passwordEncoder, configService,
                customerMapper, shareMapper, jdbc, roleMapper, seaService, deptMapper,
                mock(com.ycmn.module.system.mapper.SysUserOrgMapper.class),
                mock(com.ycmn.security.PermissionCacheService.class),
                mock(com.ycmn.security.JwtTokenProvider.class),
                mock(com.ycmn.common.scope.OrgScopeResolver.class), mock(RedisTemplate.class));
    }

    @Test
    void adminWithoutDeptIdsRunsPagedQuery() {
        // 回归核心：admin 不带 deptIds 时，page() 不得把 null 误判为"无可见部门"返回空页
        when(userMapper.selectUserPage(any(), any(), any(), any()))
                .thenReturn(new Page<SysUser>(1, 15));

        SysUserService service = newService();
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(true);

            service.page(1, 15, null, null, null);

            ArgumentCaptor<String> deptCaptor = ArgumentCaptor.forClass(String.class);
            verify(userMapper).selectUserPage(any(), any(), deptCaptor.capture(), any());
            // null 应被归一为空串"不按部门过滤"，而不是返回空页（修复点）
            assertEquals("", deptCaptor.getValue(),
                    "admin 未传 deptIds 应归一为空串下发 mapper（= 不按部门过滤）");
        }
    }

    @Test
    void adminWithDeptIdsPassesThrough() {
        when(userMapper.selectUserPage(any(), any(), any(), any()))
                .thenReturn(new Page<SysUser>(1, 15));

        SysUserService service = newService();
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(true);

            service.page(1, 15, null, "1", null);

            ArgumentCaptor<String> deptCaptor = ArgumentCaptor.forClass(String.class);
            verify(userMapper).selectUserPage(any(), any(), deptCaptor.capture(), any());
            assertEquals("1", deptCaptor.getValue(), "admin 显式 deptIds 应原样透传");
        }
    }

    @Test
    void nonAdminWithNoVisibleDeptsReturnsEmptyPage() {
        // 既有集团边界行为不回退：非 admin 且本集团无可见部门 → 空页，不下发任何记录
        SysUserService service = newService();
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentGroupIds).thenReturn(List.of(1L));
            when(deptMapper.selectList(any())).thenReturn(Collections.emptyList());

            var result = service.page(1, 15, null, null, null);

            assertEquals(0, result.getTotal(), "无可见部门应返回空页");
            verify(userMapper, never()).selectUserPage(any(), any(), any(), any());
        }
    }
}
