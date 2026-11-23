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
    /** 【A2 §2.5】双写协作 mock：旧表入口保存/删除/续期必须同步触发新表 upsert/清理 */
    private final LegacyFileGrantSyncService legacySync = mock(LegacyFileGrantSyncService.class);
    private final FolderTypeDefaultService svc =
            new FolderTypeDefaultService(grantMapper, folderMapper, roleMapper, userMapper,
                    mock(com.aigicrm.security.SecurityAuditService.class), legacySync);

    private SysRole role(long id, String code, int status) {
        SysRole r = new SysRole(); r.setId(id); r.setRoleCode(code); r.setStatus(status); return r;
    }

    @Test
    void saveGrant_rejectsBadInput() {
        LocalDateTime exp = LocalDateTime.now().plusDays(30);
        saveGrantRejected("company", "FILE", null, null, null, "ROLE", 9L, List.of("view"), exp);
        saveGrantRejected("public", "TEAM", null, null, null, "ROLE", 9L, List.of("view"), exp);
        saveGrantRejected("public", "FOLDER", null, null, null, "ROLE", 9L, List.of("view"), exp); // FOLDER 词表不含 view
        saveGrantRejected("public", "FILE", null, null, null, "ROLE", 9L, List.of("create"), exp); // FILE 词表不含 create
        saveGrantRejected("public", "FILE", null, null, null, "ROLE", 9L, List.of(), exp);
        // 【2026-11-10 用户令】到期可选: NULL=永久不再拒绝; 填过去时间仍拒绝
        saveGrantRejected("public", "FILE", null, null, null, "ROLE", 9L, List.of("view"), LocalDateTime.now().minusDays(1));
        when(roleMapper.selectById(9L)).thenReturn(role(9L, "05", 0));
        saveGrantRejected("public", "FILE", null, null, null, "ROLE", 9L, List.of("view"), exp); // 停用角色
        when(folderMapper.selectById(500L)).thenReturn(null);
        saveGrantRejected("public", "FILE", 500L, null, null, "ROLE", 9L, List.of("view"), exp); // 定向夹不存在
    }

    @Test
    void saveGrant_normalizesEditAndPersists() {
        when(userMapper.selectById(42L)).thenReturn(new SysUser());
        when(grantMapper.selectOne(any())).thenReturn(null);
        LocalDateTime exp = LocalDateTime.now().plusYears(1);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            svc.saveGrant("public", "FILE", null, null, null, "USER", 42L, List.of("view", "edit", "download"), exp);
        }
        ArgumentCaptor<BizFolderTypeDefault> cap = ArgumentCaptor.forClass(BizFolderTypeDefault.class);
        verify(grantMapper).insert(cap.capture());
        assertEquals("FILE", cap.getValue().getObjectType());
        assertEquals("view,rename,replace,download", cap.getValue().getPerms(), "edit 伞归一为 rename+replace");
        assertEquals(exp, cap.getValue().getExpiresAt());
        // 【A2 §2.5】旧表保存必须双写新表
        verify(legacySync).syncUpsert(any(BizFolderTypeDefault.class));
    }

    @Test
    void renewGrant_validatesAndUpdates() {
        LocalDateTime past = LocalDateTime.now().minusDays(1);
        assertThrows(BusinessException.class, () -> svc.renewGrant(1L, past));
        assertThrows(BusinessException.class, () -> svc.renewGrant(1L, null));
        BizFolderTypeDefault g = new BizFolderTypeDefault();
        g.setId(1L); g.setPerms("view"); g.setExpiresAt(LocalDateTime.now().plusDays(1));
        when(grantMapper.selectById(1L)).thenReturn(g);
        LocalDateTime later = LocalDateTime.now().plusYears(1);
        svc.renewGrant(1L, later);
        assertEquals(later, g.getExpiresAt());
        verify(grantMapper).updateById(g);
        // 【A2 §2.5】续期必须刷新新表 expire_at（upsert 同 legacy_ref 行）
        verify(legacySync).syncUpsert(g);
    }

    @Test
    void removeGrant_deletesAndKeepsSilentOnMissing() {
        svc.removeGrant(999L); // 不存在=幂等静默
        verify(grantMapper, never()).deleteById(any(Long.class));
        verify(legacySync, never()).syncDelete(any());
        BizFolderTypeDefault g = new BizFolderTypeDefault(); g.setId(5L);
        when(grantMapper.selectById(5L)).thenReturn(g);
        svc.removeGrant(5L);
        verify(grantMapper).deleteById(5L);
        // 【A2 §2.5】删除必须清空新表 ftd:<id>: 双写行
        verify(legacySync).syncDelete(5L);
    }

    private BusinessException saveGrantRejected(String folderType, String objectType, Long folderId,
                                                Long scopeOrgId, String scopeLevel, String subjectType,
                                                Long subjectId, List<String> perms, LocalDateTime expiresAt) {
        return assertThrows(BusinessException.class,
                () -> svc.saveGrant(folderType, objectType, folderId, scopeOrgId, scopeLevel,
                        subjectType, subjectId, perms, expiresAt));
    }
}
