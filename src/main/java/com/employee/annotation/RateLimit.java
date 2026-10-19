package com.employee.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流：绑定一个 Resilience4j RateLimiter 实例（在 application.yml 配置）。
 * 由 RateLimitAspect 执行，超限返回 429 统一 Result。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** Resilience4j 限流器实例名，如 login-limiter / api-limiter */
    String value() default "api-limiter";
}
