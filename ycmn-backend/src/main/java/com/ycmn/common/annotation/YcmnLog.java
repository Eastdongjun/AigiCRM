package com.ycmn.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志声明注解：挂在 Controller 写方法上, 覆盖 LogAspect 的 URL 推断值。
 * 未声明时 LogAspect 按 URL/HTTP method 推断(兼容存量行为)。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface YcmnLog {

    /** 模块名, 如 "人员"/"客户"/"审批"; 空 = 用 URL 推断 */
    String module() default "";

    /** 动作名, 如 "重置密码"; 空 = 用 URL/HTTP method 推断 */
    String action() default "";

    /** 目标类型, 如 "user"/"customer"; 写入 sys_log.target_type */
    String targetType() default "";
}
