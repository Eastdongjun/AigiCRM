package com.aigicrm.module.fabushi.service;

import com.aigicrm.module.fabushi.entity.BizFabushiPost;
import com.aigicrm.module.fabushi.mapper.BizFabushiBookMapper;
import com.aigicrm.module.fabushi.mapper.BizFabushiCommentMapper;
import com.aigicrm.module.fabushi.mapper.BizFabushiLikeMapper;
import com.aigicrm.module.fabushi.mapper.BizFabushiPostAttachmentMapper;
import com.aigicrm.module.fabushi.mapper.BizFabushiPostMapper;
import com.aigicrm.module.fabushi.mapper.BizFabushiReadStatusMapper;
import com.aigicrm.module.fabushi.mapper.BizFabushiSectionMapper;
// recitationService 由下方字段注入（D14）
import com.aigicrm.module.file.mapper.BizFileMapper;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.security.SecurityAuditService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 【F1 2026-11-19 spec v2】"一月内上传显示 NEW" 窗口钉(方案 F1-2):
 * isNew=createdAt ∈ (now-30d, now]; 边界用 ±安全余量规避时钟竞态:
 * 29d23h59m 之前(即 minusDays(30).plusMinutes(1))→true; 30d+1s →false; createdAt=null →false。
 */
class FabushiPostNewFlagTest {

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
        // fillInteractions 对 like/read/comment 三实体也建 Lambda wrapper, 需一并预热缓存
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, BizFabushiPost.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant,
                com.aigicrm.module.fabushi.entity.BizFabushiLike.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant,
                com.aigicrm.module.fabushi.entity.BizFabushiReadStatus.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant,
                com.aigicrm.module.fabushi.entity.BizFabushiComment.class);
    }

    private BizFabushiPost post(LocalDateTime createdAt) {
        BizFabushiPost p = new BizFabushiPost();
        p.setId(1L);
        p.setUserId(5L);
        p.setStatus("published");
        p.setCreatedAt(createdAt);
        return p;
    }

    @Test
    void newWindow_freshIn_true_oldOut_false_nullSafe() {
        LocalDateTime now = LocalDateTime.now();
        BizFabushiPost fresh = post(now.minusDays(30).plusMinutes(1));   // 29d23h59m 前
        BizFabushiPost old = post(now.minusDays(30).minusSeconds(1));    // 30d1s 前
        BizFabushiPost noTime = post(null);
        Page<BizFabushiPost> page = new Page<>(1, 10);
        page.setRecords(List.of(fresh, old, noTime));
        when(postMapper.selectPage(any(), any())).thenReturn(page);

        svc.page(null, null, null, false, null, null, 1, 10);

        assertEquals(Boolean.TRUE, fresh.getIsNew(), "30 天内应标 NEW");
        assertNotEquals(Boolean.TRUE, old.getIsNew(), "30 天外不应标 NEW");
        assertNotEquals(Boolean.TRUE, noTime.getIsNew(), "createdAt=null 不标 NEW(且不抛 NPE)");
    }
}
