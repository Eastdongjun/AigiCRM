package com.aigicrm.module.system.service;

import com.aigicrm.module.system.mapper.SysPermissionRuleMapper;
import com.aigicrm.module.system.mapper.SysPositionMapper;
import com.aigicrm.module.system.mapper.SysPositionRuleMapper;
import com.aigicrm.module.system.mapper.SysUserPositionMapper;
import com.aigicrm.security.PermissionCacheService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 【P1 2026-09-29 复审修复】清空路径缓存一致性回归钉(服务层)。
 * 修复前: saveRules/assignUserPositions 在空列表时 early-return,
 * 跳过缓存失效与版本递增——清空规则/清空岗位后旧权限在缓存中残留至 TTL。
 */
class SysPositionRuleGuardTest {

    private static SysPositionService newService(PermissionCacheService cache) {
        return new SysPositionService(
                mock(SysPositionMapper.class),
                mock(SysPositionRuleMapper.class),
                mock(SysUserPositionMapper.class),
                mock(SysPermissionRuleMapper.class),
                cache,
                mock(JdbcTemplate.class));
    }

    @Test
    void saveRules_clearingRules_stillEvictsAndBumps() {
        PermissionCacheService cache = mock(PermissionCacheService.class);
        SysPositionService svc = newService(cache);
        // 清空规则(空列表): 缓存失效+版本递增必须执行(修复前被 early-return 跳过)
        svc.saveRules(9L, List.of());
        verify(cache, atLeastOnce()).evictByPosition(9L);
    }

    @Test
    void saveRules_nullRules_alsoEvicts() {
        PermissionCacheService cache = mock(PermissionCacheService.class);
        SysPositionService svc = newService(cache);
        svc.saveRules(9L, null);
        verify(cache, atLeastOnce()).evictByPosition(9L);
    }

    @Test
    void assignUserPositions_clearingAll_stillEvictsAndBumps() {
        PermissionCacheService cache = mock(PermissionCacheService.class);
        SysPositionService svc = newService(cache);
        // 同类缺陷: 清空用户全部岗位时缓存失效必须执行(修复前被 early-return 跳过)
        svc.assignUserPositions(7L, List.of());
        verify(cache, atLeastOnce()).evict(7L);
    }
}
