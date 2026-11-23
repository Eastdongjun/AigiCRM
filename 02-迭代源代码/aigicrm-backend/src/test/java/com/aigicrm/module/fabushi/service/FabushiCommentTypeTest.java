package com.aigicrm.module.fabushi.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.module.fabushi.entity.BizFabushiComment;
import com.aigicrm.module.fabushi.entity.BizFabushiPost;
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
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 【F2 2026-11-19 拍板③】评论类型透传钉:
 * 透传核查结论——原链路 controller→addCommentData 只取 content, addComment 把 type 硬编码 "text",
 * type/audioFileId 从未透传(DDL type 列注释却预留三态)。本批接线后钉住:
 * 默认 text / highlight 放行 / audio 须 audioFileId+fabushi 空间校验, 标题可选 / 非法类型 400。
 */
class FabushiCommentTypeTest {

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
        org.apache.ibatis.builder.MapperBuilderAssistant assistant =
                new org.apache.ibatis.builder.MapperBuilderAssistant(cfg, "");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, BizFabushiPost.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant,
                com.aigicrm.module.fabushi.entity.BizFabushiPostAttachment.class);
        doAnswer(inv -> {
            BizFabushiComment c = inv.getArgument(0);
            c.setId(88L);
            return 1;
        }).when(commentMapper).insert(any(BizFabushiComment.class));
    }

    private void mockPost(Long fileId, String fileScope) {
        BizFabushiPost p = new BizFabushiPost();
        p.setId(7L);
        p.setUserId(5L);
        p.setStatus("published");
        when(postMapper.selectById(7L)).thenReturn(p);
        if (fileId != null) {
            BizFile f = new BizFile();
            f.setId(fileId);
            f.setFileName("a.mp3");
            f.setFileType("mp3");
            f.setFileScope(fileScope);
            f.setDeleted(0);
            when(fileMapper.selectById(fileId)).thenReturn(f);
        }
    }

    /** 返回捕获的落库实体(在 MockedStatic 内执行 service 调用)。 */
    private BizFabushiComment submit(Map<String, Object> body) {
        try (MockedStatic<SecurityUtils> sec = org.mockito.Mockito.mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::getCurrentUser).thenReturn(null);
            svc.addCommentData(7L, body);
        }
        ArgumentCaptor<BizFabushiComment> cap = ArgumentCaptor.forClass(BizFabushiComment.class);
        verify(commentMapper).insert(cap.capture());
        return cap.getValue();
    }

    private Map<String, Object> body(String content, String type, Long audioFileId) {
        Map<String, Object> m = new HashMap<>();
        if (content != null) m.put("content", content);
        if (type != null) m.put("type", type);
        if (audioFileId != null) m.put("audioFileId", audioFileId);
        return m;
    }

    @Test
    void defaultType_isText() {
        mockPost(null, null);
        BizFabushiComment saved = submit(body("不错", null, null));
        assertEquals("text", saved.getType());
    }

    @Test
    void highlight_persistsType() {
        mockPost(null, null);
        BizFabushiComment saved = submit(body("这段是全文纲要", "highlight", null));
        assertEquals("highlight", saved.getType());
    }

    @Test
    void audio_requiresFileId() {
        mockPost(null, null);
        BusinessException ex = submitRejected(body(null, "audio", null));
        assertEquals("语音评论缺少音频文件", ex.getMessage());
    }

    @Test
    void audio_validFile_contentOptional() {
        mockPost(2696L, "fabushi");
        BizFabushiComment saved = submit(body(null, "audio", 2696L));
        assertEquals("audio", saved.getType());
        assertEquals(2696L, saved.getAudioFileId());
        assertNull(saved.getContent(), "语音评论标题可选, 不强制");
    }

    @Test
    void audio_wrongScope_rejected() {
        mockPost(2696L, "other");
        BusinessException ex = submitRejected(body("标题", "audio", 2696L));
        assertEquals("附件须上传到法布施空间", ex.getMessage());
    }

    @Test
    void invalidType_rejected_textStillRequiresContent() {
        mockPost(null, null);
        assertEquals("评论类型不合法",
                submitRejected(body("x", "pic", null)).getMessage());
        assertEquals("评论内容不能为空",
                submitRejected(body(" ", "highlight", null)).getMessage());
    }

    private BusinessException submitRejected(Map<String, Object> body) {
        return assertThrows(BusinessException.class, () -> submit(body));
    }
}
