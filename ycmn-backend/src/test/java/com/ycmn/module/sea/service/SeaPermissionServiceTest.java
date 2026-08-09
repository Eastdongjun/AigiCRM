package com.ycmn.module.sea.service;

import com.ycmn.module.system.entity.SysDept;
import com.ycmn.module.system.entity.SysUser;
import com.ycmn.module.system.mapper.SysDeptMapper;
import com.ycmn.module.system.mapper.SysUserMapper;
import com.ycmn.security.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * 【2026-08-10 公海专项权限】按组织架构层级勾选判定测试。
 * 覆盖: admin 恒 true / 人员命中 / 部门上级链递归命中 / 未命中无权限 / 无授权行无权限。
 */
class SeaPermissionServiceTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final SysDeptMapper deptMapper = mock(SysDeptMapper.class);
    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final SeaPermissionService svc = new SeaPermissionService(jdbc, deptMapper, userMapper);

    /** 构造用户: id + deptId */
    private SysUser user(long id, Long deptId) {
        SysUser u = new SysUser();
        u.setId(id);
        u.setDeptId(deptId);
        return u;
    }

    private SysDept dept(long id, Long parentId) {
        SysDept d = new SysDept();
        d.setId(id);
        d.setParentId(parentId);
        return d;
    }

    @Test
    void admin_alwaysTrue_regardlessOfTable() {
        // admin 不走表判定, 即使表无授权行也 true
        when(jdbc.queryForMap(anyString(), any())).thenThrow(new org.springframework.dao.EmptyResultDataAccessException(1));
        when(userMapper.selectById(7L)).thenReturn(user(7L, 1L));
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(true);
            assertTrue(svc.hasPerm(SeaPermissionService.VIEW, 7L));
            assertTrue(svc.hasPerm(SeaPermissionService.EDIT, 7L));
            assertTrue(svc.hasPerm(SeaPermissionService.DELETE, 7L));
            assertTrue(svc.hasPerm(SeaPermissionService.CLAIM, 7L));
        }
    }

    @Test
    void userDirectHit_grantsPerm() {
        when(jdbc.queryForMap(anyString(), any())).thenReturn(
                Map.of("dept_ids", "10,20", "user_ids", "7,8"));
        when(userMapper.selectById(7L)).thenReturn(user(7L, 999L)); // dept 999 不在授权, 但 userId 7 命中
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            assertTrue(svc.hasPerm(SeaPermissionService.VIEW, 7L));
        }
    }

    @Test
    void deptAncestorRecursion_hitsAuthorizedDept() {
        // 授权 dept 10; 用户 dept 30 的上级链 30→20→10, 递归命中
        when(jdbc.queryForMap(anyString(), any())).thenReturn(
                new java.util.HashMap<>(Map.of("dept_ids", "10"))); // user_ids 缺省 → null
        when(userMapper.selectById(7L)).thenReturn(user(7L, 30L));
        when(deptMapper.selectList(any())).thenReturn(
                List.of(dept(10L, 1L), dept(20L, 10L), dept(30L, 20L)));
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            assertTrue(svc.hasPerm(SeaPermissionService.VIEW, 7L));
        }
    }

    @Test
    void noMatch_rejects() {
        // 授权 dept 50(不在用户 30 的上级链 30→20→10 上), user_ids=8(不是7) → 拒绝
        when(jdbc.queryForMap(anyString(), any())).thenReturn(
                Map.of("dept_ids", "50", "user_ids", "8"));
        when(userMapper.selectById(7L)).thenReturn(user(7L, 30L));
        when(deptMapper.selectList(any())).thenReturn(
                List.of(dept(10L, 1L), dept(20L, 10L), dept(30L, 20L)));
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            assertFalse(svc.hasPerm(SeaPermissionService.DELETE, 7L));
            assertFalse(svc.hasPerm(SeaPermissionService.CLAIM, 7L));
        }
    }

    @Test
    void noTableRow_rejects() {
        when(jdbc.queryForMap(anyString(), any())).thenThrow(new org.springframework.dao.EmptyResultDataAccessException(1));
        when(userMapper.selectById(7L)).thenReturn(user(7L, 1L));
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            assertFalse(svc.hasPerm(SeaPermissionService.VIEW, 7L)); // 无授权行 → 无权限(查看也不例外)
        }
    }
}
