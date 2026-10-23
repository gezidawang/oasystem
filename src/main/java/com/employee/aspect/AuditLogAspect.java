package com.employee.aspect;

import com.employee.annotation.AuditLog;
import com.employee.service.AuditLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 审计日志切面：方法成功返回后记 success，方法抛异常后也记一条 fail（含越权被拒），
 * 所有操作留档可追溯。
 * new_value 存"方法 + 入参摘要"的 JSON；password/phone 等敏感字段脱敏；
 * 跳过 Servlet/文件/绑定结果等不可序列化参数；审计自身异常吞掉，绝不影响业务。
 */
@Aspect
@Component
public class AuditLogAspect {

    private static final int MAX_JSON = 4000;

    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public AuditLogAspect(AuditLogService auditLogService, ObjectMapper objectMapper) {
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    @AfterReturning(pointcut = "@annotation(auditLog)", returning = "result")
    public void afterReturning(JoinPoint joinPoint, AuditLog auditLog, Object result) {
        try {
            MethodSignature ms = (MethodSignature) joinPoint.getSignature();
            com.employee.entity.AuditLog entry = new com.employee.entity.AuditLog();
            entry.setAction(auditLog.action());
            entry.setResource(auditLog.resource());
            entry.setDescription(auditLog.description().isEmpty() ? ms.getName() : auditLog.description());
            entry.setNewValue(buildDetailJson(ms.getName(), joinPoint.getArgs()));
            entry.setResult("success");
            auditLogService.record(entry);
        } catch (Exception ignored) {
            // 审计异常不影响业务
        }
    }

    /** 失败留档：带 @AuditLog 的方法抛异常（如越权 403、校验不通过）时也记一条 fail */
    @AfterThrowing(pointcut = "@annotation(auditLog)", throwing = "ex")
    public void afterThrowing(JoinPoint joinPoint, AuditLog auditLog, Exception ex) {
        try {
            MethodSignature ms = (MethodSignature) joinPoint.getSignature();
            com.employee.entity.AuditLog entry = new com.employee.entity.AuditLog();
            entry.setAction(auditLog.action());
            entry.setResource(auditLog.resource());
            entry.setDescription((auditLog.description().isEmpty() ? ms.getName() : auditLog.description())
                    + " [失败: " + truncate(ex.getMessage()) + "]");
            entry.setNewValue(buildDetailJson(ms.getName(), joinPoint.getArgs()));
            entry.setResult("fail");
            auditLogService.record(entry);
        } catch (Exception ignored) {
            // 审计异常不影响业务
        }
    }

    private String truncate(String msg) {
        if (msg == null) return "unknown";
        return msg.length() > 200 ? msg.substring(0, 200) : msg;
    }

    private String buildDetailJson(String method, Object[] args) {
        List<Object> safeArgs = new ArrayList<>();
        if (args != null) {
            for (Object arg : args) {
                if (isUnserializable(arg)) {
                    safeArgs.add(arg == null ? null : "<" + arg.getClass().getSimpleName() + ">");
                    continue;
                }
                try {
                    Object jsonNode = objectMapper.convertValue(arg, Object.class);
                    safeArgs.add(jsonNode);
                } catch (Exception e) {
                    safeArgs.add("<" + (arg == null ? "null" : arg.getClass().getSimpleName()) + ">");
                }
            }
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("method", method);
        detail.put("args", safeArgs);
        String json;
        try {
            json = objectMapper.writeValueAsString(detail);
        } catch (Exception e) {
            json = "{\"method\":\"" + method + "\",\"argsError\":true}";
        }
        json = redact(json);
        if (json.length() > MAX_JSON) {
            json = json.substring(0, MAX_JSON - 20) + "...\"truncated\":true}";
            // 保证仍是合法 JSON
            json = "{\"truncated\":true}";
        }
        return json;
    }

    private boolean isUnserializable(Object arg) {
        if (arg == null) {
            return false;
        }
        String cn = arg.getClass().getName();
        return cn.startsWith("jakarta.servlet") || cn.startsWith("javax.servlet")
                || arg instanceof MultipartFile || cn.contains("BindingResult")
                || cn.startsWith("java.util.Locale") || cn.startsWith("org.springframework.web");
    }

    /** 敏感字段脱敏：password / phone / idCard */
    private String redact(String json) {
        return json
                .replaceAll("(?i)\"(password|pwd)\"\\s*:\\s*\"[^\"]*\"", "\"$1\":\"******\"")
                .replaceAll("(?i)\"(phone|mobile|idCard|id_card)\"\\s*:\\s*\"([^\"]{0,3})[^\"]*\"", "\"$1\":\"$2****\"");
    }
}
