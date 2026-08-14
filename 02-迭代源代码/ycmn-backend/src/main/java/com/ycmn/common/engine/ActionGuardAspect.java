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
 * 根据规则引擎按 target_entity 分组的 actions（如 {"create":false,"delete":false}），
 * 拦截对应实体 Controller 的方法，拒绝无权限的操作。未配置守卫规则的实体不受影响。
 *
 * 用法：Controller 类名推断实体（CustomerController → customer），方法名匹配 action
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
        if (ruleResult == null || ruleResult.getActionsByEntity().isEmpty()) {
            return joinPoint.proceed();
        }

        // 按当前 Controller 推断 target_entity，未配置该实体的守卫规则则放行
        String entity = resolveEntity(joinPoint.getSignature().getDeclaringType().getSimpleName());
        if (entity.isEmpty()) {
            return joinPoint.proceed();
        }
        Map<String, Boolean> actions = ruleResult.getActionsByEntity().get(entity);
        if (actions == null || actions.isEmpty()) {
            return joinPoint.proceed();
        }

        String methodName = joinPoint.getSignature().getName();

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

    /** 从 Controller 类名推断 target_entity：CustomerController → customer */
    private String resolveEntity(String className) {
        if (className == null || className.isBlank()) return "";
        String name = className.endsWith("Controller")
                ? className.substring(0, className.length() - "Controller".length())
                : className;
        if (name.isEmpty()) return "";
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
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

    /** 构造权限拒绝响应（HTTP 403，与 GlobalExceptionHandler 语义一致） */
    private Object buildDeniedResponse(String action) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 403);
        result.put("message", "操作被禁止: " + action + " 权限不足");
        result.put("data", null);
        return org.springframework.http.ResponseEntity.status(403).body(result);
    }
}
