package com.ycmn.common.scope;

import com.ycmn.common.BusinessException;
import com.ycmn.security.SecurityUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DataScopeGuard 归属校验守卫测试。
 * 静态便捷方法直接测（无 Spring 上下文）；注入版 checkOwner 用 OrgScopeResolver 匿名子类
 * 覆写 resolve() 桩化可见范围，SecurityContextHolder 模拟登录上下文。
 */
class DataScopeGuardTest {

    // ---- 静态便捷方法（brief 契约） ----

    @Test
    void checkOwnerRejectsOutOfScope() {
        // ownerId=3 不在可见范围 [1,2] 且非本人 → 必须抛 403（契约：不可访问抛 BusinessException）
        List<Long> scope = List.of(1L, 2L);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> DataScopeGuard.checkOwnerInScope(3L, scope, false));
        assertEquals(403, ex.getCode());
        assertEquals("无权操作该记录", ex.getMessage());
    }

    @Test
    void checkOwnerAllowsInScope() {
        assertDoesNotThrow(() -> DataScopeGuard.checkOwnerInScope(2L, List.of(1L, 2L), false));
    }

    @Test
    void nullScopeAllowsAll() {
        assertTrue(DataScopeGuard.isVisible(5L, null, false));
        assertDoesNotThrow(() -> DataScopeGuard.checkOwnerInScope(5L, null, false));
    }

    @Test
    void ownerIsAlwaysVisible() {
        assertTrue(DataScopeGuard.isVisible(7L, List.of(1L), true)); // ownerIsSelf=true
        assertDoesNotThrow(() -> DataScopeGuard.checkOwnerInScope(7L, List.of(1L), true));
    }

    @Test
    void nullOwnerIsNotVisible() {
        // M1：ownerId 为 null 优先拒绝（对齐注入版「记录归属缺失」语义）
        assertFalse(DataScopeGuard.isVisible(null, List.of(1L), false));
        assertFalse(DataScopeGuard.isVisible(null, null, true));
    }

    // ---- Spring 注入实例方法 checkOwner（核心守卫） ----

    /** resolver 桩：固定可见范围（null=all 不过滤；不走 Spring/DB） */
    private static DataScopeGuard guardWithScope(List<Long> scope) {
        OrgScopeResolver stub = new OrgScopeResolver(null, null, null, null) { // resolve 被覆写，configService null 安全
            @Override
            public List<Long> resolve(SecurityUser user) {
                return scope;
            }
        };
        return new DataScopeGuard(stub);
    }

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void injectedCheckOwnerRejectsOutOfResolverScope() {
        // 无登录上下文、无 override：owner 不在 resolver 可见范围 → 403
        DataScopeGuard guard = guardWithScope(List.of(1L, 2L));
        BusinessException ex = assertThrows(BusinessException.class, () -> guard.checkOwner(3L, null));
        assertEquals(403, ex.getCode());
        assertEquals("无权操作该记录", ex.getMessage());
    }

    @Test
    void injectedCheckOwnerAllowsInResolverScope() {
        DataScopeGuard guard = guardWithScope(List.of(1L, 2L));
        assertDoesNotThrow(() -> guard.checkOwner(2L, null));
    }

    @Test
    void injectedCheckOwnerAllowsViaScopeOverride() {
        // 分享等额外可见集合：owner 在 override 中（即使不在 resolver 范围）也放行
        DataScopeGuard guard = guardWithScope(List.of(1L, 2L));
        assertDoesNotThrow(() -> guard.checkOwner(9L, List.of(9L)));
    }

    @Test
    void injectedCheckOwnerRejectsOutOfOverrideAndResolver() {
        // C1 回归：owner 既不在 resolver 范围也不在 override → 必须拒绝（旧实现 fail-open 恒放行）
        DataScopeGuard guard = guardWithScope(List.of(1L, 2L));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> guard.checkOwner(9L, List.of(5L)));
        assertEquals(403, ex.getCode());
    }

    @Test
    void injectedCheckOwnerAllowsOwnerInResolverDespiteOverride() {
        // 并集语义锁定：owner ∈ resolver 但 ∉ override → 放行（两集合取并，不互斥）
        DataScopeGuard guard = guardWithScope(List.of(1L, 2L));
        assertDoesNotThrow(() -> guard.checkOwner(2L, List.of(5L)));
    }

    @Test
    void injectedCheckOwnerRejectsNullOwner() {
        DataScopeGuard guard = guardWithScope(List.of(1L));
        BusinessException ex = assertThrows(BusinessException.class, () -> guard.checkOwner(null, null));
        assertEquals(403, ex.getCode());
        assertEquals("记录归属缺失", ex.getMessage());
    }

    @Test
    void injectedCheckOwnerAllowsWhenResolverReturnsNull() {
        // resolver 返回 null（all 不过滤）→ 任意 owner 放行
        DataScopeGuard guard = guardWithScope(null);
        assertDoesNotThrow(() -> guard.checkOwner(42L, null));
    }

    @Test
    void injectedCheckOwnerAllowsSelfWithoutResolver() {
        // 已登录上下文且 owner==本人 → 直接放行，不依赖 resolver
        SecurityUser me = new SecurityUser();
        me.setUserId(7L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(me, null));
        DataScopeGuard guard = guardWithScope(List.of(1L)); // 范围不含 7
        assertDoesNotThrow(() -> guard.checkOwner(7L, null));
    }
}
