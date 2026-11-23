package com.aigicrm.module.system.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.common.scope.GroupScopeUtil;
import com.aigicrm.module.system.entity.SysGroup;
import com.aigicrm.module.system.mapper.SysGroupMapper;
import com.aigicrm.security.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 【产品口径 2026-11-15】集团写操作：
 * 仅平台管理员可创建/修改/删除；单集团部署（无集团时才可创建）；唯一活跃集团不可禁用/删除；编码创建后不可改。
 */
class SysGroupServiceUniqueGroupTest {

    private final SysGroupMapper groupMapper = mock(SysGroupMapper.class);
    private final SysGroupService service = new SysGroupService(groupMapper);

    private static SysGroup group(long id, int status) {
        SysGroup g = new SysGroup();
        g.setId(id);
        g.setGroupName("集团" + id);
        g.setGroupCode("G" + id);
        g.setStatus(status);
        return g;
    }

    private static MockedStatic<SecurityUtils> admin() {
        MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class);
        sec.when(SecurityUtils::isAdmin).thenReturn(true);
        return sec;
    }

    @Test
    void create_whenNoGroup_inserts() {
        when(groupMapper.selectCount(isNull())).thenReturn(0L);
        when(groupMapper.selectCount(notNull())).thenReturn(0L);
        try (MockedStatic<SecurityUtils> sec = admin()) {
            service.create(group(9L, 1));
        }
        verify(groupMapper).insert(any(SysGroup.class));
    }

    @Test
    void create_whenGroupExists_rejected400() {
        when(groupMapper.selectCount(isNull())).thenReturn(1L);
        try (MockedStatic<SecurityUtils> sec = admin()) {
            BusinessException ex = createRejected(group(9L, 1));
            assertEquals(400, ex.getCode());
            assertEquals("单集团部署仅允许一个集团", ex.getMessage());
            assertEquals("SINGLE_GROUP_ONLY", ex.getErrorCode());
        }
        verify(groupMapper, never()).insert(any(SysGroup.class));
    }

    @Test
    void create_nonAdmin_rejected403() {
        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::isAdmin).thenReturn(false);
            BusinessException ex = createRejected(group(9L, 1));
            assertEquals(403, ex.getCode());
            assertEquals("ADMIN_ONLY", ex.getErrorCode());
        }
    }

    @Test
    void update_lastActiveToDisabled_rejected400() {
        when(groupMapper.selectById(1L)).thenReturn(group(1L, 1));
        when(groupMapper.selectCount(any())).thenReturn(1L);
        try (MockedStatic<SecurityUtils> sec = admin()) {
            SysGroup patch = group(1L, 0);
            BusinessException ex = assertThrows(BusinessException.class, () -> service.update(patch));
            assertEquals(400, ex.getCode());
            assertEquals("不能禁用唯一集团", ex.getMessage());
        }
        verify(groupMapper, never()).updateById(any(SysGroup.class));
    }

    @Test
    void update_groupCodeChanged_rejected400() {
        when(groupMapper.selectById(1L)).thenReturn(group(1L, 1));
        try (MockedStatic<SecurityUtils> sec = admin()) {
            SysGroup patch = group(1L, 1);
            patch.setGroupCode("CHANGED");
            BusinessException ex = assertThrows(BusinessException.class, () -> service.update(patch));
            assertEquals(400, ex.getCode());
            assertEquals("集团编码创建后不可修改", ex.getMessage());
        }
    }

    @Test
    void update_nonAdmin_rejected403() {
        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::isAdmin).thenReturn(false);
            BusinessException ex = updateRejected(group(1L, 1));
            assertEquals(403, ex.getCode());
        }
    }

    @Test
    void delete_lastActiveGroup_rejected400() {
        when(groupMapper.selectById(1L)).thenReturn(group(1L, 1));
        when(groupMapper.selectCount(any())).thenReturn(1L);
        try (MockedStatic<SecurityUtils> sec = admin();
             MockedStatic<GroupScopeUtil> scope = mockStatic(GroupScopeUtil.class)) {
            scope.when(() -> GroupScopeUtil.canAccessGroup(1L)).thenReturn(true);
            BusinessException ex = assertThrows(BusinessException.class, () -> service.delete(1L));
            assertEquals(400, ex.getCode());
            assertEquals("不能删除唯一集团", ex.getMessage());
        }
        verify(groupMapper, never()).updateById(any(SysGroup.class));
    }

    @Test
    void delete_whenAnotherActiveGroupExists_softDisables() {
        when(groupMapper.selectById(1L)).thenReturn(group(1L, 1));
        when(groupMapper.selectCount(any())).thenReturn(2L);
        try (MockedStatic<SecurityUtils> sec = admin();
             MockedStatic<GroupScopeUtil> scope = mockStatic(GroupScopeUtil.class)) {
            scope.when(() -> GroupScopeUtil.canAccessGroup(1L)).thenReturn(true);
            service.delete(1L);
        }
        verify(groupMapper).updateById(any(SysGroup.class));
    }

    @Test
    void delete_nonAdmin_rejected403() {
        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::isAdmin).thenReturn(false);
            BusinessException ex = assertThrows(BusinessException.class, () -> service.delete(1L));
            assertEquals(403, ex.getCode());
        }
        verify(groupMapper, never()).updateById(any(SysGroup.class));
    }

    private BusinessException createRejected(SysGroup group) {
        return assertThrows(BusinessException.class, () -> service.create(group));
    }

    private BusinessException updateRejected(SysGroup group) {
        return assertThrows(BusinessException.class, () -> service.update(group));
    }
}
