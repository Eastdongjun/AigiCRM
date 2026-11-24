package com.aigicrm.module.fabushi.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.module.fabushi.FabushiScopeType;
import com.aigicrm.module.fabushi.entity.BizFabushiBook;
import com.aigicrm.module.fabushi.entity.BizFabushiPost;
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
import com.aigicrm.security.SecurityUser;
import com.aigicrm.security.SecurityUtils;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 【GL-11 2026-11-24】旧小程序兼容（过渡）：后端先于小程序上线时，线上旧版客户端仍会发重构前的类型值。
 * 入口接受旧别名并落库为新值——recitation(诵读)→songdu，heart(心得)/znl(正能量)→suishi；
 * 按旧类型筛选同样归一。别名沿用新类型的全部规则；未知取值仍然 400。
 * 存量数据已由 V2026.11.23.1 迁移，库里不应再有旧值：所以别名只在入口归一，落库前必须已经是新值。
 * 线上小程序全部升级到发新值的版本之后，删掉别名与本测试。
 */
class FabushiLegacyScopeTypeAliasTest {

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
        sec = org.mockito.Mockito.mockStatic(SecurityUtils.class);
        SecurityUser u = new SecurityUser();
        u.setUserId(9L);
        sec.when(SecurityUtils::getCurrentUser).thenReturn(u);
    }

    @AfterEach
    void tearDown() {
        sec.close();
    }

    private BizFabushiPost post(String scopeType, Long bookId, String title, String content) {
        BizFabushiPost p = new BizFabushiPost();
        p.setScopeType(scopeType);
        p.setBookId(bookId);
        p.setTitle(title);
        p.setContent(content);
        return p;
    }

    private BizFabushiBook approvedBook() {
        BizFabushiBook b = new BizFabushiBook();
        b.setId(5L);
        b.setStatus("approved");
        b.setDeleted(0);
        return b;
    }

    /** 发布，并返回真正交给 mapper 落库的那一行的 scope_type（同一用例里多次调用时取最后一次）。 */
    private String storedScopeType(BizFabushiPost input) {
        svc.create(input);
        ArgumentCaptor<BizFabushiPost> captor = ArgumentCaptor.forClass(BizFabushiPost.class);
        // 显式转型: BaseMapper.insert 有多个重载, 泛型捕获的结果直接传进去会被判成歧义
        verify(postMapper, atLeastOnce()).insert((BizFabushiPost) captor.capture());
        return captor.getValue().getScopeType();
    }

    private BusinessException createRejected(BizFabushiPost post) {
        String scope = post.getScopeType();
        BusinessException ex = org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class,
                () -> svc.create(post), "类型 [" + scope + "] 应被拒绝");
        verify(postMapper, never()).insert(any(BizFabushiPost.class));
        return ex;
    }

    // ---------- 发布：旧别名落库为新值 ----------

    @Test
    void recitation_isStoredAsSongdu() {
        when(bookMapper.selectById(5L)).thenReturn(approvedBook());
        assertEquals("songdu", storedScopeType(post("recitation", 5L, null, "晨读打卡")));
    }

    @Test
    void heart_isStoredAsSuishi() {
        assertEquals("suishi", storedScopeType(post("heart", null, null, "旧心得内容")));
    }

    @Test
    void znl_isStoredAsSuishi() {
        assertEquals("suishi", storedScopeType(post("znl", null, null, "旧正能量内容")));
    }

    @Test
    void newValues_areStoredUnchanged() {
        when(bookMapper.selectById(5L)).thenReturn(approvedBook());
        assertEquals("songdu", storedScopeType(post("songdu", 5L, null, "晨读")));
        assertEquals("zaidu", storedScopeType(post("zaidu", 5L, null, "再读一本")));
        assertEquals("suishi", storedScopeType(post("suishi", null, null, "随手一拍")));
    }

    // ---------- 别名沿用新类型的规则 ----------

    @Test
    void recitationAlias_requiresBook_likeSongdu() {
        BusinessException ex = createRejected(post("recitation", null, null, "晨读打卡"));
        assertEquals("该类型必须关联具体书目（先选书再发布）", ex.getMessage());
    }

    @Test
    void heartAndZnlAliases_followSuishiRules() {
        assertEquals("每日随施不设置标题",
                createRejected(post("heart", null, "旧心得标题", "内容")).getMessage());
        assertEquals("每日随施不关联书目",
                createRejected(post("znl", 5L, null, "内容")).getMessage());
    }

    // ---------- 未知取值仍然 400 ----------

    @Test
    void unknownScopeTypes_areStillRejected() {
        for (String bad : new String[]{"xxx", "", " ", "Heart", "RECITATION", "诵读"}) {
            BusinessException ex = createRejected(post(bad, null, null, "内容"));
            assertEquals("内容类型不合法", ex.getMessage(), "[" + bad + "]");
        }
    }

    @Test
    void nullScopeType_isStillRejected() {
        assertEquals("内容类型不合法", createRejected(post(null, null, null, "内容")).getMessage());
    }

    // ---------- 筛选：旧别名归一为新值；其它行为不变 ----------

    @Test
    void pageFilter_legacyAliases_mapToNewValues() {
        assertEquals("songdu", capturedScopeFilter("recitation"));
        assertEquals("suishi", capturedScopeFilter("heart"));
        assertEquals("suishi", capturedScopeFilter("znl"));
    }

    @Test
    void pageFilter_newValues_passThrough() {
        assertEquals("songdu", capturedScopeFilter("songdu"));
        assertEquals("suishi", capturedScopeFilter("suishi"));
        assertEquals("zaidu", capturedScopeFilter("zaidu"));
    }

    @Test
    void pageFilter_absent_meansNoScopeCondition() {
        assertNull(capturedScopeFilter(null));
        assertNull(capturedScopeFilter(""));
        assertNull(capturedScopeFilter(" "));
    }

    @Test
    void pageFilter_unknownValue_isNotDropped() {
        // 改前: 按未知值筛选查不到任何行。改后必须一样——不能因为"不认识"就变成不过滤, 那会把全部内容返回给调用方。
        assertEquals("xxx", capturedScopeFilter("xxx"));
    }

    /** 跑一次分页, 返回查询条件里的 scope_type 取值; 没有这个条件时返回 null（同一用例里多次调用时取最后一次）。 */
    @SuppressWarnings("unchecked")
    private String capturedScopeFilter(String scopeType) {
        when(postMapper.selectPage(any(), any())).thenReturn(new Page<>(1, 10));
        svc.page(new FabushiPostService.PageQuery(scopeType, null, null, false, 1L, null, 1, 10));
        ArgumentCaptor<Wrapper<BizFabushiPost>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(postMapper, atLeastOnce()).selectPage(any(), captor.capture());
        LambdaQueryWrapper<BizFabushiPost> qw = (LambdaQueryWrapper<BizFabushiPost>) captor.getValue();
        if (!qw.getSqlSegment().contains("scope_type")) {
            return null;
        }
        // 条件里唯一的字符串参数就是 scope_type(状态条件用的也是字符串, 但取值固定是 published)
        return qw.getParamNameValuePairs().values().stream()
                .filter(v -> v instanceof String && !"published".equals(v))
                .map(String.class::cast)
                .findFirst()
                .orElse(null);
    }

    // ---------- 归一规则本身（枚举单源） ----------

    @Test
    void normalize_mapsAliasesAndRejectsEverythingElse() {
        assertEquals("songdu", FabushiScopeType.normalize("recitation"));
        assertEquals("suishi", FabushiScopeType.normalize("heart"));
        assertEquals("suishi", FabushiScopeType.normalize("znl"));
        assertEquals("songdu", FabushiScopeType.normalize("songdu"));
        assertNull(FabushiScopeType.normalize("xxx"));
        assertNull(FabushiScopeType.normalize(""));
        assertNull(FabushiScopeType.normalize(null));
    }

    @Test
    void toCurrent_onlyTouchesAliases() {
        assertEquals("songdu", FabushiScopeType.toCurrent("recitation"));
        assertEquals("suishi", FabushiScopeType.toCurrent("znl"));
        assertEquals("zaidu", FabushiScopeType.toCurrent("zaidu"));
        assertEquals("xxx", FabushiScopeType.toCurrent("xxx"));
        assertNull(FabushiScopeType.toCurrent(null));
    }
}
