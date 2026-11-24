package com.aigicrm.module.fabushi.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.module.fabushi.entity.BizFabushiBook;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 【D1 2026-11-23】三类型分型校验矩阵钉（REQUIREMENT-DELTA-20261122 Q4/Q9/Q11/Q14 拍板）：
 * songdu/zaidu 必选书且书目 approved（Q4：A 通道也必选书，如同四项激励先选客户）；
 * suishi 免标题免书目（负例 400，朋友圈式一键发）。
 * 旧类型值 heart/recitation/znl 已由 V2026.11.23.1 迁移；【GL-11 2026-11-24】起入口把它们当作旧客户端的
 * 别名接受并落库为新值（兼容线上小程序旧版本），白名单之外的取值仍然 400。完整的别名矩阵见
 * FabushiLegacyScopeTypeAliasTest。
 */
class FabushiPostTypeValidationTest {

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
    void login() {
        sec = org.mockito.Mockito.mockStatic(SecurityUtils.class);
        SecurityUser u = new SecurityUser();
        u.setUserId(9L);
        sec.when(SecurityUtils::getCurrentUser).thenReturn(u);
    }

    @AfterEach
    void logout() {
        sec.close();
    }

    private BizFabushiBook book(String status, Integer deleted) {
        BizFabushiBook b = new BizFabushiBook();
        b.setId(5L);
        b.setStatus(status);
        b.setDeleted(deleted);
        return b;
    }

    private BizFabushiPost post(String scopeType, Long bookId, String title, String content) {
        BizFabushiPost p = new BizFabushiPost();
        p.setScopeType(scopeType);
        p.setBookId(bookId);
        p.setTitle(title);
        p.setContent(content);
        return p;
    }

    @Test
    void songdu_withoutBook_rejected() {
        BusinessException ex = createRejected(post("songdu", null, null, "晨读打卡内容"));
        assertEquals("该类型必须关联具体书目（先选书再发布）", ex.getMessage());
    }

    @Test
    void songdu_withUnapprovedBook_rejected() {
        when(bookMapper.selectById(5L)).thenReturn(book("pending", 0));
        BusinessException ex = createRejected(post("songdu", 5L, null, "内容"));
        assertEquals("关联书目不存在或未通过审核", ex.getMessage());
    }

    @Test
    void songdu_withDeletedBook_rejected() {
        when(bookMapper.selectById(5L)).thenReturn(book("approved", 1));
        BusinessException ex = createRejected(post("songdu", 5L, null, "内容"));
        assertEquals("关联书目不存在或未通过审核", ex.getMessage());
    }

    @Test
    void songdu_withMissingBook_rejected() {
        when(bookMapper.selectById(5L)).thenReturn(null);
        BusinessException ex = createRejected(post("songdu", 5L, null, "内容"));
        assertEquals("关联书目不存在或未通过审核", ex.getMessage());
    }

    @Test
    void zaidu_withoutBook_rejected() {
        BusinessException ex = createRejected(post("zaidu", null, null, "精读第八章"));
        assertEquals("该类型必须关联具体书目（先选书再发布）", ex.getMessage());
    }

    @Test
    void suishi_withTitle_rejected() {
        BusinessException ex = createRejected(post("suishi", null, "晚霞", "随手一拍"));
        assertEquals("每日随施不设置标题", ex.getMessage());
    }

    @Test
    void suishi_withBook_rejected() {
        BusinessException ex = createRejected(post("suishi", 5L, null, "随手一拍"));
        assertEquals("每日随施不关联书目", ex.getMessage());
    }

    @Test
    void unknownScopeType_rejected() {
        BusinessException ex = createRejected(post("xxx", null, "内容", "内容"));
        assertEquals("内容类型不合法", ex.getMessage());
    }

    /** 【GL-11 2026-11-24】旧值不再 400：heart 是旧客户端发的心得，落库为 suishi，并沿用 suishi 的规则。 */
    @Test
    void legacyScopeType_heart_storedAsSuishi() {
        BizFabushiPost created = svc.create(post("heart", null, null, "旧内容"));
        assertEquals("suishi", created.getScopeType());
        assertEquals("published", created.getStatus());
    }

    @Test
    void songdu_withApprovedBook_published() {
        when(bookMapper.selectById(5L)).thenReturn(book("approved", 0));
        BizFabushiPost created = svc.create(post("songdu", 5L, null, "晨读内容"));
        assertEquals("published", created.getStatus());
        assertEquals(9L, created.getUserId());
    }

    @Test
    void suishi_textOnly_published() {
        BizFabushiPost created = svc.create(post("suishi", null, null, "随手一拍晚霞"));
        assertEquals("published", created.getStatus());
    }

    private BusinessException createRejected(BizFabushiPost post) {
        return assertThrows(BusinessException.class, () -> svc.create(post));
    }
}
