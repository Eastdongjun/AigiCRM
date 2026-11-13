package com.aigicrm.common.auth;

import com.aigicrm.security.PermissionCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 【WP3→WP5 2026-11-13】权限版本号服务(规格 §3.1 "一个版本号")。
 * current(): 读 sys_perm_epoch(短 TTL 缓存)。
 * bump(): 版本+1 → 全量失效三件套(grantReader/registry/perm ctx)+旧链路 perm_version 递增兜底。
 * publish(): Redis perm:changed 频道广播(WP5 双端消费: PC visibilitychange + MP onShow 即时刷新;
 *            本 WP 先广播占位, 双端订阅在 WP5 落地)。
 * 所有权限写点(角色/用户角色/范围/岗位/组织/开关/菜单)统一调 bump()(规格 WP5 写点清单)。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermEpochService {

    public static final String CHANNEL_PERM_CHANGED = "perm:changed";

    private static final long CURRENT_TTL_MS = 30_000L;

    private final JdbcTemplate jdbc;
    private final GrantReader grantReader;
    private final PermActionRegistry registry;
    private final PermissionCacheService permissionCacheService;
    private final RedisTemplate<String, Object> redisTemplate;

    private volatile long cachedAt = 0;
    private volatile long cachedEpoch = 1;

    /** 当前版本号(30s 缓存; bump 后新请求读到新值) */
    public long current() {
        long now = System.currentTimeMillis();
        if (now - cachedAt < CURRENT_TTL_MS) return cachedEpoch;
        synchronized (this) {
            now = System.currentTimeMillis();
            if (now - cachedAt < CURRENT_TTL_MS) return cachedEpoch;
            try {
                Long e = jdbc.queryForObject("SELECT epoch FROM sys_perm_epoch WHERE id = 1", Long.class);
                if (e != null) {
                    cachedEpoch = e;
                    cachedAt = now;
                }
            } catch (Exception ex) {
                log.warn("epoch 读取失败(表未就绪?): {} → 维持缓存值", ex.getMessage());
                if (cachedAt == 0) {
                    cachedEpoch = 1;
                    cachedAt = now;
                }
            }
            return cachedEpoch;
        }
    }

    /** 版本+1 + 全量失效(授权写点统一入口); 返回新版本号 */
    public synchronized long bump() {
        try {
            jdbc.update("UPDATE sys_perm_epoch SET epoch = epoch + 1 WHERE id = 1");
        } catch (Exception e) {
            log.warn("epoch 自增失败(表未就绪?): {}", e.getMessage());
        }
        cachedAt = 0; // 立即重读
        markChanged(); // 新内核缓存失效+广播(不含 evictAll, 防递归)
        try {
            permissionCacheService.evictAll();
            jdbc.update("UPDATE sys_user SET perm_version = perm_version + 1");
        } catch (Exception e) {
            log.warn("旧链路失效兜底失败: {}", e.getMessage());
        }
        long epoch = current();
        log.info("权限版本 bump → {} (全量失效完成)", epoch);
        return epoch;
    }

    /**
     * 【WP5 2026-11-13】版本+1 + 新内核缓存失效 + 广播——**不含** evictAll/perm_version
     * (调用方=PermissionCacheService.evictAll, 旧链路失效已由其自身完成; 防递归)。
     * 全部全局权限写点经 evictAll 汇聚至此, 双端下一次请求即感知。
     */
    public synchronized long markChanged() {
        try {
            jdbc.update("UPDATE sys_perm_epoch SET epoch = epoch + 1 WHERE id = 1");
        } catch (Exception e) {
            log.warn("epoch 自增失败(表未就绪?): {}", e.getMessage());
        }
        cachedAt = 0;
        grantReader.evictAll();
        registry.invalidate();
        long epoch = current();
        publish();
        return epoch;
    }

    /** 广播(双端刷新, WP5 消费; 本 WP 先发占位) */
    public void publish() {
        try {
            redisTemplate.convertAndSend(CHANNEL_PERM_CHANGED, String.valueOf(current()));
        } catch (Exception e) {
            // Redis 不可用不阻断失效主流程(evict/perm_version 兜底已在调用方完成)
            log.warn("perm:changed 广播失败(Redis?): {}", e.getMessage());
        }
    }
}
