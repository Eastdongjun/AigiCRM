package com.aigicrm.module.file.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.module.file.entity.BizFileFolder;
import com.aigicrm.module.file.entity.BizFolderTypeDefault;
import com.aigicrm.module.file.mapper.BizFileFolderMapper;
import com.aigicrm.module.file.mapper.BizFolderTypeDefaultMapper;
import com.aigicrm.module.system.entity.SysRole;
import com.aigicrm.module.system.entity.SysUser;
import com.aigicrm.module.system.mapper.SysRoleMapper;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.security.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 【2026-11-10 文件权限终版】例外授权面回归:
 * 校验(对象组词表/主体/过期必填晚于当前/定向夹存在) + edit 伞归一 + upsert + 续期/删除。
 */
class FolderTypeDefaultServiceTest {

    private final BizFolderTypeDefaultMapper grantMapper = mock(BizFolderTypeDefaultMapper.class);
    private final BizFileFolderMapper folderMapper = mock(BizFileFolderMapper.class);
    private final SysRoleMapper roleMapper = mock(SysRoleMapper.class);
    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final FolderTypeDefaultService svc =
            new FolderTypeDefaultService(grantMapper, folderMapper, roleMapper, userMapper,
                    mock(com.aigicrm.security.SecurityAuditService.class));

    private SysRole role(long id, String code, int status) {
        SysRole r = new SysRole(); r.setId(id); r.setRoleCode(code); r.setStatus(status); return r;
    }

    @Test
    void saveGrant_rejectsBadInput() {
        LocalDateTime exp = LocalDateTime.now().plusDays(30);
        assertThrows(BusinessException.class, () -> svc.saveGrant("company", "FILE", null, null, "ROLE", 9L, List.of("view"), exp));
        assertThrows(BusinessException.class, () -> svc.saveGrant("public", "TEAM", null, null, "ROLE", 9L, List.of("view"), exp));
        assertThrows(BusinessException.class, () -> svc.saveGrant("public", "FOLDER", null, null, "ROLE", 9L, List.of("view"), exp)); // FOLDER 词表不含 view
        assertThrows(BusinessException.class, () -> svc.saveGrant("public", "FILE", null, null, "ROLE", 9L, List.of("create"), exp)); // FILE 词表不含 create
        assertThrows(BusinessException.class, () -> svc.saveGrant("public", "FILE", null, null, "ROLE", 9L, List.of(), exp));
        // 【2026-11-10 用户令】到期可选: NULL=永久不再拒绝; 填过去时间仍拒绝
        assertThrows(BusinessException.class, () -> svc.saveGrant("public", "FILE", null, null, "ROLE", 9L, List.of("view"), LocalDateTime.now().minusDays(1)));
        when(roleMapper.selectById(9L)).thenReturn(role(9L, "05", 0));
        assertThrows(BusinessException.class, () -> svc.saveGrant("public", "FILE", null, null, "ROLE", 9L, List.of("view"), exp)); // 停用角色
        when(folderMapper.selectById(500L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> svc.saveGrant("public", "FILE", 500L, null, "ROLE", 9L, List.of("view"), exp)); // 定向夹不存在
    }

    @Test
    void saveGrant_normalizesEditAndPersists() {
        when(userMapper.selectById(42L)).thenReturn(new SysUser());
        when(grantMapper.selectOne(any())).thenReturn(null);
        LocalDateTime exp = LocalDateTime.now().plusYears(1);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            svc.saveGrant("public", "FILE", null, null, "USER", 42L, List.of("view", "edit", "download"), exp);
        }
        ArgumentCaptor<BizFolderTypeDefault> cap = ArgumentCaptor.forClass(BizFolderTypeDefault.class);
        verify(grantMapper).insert(cap.capture());
        assertEquals("FILE", cap.getValue().getObjectType());
        assertEquals("view,rename,replace,download", cap.getValue().getPerms(), "edit 伞归一为 rename+replace");
        assertEquals(exp, cap.getValue().getExpiresAt());
    }

    @Test
    void renewGrant_validatesAndUpdates() {
        assertThrows(BusinessException.class, () -> svc.renewGrant(1L, LocalDateTime.now().minusDays(1)));
        assertThrows(BusinessException.class, () -> svc.renewGrant(1L, null));
        BizFolderTypeDefault g = new BizFolderTypeDefault();
        g.setId(1L); g.setPerms("view"); g.setExpiresAt(LocalDateTime.now().plusDays(1));
        when(grantMapper.selectById(1L)).thenReturn(g);
        LocalDateTime later = LocalDateTime.now().plusYears(1);
        svc.renewGrant(1L, later);
        assertEquals(later, g.getExpiresAt());
        verify(grantMapper).updateById(g);
    }

    @Test
    void removeGrant_deletesAndKeepsSilentOnMissing() {
        svc.removeGrant(999L); // 不存在=幂等静默
        verify(grantMapper, never()).deleteById(any(Long.class));
        BizFolderTypeDefault g = new BizFolderTypeDefault(); g.setId(5L);
        when(grantMapper.selectById(5L)).thenReturn(g);
        svc.removeGrant(5L);
        verify(grantMapper).deleteById(5L);
    }
}
