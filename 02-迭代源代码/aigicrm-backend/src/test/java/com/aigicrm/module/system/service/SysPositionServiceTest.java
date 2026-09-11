package com.aigicrm.module.system.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisMapperBuilderAssistant;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.aigicrm.common.BusinessException;
import com.aigicrm.module.system.entity.SysPosition;
import com.aigicrm.module.system.mapper.SysPermissionRuleMapper;
import com.aigicrm.module.system.mapper.SysPositionMapper;
import com.aigicrm.module.system.mapper.SysPositionRuleMapper;
import com.aigicrm.module.system.mapper.SysUserPositionMapper;
import com.aigicrm.security.SecurityUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SysPositionService 岗位集团边界测试（纯 mock，无 Spring 上下文）。
 * 口径与 SysRoleServiceTest 一致（仿 SysUserService.applyGroupScope）：
 * - 非 admin：列表仅返回本集团岗位（断言 wrapper 含 group_id IN 条件与集团参数值）；
 * - admin：不附加集团过滤（跨集团管理）；
 * - 取不到集团归属：退回主集团 1（fail-closed，不泄漏、不空转）；
 * - CRUD 集团校验（REQ-005 残余收口）：getById 越权集团→null、
 *   create 越权集团→拒绝且未传 groupId 时兜底当前集团、
 *   update/delete 越权集团→拒绝。
 */
class SysPositionServiceTest {

