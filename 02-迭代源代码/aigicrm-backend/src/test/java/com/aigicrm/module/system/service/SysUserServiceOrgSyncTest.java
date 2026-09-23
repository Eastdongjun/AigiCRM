package com.aigicrm.module.system.service;

import com.aigicrm.module.system.entity.SysDept;
import com.aigicrm.module.system.entity.SysUser;
import com.aigicrm.module.system.mapper.SysDeptMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.lenient;

/**
 * 【租户可见性 2026-09-23】默认组织(org_id)同步逻辑单测:
 * 集团推导链兜底输入=user.org_id(无岗位时 org→dept.group_id)。
 * 历史缺陷:新建用户从不落 org_id ⇒ 非 admin 租户表查询恒空(fail-closed)。
 */
@ExtendWith(MockitoExtension.class)
class SysUserServiceOrgSyncTest {

    @Mock
    private SysDeptMapper deptMapper;

    private SysUserService service;

    @BeforeEach
    void setUp() throws Exception {
        // 构造器参数 16 个(final 字段),单测只挂被测依赖 deptMapper,其余置 null
        Object[] nulls = new Object[16];
        service = (SysUserService) SysUserService.class.getConstructors()[0].newInstance(nulls);
        Field f = SysUserService.class.getDeclaredField("deptMapper");
        f.setAccessible(true);
        f.set(service, deptMapper);
    }

    private SysDept dept(Long id, Long groupId) {
        SysDept d = new SysDept();
        d.setId(id);
        d.setGroupId(groupId);
        return d;
    }

    @Test
    @DisplayName("部门有集团归属 → org_id=部门id")
    void syncDefaultOrg_deptWithGroup_setsOrgToDept() {
        lenient().when(deptMapper.selectById(6L)).thenReturn(dept(6L, 1L));
        SysUser u = new SysUser();
        u.setDeptId(6L);
        invokeSync(u);
        assertEquals(6L, u.getOrgId());
    }

    @Test
    @DisplayName("部门无集团/不存在 → 兜底主集团顶级组织 1")
    void syncDefaultOrg_deptWithoutGroup_fallsBackRoot() {
        lenient().when(deptMapper.selectById(9L)).thenReturn(dept(9L, null));
        SysUser u = new SysUser();
        u.setDeptId(9L);
        invokeSync(u);
        assertEquals(1L, u.getOrgId());

        SysUser u2 = new SysUser();
        u2.setDeptId(404L); // 部门不存在
        invokeSync(u2);
        assertEquals(1L, u2.getOrgId());
    }

    @Test
    @DisplayName("无部门 → 兜底主集团顶级组织 1")
    void syncDefaultOrg_noDept_fallsBackRoot() {
        SysUser u = new SysUser();
        invokeSync(u);
        assertEquals(1L, u.getOrgId());
    }

    @Test
    @DisplayName("显式指定 org_id 时尊重不覆盖")
    void syncDefaultOrg_explicitOrg_kept() {
        SysUser u = new SysUser();
        u.setDeptId(6L);
        u.setOrgId(3L);
        invokeSync(u);
        assertEquals(3L, u.getOrgId());
    }

    private void invokeSync(SysUser u) {
        try {
            java.lang.reflect.Method m = SysUserService.class.getDeclaredMethod("syncDefaultOrg", SysUser.class);
            m.setAccessible(true);
            m.invoke(service, u);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
