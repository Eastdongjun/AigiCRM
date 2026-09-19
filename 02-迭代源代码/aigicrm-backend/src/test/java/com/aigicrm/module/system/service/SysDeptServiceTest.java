package com.aigicrm.module.system.service;

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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * 【N-06 2026-09-19】部门查询数据范围回归:
 * 存量 scopeFilter(本人部门+祖先链,FEAT-20260804)+ 本轮集团边界加固(祖先链外再按
 * 所属集团收窄,脏数据 parent 跨集团不带出别集团部门;无集团 fail-closed)。
 */
class SysDeptServiceTest {

    private final SysDeptMapper deptMapper = mock(SysDeptMapper.class);
    private final SysDeptService svc = new SysDeptService(
            deptMapper, mock(SysUserMapper.class), mock(PermissionCacheService.class), mock(JdbcTemplate.class));

    private static SysDept dept(long id, Long parentId, long groupId) {
        SysDept d = new SysDept();
        d.setId(id);
        d.setParentId(parentId);
        d.setGroupId(groupId);
        d.setStatus(1);
        d.setDeptName("部门" + id);
        return d;
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void list_nonAdmin_returnsOwnDeptAndAncestorsWithinGroup() {
        // 组织:1(集团1根) → 2(内蒙古大区,集团1) → 3(内蒙办,集团1);4=太原办(兄弟,集团1);
        // 9002=验证集团B部门(不该出现)
        when(deptMapper.selectList(any())).thenReturn(java.util.List.of(
                dept(1L, 0L, 1L), dept(2L, 1L, 1L), dept(3L, 2L, 1L), dept(4L, 1L, 1L), dept(9002L, 0L, 9002L)));

        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::isAdmin).thenReturn(false);
            sec.when(SecurityUtils::getCurrentDeptId).thenReturn(3L);
            sec.when(SecurityUtils::getCurrentGroupIds).thenReturn(List.of(1L));

            List<SysDept> result = svc.list();
            assertEquals(List.of(1L, 2L, 3L), result.stream().map(SysDept::getId).toList(),
                    "非 admin 仅见本部门+祖先链;兄弟部门与别集团部门均不可见");
        }
    }

    @Test
    void list_admin_unfiltered() {
        when(deptMapper.selectList(any())).thenReturn(java.util.List.of(
                dept(1L, 0L, 1L), dept(9002L, 0L, 9002L)));

        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::isAdmin).thenReturn(true);

            assertEquals(2, svc.list().size(), "平台 admin 跨集团全量可见");
        }
    }

    @Test
    void list_noDept_empty() {
        when(deptMapper.selectList(any())).thenReturn(java.util.List.of(dept(1L, 0L, 1L)));

        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::isAdmin).thenReturn(false);
            sec.when(SecurityUtils::getCurrentDeptId).thenReturn(null);

            assertTrue(svc.list().isEmpty(), "无部门归属 → 空(fail-closed)");
        }
    }

    @Test
    void list_noGroup_failClosed() {
        when(deptMapper.selectList(any())).thenReturn(java.util.List.of(dept(1L, 0L, 1L), dept(3L, 2L, 1L)));

        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::isAdmin).thenReturn(false);
            sec.when(SecurityUtils::getCurrentDeptId).thenReturn(3L);
            sec.when(SecurityUtils::getCurrentGroupIds).thenReturn(List.of());

            assertTrue(svc.list().isEmpty(), "无集团归属 → 空(fail-closed,不退回任何部门)");
        }
    }

    @Test
    void list_crossGroupParentChain_stopsAtGroupBoundary() {
        // 脏数据:本部门 3 的 parent 指向别集团节点 9002(集团守卫须先剔除 9002,链在 3 截断)
        when(deptMapper.selectList(any())).thenReturn(java.util.List.of(
                dept(9002L, 0L, 9002L), dept(3L, 9002L, 1L), dept(4L, 1L, 1L)));

        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::isAdmin).thenReturn(false);
            sec.when(SecurityUtils::getCurrentDeptId).thenReturn(3L);
            sec.when(SecurityUtils::getCurrentGroupIds).thenReturn(List.of(1L));

            List<SysDept> result = svc.list();
            assertEquals(List.of(3L), result.stream().map(SysDept::getId).toList(),
                    "parent 链跨集团时止于集团边界,不带出别集团部门");
        }
    }
}
