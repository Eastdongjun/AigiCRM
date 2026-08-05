package com.ycmn.common.scope;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ycmn.module.system.entity.SysDept;
import com.ycmn.module.system.entity.SysUser;
import com.ycmn.module.system.mapper.SysDeptMapper;
import com.ycmn.module.system.mapper.SysUserMapper;
import com.ycmn.security.SecurityUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrgScopeResolverTest {

    @Mock SysUserMapper userMapper;
    @Mock SysDeptMapper deptMapper;

    private SysUser user(long id, Long deptId, Long managerId) {
        SysUser u = new SysUser();
        u.setId(id); u.setDeptId(deptId); u.setManagerId(managerId);
        return u;
    }
    private SysDept dept(long id, Long parentId, Integer deptType) {
        SysDept d = new SysDept();
        d.setId(id); d.setParentId(parentId); d.setDeptType(deptType);
        return d;
    }
    private SecurityUser su(Long userId, String scope, Long deptId) {
        SecurityUser s = new SecurityUser();
        s.setUserId(userId); s.setDataScope(scope); s.setDeptId(deptId);
        return s;
    }

    @Test
    void teamResolvesWholeChain() {
        // B(2) 的 manager = A(1)；C(3) 的 manager = B(2)。A 可见 {1,2,3}
        when(userMapper.selectList(any())).thenReturn(List.of(user(2, 11L, 1L), user(3, 12L, 2L)));
        OrgScopeResolver r = new OrgScopeResolver(userMapper, deptMapper);
        List<Long> u = r.resolve(su(1L, "team", 11L));
        assertTrue(u.contains(1L) && u.contains(2L) && u.contains(3L));
    }

    @Test
    void deptResolvesSubtreeUsers() {
        // 部门 11 有子部门 12；用户 A(1) 在 11，B(2) 在 12
        when(deptMapper.selectList(any())).thenReturn(List.of(dept(12, 11L, null)));
        when(userMapper.selectList(any())).thenReturn(List.of(user(2, 12L, null)));
        OrgScopeResolver r = new OrgScopeResolver(userMapper, deptMapper);
        List<Long> u = r.resolve(su(1L, "dept", 11L));
        assertTrue(u.contains(1L) && u.contains(2L));
    }

    @Test
    void allReturnsNull() {
        OrgScopeResolver r = new OrgScopeResolver(userMapper, deptMapper);
        assertNull(r.resolve(su(1L, "all", 11L)));
    }

    @Test
    void selfReturnsOnlySelf() {
        OrgScopeResolver r = new OrgScopeResolver(userMapper, deptMapper);
        assertEquals(List.of(1L), r.resolve(su(1L, "self", 11L)));
    }
}
