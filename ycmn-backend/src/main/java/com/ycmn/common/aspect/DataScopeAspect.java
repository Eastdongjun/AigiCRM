package com.ycmn.common.aspect;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ycmn.common.annotation.DataScope;
import com.ycmn.common.engine.PermissionRuleEngine.RuleResult;
import com.ycmn.common.enums.DataScopeType;
import com.ycmn.common.scope.OrgScopeResolver;
import com.ycmn.module.system.entity.SysRoleOrg;
import com.ycmn.module.system.mapper.SysOrgMapper;
import com.ycmn.module.system.mapper.SysRoleOrgMapper;
import com.ycmn.security.SecurityUser;
import com.ycmn.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据权限 AOP 切面 — 双引擎版本
 *
 * 1. 枚举引擎（兼容）：@DataScope 注解 → 按 DataScopeType 注入 WHERE
 * 2. 规则引擎（新增）：登录时预计算的 RuleResult.whereClauses → 直接注入 WHERE
 * 3. 两者合并：AND 连接
 */
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class DataScopeAspect {

    private final SysOrgMapper orgMapper;
    private final SysRoleOrgMapper roleOrgMapper;
    private final OrgScopeResolver resolver;

    @Pointcut("@annotation(com.ycmn.common.annotation.DataScope)")
    public void dataScopePointcut() {
    }

    @Around("dataScopePointcut()")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        SecurityUser user = SecurityUtils.getCurrentUser();
        if (user == null) {
            return point.proceed();
        }

        if (SecurityUtils.isAdmin()) {
            return point.proceed();
        }

        // ===== 枚举引擎 =====
        DataScopeType scopeType = DataScopeType.fromCode(user.getDataScope());
        if (scopeType == DataScopeType.ALL) {
            // all 时跳过枚举引擎，但仍可能被规则引擎限制
        }

        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        DataScope annotation = method.getAnnotation(DataScope.class);

        // ===== 规则引擎 =====
        RuleResult ruleResult = user.getRuleResult();
        String ruleWhere = (ruleResult != null) ? ruleResult.toWhereClause() : "";

        Object[] args = point.getArgs();
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof QueryWrapper) {
                @SuppressWarnings("unchecked")
                QueryWrapper<Object> wrapper = (QueryWrapper<Object>) args[i];

                // 1. 应用枚举引擎（非 all 时）
                if (scopeType != DataScopeType.ALL) {
                    applyScope(wrapper, scopeType, user, annotation);
                }

                // 2. 应用规则引擎的 WHERE 条件
                if (!ruleWhere.isEmpty()) {
                    wrapper.apply(ruleWhere);
                }
            }
        }

        return point.proceed(args);
    }

    private void applyScope(QueryWrapper<Object> wrapper, DataScopeType scopeType,
                            SecurityUser user, DataScope annotation) {
        String orgField = annotation.orgAlias().isEmpty()
                ? "dept_id" : annotation.orgAlias() + ".dept_id";

        switch (scopeType) {
            case CUSTOM -> applyCustomScope(wrapper, user, orgField);
            case ORG_AND_CHILDREN -> applyOrgChildrenScope(wrapper, user, orgField, true);
            case ORG -> applyOrgScope(wrapper, user, orgField);
            // 新六级码（team/office/region/dept）：统一经 OrgScopeResolver 取可见 userIds
            // （多角色并集在 resolver 内自动生效）；null=all 不过滤（本集团）
            case REGION, OFFICE, DEPT, TEAM -> applyResolverScope(wrapper, user, annotation);
            case SELF -> applySelfScope(wrapper, user, annotation);
            // fail-closed：未匹配范围一律仅本人，禁止无过滤放行（修复 default→{} 泄漏面）
            default -> applySelfScope(wrapper, user, annotation);
        }
    }

    /** 新六级码：resolver 可见 userIds → user_id IN (…)（null=all 不过滤） */
    private void applyResolverScope(QueryWrapper<Object> wrapper, SecurityUser user, DataScope annotation) {
        List<Long> visible = resolver.resolve(user);
        if (visible == null) return; // all：本集团不过滤
        String userField = annotation.userAlias().isEmpty()
                ? "user_id" : annotation.userAlias() + ".user_id";
        wrapper.in(userField, visible);
    }

    /** 自定义：查 sys_role_org 矩阵 */
    private void applyCustomScope(QueryWrapper<Object> wrapper, SecurityUser user, String orgField) {
        List<SysRoleOrg> roleOrgs = roleOrgMapper.selectByUserId(user.getUserId());
        if (roleOrgs.isEmpty()) {
            // 无配置 → 只查自己
            wrapper.eq(orgField, user.getDeptId());
            return;
        }

        Set<Long> allOrgIds = new HashSet<>();
        for (SysRoleOrg ro : roleOrgs) {
            if (ro.getIncludeChildren() != null && ro.getIncludeChildren() == 1) {
                allOrgIds.addAll(orgMapper.selectDescendantIds(ro.getOrgId()));
            } else {
                allOrgIds.add(ro.getOrgId());
            }
        }

        if (allOrgIds.isEmpty()) {
            wrapper.eq(orgField, -1L); // 查不到任何数据
        } else {
            wrapper.in(orgField, allOrgIds);
        }
    }

    /** 本组织及下级 */
    private void applyOrgChildrenScope(QueryWrapper<Object> wrapper, SecurityUser user,
                                       String orgField, boolean recursive) {
        if (user.getDeptId() == null) {
            wrapper.eq(orgField, -1L);
            return;
        }
        List<Long> orgIds = orgMapper.selectDescendantIds(user.getDeptId());
        wrapper.in(orgField, orgIds);
    }

    /** 仅本组织 */
    private void applyOrgScope(QueryWrapper<Object> wrapper, SecurityUser user, String orgField) {
        wrapper.eq(orgField, user.getDeptId() != null ? user.getDeptId() : -1L);
    }

    /** 仅自己 */
    private void applySelfScope(QueryWrapper<Object> wrapper, SecurityUser user, DataScope annotation) {
        String userField = annotation.userAlias().isEmpty()
                ? "user_id" : annotation.userAlias() + ".user_id";
        wrapper.eq(userField, user.getUserId());
    }
}
