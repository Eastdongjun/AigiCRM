package com.aigicrm.module.fabushi.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.module.fabushi.entity.BizFabushiPost;
import com.aigicrm.module.fabushi.entity.BizFabushiRecitation;
import com.aigicrm.module.fabushi.mapper.BizFabushiPostMapper;
import com.aigicrm.module.fabushi.mapper.BizFabushiRecitationMapper;
import com.aigicrm.module.file.entity.BizFile;
import com.aigicrm.module.file.mapper.BizFileMapper;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.security.SecurityAuditService;
import com.aigicrm.security.SecurityUser;
import com.aigicrm.security.SecurityUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 【D14 2026-11-23】复诵矩阵钉（Q6 独立实体表）：post 须 published；audio 必挂 fabushi 空间；
 * ≤60s（未知时长沿 F1 口径放行）；同人同帖可多次（计数=行数）；复诵留痕审计。
 */
class FabushiRecitationTest {

    private final BizFabushiRecitationMapper recitationMapper = mock(BizFabushiRecitationMapper.class);
    private final BizFabushiPostMapper postMapper = mock(BizFabushiPostMapper.class);
    private final BizFileMapper fileMapper = mock(BizFileMapper.class);
    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final SecurityAuditService auditService = mock(SecurityAuditService.class);
    private final FabushiRecitationService svc = new FabushiRecitationService(
            recitationMapper, postMapper, fileMapper, userMapper, auditService);

    private MockedStatic<SecurityUtils> sec;

    @BeforeEach
    void setUp() {
        sec = org.mockito.Mockito.mockStatic(SecurityUtils.class);
        SecurityUser u = new SecurityUser();
        u.setUserId(9L);
        sec.when(SecurityUtils::getCurrentUser).thenReturn(u);
    }

    @AfterEach
    void tearDown() {
        sec.close();
    }

    private BizFabushiPost publishedPost() {
        BizFabushiPost p = new BizFabushiPost();
        p.setId(31L);
        p.setUserId(8L);
        p.setStatus("published");
        p.setScopeType("songdu");
        return p;
    }

    private BizFile fabushiFile() {
        BizFile f = new BizFile();
        f.setId(600L);
        f.setFileScope("fabushi");
        return f;
    }

    @Test
    void create_postMissing_rejected() {
        when(postMapper.selectById(31L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> svc.create(31L, 600L, 30));
        assertEquals("内容不存在或已下架", ex.getMessage());
    }

    @Test
    void create_postOffline_rejected() {
        BizFabushiPost p = publishedPost();
        p.setStatus("offline");
        when(postMapper.selectById(31L)).thenReturn(p);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> svc.create(31L, 600L, 30));
        assertEquals("内容不存在或已下架", ex.getMessage());
    }

    @Test
    void create_withoutAudio_rejected() {
        when(postMapper.selectById(31L)).thenReturn(publishedPost());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> svc.create(31L, null, 30));
        assertEquals("复诵缺少音频文件", ex.getMessage());
    }

    @Test
    void create_over60s_rejected() {
        when(postMapper.selectById(31L)).thenReturn(publishedPost());
        when(fileMapper.selectById(600L)).thenReturn(fabushiFile());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> svc.create(31L, 600L, 61));
        assertEquals("复诵不能超过 60 秒", ex.getMessage());
    }

    @Test
    void create_fileNotInFabushiSpace_rejected() {
        when(postMapper.selectById(31L)).thenReturn(publishedPost());
        BizFile f = fabushiFile();
        f.setFileScope("incentive");
        when(fileMapper.selectById(600L)).thenReturn(f);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> svc.create(31L, 600L, 30));
        assertEquals("复诵音频须上传到法布施空间", ex.getMessage());
    }

    @Test
    void create_ok_andRepeatable() {
        when(postMapper.selectById(31L)).thenReturn(publishedPost());
        when(fileMapper.selectById(600L)).thenReturn(fabushiFile());
        assertDoesNotThrow(() -> svc.create(31L, 600L, 58));
        assertDoesNotThrow(() -> svc.create(31L, 600L, 42));
        // 同人同帖可多次（Q6）——两次复诵各留痕一次
        org.mockito.Mockito.verify(auditService, org.mockito.Mockito.times(2))
                .permissionChange("fabushi_recitation", "post:31");
    }

    @Test
    void create_unknownDuration_allowed() {
        // F1 口径：durationSeconds 未知（历史路径）暂放行
        when(postMapper.selectById(31L)).thenReturn(publishedPost());
        when(fileMapper.selectById(600L)).thenReturn(fabushiFile());
        assertDoesNotThrow(() -> svc.create(31L, 600L, null));
    }
}
