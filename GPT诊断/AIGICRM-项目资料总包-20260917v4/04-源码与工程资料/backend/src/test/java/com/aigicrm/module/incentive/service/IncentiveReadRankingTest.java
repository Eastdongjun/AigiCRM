package com.aigicrm.module.incentive.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 本月激励查询阅读排行榜（2026-09-13 新页面数据源）口径回归。
 *
 * <p>页面入口在系统设置下、默认仅管理员可见（权限码 incentive:read-ranking:view 只绑系统管理员），
 * 但数据口径必须与激励查询页的"已读/未读"一致：本月发布的记录为分母、该人对其中已读的条数为分子。</p>
 */
class IncentiveReadRankingTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);

    private IncentiveService service() {
        return new IncentiveService(null, null, null, null, null, null, null, jdbc);
    }

    private Map<String, Object> row(long userId, String name, String dept, int published, int read, int unread) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("userId", userId);
        row.put("realName", name);
        row.put("deptName", dept);
        row.put("publishedCount", published);
        row.put("readCount", read);
        row.put("unreadCount", unread);
        return row;
    }

    @SuppressWarnings("unchecked")
    private void givenRows(List<Map<String, Object>> rows) {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(rows);
    }

    @Test
    void ranksByReadCountThenFewerUnreadAndComputesRate() {
        List<Map<String, Object>> rows = new ArrayList<>(List.of(
                row(1L, "甲", "内蒙办", 10, 8, 2),
                row(2L, "乙", "太原办", 10, 8, 2),
                row(3L, "丙", "太原办", 10, 3, 7)));
        givenRows(rows);

        Map<String, Object> result = service().readRanking("2026-09");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> ranking = (List<Map<String, Object>>) result.get("ranking");
        assertEquals(3, ranking.size());
        // 已读相同（8=8）→ 未读相同（2=2）→ 按姓名稳定排序（String.compareTo：乙 U+4E59 < 甲 U+7532）
        assertEquals("乙", ranking.get(0).get("realName"));
        assertEquals(1, ranking.get(0).get("rank"));
        assertEquals("甲", ranking.get(1).get("realName"));
        assertEquals(2, ranking.get(1).get("rank"));
        assertEquals("丙", ranking.get(2).get("realName"));
        assertEquals(3, ranking.get(2).get("rank"));
        // 已读率 = 已读 / 本月发布数
        assertEquals(80.0, ranking.get(0).get("readRate"));
        assertEquals(30.0, ranking.get(2).get("readRate"));
        assertEquals("太原办", ranking.get(0).get("deptName"));  // 跟随排序结果（乙在太原办）
    }

    @Test
    void summarizesPublishedReadParticipantsAndAverageRate() {
        givenRows(new ArrayList<>(List.of(
                row(1L, "甲", "内蒙办", 10, 8, 2),
                row(2L, "乙", "太原办", 10, 0, 10),
                row(3L, "丙", "太原办", 10, 2, 8))));

        Map<String, Object> result = service().readRanking("2026-09");

        assertEquals("2026-09", result.get("period"));
        assertEquals(10, result.get("publishedTotal"));
        assertEquals(10, result.get("readTotal"));
        assertEquals(2, result.get("participantCount")); // 只有 2 人有阅读记录
        assertEquals(3, result.get("userCount"));
        // 平均已读率 = 已读总数 / (本月发布数 × 人数) = 10 / 30
        assertEquals(33.3, result.get("avgReadRate"));
    }

    @Test
    void blankPeriodFallsBackToCurrentMonth() {
        givenRows(new ArrayList<>());

        Map<String, Object> result = service().readRanking("   ");

        String currentMonth = LocalDate.now().toString().substring(0, 7);
        assertEquals(currentMonth, result.get("period"));
        verify(jdbc).queryForList(anyString(), eq(new Object[]{currentMonth}));
    }

    @Test
    void zeroPublishedRecordsKeepsRateAtZeroInsteadOfDividingByZero() {
        givenRows(new ArrayList<>(List.of(row(1L, "甲", "内蒙办", 0, 0, 0))));

        Map<String, Object> result = service().readRanking("2026-09");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> ranking = (List<Map<String, Object>>) result.get("ranking");
        assertEquals(0.0, ranking.get(0).get("readRate"));
        assertEquals(0.0, result.get("avgReadRate"));
        assertEquals(0, result.get("participantCount"));
    }

    @Test
    void readStatsKeepsLegacyShapeAndSharesTheSameAggregate() {
        givenRows(new ArrayList<>(List.of(row(1L, "甲", "内蒙办", 10, 8, 2))));

        List<Map<String, Object>> rows = service().readStats(null);

        assertEquals(1, rows.size());
        // 兼容旧字段（激励查询页"已读/未读"文案依赖它们）
        assertTrue(rows.get(0).containsKey("readCount"));
        assertTrue(rows.get(0).containsKey("unreadCount"));
        assertTrue(rows.get(0).containsKey("publishedCount"));
    }
}
