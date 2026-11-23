package com.aigicrm.module.fabushi.service;

import com.aigicrm.module.fabushi.entity.BizFabushiBook;
import com.aigicrm.module.fabushi.entity.BizFabushiPost;
import com.aigicrm.module.fabushi.entity.BizFabushiReadStatus;
import com.aigicrm.module.fabushi.entity.BizFabushiReadingProgress;
import com.aigicrm.module.fabushi.entity.BizFabushiShelf;
import com.aigicrm.module.fabushi.entity.BizFabushiShelfGroup;
import com.aigicrm.module.fabushi.mapper.BizFabushiBookMapper;
import com.aigicrm.module.fabushi.mapper.BizFabushiPostMapper;
import com.aigicrm.module.fabushi.mapper.BizFabushiReadStatusMapper;
import com.aigicrm.module.fabushi.mapper.BizFabushiReadingProgressMapper;
import com.aigicrm.module.fabushi.mapper.BizFabushiShelfGroupMapper;
import com.aigicrm.module.fabushi.mapper.BizFabushiShelfMapper;
import com.aigicrm.module.fabushi.mapper.BizFabushiUserSettingMapper;
import com.aigicrm.security.SecurityUser;
import com.aigicrm.security.SecurityUtils;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 【D7 切片 6】我的觉醒聚合日界/月界钉：Asia/Shanghai 今日、本月读完、累计阅读天数、当前精读书。 */
class AwakenStatusTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final BizFabushiShelfMapper shelfMapper = mock(BizFabushiShelfMapper.class);
    private final BizFabushiShelfGroupMapper groupMapper = mock(BizFabushiShelfGroupMapper.class);
    private final BizFabushiReadingProgressMapper progressMapper = mock(BizFabushiReadingProgressMapper.class);
    private final BizFabushiReadStatusMapper readStatusMapper = mock(BizFabushiReadStatusMapper.class);
    private final BizFabushiBookMapper bookMapper = mock(BizFabushiBookMapper.class);
    private final BizFabushiUserSettingMapper settingMapper = mock(BizFabushiUserSettingMapper.class);
    private final BizFabushiPostMapper postMapper = mock(BizFabushiPostMapper.class);
    private final FabushiReadingService svc = new FabushiReadingService(
            shelfMapper, groupMapper, progressMapper, readStatusMapper, bookMapper, settingMapper, postMapper);

    private MockedStatic<SecurityUtils> sec;

    @BeforeEach
    void setUp() {
        init(BizFabushiShelf.class);
        init(BizFabushiShelfGroup.class);
        init(BizFabushiBook.class);
        init(BizFabushiReadingProgress.class);
        init(BizFabushiReadStatus.class);
        init(BizFabushiPost.class);
        SecurityUser me = new SecurityUser();
        me.setUserId(5L);
        me.setUsername("tester");
        sec = mockStatic(SecurityUtils.class);
        sec.when(SecurityUtils::getCurrentUser).thenReturn(me);
        sec.when(SecurityUtils::getCurrentUserId).thenReturn(5L);
        lenient().when(groupMapper.selectList(any())).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() { sec.close(); }

    private static void init(Class<?> type) {
        MybatisConfiguration cfg = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(cfg, ""), type);
    }

    private BizFabushiPost post(long id, String scopeType) {
        BizFabushiPost p = new BizFabushiPost();
        p.setId(id);
        p.setUserId(5L);
        p.setScopeType(scopeType);
        p.setStatus("published");
        p.setCreatedAt(LocalDateTime.now(ZONE));
        return p;
    }

    private BizFabushiBook book(long id) {
        BizFabushiBook b = new BizFabushiBook();
        b.setId(id);
        b.setTitle("书" + id);
        b.setCategoryCode("guoxue");
        return b;
    }

    private BizFabushiShelf shelf(long id, long bookId, String status, LocalDateTime finishedAt) {
        BizFabushiShelf s = new BizFabushiShelf();
        s.setId(id);
        s.setUserId(5L);
        s.setBookId(bookId);
        s.setStatus(status);
        s.setPinned(0);
        s.setSortNo(1);
        s.setFinishedAt(finishedAt);
        s.setUpdatedAt(LocalDateTime.now(ZONE).minusHours(id));
        return s;
    }

    private BizFabushiReadStatus read(long postId, LocalDateTime at) {
        BizFabushiReadStatus r = new BizFabushiReadStatus();
        r.setPostId(postId);
        r.setUserId(5L);
        r.setReadAt(at);
        return r;
    }

    @Test
    @SuppressWarnings("unchecked")
    void awakenStatus_countsTodayMonthAndCumulative() {
        when(postMapper.selectList(any())).thenReturn(List.of(
                post(10L, "songdu"), post(11L, "songdu"), post(12L, "suishi"), post(13L, "zaidu")));
        LocalDateTime thisMonth = YearMonth.now(ZONE).atDay(1).atStartOfDay().plusDays(1);
        LocalDateTime lastMonth = YearMonth.now(ZONE).minusMonths(1).atDay(1).atStartOfDay();
        when(shelfMapper.selectList(any())).thenReturn(List.of(
                shelf(1L, 101L, "reading", null),
                shelf(2L, 102L, "finished", thisMonth),
                shelf(3L, 103L, "finished", lastMonth),
                shelf(4L, 104L, "wish", null)));
        when(bookMapper.selectList(any())).thenReturn(List.of(
                book(101L), book(102L), book(103L), book(104L)));
        BizFabushiReadingProgress p = new BizFabushiReadingProgress();
        p.setUserId(5L);
        p.setBookId(101L);
        p.setLastPage(30);
        p.setTotalPages(100);
        when(progressMapper.selectList(any())).thenReturn(List.of(p));
        LocalDate today = LocalDate.now(ZONE);
        when(readStatusMapper.selectList(any())).thenReturn(List.of(
                read(10L, today.atTime(9, 0)),
                read(11L, today.atTime(21, 0)),
                read(12L, today.minusDays(1).atTime(9, 0))));

        Map<String, Object> data = svc.awakenStatus();

        assertEquals(today.toString(), data.get("date"));
        assertEquals(2L, data.get("todayRecitations"), "本人当日诵读帖计数（复诵不计入本人完成度）");
        assertEquals(1L, data.get("todayGifts"), "本人当日随施帖计数");
        assertEquals(4L, data.get("todayPosts"), "当日全部已发布内容=红点判据");
        assertEquals(1L, data.get("rereadingBooks"), "再读一本=在读状态书架数");
        assertEquals(1L, data.get("finishedThisMonth"), "仅本月完成时刻计入本月读完");
        assertEquals(2L, data.get("totalReadDays"), "同日多次阅读只算 1 天");
        Map<String, Object> current = (Map<String, Object>) data.get("currentBook");
        assertNotNull(current);
        assertEquals(101L, current.get("bookId"));
        assertEquals(30, current.get("lastPage"));
        assertEquals(30, current.get("progressPct"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void awakenStatus_queryWindow_isShanghaiToday() {
        when(postMapper.selectList(any())).thenReturn(List.of());
        when(shelfMapper.selectList(any())).thenReturn(List.of());
        when(readStatusMapper.selectList(any())).thenReturn(List.of());

        svc.awakenStatus();

        ArgumentCaptor<LambdaQueryWrapper<BizFabushiPost>> cap =
                ArgumentCaptor.forClass((Class) LambdaQueryWrapper.class);
        verify(postMapper).selectList(cap.capture());
        String segment = cap.getValue().getSqlSegment();
        Map<String, Object> params = cap.getValue().getParamNameValuePairs();
        assertTrue(segment.contains("created_at"), "按发布日期窗口过滤: " + segment);
        assertTrue(segment.contains("status"), "仅已发布内容计入: " + segment);
        assertTrue(params.containsValue(LocalDate.now(ZONE).atStartOfDay()), "窗口起点=上海今日 0 点");
        assertTrue(params.containsValue(LocalDate.now(ZONE).plusDays(1).atStartOfDay()), "窗口终点=明日 0 点");
        assertTrue(params.containsValue(5L), "仅当前用户");
    }
}
