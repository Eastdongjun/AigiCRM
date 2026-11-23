package com.aigicrm.module.fabushi.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.module.fabushi.entity.BizFabushiPost;
import com.aigicrm.module.fabushi.entity.BizFabushiPostAttachment;
import com.aigicrm.module.fabushi.mapper.BizFabushiBookMapper;
import com.aigicrm.module.fabushi.mapper.BizFabushiCommentMapper;
import com.aigicrm.module.fabushi.mapper.BizFabushiLikeMapper;
import com.aigicrm.module.fabushi.mapper.BizFabushiPostAttachmentMapper;
import com.aigicrm.module.fabushi.mapper.BizFabushiPostMapper;
import com.aigicrm.module.fabushi.mapper.BizFabushiReadStatusMapper;
import com.aigicrm.module.fabushi.mapper.BizFabushiSectionMapper;
// recitationService 由下方字段注入（D14）
import com.aigicrm.module.file.entity.BizFile;
import com.aigicrm.module.file.mapper.BizFileMapper;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.security.SecurityAuditService;
import com.aigicrm.security.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 【F1 2026-11-19 spec v2】附件分型限额矩阵钉(方案 F1-1):
 * 图≤5 / 视≤5 / 音≤10 段且单段≤60s; 边界=恰好达标放行、超一拒;
 * 音 durationSeconds 未知(旧聊天选文件路径)暂放行——F2 录音上线后必填。
 */
class FabushiAttachmentLimitTest {

    private final FabushiRecitationService recitationService = mock(FabushiRecitationService.class);
    private final BizFabushiPostMapper postMapper = mock(BizFabushiPostMapper.class);
    private final BizFabushiBookMapper bookMapper = mock(BizFabushiBookMapper.class);
    private final BizFabushiCommentMapper commentMapper = mock(BizFabushiCommentMapper.class);
    private final BizFabushiLikeMapper likeMapper = mock(BizFabushiLikeMapper.class);
    private final BizFabushiReadStatusMapper readStatusMapper = mock(BizFabushiReadStatusMapper.class);
    private final BizFabushiPostAttachmentMapper attachmentMapper = mock(BizFabushiPostAttachmentMapper.class);
    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final BizFileMapper fileMapper = mock(BizFileMapper.class);
    private final SecurityAuditService auditService = mock(SecurityAuditService.class);
    private final FabushiPostService svc = new FabushiPostService(
            postMapper, bookMapper, commentMapper, likeMapper, recitationService, readStatusMapper,
            attachmentMapper, userMapper, fileMapper, auditService,
            mock(BizFabushiSectionMapper.class));