    /** 纯 mock 单测无 MyBatis 启动,手动注册 TableInfo 供 LambdaQueryWrapper 解析列名 */
    @BeforeAll
    static void initTableInfo() {
        MybatisMapperBuilderAssistant assistant =
                new MybatisMapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SysPosition.class);
    }

    private static SysPositionService newServiceWith(SysPositionMapper positionMapper) {
        return new SysPositionService(positionMapper,
                mock(SysPositionRuleMapper.class),
                mock(SysUserPositionMapper.class),
                mock(SysPermissionRuleMapper.class),
                mock(com.aigicrm.security.PermissionCacheService.class),
                mock(org.springframework.jdbc.core.JdbcTemplate.class));
    }

    @Test
    void list_nonAdminFiltersByGroupIds() {
        SysPositionMapper positionMapper = mock(SysPositionMapper.class);
        SysPositionService service = newServiceWith(positionMapper);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentGroupIds).thenReturn(List.of(1L));

            service.list();
            @SuppressWarnings("unchecked")

            ArgumentCaptor<LambdaQueryWrapper<SysPosition>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
            verify(positionMapper).selectList(captor.capture());
            String expr = captor.getValue().getSqlSegment();
            assertTrue(expr.contains("group_id"), "非 admin 应附加 group_id 过滤，实际: " + expr);
            assertTrue(captor.getValue().getParamNameValuePairs().containsValue(1L),
                    "应限定到当前集团(1)，实际: " + captor.getValue().getParamNameValuePairs());
        }
    }

    @Test
    void list_adminSeesAllGroups() {
        SysPositionMapper positionMapper = mock(SysPositionMapper.class);
        SysPositionService service = newServiceWith(positionMapper);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(true);

            service.list();
            @SuppressWarnings("unchecked")

            ArgumentCaptor<LambdaQueryWrapper<SysPosition>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
            verify(positionMapper).selectList(captor.capture());
            String expr = captor.getValue().getSqlSegment();
            assertFalse(expr.contains("group_id"), "admin 不应附加集团过滤，实际: " + expr);
        }
    }

    @Test
    void list_emptyGroupIdsFallsBackToMainGroup() {
        // 取不到集团归属（无岗位/无组织）时退主集团 1（fail-closed，与 MybatisPlusConfig.resolveTenantId 同口径）
        SysPositionMapper positionMapper = mock(SysPositionMapper.class);
        SysPositionService service = newServiceWith(positionMapper);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentGroupIds).thenReturn(List.of());

            service.list();
            @SuppressWarnings("unchecked")

            ArgumentCaptor<LambdaQueryWrapper<SysPosition>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
            verify(positionMapper).selectList(captor.capture());
            String expr = captor.getValue().getSqlSegment();
            assertTrue(expr.contains("group_id"), "集团为空也应过滤到主集团，实际: " + expr);
            assertTrue(captor.getValue().getParamNameValuePairs().containsValue(1L),
                    "应退回主集团 1，实际: " + captor.getValue().getParamNameValuePairs());
        }
    }

    // ---- 单条读写集团校验（REQ-005 残余收口，与 SysRoleServiceTest 同口径） ----

    private static SysPosition positionOf(Long id, Long groupId) {
        SysPosition p = new SysPosition();
        p.setId(id);
        p.setGroupId(groupId);
        return p;
    }

    @Test
    void getById_otherGroupPosition_returnsNull() {
        SysPositionMapper positionMapper = mock(SysPositionMapper.class);
        SysPositionService service = newServiceWith(positionMapper);
        when(positionMapper.selectById(5L)).thenReturn(positionOf(5L, 2L));
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentGroupIds).thenReturn(List.of(1L));

            assertNull(service.getById(5L));
        }
    }

    @Test
    void create_otherGroupRejected() {
        SysPositionMapper positionMapper = mock(SysPositionMapper.class);
        SysPositionService service = newServiceWith(positionMapper);
        SysPosition pos = positionOf(null, 2L);
        pos.setCode("p2");
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentGroupIds).thenReturn(List.of(1L));

            assertThrows(BusinessException.class, () -> service.create(pos));
            verify(positionMapper, never()).insert(any(SysPosition.class));
        }
    }

    @Test
    void create_noGroupId_defaultsToCurrentGroup() {
        SysPositionMapper positionMapper = mock(SysPositionMapper.class);
        SysPositionService service = newServiceWith(positionMapper);
        SysPosition pos = new SysPosition();
        pos.setCode("p9");
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentGroupIds).thenReturn(List.of(1L));

            service.create(pos);

            ArgumentCaptor<SysPosition> captor = ArgumentCaptor.forClass(SysPosition.class);
            verify(positionMapper).insert(captor.capture());
            assertEquals(1L, captor.getValue().getGroupId(), "未传 groupId 应默认当前用户集团");
        }
    }

    @Test
    void update_otherGroupPositionRejected() {
        SysPositionMapper positionMapper = mock(SysPositionMapper.class);
        SysPositionService service = newServiceWith(positionMapper);
        when(positionMapper.selectById(5L)).thenReturn(positionOf(5L, 2L));
        SysPosition payload = positionOf(5L, null);
        payload.setName("改名");
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentGroupIds).thenReturn(List.of(1L));

            assertThrows(BusinessException.class, () -> service.update(payload));
            verify(positionMapper, never()).updateById(any(SysPosition.class));
        }
    }

    @Test
    void delete_otherGroupPositionRejected() {
        SysPositionMapper positionMapper = mock(SysPositionMapper.class);
        SysPositionService service = newServiceWith(positionMapper);
        when(positionMapper.selectById(5L)).thenReturn(positionOf(5L, 2L));
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentGroupIds).thenReturn(List.of(1L));

            assertThrows(BusinessException.class, () -> service.delete(5L));
            verify(positionMapper, never()).deleteById(5L);
        }
    }

    // ---- 【2026-08-14 缓存自愈】权限版本递增（参照 SysRoleService.delete 顺序教训：反查依赖绑定表，必须先递增再删绑定） ----

    /** 删除岗位必须先 evict + 版本递增(依赖 sys_user_position 子查询)，再删绑定；
     *  否则绑定删除后子查询反查为空、bump 静默失效，旧权限残留至 TTL */
    @Test
    @SuppressWarnings("unchecked")
    void delete_bumpsUserPermVersionsBeforeRemovingBindings() {
        SysPositionMapper positionMapper = mock(SysPositionMapper.class);
        SysUserPositionMapper userPositionMapper = mock(SysUserPositionMapper.class);
        com.aigicrm.security.PermissionCacheService cache = mock(com.aigicrm.security.PermissionCacheService.class);
        org.springframework.jdbc.core.JdbcTemplate jdbc = mock(org.springframework.jdbc.core.JdbcTemplate.class);
        SysPositionService service = new SysPositionService(positionMapper,
                mock(SysPositionRuleMapper.class), userPositionMapper,
                mock(SysPermissionRuleMapper.class), cache, jdbc);
        when(positionMapper.selectById(5L)).thenReturn(positionOf(5L, 1L));
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(true);

            service.delete(5L);

            // 顺序：evict + 版本递增(子查询依赖 sys_user_position) → 删岗位-规则绑定 → 删用户-岗位绑定
            org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(jdbc, userPositionMapper, cache);
            inOrder.verify(cache).evictByPosition(5L);
            inOrder.verify(jdbc).update(org.mockito.ArgumentMatchers.contains("perm_version"),
                    org.mockito.ArgumentMatchers.eq(5L));
            inOrder.verify(userPositionMapper).delete(any(LambdaQueryWrapper.class));
        }
    }

    /** 用户-岗位分配变更：evict 后用户级版本递增兜底 */
    @Test
    void assignUserPositions_bumpsUserPermVersion() {
        SysPositionMapper positionMapper = mock(SysPositionMapper.class);
        com.aigicrm.security.PermissionCacheService cache = mock(com.aigicrm.security.PermissionCacheService.class);
        org.springframework.jdbc.core.JdbcTemplate jdbc = mock(org.springframework.jdbc.core.JdbcTemplate.class);
        SysPositionService service = new SysPositionService(positionMapper,
                mock(SysPositionRuleMapper.class), mock(SysUserPositionMapper.class),
                mock(SysPermissionRuleMapper.class), cache, jdbc);

        service.assignUserPositions(9L, List.of(1L, 2L));

        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(jdbc, cache);
        inOrder.verify(cache).evict(9L);
        inOrder.verify(jdbc).update(org.mockito.ArgumentMatchers.contains("perm_version"),
                org.mockito.ArgumentMatchers.eq(9L));
    }
}
