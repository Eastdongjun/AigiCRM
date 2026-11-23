package com.aigicrm.module.fabushi.service;

import com.aigicrm.common.BusinessException;
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
import com.aigicrm.security.SecurityUser;
import com.aigicrm.security.SecurityUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 【D13 2026-11-23】本人修改/删除矩阵钉（计划切片 2，权限审计口径）：
 * PUT 仅作者本人（admin 不代编辑）；DELETE 作者本人或 admin；offline 帖禁编辑；
 * 编辑后分型规则保持（suishi 不得带标题）；软删不断文件（附件不动）。
 */
class FabushiPostEditDeleteTest {

    private static final long AUTHOR_ID = 8L;
    private static final long OTHER_ID = 9L;

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

    private MockedStatic<SecurityUtils> sec;

    @BeforeEach
    void setUp() {
        sec = Mockito.mockStatic(SecurityUtils.class);
    }

    @AfterEach
    void tearDown() {
        sec.close();
    }

    private void loginAs(long userId, boolean admin) {
        SecurityUser u = new SecurityUser();
        u.setUserId(userId);
        sec.when(SecurityUtils::getCurrentUser).thenReturn(u);
        sec.when(SecurityUtils::isAdmin).thenReturn(admin);
        sec.when(SecurityUtils::getCurrentUserId).thenReturn(userId);
    }

    private BizFabushiPost post(long authorId, String status, String scopeType) {
        BizFabushiPost p = new BizFabushiPost();
        p.setId(77L);
        p.setUserId(authorId);
        p.setStatus(status);
        p.setScopeType(scopeType);
        p.setTitle("原标题");
        p.setContent("原内容");
        return p;
    }

    private Map<String, Object> body(String title, String content) {
        Map<String, Object> m = new HashMap<>();
        if (title != null) {
            m.put("title", title);
        }
        if (content != null) {
            m.put("content", content);
        }
        return m;
    }

    @Test
    void update_byOther_rejected() {
        loginAs(OTHER_ID, false);
        when(postMapper.selectById(77L)).thenReturn(post(AUTHOR_ID, "published", "songdu"));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> svc.updateOwn(77L, body("改标题", "改内容")));
        assertEquals("只能编辑自己发布的内容", ex.getMessage());
    }

    @Test
    void update_byAdmin_rejected() {
        loginAs(OTHER_ID, true);
        when(postMapper.selectById(77L)).thenReturn(post(AUTHOR_ID, "published", "songdu"));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> svc.updateOwn(77L, body("管理员代改", "不该发生")));
        assertEquals("只能编辑自己发布的内容", ex.getMessage());
    }

    @Test
    void update_byAuthor_ok() {
        loginAs(AUTHOR_ID, false);
        when(postMapper.selectById(77L)).thenReturn(post(AUTHOR_ID, "published", "songdu"));
        assertDoesNotThrow(() -> svc.updateOwn(77L, body("新标题", "新内容")));
        verify(postMapper).updateById(any(BizFabushiPost.class));
        verify(auditService).permissionChange("fabushi_post_edit", "post:77");
    }

    @Test
    void update_offlinePost_rejected() {
        loginAs(AUTHOR_ID, false);
        when(postMapper.selectById(77L)).thenReturn(post(AUTHOR_ID, "offline", "songdu"));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> svc.updateOwn(77L, body("改", "改")));
        assertEquals("已下架内容不可编辑", ex.getMessage());
    }

    @Test
    void update_suishi_withTitle_rejected() {
        loginAs(AUTHOR_ID, false);
        when(postMapper.selectById(77L)).thenReturn(post(AUTHOR_ID, "published", "suishi"));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> svc.updateOwn(77L, body("随施不该有标题", null)));
        assertEquals("每日随施不设置标题", ex.getMessage());
    }

    @Test
    void update_emptyContent_rejected() {
        loginAs(AUTHOR_ID, false);
        BizFabushiPost blank = post(AUTHOR_ID, "published", "songdu");
        blank.setTitle(null);
        blank.setContent(null);
        when(postMapper.selectById(77L)).thenReturn(blank);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> svc.updateOwn(77L, body(null, "  ")));
        assertEquals("内容不能为空", ex.getMessage());
    }

    @Test
    void delete_byAuthor_ok() {
        loginAs(AUTHOR_ID, false);
        when(postMapper.selectById(77L)).thenReturn(post(AUTHOR_ID, "published", "suishi"));
        assertDoesNotThrow(() -> svc.deleteOwn(77L));
        verify(postMapper).deleteById(77L);
        verify(auditService).permissionChange("fabushi_post_delete", "post:77");
    }

    @Test
    void delete_byAdmin_ok() {
        loginAs(OTHER_ID, true);
        when(postMapper.selectById(77L)).thenReturn(post(AUTHOR_ID, "offline", "zaidu"));
        assertDoesNotThrow(() -> svc.deleteOwn(77L));
        verify(postMapper).deleteById(77L);
    }

    @Test
    void delete_byOther_rejected() {
        loginAs(OTHER_ID, false);
        when(postMapper.selectById(77L)).thenReturn(post(AUTHOR_ID, "published", "songdu"));
        BusinessException ex = assertThrows(BusinessException.class, () -> svc.deleteOwn(77L));
        assertEquals("只能删除自己发布的内容", ex.getMessage());
        verify(postMapper, never()).deleteById(77L);
    }

    @Test
    void restoreDeleted_rows0_notFound() {
        when(postMapper.restoreDeleted(77L)).thenReturn(0);
        BusinessException ex = assertThrows(BusinessException.class, () -> svc.restoreDeleted(77L));
        assertEquals("已删除内容不存在", ex.getMessage());
    }

    @Test
    void restoreDeleted_ok() {
        when(postMapper.restoreDeleted(77L)).thenReturn(1);
        assertDoesNotThrow(() -> svc.restoreDeleted(77L));
        verify(auditService).permissionChange("fabushi_post_restore_deleted", "post:77");
    }
}
