package com.aigicrm.module.file.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.module.file.entity.BizFolderTypeDefault;
import com.aigicrm.module.file.mapper.BizFolderTypeDefaultMapper;
import com.aigicrm.module.system.entity.SysRole;
import com.aigicrm.module.system.entity.SysUser;
import com.aigicrm.module.system.mapper.SysRoleMapper;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.security.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 【2026-11-09 文件权限 v2】主体扩展(ROLE|USER)/词表归一/建夹门槛三面回归:
 * ①非法类型/主体/权限项/停用角色拒绝; ②'edit' 伞写入时归一为 rename+replace;
 * ③canCreateRoot 认 USER 行(点名本人)与 ROLE 行(本人角色)且含 create;
 * ④v1 materializeAcl 已退役(实时生效语义)——本类不再有物化用例。
 */
class FolderTypeDefaultServiceTest {

    private final BizFolderTypeDefaultMapper defaultMapper = mock(BizFolderTypeDefaultMapper.class);
    private final SysRoleMapper roleMapper = mock(SysRoleMapper.class);
    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final FolderTypeDefaultService svc =
            new FolderTypeDefaultService(defaultMapper, roleMapper, userMapper,
            mock(com.aigicrm.security.SecurityAuditService.class)); // 体检P4 审计依赖

    private SysRole role(long id, String code, int status) {
        SysRole r = new SysRole();
        r.setId(id);
        r.setRoleCode(code);
        r.setStatus(status);
        return r;
    }

    private SysUser user(long id) {
        SysUser u = new SysUser();
        u.setId(id);
        u.setUsername("u" + id);
        u.setRealName("用户" + id);
        return u;
    }

    private BizFolderTypeDefault row(String folderType, String subjectType, long subjectId, String perms) {
        BizFolderTypeDefault d = new BizFolderTypeDefault();
        d.setFolderType(folderType);
        d.setSubjectType(subjectType);
        d.setSubjectId(subjectId);
        d.setPerms(perms);
        return d;
    }

    @Test
    void saveDefault_rejectsUnknownTypeSubjectAndPerm() {
        assertThrows(BusinessException.class, () -> svc.saveDefault("company", "ROLE", 9L, List.of("view")));
        assertThrows(BusinessException.class, () -> svc.saveDefault("public", "TEAM", 9L, List.of("view"))); // 主体白名单
        assertThrows(BusinessException.class, () -> svc.saveDefault("public", "ROLE", 9L, List.of("file:upload"))); // 长词表回潮拒绝
        assertThrows(BusinessException.class, () -> svc.saveDefault("public", "ROLE", 9L, List.of()));
        when(roleMapper.selectById(9L)).thenReturn(role(9L, "05", 0)); // 停用角色
        assertThrows(BusinessException.class, () -> svc.saveDefault("public", "ROLE", 9L, List.of("view")));
        when(userMapper.selectById(88L)).thenReturn(null); // 用户不存在
        assertThrows(BusinessException.class, () -> svc.saveDefault("public", "USER", 88L, List.of("view")));
    }

    @Test
    void saveDefault_normalizesEditUmbrellaToLeafActions() {
        // v1 调用方传 'edit' → 落库归一为 rename+replace(数据面收敛到细词)
        when(roleMapper.selectById(9L)).thenReturn(role(9L, "05", 1));
        when(defaultMapper.selectOne(any())).thenReturn(null);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            svc.saveDefault("public", "ROLE", 9L, List.of("view", "edit", "create"));
        }
        ArgumentCaptor<BizFolderTypeDefault> cap = ArgumentCaptor.forClass(BizFolderTypeDefault.class);
        verify(defaultMapper).insert(cap.capture());
        assertEquals("public", cap.getValue().getFolderType());
        assertEquals("ROLE", cap.getValue().getSubjectType());
        assertEquals(9L, cap.getValue().getSubjectId());
        assertEquals("view,rename,replace,create", cap.getValue().getPerms(), "edit 伞必须归一为 rename+replace");
    }

    @Test
    void saveDefault_userSubject_persistsAndUpserts() {
        when(userMapper.selectById(42L)).thenReturn(user(42L));
        when(defaultMapper.selectOne(any())).thenReturn(null);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            svc.saveDefault("public", "USER", 42L, List.of("rename", "delete"));
        }
        ArgumentCaptor<BizFolderTypeDefault> cap = ArgumentCaptor.forClass(BizFolderTypeDefault.class);
        verify(defaultMapper).insert(cap.capture());
        assertEquals("USER", cap.getValue().getSubjectType());
        assertEquals(42L, cap.getValue().getSubjectId());
        assertEquals("rename,delete", cap.getValue().getPerms());
    }

    @Test
    void canCreateRoot_userSubjectRow_grants() {
        // 点名某人的类型授权含 create → 该人可建公共根夹(admin 之外的第二条路)
        when(defaultMapper.selectList(any())).thenReturn(List.of(row("public", "USER", 42L, "view,create")));
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(42L);
            assertTrue(svc.canCreateRoot("public"), "USER 行点名本人且含 create 应放行");

            m.when(SecurityUtils::getCurrentUserId).thenReturn(43L);
            assertFalse(svc.canCreateRoot("public"), "非点名本人不放行");
        }
    }

    @Test
    void canCreateRoot_roleSubject_matchesViaRoleCode() {
        when(defaultMapper.selectList(any())).thenReturn(List.of(row("public", "ROLE", 9L, "view,upload,create")));
        when(roleMapper.selectById(9L)).thenReturn(role(9L, "05", 1));
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentRoleCodes).thenReturn(List.of("05"));
            assertTrue(svc.canCreateRoot("public"), "本人角色(05)行含 create 应放行");

            m.when(SecurityUtils::getCurrentRoleCodes).thenReturn(List.of("06"));
            assertFalse(svc.canCreateRoot("public"), "非授权行角色(06)不放行");
        }
    }

    @Test
    void canCreateRoot_rowWithoutCreateToken_denies() {
        when(defaultMapper.selectList(any())).thenReturn(List.of(row("public", "ROLE", 9L, "view,rename,replace")));
        when(roleMapper.selectById(9L)).thenReturn(role(9L, "05", 1));
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentRoleCodes).thenReturn(List.of("05"));
            assertFalse(svc.canCreateRoot("public"), "授权行不含 create 不放行建根夹(其余权限照常)");
        }
    }

    @Test
    void materializeAcl_isRetired() {
        // 【防复活守卫】v2 实时生效语义: 类型授权不再落 biz_folder_acl 快照(勾了即生效/取消即收回)。
        // 若有人重新引入物化, 该用例提醒先改设计文档与判定层口径。
        java.lang.reflect.Method[] methods = FolderTypeDefaultService.class.getDeclaredMethods();
        for (java.lang.reflect.Method m : methods) {
            assertFalse("materializeAcl".equals(m.getName()), "materializeAcl 已于 V2026.11.09.5 退役, 不得复活");
        }
        verify(defaultMapper, never()).insert(any(BizFolderTypeDefault.class));
    }
}
