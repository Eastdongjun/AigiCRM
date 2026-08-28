package com.ycmn.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作守卫实体声明注解
 *
 * 【需求】规则引擎操作控制（ActionGuardAspect）的实体识别：显式声明 Controller 守卫的
 *         target_entity，替代"类名推断"（CustomerController → customer），防止新模块命名
 *         与实体名不一致导致守卫漏拦截
 * 【方法】标在 Controller 类或方法上，Aspect 优先读注解 value，未标注才退回类名推断
 * 【业务规则】value 必须与 sys_permission_rule.target_entity 对齐（如 customer）；
 *             类级注解整类生效，方法级注解覆盖类级
 * 【参数/返回】value 实体名；无
 * 【注意】注解仅声明不拦截，实际守卫由 ActionGuardAspect + RuleResult 生效
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface GuardEntity {

    /** target_entity 实体名（如 customer），空串=未声明（走类名推断兜底） */
    String value() default "";
}
