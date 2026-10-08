package com.aigicrm.common.scope;

import com.aigicrm.security.SecurityUser;
import com.aigicrm.security.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mockStatic;

/**
 * 【C2/C3 2026-10-08】tenantCachePrefix 与 MybatisPlusConfig 租户拦截器取 tenant_id 同口径:
 * 会话 currentGroupId 优先 → 所属集团最小 id(resolveTenantId 的多集团 fail-closed) → 取不到=fail-closed 固定前缀。
 * 统计看板/积分榜共享缓存键都以此为集团前缀,口径漂移即跨集团互串,此处钉住。
 */
class GroupScopeUtilTest {

    @Test
    void tenantCachePrefix_sessionGroupWins() {
        var user = new SecurityUser();
        user.setCurrentGroupId(1L);
        user.setGroupIds(List.of(3L, 1L));
        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::getCurrentUser).thenReturn(user);
            sec.when(SecurityUtils::getCurrentGroupIds).thenReturn(user.getGroupIds());
            assertEquals("g1:", GroupScopeUtil.tenantCachePrefix(),
                    "会话 currentGroupId 优先(与拦截器 getTenantId 读取顺序一致)");
        }
    }

    @Test
    void tenantCachePrefix_multiGroupTakesMin() {
        var user = new SecurityUser();
        user.setGroupIds(List.of(5L, 3L, 9L));
        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::getCurrentUser).thenReturn(user);
            sec.when(SecurityUtils::getCurrentGroupIds).thenReturn(user.getGroupIds());
            assertEquals("g3:", GroupScopeUtil.tenantCachePrefix(),
                    "多集团取排序最小 id(与 resolveTenantId 的 fail-closed 同口径)");
        }
    }

    @Test
    void tenantCachePrefix_noGroupFailsClosed() {
        var user = new SecurityUser(); // 无 currentGroupId 无 groupIds
        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::getCurrentUser).thenReturn(user);
            sec.when(SecurityUtils::getCurrentGroupIds).thenReturn(List.of());
            assertEquals("g-none:", GroupScopeUtil.tenantCachePrefix(),
                    "取不到集团必须用 fail-closed 固定前缀(对应租户插件 tenant_id = NULL 恒空)");
        }
    }

    @Test
    void tenantCachePrefix_singleGroup() {
        var user = new SecurityUser();
        user.setGroupIds(List.of(7L));
        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::getCurrentUser).thenReturn(user);
            sec.when(SecurityUtils::getCurrentGroupIds).thenReturn(user.getGroupIds());
            assertEquals("g7:", GroupScopeUtil.tenantCachePrefix());
        }
    }
}
