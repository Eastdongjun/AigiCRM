package com.ycmn.module.incentive.service;

import com.ycmn.common.scope.OrgScopeResolver;
import com.ycmn.module.customer.mapper.BizCustomerMapper;
import com.ycmn.module.customer.mapper.BizCustomerShareMapper;
import com.ycmn.module.file.mapper.BizFileMapper;
import com.ycmn.module.incentive.mapper.BizIncentiveCategoryMapper;
import com.ycmn.module.incentive.mapper.BizIncentiveRecordMapper;
import com.ycmn.module.system.mapper.SysDeptMapper;
import com.ycmn.module.system.mapper.SysOrgMapper;
import com.ycmn.module.system.mapper.SysUserMapper;
import com.ycmn.security.SecurityUser;
import com.ycmn.security.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * I1：IncentiveService.getDataScopeUserIds 新六级 code 统一路由 OrgScopeResolver
 * （修复新 code 无 case → 仅本人的降级；all → null 表示全集团无限制）。
 */
@ExtendWith(MockitoExtension.class)
class IncentiveDataScopeTest {

    @Mock
    OrgScopeResolver resolver;

    private IncentiveService newService() {
        return new IncentiveService(
                mock(BizIncentiveCategoryMapper.class),
                mock(BizIncentiveRecordMapper.class),
                mock(SysUserMapper.class),
                mock(BizFileMapper.class),
                mock(BizCustomerMapper.class),
                mock(BizCustomerShareMapper.class),
                mock(SysDeptMapper.class),
                mock(SysOrgMapper.class),
                mock(JdbcTemplate.class),
                resolver);
    }

    private SecurityUser su(Long userId, String scope) {
        SecurityUser s = new SecurityUser();
        s.setUserId(userId);
        s.setDataScope(scope);
        return s;
    }

    /** team 码 → resolver 可见集并入（修复 default 仅本人降级） */
    @Test
    void teamCodeRoutesThroughResolver() {
        SecurityUser su = su(1L, "team");
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentDataScope).thenReturn("team");
            m.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            m.when(SecurityUtils::getCurrentUser).thenReturn(su);
            when(resolver.resolve(su)).thenReturn(List.of(1L, 2L, 3L));

            IncentiveService svc = newService();
            @SuppressWarnings("unchecked")
            Set<Long> u = (Set<Long>) ReflectionTestUtils.invokeMethod(svc, "getDataScopeUserIds");

            assertEquals(Set.of(1L, 2L, 3L), u, "team 码必须经 resolver 生成可见集合");
        }
    }

    /** all 码（非 admin）→ resolver null → getDataScopeUserIds 返回 null（全集团无限制） */
    @Test
    void allCodeReturnsNullMeaningUnlimited() {
        SecurityUser su = su(1L, "all");
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentDataScope).thenReturn("all");
            m.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            m.when(SecurityUtils::getCurrentUser).thenReturn(su);
            when(resolver.resolve(su)).thenReturn(null);

            IncentiveService svc = newService();
            @SuppressWarnings("unchecked")
            Set<Long> u = (Set<Long>) ReflectionTestUtils.invokeMethod(svc, "getDataScopeUserIds");

            assertNull(u, "all 码应返回 null 表示全集团无限制");
        }
    }

    /** computeVisibility：getDataScopeUserIds=null → type=all（查询不套范围过滤） */
    @Test
    void computeVisibilityTreatsNullAsAll() {
        SecurityUser su = su(1L, "all");
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentDataScope).thenReturn("all");
            m.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            m.when(SecurityUtils::getCurrentUser).thenReturn(su);
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            when(resolver.resolve(su)).thenReturn(null);

            IncentiveService svc = newService();
            @SuppressWarnings("unchecked")
            Map<String, Object> v = (Map<String, Object>) ReflectionTestUtils.invokeMethod(svc, "computeVisibility");

            assertEquals("all", v.get("type"), "null 可见集应视为全集团无限制");
        }
    }
}
