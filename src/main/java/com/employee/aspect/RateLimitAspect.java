package com.employee.aspect;

import com.employee.annotation.RateLimit;
import com.employee.exception.BusinessException;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * 接口限流切面：对标注 {@link RateLimit} 的方法按指定 Resilience4j 限流器放行/拒绝。
 * 超限返回 429 统一 Result（经 GlobalExceptionHandler 包装，不变 500）。
 */
@Aspect
@Component
public class RateLimitAspect {

    private final RateLimiterRegistry rateLimiterRegistry;

    public RateLimitAspect(RateLimiterRegistry rateLimiterRegistry) {
        this.rateLimiterRegistry = rateLimiterRegistry;
    }

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        RateLimiter limiter = rateLimiterRegistry.rateLimiter(rateLimit.value());
        if (!limiter.acquirePermission()) {
            throw new BusinessException(429, "请求过于频繁，请稍后再试");
        }
        return joinPoint.proceed();
    }
}
