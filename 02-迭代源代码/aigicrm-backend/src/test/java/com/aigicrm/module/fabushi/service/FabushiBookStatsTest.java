package com.aigicrm.module.fabushi.service;

import com.aigicrm.module.fabushi.FabushiScopeType;
import com.aigicrm.module.fabushi.entity.BizFabushiBook;
import com.aigicrm.module.fabushi.entity.BizFabushiPost;
import com.aigicrm.module.fabushi.entity.BizFabushiRecitation;
import com.aigicrm.module.fabushi.mapper.BizFabushiBookMapper;
import com.aigicrm.module.fabushi.mapper.BizFabushiPostMapper;
import com.aigicrm.module.fabushi.mapper.BizFabushiRecitationMapper;
import com.aigicrm.module.file.mapper.BizFileMapper;
import com.aigicrm.module.file.service.FabushiFolderService;
import com.aigicrm.module.system.mapper.SysUserMapper;
import com.aigicrm.module.system.service.RecycleBinService;
import com.aigicrm.module.system.service.SysDictService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 【P1 2026-11-23】书目列表分类型统计（原型右栏「今日推荐书目」数据行）：
 * 诵读 / 随施 / 再读 / 复诵 四个数必须按 D1 类型真算，且历史字段 heartCount/recitationCount
 * （小程序按此读「心得」「诵读」）必须与新字段保持一致——否则小程序显示会漂。
 */
class FabushiBookStatsTest {

    private final BizFabushiBookMapper bookMapper = mock(BizFabushiBookMapper.class);
    private final BizFabushiPostMapper postMapper = mock(BizFabushiPostMapper.class);
    private final BizFabushiRecitationMapper recitationMapper = mock(BizFabushiRecitationMapper.class);
    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final SysDictService dictService = mock(SysDictService.class);
    private final com.aigicrm.security.SecurityAuditService auditService =
            mock(com.aigicrm.security.SecurityAuditService.class);
    private final FabushiFolderService folderService = mock(FabushiFolderService.class);
    private final RecycleBinService recycleBinService = mock(RecycleBinService.class);

    private final FabushiBookService svc = new FabushiBookService(
            bookMapper, postMapper, recitationMapper, mock(BizFileMapper.class),
            userMapper, dictService, auditService, folderService, recycleBinService);

