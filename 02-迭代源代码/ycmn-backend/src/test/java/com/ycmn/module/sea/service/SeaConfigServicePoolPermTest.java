package com.ycmn.module.sea.service;

import com.ycmn.module.sea.entity.BizSeaConfig;
import com.ycmn.module.sea.mapper.BizSeaConfigMapper;
import com.ycmn.module.sea.mapper.BizSeaPoolMapper;
import com.ycmn.module.system.entity.SysDept;
import com.ycmn.module.system.mapper.SysDeptMapper;
import com.ycmn.module.system.mapper.SysUserMapper;
import com.ycmn.security.SecurityUser;
import com.ycmn.security.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * 【2026-08-10 公海池级权限】池级判定测试。
 * 覆盖: admin 恒 true / 组织递归命中 / 人员命中 / 未命中无权限 / 空授权无权限 / 查看权限。
 */
class SeaConfigServicePoolPermTest {

    private final BizSeaConfigMapper configMapper = mock(BizSeaConfigMapper.class);
    private final BizSeaPoolMapper seaPoolMapper = mock(BizSeaPoolMapper.class);
    private final SysDeptMapper deptMapper = mock(SysDeptMapper.class);
    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final SeaConfigService svc = new SeaConfigService(configMapper, seaPoolMapper, deptMapper, userMapper);

    private SecurityUser user(long id, Long deptId, String dataScope) {
        SecurityUser u = new SecurityUser();
        u.setUserId(id);
        u.setDeptId(deptId);
        u.setDataScope(dataScope);
        return u;
    }

    private BizSeaConfig config(String editDeptIds, String editUserIds) {
        BizSeaConfig c = new BizSeaConfig();
        c.setId(10L);
        c.setEditDeptIds(editDeptIds);
        c.setEditUserIds(editUserIds);
        return c;
    }

    private SysDept dept(long id, Long parentId) {
        SysDept d = new SysDept();
        d.setId(id);
        d.setParentId(parentId);
        return d;
    }

    @Test
    void admin_alwaysTrue() {
        when(configMapper.selectById(10L)).thenReturn(config("20", null));
        when(deptMapper.selectList(any())).thenReturn(List.of());
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(true);
            assertTrue(svc.hasPoolPerm(10L, SeaConfigService.PERM_EDIT, user(7L, 30L, "self")));
        }
    }

    @Test
    void deptAncestorRecursion_hits() {
        when(configMapper.selectById(10L)).thenReturn(config("20", null)); // 授权部门20
        when(deptMapper.selectList(any())).thenReturn(
                List.of(dept(10L, 1L), dept(20L, 10L), dept(30L, 20L))); // 用户30→20→10
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            assertTrue(svc.hasPoolPerm(10L, SeaConfigService.PERM_EDIT, user(7L, 30L, "self")));
        }
    }

    @Test
    void userDirectHit_grants() {
        when(configMapper.selectById(10L)).thenReturn(config(null, "7,8"));
        when(deptMapper.selectList(any())).thenReturn(List.of());
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            assertTrue(svc.hasPoolPerm(10L, SeaConfigService.PERM_EDIT, user(7L, 999L, "self")));
        }
    }

    @Test
    void noMatch_rejects() {
        when(configMapper.selectById(10L)).thenReturn(config("50", "8")); // 授权50, 用户不在
        when(deptMapper.selectList(any())).thenReturn(
                List.of(dept(10L, 1L), dept(20L, 10L), dept(30L, 20L)));
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            assertFalse(svc.hasPoolPerm(10L, SeaConfigService.PERM_EDIT, user(7L, 30L, "self")));
        }
    }

    @Test
    void emptyAuth_rejects() {
        when(configMapper.selectById(10L)).thenReturn(config(null, null)); // 无授权
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            assertFalse(svc.hasPoolPerm(10L, SeaConfigService.PERM_EDIT, user(7L, 1L, "self")));
        }
    }

    @Test
    void view_usesVisibleRange() {
        BizSeaConfig c = config(null, null);
        c.setVisibleDeptIds("20");
        when(configMapper.selectById(10L)).thenReturn(c);
        when(deptMapper.selectList(any())).thenReturn(
                List.of(dept(10L, 1L), dept(20L, 10L), dept(30L, 20L)));
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            assertTrue(svc.hasPoolPerm(10L, SeaConfigService.PERM_VIEW, user(7L, 30L, "self")));
        }
    }
}
