package com.ycmn.common.engine;

import com.ycmn.common.annotation.GuardEntity;
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

        // 【2026-08-27 规则引擎接通】实体识别：优先 @GuardEntity 显式注解（防类名推断漂移），
        // 未标注时退回类名推断（CustomerController → customer）兼容存量 Controller。
        String entity = resolveEntity(joinPoint);
        if (entity.isEmpty()) {
            return joinPoint.proceed();
        }
        Map<String, Boolean> actions = ruleResult.getActionsByEntity().get(entity);
        if (actions == null || actions.isEmpty()) {
            return joinPoint.proceed();
        }

        String methodName = joinPoint.getSignature().getName();

        // 检查是否需要拦截（合并嵌套 if，SonarLint S1066：动作存在且显式禁止时拒绝）
        String action = extractAction(methodName);
        if (action != null && actions.containsKey(action) && Boolean.FALSE.equals(actions.get(action))) {
            return buildDeniedResponse(action);
        }

        return joinPoint.proceed();
    }

    /**
     * 实体识别：优先 @GuardEntity 显式注解（类或方法级），未标注退回类名推断。
     * 【需求】新模块接入守卫时用注解显式声明实体，不依赖 Controller 命名巧合
     * 【方法】先查类级注解（含父类），再查方法级；都无则类名推断
     * 【参数】joinPoint 切点（含签名与目标对象）
     * 【返回】target_entity 字符串（如 customer）；无法识别返回空串（放行）
     */
    private String resolveEntity(ProceedingJoinPoint joinPoint) {
        Object target = joinPoint.getTarget();
        Class<?> clazz = target != null ? target.getClass() : null;
        // 类级注解（含代理类的原始类型）
        if (clazz != null) {
            GuardEntity classAnn = clazz.getAnnotation(GuardEntity.class);
            if (classAnn == null) {
                Class<?> superClass = clazz.getSuperclass();
                if (superClass != null && superClass != Object.class) {
                    classAnn = superClass.getAnnotation(GuardEntity.class);
                }
            }
            if (classAnn != null && !classAnn.value().isBlank()) {
                return classAnn.value();
            }
        }
        // 方法级注解（AspectJ MethodSignature 转 java.lang.reflect.Method 后取注解）
        java.lang.reflect.Method method = null;
        if (joinPoint.getSignature() instanceof org.aspectj.lang.reflect.MethodSignature ms) {
            method = ms.getMethod();
        }
        GuardEntity methodAnn = method != null ? method.getAnnotation(GuardEntity.class) : null;
        if (methodAnn != null && !methodAnn.value().isBlank()) {
            return methodAnn.value();
        }
        // 兜底：类名推断
        String className = joinPoint.getSignature().getDeclaringType().getSimpleName();
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
