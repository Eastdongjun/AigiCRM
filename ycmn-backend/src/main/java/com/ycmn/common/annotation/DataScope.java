package com.ycmn.common.annotation;

import java.lang.annotation.*;

/**
 * 数据权限过滤注解 — 参考若依 @DataScope
 * 标注在 Mapper 方法上，AOP 自动注入 WHERE 条件
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataScope {

    /** 组织字段在 SQL 中的别名，如 "o" 表示 o.dept_id */
    String orgAlias() default "";

    /** 创建人字段在 SQL 中的别名，如 "u" 表示 u.user_id */
    String userAlias() default "";
}
