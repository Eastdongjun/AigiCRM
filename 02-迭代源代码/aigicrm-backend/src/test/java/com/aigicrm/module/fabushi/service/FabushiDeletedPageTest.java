package com.aigicrm.module.fabushi.service;

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
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 【SMOKE-20261123 F1】治理侧「已删除」分页回归钉。
 *
 * <p>缺陷经过：{@code selectDeletedPage} 原签名为
 * {@code (Page, @Param(Constants.WRAPPER) Wrapper)} + SQL {@code ... ${ew.customSqlSegment} ...}，
 * service 调用处传的是 {@code null} ⇒ 片段为空 ⇒ 生成的 SQL 没有 WHERE
 * ⇒ 「已删除」页签列出**全部**内容。2026-11-23 测试环境实测：{@code total=6}，
 * 6 条 {@code deleted} 全为 0，即一条都没删。
 *
 * <p>本测试把「筛选条件写在 SQL 里、不依赖调用方传 Wrapper」钉成契约：
 * 只要有人把 Wrapper 参数或 {@code ${}} 拼接加回来，这里立刻红。
 */
class FabushiDeletedPageTest {

    private final FabushiRecitationService recitationService = mock(FabushiRecitationService.class);
    private final BizFabushiPostMapper postMapper = mock(BizFabushiPostMapper.class);
    private final FabushiPostService svc = new FabushiPostService(
            postMapper,
            mock(BizFabushiBookMapper.class),
            mock(BizFabushiCommentMapper.class),
            mock(BizFabushiLikeMapper.class),
            recitationService,
            mock(BizFabushiReadStatusMapper.class),
            mock(BizFabushiPostAttachmentMapper.class),
            mock(SysUserMapper.class),
            mock(BizFileMapper.class),
            mock(SecurityAuditService.class),
            mock(BizFabushiSectionMapper.class));

    /** SQL 必须自带 deleted = 1，否则逻辑删被绕过。 */
    @Test
    void deletedPageSqlFiltersDeletedRows() throws Exception {
        Method m = BizFabushiPostMapper.class.getMethod("selectDeletedPage", Page.class);
        String sql = String.join(" ", m.getAnnotation(Select.class).value()).replaceAll("\\s+", " ").toLowerCase();
        assertTrue(sql.contains("deleted = 1") || sql.contains("deleted=1"),
                "已删除分页 SQL 必须自带 deleted = 1 过滤，实际: " + sql);
    }

    /** 不得再用 ${} 拼接：条件一旦由调用方决定，传 null 就会退化成全表。 */
    @Test
    void deletedPageSqlHasNoStringInterpolation() throws Exception {
        Method m = BizFabushiPostMapper.class.getMethod("selectDeletedPage", Page.class);
        String sql = String.join(" ", m.getAnnotation(Select.class).value());
        assertFalse(sql.contains("${"), "已删除分页 SQL 不得使用 ${} 拼接，实际: " + sql);
    }

    /** 签名只收 Page：没有 Wrapper 参数，调用方就无从把条件弄丢。 */
    @Test
    void deletedPageTakesOnlyPageArgument() throws Exception {
        Method m = BizFabushiPostMapper.class.getMethod("selectDeletedPage", Page.class);
        assertEquals(1, m.getParameterCount(),
                "selectDeletedPage 只应接收 Page，多出的 Wrapper 参数是本次缺陷的成因");
    }

    /** service 原样透出 mapper 的 records/total（不二次过滤、不重排）。 */
    @Test
    void deletedPageDataPassesThroughMapperResult() {
        BizFabushiPost p = new BizFabushiPost();
        p.setId(42L);
        p.setDeleted(1);
        Page<BizFabushiPost> page = new Page<>(1, 10);
        page.setRecords(List.of(p));
        page.setTotal(1L);
        when(postMapper.selectDeletedPage(any(Page.class))).thenReturn((IPage<BizFabushiPost>) page);

        Map<String, Object> data = svc.deletedPageData(1, 10);

        assertEquals(1L, data.get("total"));
        assertEquals(List.of(p), data.get("records"));
    }
}
