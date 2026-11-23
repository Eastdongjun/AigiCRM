package com.aigicrm.module.fabushi.service;

import com.aigicrm.common.BusinessException;
import com.aigicrm.module.fabushi.entity.BizFabushiBook;
import com.aigicrm.module.fabushi.entity.BizFabushiBookmark;
import com.aigicrm.module.fabushi.mapper.BizFabushiBookMapper;
import com.aigicrm.module.fabushi.mapper.BizFabushiBookmarkMapper;
import com.aigicrm.security.SecurityUser;
import com.aigicrm.security.SecurityUtils;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 【D19/D24 切片 7】阅读器页码书签：同页幂等、本人隔离、只删本人、参数白名单。 */
class FabushiBookmarkTest {

    private final BizFabushiBookmarkMapper bookmarkMapper = mock(BizFabushiBookmarkMapper.class);
    private final BizFabushiBookMapper bookMapper = mock(BizFabushiBookMapper.class);
    private final FabushiBookmarkService svc = new FabushiBookmarkService(bookmarkMapper, bookMapper);

    private MockedStatic<SecurityUtils> sec;

    @BeforeEach
    void setUp() {
        init(BizFabushiBookmark.class);
        init(BizFabushiBook.class);
        SecurityUser me = new SecurityUser();
        me.setUserId(5L);
        me.setUsername("tester");
        sec = mockStatic(SecurityUtils.class);
        sec.when(SecurityUtils::getCurrentUser).thenReturn(me);
        sec.when(SecurityUtils::getCurrentUserId).thenReturn(5L);
        when(bookMapper.selectById(11L)).thenReturn(book());
    }

    @AfterEach
    void tearDown() { sec.close(); }

    private static void init(Class<?> type) {
        MybatisConfiguration cfg = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(cfg, ""), type);
    }

    private static BizFabushiBook book() {
        BizFabushiBook b = new BizFabushiBook();
        b.setId(11L);
        b.setTitle("大学");
        b.setStatus("approved");
        return b;
    }

    private static BizFabushiBookmark bookmark(long id, long userId, int page) {
        BizFabushiBookmark row = new BizFabushiBookmark();
        row.setId(id);
        row.setUserId(userId);
        row.setBookId(11L);
        row.setPage(page);
        row.setCreatedAt(LocalDateTime.of(2026, 11, 23, 10, 0));
        return row;
    }

    @Test
    void add_isIdempotentForSameUserBookPage() {
        when(bookmarkMapper.selectOne(any())).thenReturn(bookmark(91L, 5L, 7));
        Map<String, Object> out = svc.add(11L, Map.of("page", 7, "note", "重要段落"));

        assertEquals(91L, out.get("id"));
        assertEquals(7, out.get("page"));
        verify(bookmarkMapper, never()).insert(any(BizFabushiBookmark.class));
        verify(bookmarkMapper).updateById(any(BizFabushiBookmark.class));
    }

    @Test
    void list_returnsOnlyOwnBookmarksSortedByPage() {
        when(bookmarkMapper.selectList(any())).thenReturn(List.of(bookmark(91L, 5L, 3), bookmark(92L, 5L, 12)));

        List<Map<String, Object>> rows = svc.list(11L);

        assertEquals(2, rows.size());
        assertEquals(3, rows.get(0).get("page"));
        assertEquals(12, rows.get(1).get("page"));
        verify(bookmarkMapper).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    void remove_rejectsOtherUsersBookmark() {
        when(bookmarkMapper.selectById(91L)).thenReturn(bookmark(91L, 9L, 7));

        BusinessException ex = assertThrows(BusinessException.class, () -> svc.remove(91L));

        assertEquals(404, ex.getCode());
        verify(bookmarkMapper, never()).deleteById(ArgumentMatchers.<Serializable>any());
    }

    @Test
    void add_rejectsInvalidPageAndOversizedNote() {
        Map<String, Object> zeroPage = Map.of("page", 0);
        Map<String, Object> textPage = Map.of("page", "abc");
        Map<String, Object> longNote = new java.util.LinkedHashMap<>();
        longNote.put("page", 2);
        longNote.put("note", "x".repeat(201));
        assertThrows(BusinessException.class, () -> svc.add(11L, zeroPage));
        assertThrows(BusinessException.class, () -> svc.add(11L, textPage));
        assertThrows(BusinessException.class, () -> svc.add(11L, longNote));
        verify(bookmarkMapper, never()).insert(any(BizFabushiBookmark.class));
    }
}
