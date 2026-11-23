package com.aigicrm.module.fabushi.service;

import com.aigicrm.module.fabushi.entity.BizFabushiBook;
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
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 【D20/D23 切片 6】书架三态、置顶/排序、分组绑定与批量移出为根因级回归钉。 */
class ShelfWishPinTest {

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
        init(com.aigicrm.module.fabushi.entity.BizFabushiPost.class);
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

    private BizFabushiBook book(long id) {
        BizFabushiBook b = new BizFabushiBook();
        b.setId(id);
        b.setTitle("大学");
        b.setStatus("approved");
        b.setCategoryCode("guoxue");
        return b;
    }

    private BizFabushiShelfGroup group(long id) {
        BizFabushiShelfGroup g = new BizFabushiShelfGroup();
        g.setId(id);
        g.setUserId(5L);
        g.setName("精读");
        g.setGroupType("custom");
        return g;
    }

    private BizFabushiShelf shelf(long id, long bookId, String status) {
        BizFabushiShelf s = new BizFabushiShelf();
        s.setId(id);
        s.setUserId(5L);
        s.setBookId(bookId);
        s.setStatus(status);
        s.setPinned(0);
        s.setSortNo(1);
        s.setUpdatedAt(LocalDateTime.now());
        return s;
    }

    @Test
    void addShelf_acceptsWishAndCustomGroup() {
        when(bookMapper.selectById(11L)).thenReturn(book(11L));
        when(shelfMapper.selectList(any())).thenReturn(List.of());
        when(groupMapper.selectById(9L)).thenReturn(group(9L));

        svc.addShelf(11L, Map.of("status", "wish", "groupId", 9));

        ArgumentCaptor<BizFabushiShelf> cap = ArgumentCaptor.forClass(BizFabushiShelf.class);
        verify(shelfMapper).insert(cap.capture());
        assertEquals("wish", cap.getValue().getStatus(), "加入书架可直接进想读愿望单");
        assertEquals(9L, cap.getValue().getGroupId());
        assertEquals(0, cap.getValue().getPinned());
        assertEquals(1, cap.getValue().getSortNo(), "新书默认排到手动序列末尾");
        assertNull(cap.getValue().getFinishedAt());
    }

    @Test
    void updateShelf_pinSortAndFinishTimestamp() {
        BizFabushiShelf row = shelf(3L, 11L, "reading");
        when(shelfMapper.selectById(3L)).thenReturn(row);

        svc.updateShelf(3L, Map.of("pinned", true, "sortNo", 7, "status", "finished"));

        ArgumentCaptor<BizFabushiShelf> cap = ArgumentCaptor.forClass(BizFabushiShelf.class);
        verify(shelfMapper).updateById(cap.capture());
        assertEquals(1, cap.getValue().getPinned());
        assertEquals(7, cap.getValue().getSortNo());
        assertEquals("finished", cap.getValue().getStatus());
        assertNotNull(cap.getValue().getFinishedAt(), "完成时刻显式落库，供本月读完统计使用");
    }

    @Test
    void updateShelf_leavingFinishedClearsTimestamp() {
        BizFabushiShelf row = shelf(4L, 12L, "finished");
        row.setFinishedAt(LocalDateTime.now().minusDays(2));
        when(shelfMapper.selectById(4L)).thenReturn(row);

        svc.updateShelf(4L, Map.of("status", "reading"));

        ArgumentCaptor<BizFabushiShelf> cap = ArgumentCaptor.forClass(BizFabushiShelf.class);
        verify(shelfMapper).updateById(cap.capture());
        assertNull(cap.getValue().getFinishedAt(), "从已读完回到在读，必须清完成时刻");
    }

    @Test
    void batchReorder_assignsSequentialSortWithoutTouchingProgress() {
        BizFabushiShelf a = shelf(1L, 11L, "reading");
        BizFabushiShelf b = shelf(2L, 12L, "wish");
        when(shelfMapper.selectList(any())).thenReturn(List.of(b, a));

        svc.batchShelf(List.of(2L, 1L), Map.of("action", "reorder"));

        ArgumentCaptor<BizFabushiShelf> cap = ArgumentCaptor.forClass(BizFabushiShelf.class);
        verify(shelfMapper, times(2)).updateById(cap.capture());
        assertEquals(1, cap.getAllValues().get(0).getSortNo());
        assertEquals(2, cap.getAllValues().get(1).getSortNo());
        verifyNoInteractions(progressMapper);
        verify(shelfMapper, never()).delete(ArgumentMatchers.<LambdaQueryWrapper<BizFabushiShelf>>any());
    }

    @Test
    void deleteGroup_unbindsBooksButKeepsShelfRows() {
        when(groupMapper.selectById(9L)).thenReturn(group(9L));
        BizFabushiShelf row = shelf(3L, 11L, "reading");
        row.setGroupId(9L);
        when(shelfMapper.selectList(any())).thenReturn(List.of(row));

        svc.deleteGroup(9L);

        ArgumentCaptor<BizFabushiShelf> cap = ArgumentCaptor.forClass(BizFabushiShelf.class);
        verify(shelfMapper).updateById(cap.capture());
        assertNull(cap.getValue().getGroupId(), "删组只解绑，书架行与阅读进度不动");
        verify(shelfMapper, never()).deleteById(ArgumentMatchers.<Serializable>any());
        verify(groupMapper).deleteById(9L);
        verifyNoInteractions(progressMapper);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shelfData_sortsPinnedFirstThenSortNo() {
        BizFabushiShelf pinned = shelf(1L, 11L, "reading");
        pinned.setPinned(1);
        pinned.setSortNo(9);
        BizFabushiShelf normal = shelf(2L, 12L, "wish");
        normal.setSortNo(1);
        when(shelfMapper.selectList(any())).thenReturn(List.of(pinned, normal));
        when(bookMapper.selectList(any())).thenReturn(List.of(book(11L), book(12L)));
        when(progressMapper.selectList(any())).thenReturn(List.of());

        Map<String, Object> data = svc.shelfData();
        @SuppressWarnings("unchecked") List<Map<String, Object>> records = (List<Map<String, Object>>) data.get("records");
        assertEquals(1L, records.get(0).get("id"), "置顶行排第一，即使 sortNo 更大");
        assertEquals(11L, records.get(0).get("bookId"));

        ArgumentCaptor<LambdaQueryWrapper<BizFabushiShelf>> cap =
                ArgumentCaptor.forClass((Class) LambdaQueryWrapper.class);
        verify(shelfMapper).selectList(cap.capture());
        assertTrue(cap.getValue().getSqlSegment().contains("pinned"));
        assertTrue(cap.getValue().getSqlSegment().contains("sort_no"));
    }
}
