package com.aigicrm.module.system.service;

import com.aigicrm.common.scope.OrgScopeResolver;
import com.aigicrm.module.system.entity.SysAuditLog;
import com.aigicrm.module.system.mapper.SysAuditLogMapper;
import com.aigicrm.security.SecurityUser;
import com.aigicrm.security.SecurityUtils;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 【A2 2026-10-20 审计·集团边界】SysAuditLogService 非 admin 收口回归:
 * sys_audit_log 无租户列不走租户拦截器, 非 admin(system:log:view 曾绑销售总监 role '0')
 * 必须按"当前集团成员 actorId 集合"过滤, 空集返回空页, 防跨集团读取登录失败/越权事件(用户名/IP)。
 * 按 OrgScopeResolver 集团口径 mock resolver(不 mock 静态 GroupScopeUtil, 与真实链路一致)。
 */
class SysAuditLogServiceTest {

    private final SysAuditLogMapper auditLogMapper = mock(SysAuditLogMapper.class);
    private final OrgScopeResolver scopeResolver = mock(OrgScopeResolver.class);
    private final SysAuditLogService service = new SysAuditLogService(auditLogMapper, scopeResolver);

    /** getSqlSegment 需要 Lambda 列名缓存: 纯单测无 MyBatis 容器, 注册实体 TableInfo */
    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SysAuditLog.class);
    }

    private static IPage<SysAuditLog> emptyPage() {
        Page<SysAuditLog> page = new Page<>(1, 20);
        page.setRecords(List.of());
        page.setTotal(0);
        return page;
    }

    /** 非 admin: wrapper 必须按集团成员 actorId 集合 IN 过滤(过滤器即 DB 边界, A 集团成员集不含 B 集团 actor) */
    @Test
    void page_nonAdmin_scopesToCurrentGroupMemberActorIds() {
        SecurityUser director = new SecurityUser();
        director.setUserId(7L);
        director.setRoleCodes(List.of("0")); // 销售总监, 非 admin
        // resolver 返回"A 集团成员"(7,8,9); B 集团成员 99 不在集合内
        when(scopeResolver.resolveModuleUsersWithinGroup(director, "system")).thenReturn(List.of(7L, 8L, 9L));
        when(auditLogMapper.selectPage(any(), any())).thenAnswer(inv -> {
            // 模拟 DB 按 wrapper 过滤: actor_id IN (7,8,9) 之外(B 集团 99 / NULL 匿名行)不返回
            LambdaQueryWrapper<SysAuditLog> w = inv.getArgument(1);
            w.getSqlSegment();
            Map<String, Object> params = w.getParamNameValuePairs();
            for (Object v : params.values()) {
                if (v instanceof Long l && (l == 7L || l == 8L || l == 9L)) {
                    return emptyPage();
                }
            }
            throw new AssertionError("wrapper 未按集团成员 actorId 过滤: " + params.values());
        });

        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::isAdmin).thenReturn(false);
            security.when(SecurityUtils::getCurrentUser).thenReturn(director);

            service.page(1, 20, null, null);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<LambdaQueryWrapper<SysAuditLog>> captor =
                    ArgumentCaptor.forClass(LambdaQueryWrapper.class);
            verify(auditLogMapper).selectPage(any(), captor.capture());
            String segment = captor.getValue().getSqlSegment();
            assertTrue(segment.contains("actor_id IN"), "应按集团成员 actorId 集合过滤, 实际: " + segment);
            Map<String, Object> params = captor.getValue().getParamNameValuePairs();
            assertTrue(params.containsValue(7L) && params.containsValue(8L) && params.containsValue(9L),
                    "A 集团成员应在过滤参数内: " + params.values());
            assertFalse(params.containsValue(99L), "B 集团 actor 不得出现在过滤参数内(跨集团不可读)");
            assertFalse(params.containsValue(null), "NULL actor 的匿名事件不在成员集合内, 非 admin 不可见");
        }
    }

    /** 非 admin + 集团成员空集(无集团归属/空集团): fail-closed, eq(actorId, Long.MIN_VALUE) → 空页 */
    @Test
    void page_nonAdminEmptyGroupMembers_returnsEmptyPageViaImpossibleActorId() {
        SecurityUser loner = new SecurityUser();
        loner.setUserId(7L);
        loner.setRoleCodes(List.of("0"));
        when(scopeResolver.resolveModuleUsersWithinGroup(loner, "system")).thenReturn(List.of());
        when(auditLogMapper.selectPage(any(), any())).thenReturn(emptyPage());

        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::isAdmin).thenReturn(false);
            security.when(SecurityUtils::getCurrentUser).thenReturn(loner);

            IPage<Map<String, Object>> result = service.page(1, 20, null, null);

            assertEquals(0, result.getTotal(), "空集必须返回空页, 不得放行全量");
            @SuppressWarnings("unchecked")
            ArgumentCaptor<LambdaQueryWrapper<SysAuditLog>> captor =
                    ArgumentCaptor.forClass(LambdaQueryWrapper.class);
            verify(auditLogMapper).selectPage(any(), captor.capture());
            String segment = captor.getValue().getSqlSegment();
            assertTrue(segment.contains("actor_id ="), "空集应退化为恒不命中的 eq 条件, 实际: " + segment);
            assertTrue(captor.getValue().getParamNameValuePairs().containsValue(Long.MIN_VALUE),
                    "空集哨兵值应为 Long.MIN_VALUE(与 SysLogService.buildBase 同口径)");
        }
    }

    /** admin: 不追加任何集团条件(平台管理员语义, 平台全量) */
    @Test
    void page_admin_keepsPlatformWideAccessWithoutGroupFilter() {
        when(auditLogMapper.selectPage(any(), any())).thenReturn(emptyPage());

        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::isAdmin).thenReturn(true);

            service.page(1, 20, null, null);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<LambdaQueryWrapper<SysAuditLog>> captor =
                    ArgumentCaptor.forClass(LambdaQueryWrapper.class);
            verify(auditLogMapper).selectPage(any(), captor.capture());
            String segment = captor.getValue().getSqlSegment();
            assertFalse(segment.contains("actor_id"), "admin 不得追加 actor 范围条件, 实际: " + segment);
        }
    }

    /** 显式 actorId 参数与非 admin 集团收口叠加(两个 eq/in 条件 AND 共存), 保持原有过滤能力 */
    @Test
    void page_nonAdminExplicitActorId_stillIntersectsGroupScope() {
        SecurityUser director = new SecurityUser();
        director.setUserId(8L);
        director.setRoleCodes(List.of("0"));
        when(scopeResolver.resolveModuleUsersWithinGroup(director, "system")).thenReturn(List.of(7L, 8L));
        when(auditLogMapper.selectPage(any(), any())).thenReturn(emptyPage());

        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::isAdmin).thenReturn(false);
            security.when(SecurityUtils::getCurrentUser).thenReturn(director);

            service.page(1, 20, null, 7L);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<LambdaQueryWrapper<SysAuditLog>> captor =
                    ArgumentCaptor.forClass(LambdaQueryWrapper.class);
            verify(auditLogMapper).selectPage(any(), captor.capture());
            String segment = captor.getValue().getSqlSegment();
            assertTrue(segment.contains("actor_id =") && segment.contains("actor_id IN"),
                    "显式 actorId 与集团集合应同时生效(AND), 实际: " + segment);
        }
    }
}
