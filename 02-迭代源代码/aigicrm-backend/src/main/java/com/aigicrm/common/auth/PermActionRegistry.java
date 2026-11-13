package com.aigicrm.common.auth;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 【WP3 鉴权内核 2026-11-13】动作/资源注册表(sys_resource ⋈ sys_action 内存态, 规格 §3.1/§3.3)。
 * 登记版"动作轴声明表"的一等公民化: ScopeQuery/AuthorizationService 按码查资源列与档位属性。
 * 失效: 短 TTL(30s) + epoch 版本号(PermEpochService.bump 后 30s 内全网生效, WP5 收紧为即时广播)。
 * 注册表不可用 → 查询失败即 fail-closed(返回 null, 调用方按不可鉴权拒绝), 绝不降级放行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PermActionRegistry {

    /** 动作定义(不可变) */
    @Getter
    @RequiredArgsConstructor
    public static class ActionDef {
        private final String code;           // 权限码字符串(= sys_action.code, 现有码一字不改)
        private final String module;         // 资源 module_code(读开关键 perm.read.<module> 用)
        private final String resourceCode;   // 资源
        private final String ownerColumn;    // 资源 owner_column(OWNER 锚点过滤列)
        private final String deptColumn;     // 资源 dept_column(ORG 锚点过滤列)
        private final boolean scopeRequired; // 是否参与数据范围(0=入口/路由码, no-op)
        private final boolean viewLike;      // 查看类(上级/负责人开关唯一作用面)
    }

    private static final long TTL_MS = 30_000L;

    private final JdbcTemplate jdbc;
    private final Map<String, ActionDef> cache = new ConcurrentHashMap<>();
    private final AtomicLong loadedAt = new AtomicLong(0);

    /** 按码取动作定义; 注册表空/码未注册返回 null(调用方 fail-closed) */
    public ActionDef of(String actionCode) {
        if (actionCode == null) return null;
        refreshIfNeeded();
        return cache.get(actionCode);
    }

    /** 失效(授权/资源定义写点、epoch bump 时调用) */
    public void invalidate() {
        loadedAt.set(0);
    }

    private void refreshIfNeeded() {
        long now = System.currentTimeMillis();
        long last = loadedAt.get();
        if (now - last < TTL_MS && last != 0 && !cache.isEmpty()) return;
        synchronized (this) {
            now = System.currentTimeMillis();
            last = loadedAt.get();
            if (now - last < TTL_MS && last != 0 && !cache.isEmpty()) return;
            try {
                Map<String, ActionDef> fresh = new ConcurrentHashMap<>();
                List<Map<String, Object>> rows = jdbc.queryForList(
                        "SELECT a.code, r.module_code, r.resource_code, r.owner_column, r.dept_column, " +
                        "a.scope_required, a.view_like " +
                        "FROM sys_action a JOIN sys_resource r ON r.id = a.resource_id " +
                        "WHERE a.status = 1 AND r.status = 1");
                for (Map<String, Object> row : rows) {
                    String code = String.valueOf(row.get("code"));
                    fresh.put(code, new ActionDef(
                            code,
                            String.valueOf(row.get("module_code")),
                            String.valueOf(row.get("resource_code")),
                            row.get("owner_column") != null ? String.valueOf(row.get("owner_column")) : null,
                            row.get("dept_column") != null ? String.valueOf(row.get("dept_column")) : null,
                            ((Number) row.get("scope_required")).intValue() == 1,
                            ((Number) row.get("view_like")).intValue() == 1));
                }
                if (fresh.isEmpty()) {
                    // 空注册表=元数据未就绪(灰度前)——保留旧缓存/空表, 记录告警, 不视为合法终态
                    log.warn("sys_action 注册表为空(内核元数据未就绪?), 维持现状");
                    if (cache.isEmpty()) loadedAt.set(now); // 空也计时, 防每请求打库
                    return;
                }
                cache.clear();
                cache.putAll(fresh);
                loadedAt.set(now);
            } catch (Exception e) {
                // 表未建(灰度前环境): 保留空缓存 + 短 TTL, ScopeQuery 侧按码未注册 fail-closed
                log.warn("动作注册表加载失败(内核表未就绪?): {}", e.getMessage());
                if (cache.isEmpty()) loadedAt.set(now);
            }
        }
    }
}
