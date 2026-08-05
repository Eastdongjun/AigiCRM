package com.ycmn.common.aspect;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ycmn.common.annotation.DataScope;
import com.ycmn.common.scope.OrgScopeResolver;
import com.ycmn.module.system.mapper.SysOrgMapper;
import com.ycmn.module.system.mapper.SysRoleOrgMapper;
import com.ycmn.security.SecurityUser;
import com.ycmn.security.SecurityUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * I1：DataScopeAspect 新六级码经 OrgScopeResolver 注入 user_id IN；
 * 未匹配范围 fail-closed（仅本人），禁止 default→{} 无过滤放行。
 */
@ExtendWith(MockitoExtension.class)
class DataScopeAspectTest {

    @Mock SysOrgMapper orgMapper;
    @Mock SysRoleOrgMapper roleOrgMapper;
    @Mock OrgScopeResolver resolver;
    @Mock ProceedingJoinPoint joinPoint;
    @Mock MethodSignature signature;

    @DataScope(userAlias = "u")
    private void annotatedMethod(QueryWrapper<Object> w) {
    }

    private SecurityUser su(Long userId, String scope) {
        SecurityUser s = new SecurityUser();
        s.setUserId(userId);
        s.setDataScope(scope);
        return s;
    }

    private DataScopeAspect newAspect() {
        return new DataScopeAspect(orgMapper, roleOrgMapper, resolver);
    }

    /** 新六级码（team）：经 resolver 生成 u.user_id IN (…) 条件 */
    @Test
    void newScopeCodeInjectsUserIdInCondition() throws Throwable {
        SecurityUser user = su(1L, "team");
        Method method = DataScopeAspectTest.class.getDeclaredMethod("annotatedMethod", QueryWrapper.class);
        when(signature.getMethod()).thenReturn(method);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(resolver.resolve(user)).thenReturn(List.of(1L, 2L, 3L));

        QueryWrapper<Object> wrapper = new QueryWrapper<>();
        when(joinPoint.getArgs()).thenReturn(new Object[]{wrapper});

        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUser).thenReturn(user);
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            newAspect().around(joinPoint);
        }

        assertTrue(wrapper.getSqlSegment().toLowerCase().contains("u.user_id"),
                "team 码必须注入 user_id IN 条件: " + wrapper.getSqlSegment());
    }

    /** 未匹配范围（未知码 → SELF）：fail-closed 注入本人 user_id 条件，不允许无过滤放行 */
    @Test
    void unknownScopeFailsClosedToSelf() throws Throwable {
        SecurityUser user = su(1L, "weird_scope");
        Method method = DataScopeAspectTest.class.getDeclaredMethod("annotatedMethod", QueryWrapper.class);
        when(signature.getMethod()).thenReturn(method);
        when(joinPoint.getSignature()).thenReturn(signature);

        QueryWrapper<Object> wrapper = new QueryWrapper<>();
        when(joinPoint.getArgs()).thenReturn(new Object[]{wrapper});

        try (MockedStatic<SecurityUtils> m = mockStatic(SecurityUtils.class)) {
            m.when(SecurityUtils::getCurrentUser).thenReturn(user);
            m.when(SecurityUtils::isAdmin).thenReturn(false);
            newAspect().around(joinPoint);
        }

        assertTrue(wrapper.getSqlSegment().toLowerCase().contains("u.user_id"),
                "未匹配范围必须 fail-closed 注入 user_id 条件: " + wrapper.getSqlSegment());
    }
}
