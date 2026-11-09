package com.aigicrm.module.file.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.module.file.entity.BizFolderAcl;
import com.aigicrm.module.file.entity.BizFolderTypeDefault;
import com.aigicrm.module.file.mapper.BizFolderAclMapper;
import com.aigicrm.module.file.mapper.BizFolderTypeDefaultMapper;
import com.aigicrm.module.system.entity.SysRole;
import com.aigicrm.module.system.mapper.SysRoleMapper;
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
 * 【2026-11-09 类型级默认权限】校验/建夹门槛/物化三面回归:
 * ①非法类型/权限项/停用角色拒绝; ②canCreateRoot 只认本人角色且默认行含 create;
 * ③materializeAcl 剔除 create 后逐行物化进 biz_folder_acl。
 */
class FolderTypeDefaultServiceTest {

    private final BizFolderTypeDefaultMapper defaultMapper = mock(BizFolderTypeDefaultMapper.class);
    private final BizFolderAclMapper aclMapper = mock(BizFolderAclMapper.class);
    private final SysRoleMapper roleMapper = mock(SysRoleMapper.class);
    private final FolderTypeDefaultService svc =
            new FolderTypeDefaultService(defaultMapper, aclMapper, roleMapper);

    private SysRole role(long id, String code, int status) {
        SysRole r = new SysRole();
        r.setId(id);
        r.setRoleCode(code);
        r.setStatus(status);
        return r;
    }

    @Test
    void saveDefault_rejectsUnknownTypeAndPermAndDisabledRole() {
        assertThrows(BusinessException.class, () -> svc.saveDefault("company", 9L, List.of("view")));
        assertThrows(BusinessException.class, () -> svc.saveDefault("public", 9L, List.of("file:upload"))); // 长词表回潮拒绝
        assertThrows(BusinessException.class, () -> svc.saveDefault("public", 9L, List.of()));
        when(roleMapper.selectById(9L)).thenReturn(role(9L, "05", 0)); // 停用角色
        assertThrows(BusinessException.class, () -> svc.saveDefault("public", 9L, List.of("view")));
    }

    @Test
    void saveDefault_acceptsShortVocabPlusCreate_andUpserts() {
        when(roleMapper.selectById(9L)).thenReturn(role(9L, "05", 1));
        when(defaultMapper.selectOne(any())).thenReturn(null);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            svc.saveDefault("public", 9L, List.of("view", "upload", "edit", "delete", "share", "download", "create"));
        }
        ArgumentCaptor<BizFolderTypeDefault> cap = ArgumentCaptor.forClass(BizFolderTypeDefault.class);
        verify(defaultMapper).insert(cap.capture());
        assertEquals("public", cap.getValue().getFolderType());
        assertTrue(cap.getValue().getPerms().contains("create"));
    }

    @Test
    void canCreateRoot_onlyWhenOwnRoleRowContainsCreate() {
        BizFolderTypeDefault row = new BizFolderTypeDefault();
        row.setFolderType("public");
        row.setRoleId(9L);
        row.setPerms("view,upload,create");
        when(defaultMapper.selectList(any())).thenReturn(List.of(row));
        when(roleMapper.selectById(9L)).thenReturn(role(9L, "05", 1));
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentRoleCodes).thenReturn(List.of("05"));
            assertTrue(svc.canCreateRoot("public"), "本人角色(05)默认行含 create 应放行");

            m.when(SecurityUtils::getCurrentRoleCodes).thenReturn(List.of("06"));
            assertFalse(svc.canCreateRoot("public"), "非默认行角色(06)不放行");
        }
    }

    @Test
    void canCreateRoot_rowWithoutCreateToken_denies() {
        BizFolderTypeDefault row = new BizFolderTypeDefault();
        row.setFolderType("public");
        row.setRoleId(9L);
        row.setPerms("view,upload,edit"); // 无 create
        when(defaultMapper.selectList(any())).thenReturn(List.of(row));
        when(roleMapper.selectById(9L)).thenReturn(role(9L, "05", 1));
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentRoleCodes).thenReturn(List.of("05"));
            assertFalse(svc.canCreateRoot("public"), "默认行不含 create 不放行建根夹(其余权限照常)");
        }
    }

    @Test
    void materializeAcl_stripsCreateAndInsertsAclRows() {
        BizFolderTypeDefault row = new BizFolderTypeDefault();
        row.setFolderType("public");
        row.setRoleId(9L);
        row.setPerms("view,upload,create");
        when(defaultMapper.selectList(any())).thenReturn(List.of(row));
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            svc.materializeAcl(123L, "public");
        }
        ArgumentCaptor<BizFolderAcl> cap = ArgumentCaptor.forClass(BizFolderAcl.class);
        verify(aclMapper).insert(cap.capture());
        assertEquals(123L, cap.getValue().getFolderId());
        assertEquals("view,upload", cap.getValue().getPerms(), "create 不得物化进逐夹 ACL");
    }

    @Test
    void materializeAcl_createOnlyRow_skipsInsert() {
        BizFolderTypeDefault row = new BizFolderTypeDefault();
        row.setFolderType("public");
        row.setRoleId(9L);
        row.setPerms("create"); // 只有 create, 剔除后无有效权限
        when(defaultMapper.selectList(any())).thenReturn(List.of(row));
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            svc.materializeAcl(123L, "public");
        }
        verify(aclMapper, never()).insert(any(BizFolderAcl.class));
    }
}
