package com.employee.aspect;

import com.employee.annotation.NoRepeatSubmit;
import com.employee.exception.BusinessException;
import com.employee.security.LoginUser;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * 幂等提交切面：基于 Redis SETNX，在时间窗内对「同一用户 + 同一方法 + 同一参数」去重。
 * Redis 不可用时 fail-open（放行），不阻断业务。
 */
@Aspect
@Component
public class NoRepeatSubmitAspect {

    private final StringRedisTemplate redisTemplate;

    public NoRepeatSubmitAspect(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Around("@annotation(noRepeatSubmit)")
    public Object around(ProceedingJoinPoint joinPoint, NoRepeatSubmit noRepeatSubmit) throws Throwable {
        String key = buildKey(joinPoint);
        boolean first = true;
        try {
            Boolean ok = redisTemplate.opsForValue()
                    .setIfAbsent(key, "1", noRepeatSubmit.intervalSeconds(), TimeUnit.SECONDS);
            first = Boolean.TRUE.equals(ok);
        } catch (Exception e) {
            // Redis 异常：放行，不因限流组件影响主流程
            first = true;
        }
        if (!first) {
            throw new BusinessException(429, noRepeatSubmit.message());
        }
        return joinPoint.proceed();
    }

    private String buildKey(ProceedingJoinPoint joinPoint) {
        MethodSignature ms = (MethodSignature) joinPoint.getSignature();
        String method = ms.getDeclaringTypeName() + "." + ms.getName();
        String argsHash = DigestUtils.md5DigestAsHex(
                argToString(joinPoint.getArgs()).getBytes(StandardCharsets.UTF_8));
        return "noreq:" + currentUserId() + ":" + method + ":" + argsHash;
    }

    private String argToString(Object[] args) {
        if (args == null || args.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Object arg : args) {
            if (arg == null) {
                sb.append("null;");
                continue;
            }
            String cn = arg.getClass().getName();
            if (cn.startsWith("jakarta.servlet") || cn.startsWith("javax.servlet")
                    || cn.startsWith("org.springframework.web")) {
                sb.append("<skip>;");
                continue;
            }
            sb.append(arg).append(';');
        }
        return sb.toString();
    }

    private String currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser loginUser) {
            return String.valueOf(loginUser.getUserId());
        }
        return "anonymous";
    }
}
