package com.aigicrm.security;

import com.aigicrm.module.system.mapper.SysRoleMapper;
import com.aigicrm.module.system.mapper.SysUserPositionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 【2026-08-09 Redis 权限缓存】PermissionCacheService 失效粒度测试。
 * 验证: 单用户/角色级反查/岗位级反查/全量失效的 key 与删除范围正确。
 */
class PermissionCacheServiceTest {

    private RedisTemplate<String, Object> redisTemplate;
    private ValueOperations<String, Object> valueOps;
    private SysRoleMapper roleMapper;
    private SysUserPositionMapper userPositionMapper;
    private PermissionCacheService svc;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(RedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        roleMapper = mock(SysRoleMapper.class);
        userPositionMapper = mock(SysUserPositionMapper.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        epochService = mock(com.aigicrm.common.auth.PermEpochService.class);
        org.springframework.beans.factory.ObjectProvider<com.aigicrm.common.auth.PermEpochService> epochProv =
                mock(org.springframework.beans.factory.ObjectProvider.class);
        when(epochProv.getIfAvailable()).thenReturn(epochService);
        svc = new PermissionCacheService(redisTemplate, roleMapper, userPositionMapper,
                epochProv,
                org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class));
    }

    /** 【三轮检查 P0-1 补测 2026-11-13】角色更新 → epoch 必须恰好跳变一次(批量收敛+事务后执行) */
    @Test
    @SuppressWarnings("unchecked")
    void evictByRole_marksEpochExactlyOnce() {
        when(roleMapper.selectUserIdsByRoleId(5L)).thenReturn(java.util.List.of(1L, 2L, 3L));
        svc.evictByRole(5L);
        verify(epochService, times(1)).markChanged();
        verify(redisTemplate, times(3)).delete(org.mockito.ArgumentMatchers.contains("perm:ctx:"));
    }

    /** 【三轮检查 P1 补测】无事务上下文: evict(单用户) → epoch 也跳变一次(P0-1 三轮修复回归钉) */
    @Test
    @SuppressWarnings("unchecked")
    void evict_marksEpochOnce() {
        svc.evict(7L);
        verify(epochService, times(1)).markChanged();
        verify(redisTemplate).delete("perm:ctx:7");
    }

    private com.aigicrm.common.auth.PermEpochService epochService;

    @Test
    void put_usesPrefixedKeyWithTtl() {
        PermissionContext ctx = new PermissionContext();
        ctx.setUserId(7L);
        ctx.setUsername("ty101");

        svc.put(7L, ctx);

        verify(valueOps).set(eq("perm:ctx:7"), eq(ctx), any(Duration.class));
    }