    @BeforeEach
    void initLambdaCache() {
        com.baomidou.mybatisplus.core.MybatisConfiguration cfg = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(cfg, ""),
                BizFabushiPost.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(cfg, ""),
                BizFabushiPostAttachment.class);
    }

    private BizFabushiPost ownedPost() {
        BizFabushiPost p = new BizFabushiPost();
        p.setId(7L);
        p.setUserId(5L);
        p.setStatus("published");
        p.setScopeType("songdu");
        return p;
    }

    private BizFile fabushiFile() {
        BizFile f = new BizFile();
        f.setId(2696L);
        f.setFileName("a.png");
        f.setFileScope("fabushi");
        f.setDeleted(0);
        return f;
    }

    /** 现有附件构造: images 张图 + videos 段视 + audios 段音(时长各异, 均合法)。 */
    private void mockExisting(int images, int videos, int audios) {
        List<BizFabushiPostAttachment> rows = new ArrayList<>();
        for (int i = 0; i < images; i++) rows.add(att("image"));
        for (int i = 0; i < videos; i++) rows.add(att("video"));
        for (int i = 0; i < audios; i++) rows.add(att("audio"));
        when(attachmentMapper.selectList(any())).thenReturn(rows);
    }

    private BizFabushiPost ownedSuishiPost() {
        BizFabushiPost p = ownedPost();
        p.setScopeType("suishi");
        return p;
    }

    private BizFabushiPostAttachment att(String type) {
        BizFabushiPostAttachment a = new BizFabushiPostAttachment();
        a.setPostId(7L);
        a.setFileId(100L);
        a.setFileType(type);
        return a;
    }

    private Map<String, Object> body(String type, Integer durationSeconds) {
        Map<String, Object> m = new HashMap<>();
        m.put("fileId", 2696L);
        m.put("fileType", type);
        if (durationSeconds != null) m.put("durationSeconds", durationSeconds);
        return m;
    }

    private void run(Runnable r) {
        when(postMapper.selectById(7L)).thenReturn(ownedPost());
        when(fileMapper.selectById(2696L)).thenReturn(fabushiFile());
        // mock insert 不回填自增 id——补 id 防 addAttachment 返回 Map.of(id=null) NPE
        org.mockito.Mockito.doAnswer(inv -> {
            BizFabushiPostAttachment a = inv.getArgument(0);
            a.setId(999L);
            return 1;
        }).when(attachmentMapper).insert(any(BizFabushiPostAttachment.class));
        try (MockedStatic<SecurityUtils> sec = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::getCurrentUserId).thenReturn(5L);
            r.run();
        }
    }

    private BusinessException reject(String type, Integer dur, int images, int videos, int audios) {
        mockExisting(images, videos, audios);
        return assertThrows(BusinessException.class, () -> run(() -> svc.addAttachment(7L, body(type, dur))));
    }

    private void allow(String type, Integer dur, int images, int videos, int audios) {
        mockExisting(images, videos, audios);
        run(() -> assertDoesNotThrow(() -> svc.addAttachment(7L, body(type, dur))));
    }

    // ===== 图 ≤5（songdu/zaidu） / ≤9（suishi，Q14） =====

    @Test
    void image_5th_allowed_6th_rejected() {
        allow("image", null, 4, 0, 0);
        BusinessException ex = reject("image", null, 5, 0, 0);
        assertEquals(400, ex.getCode());
        assertEquals("图片已达上限5张", ex.getMessage());
    }

    // ===== 【Q14 2026-11-23】随施 9 张 / 其他类型 5 张 =====
    // 注意：allow/reject 内部 run() 会把 post stub 重置为 songdu——本组用例显式 stub，不走 run()

    private void mockExistingImages(int images) {
        List<BizFabushiPostAttachment> rows = new ArrayList<>();
        for (int i = 0; i < images; i++) rows.add(att("image"));
        when(attachmentMapper.selectList(any())).thenReturn(rows);
        when(fileMapper.selectById(2696L)).thenReturn(fabushiFile());
    }

    @Test
    void suishi_image_9th_allowed_10th_rejected() {
        when(postMapper.selectById(7L)).thenReturn(ownedSuishiPost());
        org.mockito.Mockito.doAnswer(inv -> {
            BizFabushiPostAttachment x = inv.getArgument(0);
            x.setId(999L);
            return 1;
        }).when(attachmentMapper).insert(any(BizFabushiPostAttachment.class));
        try (MockedStatic<SecurityUtils> sec = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::getCurrentUserId).thenReturn(5L);
            mockExistingImages(8);
            assertDoesNotThrow(() -> svc.addAttachment(7L, body("image", null)));
            mockExistingImages(9);
            BusinessException ex = addAttachmentRejected(body("image", null));
            assertEquals("图片已达上限9张", ex.getMessage());
        }
    }

    @Test
    void zaidu_image_still_capped_at_5() {
        BizFabushiPost p = ownedPost();
        p.setScopeType("zaidu");
        when(postMapper.selectById(7L)).thenReturn(p);
        try (MockedStatic<SecurityUtils> sec = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::getCurrentUserId).thenReturn(5L);
            mockExistingImages(5);
            BusinessException ex = addAttachmentRejected(body("image", null));
            assertEquals("图片已达上限5张", ex.getMessage());
        }
    }

    // ===== 视 ≤5 =====

    @Test
    void video_5th_allowed_6th_rejected() {
        allow("video", null, 0, 4, 0);
        BusinessException ex = reject("video", null, 0, 5, 0);
        assertEquals("视频已达上限5段", ex.getMessage());
    }

    // ===== 音 ≤10 且单段 ≤60s =====

    @Test
    void audio_10th_allowed_11th_rejected() {
        allow("audio", 30, 0, 0, 9);
        BusinessException ex = reject("audio", 30, 0, 0, 10);
        assertEquals("语音已达上限10段", ex.getMessage());
    }

    @Test
    void audio_60s_allowed_61s_rejected() {
        allow("audio", 60, 0, 0, 0);
        BusinessException ex = reject("audio", 61, 0, 0, 0);
        assertEquals("单段语音不得超过60秒", ex.getMessage());
    }

    @Test
    void audio_unknownDuration_passes_untilF2() {
        // 旧路径(聊天选文件)无时长——暂放行; F2 录音上线后改必填
        allow("audio", null, 0, 0, 9);
        assertTrue(true);
    }

    private BusinessException addAttachmentRejected(Map<String, Object> body) {
        return assertThrows(BusinessException.class, () -> svc.addAttachment(7L, body));
    }
}
