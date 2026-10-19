package com.employee.service;

import com.employee.entity.AuditLog;
import com.employee.mapper.AuditLogMapper;
import com.employee.security.LoginUser;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

/**
 * 审计日志写入服务。
 * - record 以独立事务（REQUIRES_NEW）提交，保证不因业务回滚而丢审计；
 * - 全程 try/catch 吞异常，绝不影响主业务；
 * - 自动补全操作人（SecurityContext）、IP、UA、时间。
 * 注意：sys_audit_logs.user_id NOT NULL —— 匿名场景（如登录失败）不应调用本方法。
 */
@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditLogMapper auditLogMapper;

    public AuditLogService(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditLog entry) {
        try {
            if (entry.getUserId() == null) {
                entry.setUserId(currentUserId());
            }
            if (entry.getIpAddress() == null) {
                entry.setIpAddress(currentIp());
            }
            if (entry.getUserAgent() == null) {
                entry.setUserAgent(currentUserAgent());
            }
            if (entry.getCreatedAt() == null) {
                entry.setCreatedAt(LocalDateTime.now());
            }
            auditLogMapper.insert(entry);
        } catch (Exception e) {
            // 审计失败绝不影响业务
            log.warn("写入审计日志失败：{}", e.getMessage());
        }
    }

    private Integer currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser loginUser) {
            return loginUser.getUserId();
        }
        return null;
    }

    private HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        return attrs instanceof ServletRequestAttributes sra ? sra.getRequest() : null;
    }

    private String currentIp() {
        HttpServletRequest req = currentRequest();
        if (req == null) {
            return null;
        }
        String ip = req.getHeader("X-Forwarded-For");
        if (!StringUtils.hasText(ip)) {
            ip = req.getHeader("X-Real-IP");
        }
        if (!StringUtils.hasText(ip)) {
            ip = req.getRemoteAddr();
        }
        if (StringUtils.hasText(ip) && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    private String currentUserAgent() {
        HttpServletRequest req = currentRequest();
        if (req == null) {
            return null;
        }
        String ua = req.getHeader("User-Agent");
        return ua != null && ua.length() > 500 ? ua.substring(0, 500) : ua;
    }
}