    /** 【修复 2026-08-09 P2-3】事务内 evict 延后到 afterCommit——避免 evict→commit 窗口重建旧缓存 */
    @Test
    void evict_inTransaction_defersToAfterCommit() {
        // 开启事务同步
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            svc.evict(7L);
            // 事务未提交前不应立即 delete
            verify(redisTemplate, never()).delete("perm:ctx:7");
            // afterCommit 触发后应 delete
            org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()
                    .forEach(s -> s.afterCommit());
            verify(redisTemplate).delete("perm:ctx:7");
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void evict_deletesPrefixedKey() {
        svc.evict(7L);
        verify(redisTemplate).delete("perm:ctx:7");
    }

    @Test
    void evictByRole_reverseQueriesAndEvictsEachUser() {
        when(roleMapper.selectUserIdsByRoleId(10L)).thenReturn(List.of(7L, 8L, 9L));

        svc.evictByRole(10L);

        verify(redisTemplate).delete("perm:ctx:7");
        verify(redisTemplate).delete("perm:ctx:8");
        verify(redisTemplate).delete("perm:ctx:9");
    }

    @Test
    void evictByPosition_reverseQueriesAndEvictsEachUser() {
        when(userPositionMapper.selectUserIdsByPositionId(3L)).thenReturn(List.of(1L, 2L));

        svc.evictByPosition(3L);

        verify(redisTemplate).delete("perm:ctx:1");
        verify(redisTemplate).delete("perm:ctx:2");
    }

    @Test
    void evictAll_deletesAllPrefixedKeys() {
        when(redisTemplate.keys("perm:ctx:*")).thenReturn(java.util.Set.of("perm:ctx:1", "perm:ctx:2"));

        svc.evictAll();

        @SuppressWarnings("unchecked") // Set 是接口, forClass 需要具体类, 此处为必须的 unchecked
        ArgumentCaptor<java.util.Set<String>> captor =
                ArgumentCaptor.forClass((Class<java.util.Set<String>>) (Class<?>) java.util.Set.class);
        verify(redisTemplate).delete(captor.capture());
        assertEquals(2, captor.getValue().size());
    }

    @Test
    void get_returnsNullWhenNotCached() {
        when(valueOps.get("perm:ctx:7")).thenReturn(null);
        assertNull(svc.get(7L));
    }

    // ==================== 真实序列化往返（复核 2026-08-09） ====================

    /**
     * 用 RedisConfig 完全一致的 Jackson 配置做 PermissionContext 序列化往返。
     * 覆盖本重构风险最高的点：RuleResult 内嵌类 + SysPosition/SysPermissionRule 实体的
     * 多态反序列化（BasicPolymorphicTypeValidator 白名单 com.aigicrm.* 是否兼容）。
     * 防止后续 PermissionContext 加字段/实体变更破坏白名单兼容。
     */
    @Test
    void realJacksonRoundTrip_preservesPermissionContext() {
        com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
        om.setVisibility(com.fasterxml.jackson.annotation.PropertyAccessor.ALL,
                com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY);
        om.activateDefaultTyping(
                com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator.builder()
                        .allowIfSubType("com.aigicrm.")
                        .allowIfSubType("java.util.")
                        .allowIfSubType("java.time.")
                        .allowIfSubType("java.lang.")
                        .build(),
                com.fasterxml.jackson.databind.ObjectMapper.DefaultTyping.NON_FINAL);
        om.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        com.aigicrm.config.WhitelistedJsonRedisSerializer ser =
                new com.aigicrm.config.WhitelistedJsonRedisSerializer(om);

        PermissionContext ctx = new PermissionContext();
        ctx.setUserId(7L);
        ctx.setUsername("ty101");
        ctx.setDataScope("team");
        ctx.setRoleCodes(List.of("sales"));
        ctx.setPermissions(java.util.Set.of("customer:view"));

        // 填充的 RuleResult（非 EMPTY，含 unmodifiable 集合）
        com.aigicrm.common.engine.PermissionRuleEngine.RuleResult rr =
                com.aigicrm.common.engine.PermissionRuleEngine.RuleResult.builder()
                        .whereClauses(java.util.List.of("owner_id = 7"))
                        .hiddenFields(java.util.Set.of("phone"))
                        .build();
        ctx.setRuleResult(rr);
        // 含 LocalDateTime 的实体
        com.aigicrm.module.system.entity.SysPosition pos = new com.aigicrm.module.system.entity.SysPosition();
        pos.setId(3L);
        pos.setCode("sales_pos");
        pos.setName("销售岗");
        ctx.setPositions(List.of(pos));

        // 非空 permissionRules（与 UserDetailsServiceImpl:186 同用 stream().toList() → ImmutableCollections$ListN）
        com.aigicrm.module.system.entity.SysPermissionRule rule = new com.aigicrm.module.system.entity.SysPermissionRule();
        rule.setId(9L);
        rule.setCode("dept_visible");
        rule.setRuleType("column_filter");
        ctx.setPermissionRules(java.util.List.of(rule).stream().collect(java.util.stream.Collectors.toUnmodifiableList()));

        byte[] bytes = ser.serialize(ctx);
        Object roundTripped = ser.deserialize(bytes);

        assertTrue(roundTripped instanceof PermissionContext);
        PermissionContext restored = (PermissionContext) roundTripped;
        assertEquals(7L, restored.getUserId());
        assertEquals("team", restored.getDataScope());
        assertEquals(1, restored.getRoleCodes().size());
        assertEquals(1, restored.getPermissions().size());
        assertEquals("owner_id = 7", restored.getRuleResult().getWhereClauses().get(0));
        assertEquals(1, restored.getPositions().size());
        assertEquals("sales_pos", restored.getPositions().get(0).getCode());
        assertEquals(1, restored.getPermissionRules().size());
        assertEquals("dept_visible", restored.getPermissionRules().get(0).getCode());
    }
}
