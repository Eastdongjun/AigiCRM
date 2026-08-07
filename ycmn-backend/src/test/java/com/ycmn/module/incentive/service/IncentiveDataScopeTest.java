package com.ycmn.module.incentive.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisMapperBuilderAssistant;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ycmn.common.scope.OrgScopeResolver;
import com.ycmn.module.customer.entity.BizCustomerShare;
import com.ycmn.module.customer.mapper.BizCustomerMapper;
import com.ycmn.module.customer.mapper.BizCustomerShareMapper;
import com.ycmn.module.file.mapper.BizFileMapper;
import com.ycmn.module.incentive.entity.BizIncentiveRecord;
import com.ycmn.module.incentive.mapper.BizIncentiveCategoryMapper;
import com.ycmn.module.incentive.mapper.BizIncentiveRecordMapper;
import com.ycmn.module.system.mapper.SysDeptMapper;
import com.ycmn.module.system.mapper.SysOrgMapper;
import com.ycmn.module.system.mapper.SysUserMapper;
import com.ycmn.security.SecurityUser;
import com.ycmn.security.SecurityUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * I1：IncentiveService.getDataScopeUserIds 新六级 code 统一路由 OrgScopeResolver
 * （修复新 code 无 case → 仅本人的降级；all → null 表示全集团无限制）。
 */
@ExtendWith(MockitoExtension.class)
class IncentiveDataScopeTest {

    @Mock
    OrgScopeResolver resolver;
    @Mock
    BizIncentiveRecordMapper recordMapper;
    @Mock
    BizCustomerShareMapper shareMapper;
    @Mock
    JdbcTemplate jdbc;

    /** 纯 mock 单测，手动注册 TableInfo 供 LambdaQueryWrapper 渲染 SQL 断言 */
    @BeforeAll
    static void initTableInfo() {
        MybatisMapperBuilderAssistant assistant =
                new MybatisMapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, BizIncentiveRecord.class);
    }

    private IncentiveService newService() {
        return new IncentiveService(
                mock(BizIncentiveCategoryMapper.class),
                recordMapper,
                mock(SysUserMapper.class),
                mock(BizFileMapper.class),
                mock(BizCustomerMapper.class),
                shareMapper,
                mock(SysDeptMapper.class),
                mock(SysOrgMapper.class),
                jdbc,
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

    // ===== 激励查询数据范围修复（2026-08-07）：GET /records 不再强制仅本人 =====

    /** 未传 userId → 按数据范围过滤（上级/负责人可见下级） */
    @Test
    void getRecords_withoutUserId_appliesDataScope() {
        SecurityUser su = su(1L, "self");
        when(shareMapper.selectList(any())).thenReturn(List.of());
        when(recordMapper.selectList(any())).thenReturn(List.of());
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentUser).thenReturn(su);
            m.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            m.when(SecurityUtils::getCurrentDataScope).thenReturn("self");
            when(resolver.resolve(su)).thenReturn(List.of(1L, 2L));

            IncentiveService svc = newService();
            svc.getRecords(null, null, null);

            ArgumentCaptor<LambdaQueryWrapper<BizIncentiveRecord>> cap = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
            verify(recordMapper).selectList(cap.capture());
            assertTrue(cap.getValue().getSqlSegment().contains("user_id"),
                    "未指定 userId 应按数据范围过滤 user_id，实际: " + cap.getValue().getSqlSegment());
        }
    }

    /** 显式传越权 userId（不在可见范围）→ 返回空且不查询（防 IDOR） */
    @Test
    void getRecords_withUserIdOutOfScope_returnsEmpty() {
        SecurityUser su = su(1L, "self");
        when(shareMapper.selectList(any())).thenReturn(List.of());
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentUser).thenReturn(su);
            m.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            m.when(SecurityUtils::getCurrentDataScope).thenReturn("self");
            when(resolver.resolve(su)).thenReturn(List.of(1L, 2L));

            IncentiveService svc = newService();
            assertEquals(List.of(), svc.getRecords(999L, null, null));
            verify(recordMapper, never()).selectList(any());
        }
    }

    /** 可见范围内的 userId 可查；范围外拒绝 */
    @Test
    void canViewIncentiveUser_reflectsVisibleScope() {
        SecurityUser su = su(1L, "self");
        when(shareMapper.selectList(any())).thenReturn(List.of());
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentUser).thenReturn(su);
            m.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            m.when(SecurityUtils::getCurrentDataScope).thenReturn("self");
            when(resolver.resolve(su)).thenReturn(List.of(1L, 2L));

            IncentiveService svc = newService();
            assertTrue((Boolean) ReflectionTestUtils.invokeMethod(svc, "canViewIncentiveUser", 2L),
                    "可见范围内的 userId 应可查看");
            assertTrue(!(Boolean) ReflectionTestUtils.invokeMethod(svc, "canViewIncentiveUser", 999L),
                    "范围外 userId 应拒绝");
        }
    }
}