    @BeforeEach
    void setUp() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "");
        TableInfoHelper.initTableInfo(assistant, BizFabushiBook.class);
        TableInfoHelper.initTableInfo(assistant, BizFabushiPost.class);
        TableInfoHelper.initTableInfo(assistant, BizFabushiRecitation.class);
        when(dictService.listDataByCode(any())).thenReturn(List.of());
        when(userMapper.selectByIds(any())).thenReturn(List.of());
    }

    private static BizFabushiPost post(long id, long bookId, long userId, FabushiScopeType type) {
        BizFabushiPost p = new BizFabushiPost();
        p.setId(id);
        p.setBookId(bookId);
        p.setUserId(userId);
        p.setScopeType(type.getCode());
        p.setStatus("published");
        return p;
    }

    @Test
    @DisplayName("书目按类型计数：诵读/随施/再读各算各的，复诵按内容汇总，且历史字段与语义字段一致")
    @SuppressWarnings("unchecked")
    void pageData_countsPerScopeAndRecitations() {
        BizFabushiBook b1 = new BizFabushiBook();
        b1.setId(1L);
        b1.setTitle("了凡四训");
        b1.setStatus("approved");
        BizFabushiBook b2 = new BizFabushiBook();
        b2.setId(2L);
        b2.setTitle("空书");
        b2.setStatus("approved");

        Page<BizFabushiBook> page = new Page<>(1, 20);
        page.setRecords(List.of(b1, b2));
        page.setTotal(2);
        when(bookMapper.selectPage(any(), any())).thenReturn(page);

        when(postMapper.selectList(any())).thenReturn(List.of(
                post(11L, 1L, 100L, FabushiScopeType.SONGDU),
                post(12L, 1L, 100L, FabushiScopeType.SUISHI),
                post(13L, 1L, 200L, FabushiScopeType.ZAIDU),
                // 同一人第二次「再读」本书：条数 +1，人数不 +1（原型「再读 N 人」按人计）
                post(14L, 1L, 200L, FabushiScopeType.ZAIDU)));

        BizFabushiRecitation r1 = new BizFabushiRecitation();
        r1.setId(1L);
        r1.setPostId(11L);
        BizFabushiRecitation r2 = new BizFabushiRecitation();
        r2.setId(2L);
        r2.setPostId(11L);
        when(recitationMapper.selectList(any())).thenReturn(List.of(r1, r2));

        Page<BizFabushiBook> got = svc.page(null, null, null, 1, 20);

        BizFabushiBook a = got.getRecords().get(0);
        assertEquals(4L, a.getPostCount(), "总条数");
        assertEquals(2L, a.getMemberCount(), "参与人数=去重作者数(不是条数)");
        assertEquals(1L, a.getSongduCount(), "诵读 1 条");
        assertEquals(1L, a.getSuishiCount(), "随施 1 条");
        assertEquals(2L, a.getZaiduCount(), "再读 2 条");
        assertEquals(1L, a.getZaiduMemberCount(), "再读人数=去重作者数");
        assertEquals(2L, a.getRecitationTotal(), "复诵总次数按内容汇总");
        // 历史字段（小程序读「心得」「诵读」）必须与新字段同值，避免双端显示漂移
        assertEquals(a.getSuishiCount(), a.getHeartCount(), "heartCount 历史别名 = 随施条数");
        assertEquals(a.getSongduCount(), a.getRecitationCount(), "recitationCount 历史别名 = 诵读条数");

        BizFabushiBook b = got.getRecords().get(1);
        assertEquals(0L, b.getPostCount(), "无内容书目不得为 null");
        assertEquals(0L, b.getSongduCount());
        assertEquals(0L, b.getSuishiCount());
        assertEquals(0L, b.getZaiduCount());
        assertEquals(0L, b.getRecitationTotal());

        // 无 N+1：内容一次查完、复诵一次查完（书目数量不放大查询次数）
        verify(postMapper, times(1)).selectList(any());
        verify(recitationMapper, times(1)).selectList(any());
    }

    @Test
    @DisplayName("无任何内容时不应查复诵表（空 in 条件会退化成全表/异常）")
    @SuppressWarnings("unchecked")
    void pageData_noPostsSkipsRecitationQuery() {
        BizFabushiBook b1 = new BizFabushiBook();
        b1.setId(1L);
        b1.setTitle("空书");
        Page<BizFabushiBook> page = new Page<>(1, 20);
        page.setRecords(List.of(b1));
        page.setTotal(1);
        when(bookMapper.selectPage(any(), any())).thenReturn(page);
        when(postMapper.selectList(any())).thenReturn(List.of());

        Page<BizFabushiBook> got = svc.page(null, null, null, 1, 20);

        assertEquals(0L, got.getRecords().get(0).getRecitationTotal());
        verify(recitationMapper, times(0)).selectList(any());
    }

    @Test
    @DisplayName("detailData 也带分类型统计（详情页与列表同口径）")
    @SuppressWarnings("unchecked")
    void detailData_carriesSameCounts() {
        BizFabushiBook b1 = new BizFabushiBook();
        b1.setId(7L);
        b1.setTitle("易经");
        b1.setStatus("approved");
        when(bookMapper.selectById(7L)).thenReturn(b1);
        when(postMapper.selectList(any())).thenReturn(List.of(
                post(21L, 7L, 100L, FabushiScopeType.ZAIDU)));
        when(recitationMapper.selectList(any())).thenReturn(List.of());

        Object data = svc.detailData(7L);
        BizFabushiBook book = (BizFabushiBook) ((java.util.Map<?, ?>) data).get("book");
        assertSame(b1, book);
        assertEquals(1L, book.getZaiduCount());
        assertEquals(0L, book.getSongduCount());
    }
}
