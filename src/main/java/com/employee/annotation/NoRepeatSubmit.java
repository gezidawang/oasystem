package com.employee.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 幂等提交：同一用户 + 同一接口 + 同一参数，在时间窗内只允许一次，
 * 防止双击/重放产生重复记录。由 NoRepeatSubmitAspect 基于 Redis SETNX 实现。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface NoRepeatSubmit {

    /** 重复提交提示语 */
    String message() default "请勿重复提交";

    /** 去重时间窗（秒），窗内相同请求视为重复 */
    int intervalSeconds() default 5;
}
