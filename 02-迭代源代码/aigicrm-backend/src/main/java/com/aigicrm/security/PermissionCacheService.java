package com.aigicrm.security;

import com.aigicrm.module.system.mapper.SysRoleMapper;
import com.aigicrm.module.system.mapper.SysUserPositionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 权限上下文 Redis 缓存（2026-08-09 Redis 权限缓存重构）。
 *
 * 取代 UserDetailsServiceImpl 的本地 Caffeine 60s 缓存，核心收益：
 * - 写时精确 evict：角色/用户/岗位/规则/菜单变更立即失效，保持"权限调整即时生效"
 * - 全局共享：多实例部署时任一实例 evict 对所有实例生效（生产当前单实例，为未来多实例做准备）
 * - 跨重启保留：Redis 不随容器重启清空
 *
 * key 设计：`perm:ctx:{userId}`，含 TTL 兜底(10min)防漏网(如异常路径/漏配 evict 点)。
 * 失效策略：
 * - 用户级写点 → evict(userId)
 * - 角色级写点 → evictByRole(roleId) 经 SysRoleMapper 反查该角色下所有用户逐个 evict
 * - 岗位级写点 → evictByPosition(positionId) 经 SysUserPositionMapper 反查岗位下用户逐个 evict
 * - 规则/菜单定义 → 爆炸半径最大，evictAll 全量清空(低频操作，代价可接受)
 *
 * ⚠️ 卫生约束：markLeave(禁用用户) 应 evict——保持"权限调整立即生效"语义。
 * loadUserByUsername 每次请求先查库校验 status==0 再查缓存，禁用用户在缓存命中下也会被拒；
 * evict 是避免缓存内残留禁用前上下文的卫生操作（非安全必要）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionCacheService {

    private static final String KEY_PREFIX = "perm:ctx:";
    private static final Duration TTL = Duration.ofMinutes(10);

    private final RedisTemplate<String, Object> redisTemplate;
    private final SysRoleMapper roleMapper;
    private final SysUserPositionMapper userPositionMapper;
    /** 【WP5 2026-11-13】权限版本号(ObjectProvider 断循环: evictAll→markChanged, bump→evictAll) */
    private final org.springframework.beans.factory.ObjectProvider<com.aigicrm.common.auth.PermEpochService> permEpochServiceProvider;
    /** 【P0-4 修复 2026-11-13】任职/闭包运行期维护(ObjectProvider; 组织/岗位/人员写点经本类汇聚触发) */
    private final org.springframework.beans.factory.ObjectProvider<com.aigicrm.common.auth.KernelOrgSyncService> orgSyncServiceProvider;

    /** 读取缓存上下文；未命中返回 null（调用方回源构建） */
    public PermissionContext get(Long userId) {
        if (userId == null) return null;
        try {
            Object v = redisTemplate.opsForValue().get(KEY_PREFIX + userId);
            return v instanceof PermissionContext ? (PermissionContext) v : null;
        } catch (Exception e) {
            // Redis 不可用/序列化失败时降级为未命中(回源查库)，不影响鉴权
            log.warn("读取权限缓存失败 userId={}", userId, e);
            return null;
        }
    }

    /** 写入缓存（TTL 兜底） */
    public void put(Long userId, PermissionContext ctx) {
        if (userId == null || ctx == null) return;
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + userId, ctx, TTL);
        } catch (Exception e) {
            log.warn("写入权限缓存失败 userId={}", userId, e);
        }
    }

    /** 单用户失效（用户级写点：角色绑定/dataScopeOverride/orgs/禁用/返聘/部门/岗位变更） */
    public void evict(Long userId) {
        if (userId == null) return;
        // 【修复 2026-08-09 P2-3】事务内延后到 afterCommit 再 evict：
        // 避免"evict→commit"窗口内并发请求用旧提交数据重建缓存，导致本次 evict 失效、旧上下文残留到 TTL。
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            Long uid = userId;
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() { doEvict(uid); syncEmployment(uid); }
                    });
        } else {
            doEvict(userId);
            syncEmployment(userId);
        }
    }

    /** 【P0-4 修复 2026-11-13】用户级写点(部门/岗位/角色变更)→ 任职表同步(幂等; 提交后执行; 失败不阻断) */
    private void syncEmployment(Long userId) {
        try {
            com.aigicrm.common.auth.KernelOrgSyncService sync = orgSyncServiceProvider.getIfAvailable();
            if (sync != null) sync.syncUserEmployment(userId);
        } catch (Exception e) {
            log.warn("任职同步触发失败 userId={}: {}", userId, e.getMessage());
        }
    }

    private void doEvict(Long userId) {
        try {
            redisTemplate.delete(KEY_PREFIX + userId);
        } catch (Exception e) {
            log.warn("失效权限缓存失败 userId={}", userId, e);
        }
    }

    /** 角色级失效：反查该角色下所有用户逐个 evict（角色菜单/dataScope/orgs/删角色） */
    public void evictByRole(Long roleId) {
        if (roleId == null) return;
        try {
            for (Long uid : roleMapper.selectUserIdsByRoleId(roleId)) {
                evict(uid);
            }
        } catch (Exception e) {
            log.warn("角色级失效失败 roleId={}", roleId, e);
        }
    }

    /** 岗位级失效：反查该岗位下所有用户逐个 evict（岗位-规则绑定/用户-岗位分配/删岗位） */
    public void evictByPosition(Long positionId) {
        if (positionId == null) return;
        try {
            for (Long uid : userPositionMapper.selectUserIdsByPositionId(positionId)) {
                evict(uid);
            }
        } catch (Exception e) {
            log.warn("岗位级失效失败 positionId={}", positionId, e);
        }
    }

    /** 全量失效（规则/菜单定义/组织结构等爆炸半径最大的写点，低频操作） */
    public void evictAll() {
        // 【修复 2026-08-09 P2-3】与 evict 同理，事务内延后到 afterCommit
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() { doEvictAll(); afterCommitEpoch(); rebuildOrgData(); }
                    });
        } else {
            doEvictAll();
            afterCommitEpoch();
            rebuildOrgData();
        }
    }

    /** 【P0-4 修复 2026-11-13】全局写点(组织/岗位结构变更)→ 闭包+全量任职重建(幂等; 失败不阻断) */
    private void rebuildOrgData() {
        try {
            com.aigicrm.common.auth.KernelOrgSyncService sync = orgSyncServiceProvider.getIfAvailable();
            if (sync != null) {
                sync.rebuildClosure();
                sync.rebuildAllEmployment();
            }
        } catch (Exception e) {
            log.warn("组织数据重建触发失败: {}", e.getMessage());
        }
    }

    /** 【WP5】全量权限写点 → 权限版本号+1(双端感知); 事务提交后执行, 失败不阻断 */
    private void afterCommitEpoch() {
        try {
            com.aigicrm.common.auth.PermEpochService epoch = permEpochServiceProvider.getIfAvailable();
            if (epoch != null) epoch.markChanged();
        } catch (Exception e) {
            log.warn("权限版本 markChanged 失败(表未就绪?): {}", e.getMessage());
        }
    }

    private void doEvictAll() {
        try {
            java.util.Set<String> keys = redisTemplate.keys(KEY_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.warn("全量失效权限缓存失败", e);
        }
    }
}
