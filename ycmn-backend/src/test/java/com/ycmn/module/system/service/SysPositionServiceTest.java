package com.ycmn.module.system.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisMapperBuilderAssistant;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ycmn.module.system.entity.SysPosition;
import com.ycmn.module.system.mapper.SysPermissionRuleMapper;
import com.ycmn.module.system.mapper.SysPositionMapper;
import com.ycmn.module.system.mapper.SysPositionRuleMapper;
import com.ycmn.module.system.mapper.SysUserPositionMapper;
import com.ycmn.security.SecurityUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

/**
 * SysPositionService 岗位列表集团边界测试（纯 mock，无 Spring 上下文）。
 * 口径与 SysRoleServiceTest 一致（仿 SysUserService.applyGroupScope）：
 * - 非 admin：列表仅返回本集团岗位（断言 wrapper 含 group_id IN 条件与集团参数值）；
 * - admin：不附加集团过滤（跨集团管理）；
 * - 取不到集团归属：退回主集团 1（fail-closed，不泄漏、不空转）。
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
                mock(SysPermissionRuleMapper.class));
    }

    @Test
    void list_nonAdminFiltersByGroupIds() {
        SysPositionMapper positionMapper = mock(SysPositionMapper.class);
        SysPositionService service = newServiceWith(positionMapper);
        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            m.when(SecurityUtils::getCurrentGroupIds).thenReturn(List.of(1L));

            service.list();

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

            ArgumentCaptor<LambdaQueryWrapper<SysPosition>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
            verify(positionMapper).selectList(captor.capture());
            String expr = captor.getValue().getSqlSegment();
            assertTrue(expr.contains("group_id"), "集团为空也应过滤到主集团，实际: " + expr);
            assertTrue(captor.getValue().getParamNameValuePairs().containsValue(1L),
                    "应退回主集团 1，实际: " + captor.getValue().getParamNameValuePairs());
        }
    }
}
