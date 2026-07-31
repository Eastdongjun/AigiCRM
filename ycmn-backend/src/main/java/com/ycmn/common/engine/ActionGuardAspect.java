package com.ycmn.common.engine;

import com.ycmn.common.engine.PermissionRuleEngine.RuleResult;
import com.ycmn.security.SecurityUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 操作控制切面
 *
 * 根据规则引擎计算的 actions（如 {"create":false,"delete":false}），
 * 拦截 Controller 方法，拒绝无权限的操作。
 *
 * 用法：Controller 方法名需匹配权限 action 名称
 *   createXxx  → 需要 actions.create = true
 *   deleteXxx  → 需要 actions.delete = true
 *   exportXxx  → 需要 actions.export = true
 */
@Aspect
@Component
public class ActionGuardAspect {

    /** 拦截所有 controller 包下的方法 */
    @Around("execution(* com.ycmn.module..controller.*.*(..))")
    public Object guard(ProceedingJoinPoint joinPoint) throws Throwable {
        RuleResult ruleResult = SecurityUtils.getCurrentRuleResult();
        if (ruleResult == null || ruleResult.getActions().isEmpty()) {
            return joinPoint.proceed();
        }

        String methodName = joinPoint.getSignature().getName();
        Map<String, Boolean> actions = ruleResult.getActions();

        // 检查是否需要拦截
        String action = extractAction(methodName);
        if (action != null && actions.containsKey(action)) {
            if (Boolean.FALSE.equals(actions.get(action))) {
                // 返回权限不足的响应
                return buildDeniedResponse(action);
            }
        }

        return joinPoint.proceed();
    }

    /** 从方法名提取操作类型 */
    private String extractAction(String methodName) {
        String lower = methodName.toLowerCase();
        if (lower.startsWith("create") || lower.startsWith("add") || lower.startsWith("save") || lower.startsWith("insert"))
            return "create";
        if (lower.startsWith("update") || lower.startsWith("edit") || lower.startsWith("modify"))
            return "edit";
        if (lower.startsWith("delete") || lower.startsWith("remove"))
            return "delete";
        if (lower.startsWith("export") || lower.startsWith("download"))
            return "export";
        if (lower.startsWith("import") || lower.startsWith("upload"))
            return "import";
        return null;
    }

    /** 构造权限拒绝响应 */
    private Object buildDeniedResponse(String action) {
        try {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("code", 403);
            result.put("message", "操作被禁止: " + action + " 权限不足");
            result.put("data", null);
            return result;
        } catch (Exception e) {
            return Map.of("code", 403, "message", "操作被禁止: " + action + " 权限不足");
        }
    }
}
