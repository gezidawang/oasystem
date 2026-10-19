package com.employee.interceptor;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * 全局接口限流拦截器：对 /api/** 应用 api-limiter（Resilience4j，配置见 application.yml）。
 * 超限返回 429 统一 Result，不抛 500。
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiterRegistry rateLimiterRegistry;

    public RateLimitInterceptor(RateLimiterRegistry rateLimiterRegistry) {
        this.rateLimiterRegistry = rateLimiterRegistry;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        RateLimiter limiter = rateLimiterRegistry.rateLimiter("api-limiter");
        if (limiter.acquirePermission()) {
            return true;
        }
        response.setStatus(429);
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"success\":false,\"code\":429,\"message\":\"请求过于频繁，请稍后再试\",\"data\":null}");
        return false;
    }
}
