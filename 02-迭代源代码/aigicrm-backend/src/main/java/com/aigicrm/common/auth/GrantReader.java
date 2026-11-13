package com.aigicrm.common.auth;

import com.aigicrm.security.SecurityUser;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 【WP3 鉴权内核 2026-11-13】授权装载器(规格 §3.2 activeGrants)。
 * 按主体(用户/岗位/角色 三类并集)读 sys_perm_grant 的 ALLOW+未过期授权, 按动作码归组。
 * 失效: 用户级短 TTL(30s); WP5 epoch 广播收紧为"保存即生效"。
 * 表未就绪(灰度前环境)→ 返回空(fail-closed, 授权域无数据=无新内核范围), 旧链路仍权威。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GrantReader {

    /** 单条授权(scope_type + anchor + 归属动作 + 原始范围配置) */
    @Getter
    @RequiredArgsConstructor
    public static class GrantRow {
        private final String scopeType;
        private final String anchor;
        private final String subjectType;   // USER/POSITION/ROLE
        private final String subjectId;
        /** 【P0-3 修复 2026-11-13】归属动作码(CUSTOM 精确行取数用) */
        private final String actionCode;
        /** 【P0-3】原始范围配置(scope_config JSON 原文, CUSTOM 解析 orgId+includeChildren) */
        private final String scopeConfig;

        /** 兼容构造(测试/非 CUSTOM 场景; actionCode/scopeConfig 为空) */
        public GrantRow(String scopeType, String anchor, String subjectType, String subjectId) {
            this(scopeType, anchor, subjectType, subjectId, null, null);
        }
    }

    private static final long TTL_MS = 30_000L;

    private final JdbcTemplate jdbc;
    private final Map<Long, CacheEntry> cache = new ConcurrentHashMap<>();

    private record CacheEntry(Map<String, List<GrantRow>> byAction, long expireAt) {
        boolean expired() { return System.currentTimeMillis() >= expireAt; }
    }

    /** 按动作码取该用户全部有效授权; 空=无授权(fail-closed 语义由调用方决定) */
    public List<GrantRow> grantsOf(SecurityUser user, String actionCode) {
        Map<String, List<GrantRow>> byAction = byAction(user);
        if (byAction == null) return List.of();
        return byAction.getOrDefault(actionCode, List.of());
    }

    /** 失效(授权写点/epoch bump) */
    public void evict(Long userId) {
        if (userId != null) cache.remove(userId);
    }

    public void evictAll() {
        cache.clear();
    }

    private Map<String, List<GrantRow>> byAction(SecurityUser user) {
        if (user == null || user.getUserId() == null) return null;
        CacheEntry e = cache.get(user.getUserId());
        if (e != null && !e.expired()) return e.byAction();
        synchronized (this) {
            e = cache.get(user.getUserId());
            if (e != null && !e.expired()) return e.byAction();
            Map<String, List<GrantRow>> fresh = load(user);
            cache.put(user.getUserId(), new CacheEntry(fresh, System.currentTimeMillis() + TTL_MS));
            return fresh;
        }
    }

    private Map<String, List<GrantRow>> load(SecurityUser user) {
        Map<String, List<GrantRow>> out = new HashMap<>();
        try {
            List<String> subjects = new ArrayList<>();
            subjects.add("USER:" + user.getUserId());
            if (user.getPositions() != null) {
                for (var p : user.getPositions()) {
                    if (p != null && p.getId() != null) subjects.add("POSITION:" + p.getId());
                }
            }
            if (user.getRoleCodes() != null) {
                for (String rc : user.getRoleCodes()) {
                    if (rc != null && !rc.isBlank()) subjects.add("ROLE:" + rc);
                }
            }
            if (subjects.isEmpty()) return out;

            // 动态 IN 由主体数(≤几十)拼接占位符, 值经参数绑定(Mimosa: 禁拼接 SQL 值)
            String placeholders = String.join(",", subjects.stream().map(s -> "?").toList());
            Timestamp now = Timestamp.valueOf(LocalDateTime.now());
            jdbc.query(
                    "SELECT subject_type, subject_id, action_code, scope_type, anchor, scope_config FROM sys_perm_grant " +
                    "WHERE tenant_id = 1 AND effect = 'ALLOW' " +
                    "AND (expire_at IS NULL OR expire_at > ?) " +
                    "AND CONCAT(subject_type, ':', subject_id) IN (" + placeholders + ")",
                    rs -> {
                        GrantRow row = new GrantRow(
                                rs.getString("scope_type"),
                                rs.getString("anchor"),
                                rs.getString("subject_type"),
                                rs.getString("subject_id"),
                                rs.getString("action_code"),
                                rs.getString("scope_config"));
                        out.computeIfAbsent(rs.getString("action_code"), k -> new ArrayList<>()).add(row);
                    }, prepend(now, subjects.toArray()));
            return out;
        } catch (Exception ex) {
            // 内核授权表未就绪/查询失败 → 空授权(fail-closed; 读开关默认 old 时新内核不被消费)
            log.warn("sys_perm_grant 装载失败(内核未就绪?): {}", ex.getMessage());
            return new HashMap<>();
        }
    }

    private Object[] prepend(Object first, Object... rest) {
        Object[] all = new Object[rest.length + 1];
        all[0] = first;
        System.arraycopy(rest, 0, all, 1, rest.length);
        return all;
    }
}
