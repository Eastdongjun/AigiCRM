package com.aigicrm.module.fabushi.service;

import com.aigicrm.module.fabushi.entity.BizFabushiBook;
import com.aigicrm.module.fabushi.entity.BizFabushiPost;
import com.aigicrm.module.fabushi.entity.BizFabushiSection;
import com.aigicrm.module.fabushi.mapper.BizFabushiBookMapper;
import com.aigicrm.module.fabushi.mapper.BizFabushiCommentMapper;
import com.aigicrm.module.fabushi.mapper.BizFabushiLikeMapper;
import com.aigicrm.module.fabushi.mapper.BizFabushiPostAttachmentMapper;
import com.aigicrm.module.fabushi.mapper.BizFabushiPostMapper;
import com.aigicrm.module.fabushi.mapper.BizFabushiReadStatusMapper;
import com.aigicrm.module.fabushi.mapper.BizFabushiSectionMapper;
import com.aigicrm.module.file.mapper.BizFileMapper;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.security.SecurityAuditService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 【P2 2026-11-23】列表联显字段钉：bookTitle / sectionTitle 必须由 fillInteractions 批量装配。
 *
 * 背景：BizFabushiPost.bookTitle 此前只有 @TableField(exist=false) 声明、从未被赋值，恒为 null
 * （前端靠本地书目表兜底才没露馅）。「按书浏览」表格的「章节」列没有兜底数据源，故一并补齐并在此钉住：
 * - 有 bookId/sectionId → 取到名字
 * - 无 bookId/sectionId → null（不抛错、不查空集）
 */
class FabushiPostDisplayFieldsTest {

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
    private final BizFabushiSectionMapper sectionMapper = mock(BizFabushiSectionMapper.class);
    private final FabushiPostService svc = new FabushiPostService(
            postMapper, bookMapper, commentMapper, likeMapper, recitationService, readStatusMapper,
            attachmentMapper, userMapper, fileMapper, auditService, sectionMapper);

    @BeforeEach
    void initLambdaCache() {
        com.baomidou.mybatisplus.core.MybatisConfiguration cfg = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        org.apache.ibatis.builder.MapperBuilderAssistant assistant =
                new org.apache.ibatis.builder.MapperBuilderAssistant(cfg, "");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, BizFabushiPost.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant,
                com.aigicrm.module.fabushi.entity.BizFabushiLike.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant,
                com.aigicrm.module.fabushi.entity.BizFabushiReadStatus.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant,
                com.aigicrm.module.fabushi.entity.BizFabushiComment.class);
    }

    private BizFabushiPost post(long id, Long bookId, Long sectionId) {
        BizFabushiPost p = new BizFabushiPost();
        p.setId(id);
        p.setUserId(5L);
        p.setStatus("published");
        p.setBookId(bookId);
        p.setSectionId(sectionId);
        return p;
    }

    @Test
    void fillInteractions_populatesBookAndSectionTitles() {
        BizFabushiPost withRef = post(1L, 7L, 3L);
        BizFabushiPost withoutRef = post(2L, null, null);
        Page<BizFabushiPost> page = new Page<>(1, 10);
        page.setRecords(List.of(withRef, withoutRef));
        when(postMapper.selectPage(any(), any())).thenReturn(page);

        BizFabushiBook book = new BizFabushiBook();
        book.setId(7L);
        book.setTitle("道德经");
        when(bookMapper.selectByIds(any())).thenReturn(List.of(book));

        BizFabushiSection section = new BizFabushiSection();
        section.setId(3L);
        section.setTitle("第八章 · 上善若水");
        when(sectionMapper.selectByIds(any())).thenReturn(List.of(section));

        svc.page(new FabushiPostService.PageQuery(null, 7L, null, false, null, null, 1, 10));

        assertEquals("道德经", withRef.getBookTitle(), "有 bookId 必须联显书名");
        assertEquals("第八章 · 上善若水", withRef.getSectionTitle(), "有 sectionId 必须联显章节名");
        assertNull(withoutRef.getBookTitle(), "无 bookId 不联显（随施类内容不关联书目）");
        assertNull(withoutRef.getSectionTitle(), "无 sectionId 不联显");
    }
}
