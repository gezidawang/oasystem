package com.employee.service;

import com.employee.dto.LoginRequest;
import com.employee.entity.AuditLog;
import com.employee.entity.Role;
import com.employee.entity.User;
import com.employee.exception.BusinessException;
import com.employee.mapper.PermissionMapper;
import com.employee.mapper.RoleMapper;
import com.employee.mapper.UserMapper;
import com.employee.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 认证服务：登录校验、失败锁定、签发 JWT、返回用户与权限。
 */
@Service
public class AuthService {

    // 传统企业（AD域）模式：账号错5次/10分 → 锁账号30分，误锁由管理员解锁（user:manage）；
    // 叠加 IP 锁（阈值放宽到20，挡快速爆破同时降低 NAT 出口误伤）作为第二层。
    private static final int MAX_FAIL = 5;
    private static final long FAIL_WINDOW_MIN = 10;
    private static final long LOCK_MIN = 30;
    private static final int IP_FAIL_MAX = 20;
    private static final long IP_FAIL_WINDOW_MIN = 10;
    private static final long IP_LOCK_MIN = 30;
    // 每IP每分钟登录请求上限（含成功），防高频洪水
    private static final int IP_REQ_MAX_PER_MIN = 30;

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;
    private final AuditLogService auditLogService;
    private final PermissionCacheService permissionCacheService;

    public AuthService(UserMapper userMapper, RoleMapper roleMapper, PermissionMapper permissionMapper,
                       PasswordEncoder passwordEncoder, JwtUtil jwtUtil, StringRedisTemplate redisTemplate,
                       AuditLogService auditLogService, PermissionCacheService permissionCacheService) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.redisTemplate = redisTemplate;
        this.auditLogService = auditLogService;
        this.permissionCacheService = permissionCacheService;
    }

    public Map<String, Object> login(LoginRequest request) {
        String username = request.getUsername();

        // 0. IP 锁：该 IP 失败次数超限后，登录任何账号都被拒（换号无效）
        String ip = clientIp();
        if (isIpLocked(ip)) {
            throw new BusinessException(423, "该IP登录失败次数过多，已被限制" + IP_LOCK_MIN + "分钟，期间无法登录任何账号");
        }
        if (countIpRequest(ip) > IP_REQ_MAX_PER_MIN) {
            throw new BusinessException(429, "登录请求过于频繁，请稍后再试");
        }

        // 1. 账号锁定校验（传统 AD：错5次锁30分，不分来源IP；误锁走管理员解锁）
        if (isLocked(username)) {
            throw new BusinessException(423, "密码错误次数过多，账号已锁定" + LOCK_MIN + "分钟，请联系管理员解锁");
        }

        // 2. 查询用户
        User user = userMapper.selectByUsername(username);
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            // 为避免账号枚举，统一提示；失败同时计入账号与 IP 两维度
            registerFail(username);
            registerIpFail(ip);
            throw new BusinessException(401, "用户名或密码错误");
        }

        // 3. 校验密码
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            registerFail(username);
            registerIpFail(ip);
            throw new BusinessException(401, "用户名或密码错误");
        }

        // 4. 登录成功：清除该账号失败计数（AD 同款语义），签发 token
        clearFail(username);
        Role role = roleMapper.selectById(user.getRoleId());
        String roleCode = role == null ? "" : role.getRoleCode();
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRoleId(), roleCode);

        // 更新最后登录时间（仅更新需要的字段）
        User update = new User();
        update.setId(user.getId());
        update.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(update);

        List<String> permissions = permissionCacheService.getPermCodes(user.getRoleId());

        // 登录成功审计：登录时 SecurityContext 尚未建立，显式设置 userId
        AuditLog audit = new AuditLog();
        audit.setUserId(user.getId());
        audit.setAction("CREATE");
        audit.setResource("auth");
        audit.setDescription("登录成功：" + user.getUsername());
        audit.setNewValue("{\"username\":\"" + user.getUsername() + "\"}");
        auditLogService.record(audit);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("token", token);
        result.put("user", buildUserView(user, roleCode));
        result.put("permissions", permissions);
        return result;
    }

    /** 返回当前登录用户信息 + 权限 */
    public Map<String, Object> getMe(Integer userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        Role role = roleMapper.selectById(user.getRoleId());
        String roleCode = role == null ? "" : role.getRoleCode();
        List<String> permissions = permissionCacheService.getPermCodes(user.getRoleId());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("user", buildUserView(user, roleCode));
        result.put("permissions", permissions);
        return result;
    }

    private Map<String, Object> buildUserView(User user, String roleCode) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", user.getId());
        view.put("username", user.getUsername());
        view.put("realName", user.getRealName());
        view.put("employeeNo", user.getEmployeeNo());
        view.put("roleId", user.getRoleId());
        view.put("roleCode", roleCode);
        view.put("departmentId", user.getDepartmentId());
        return view;
    }

    // ---------- 失败锁定：账号级 + IP 级双层（全部容错，Redis 异常时不阻断主流程）----------

    private boolean isLocked(String username) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey("login:lock:" + username));
        } catch (Exception e) {
            return false;
        }
    }

    private void registerFail(String username) {
        try {
            String failKey = "login:fail:" + username;
            Long fails = redisTemplate.opsForValue().increment(failKey);
            if (fails != null) {
                redisTemplate.expire(failKey, FAIL_WINDOW_MIN, TimeUnit.MINUTES);
                if (fails >= MAX_FAIL) {
                    redisTemplate.opsForValue().set("login:lock:" + username, "1", LOCK_MIN, TimeUnit.MINUTES);
                    redisTemplate.delete(failKey);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void clearFail(String username) {
        try {
            redisTemplate.delete("login:fail:" + username);
        } catch (Exception ignored) {
        }
    }

    /** 管理员解锁：清除账号锁与失败计数（传统企业的“帮助台解锁”） */
    public void unlockAccount(String username) {
        try {
            redisTemplate.delete("login:lock:" + username);
            redisTemplate.delete("login:fail:" + username);
        } catch (Exception e) {
            throw new BusinessException(500, "解锁失败：Redis 不可用");
        }
    }

    /** 解析客户端真实 IP：X-Forwarded-For 首选（兼容反向代理），取第一跳 */
    private String clientIp() {
        try {
            ServletRequestAttributes attr =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attr == null) {
                return "unknown";
            }
            HttpServletRequest req = attr.getRequest();
            String xff = req.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                return xff.split(",")[0].trim();
            }
            String real = req.getHeader("X-Real-IP");
            if (real != null && !real.isBlank()) {
                return real.trim();
            }
            return req.getRemoteAddr();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private boolean isIpLocked(String ip) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey("login:iplock:" + ip));
        } catch (Exception e) {
            return false;
        }
    }

    /** 每 IP 每分钟登录请求计数（含成功），超限拒新请求 */
    private long countIpRequest(String ip) {
        try {
            String key = "login:ipreq:" + ip;
            Long n = redisTemplate.opsForValue().increment(key);
            if (n != null && n == 1L) {
                redisTemplate.expire(key, 60, TimeUnit.SECONDS);
            }
            return n == null ? 0 : n;
        } catch (Exception e) {
            return 0;
        }
    }

    /** 每 IP 失败计数（不区分账号）：10 分钟内累计 20 次失败 → 锁 IP 30 分钟 */
    private void registerIpFail(String ip) {
        try {
            String failKey = "login:ipfail:" + ip;
            Long fails = redisTemplate.opsForValue().increment(failKey);
            if (fails != null) {
                redisTemplate.expire(failKey, IP_FAIL_WINDOW_MIN, TimeUnit.MINUTES);
                if (fails >= IP_FAIL_MAX) {
                    redisTemplate.opsForValue()
                            .set("login:iplock:" + ip, "1", IP_LOCK_MIN, TimeUnit.MINUTES);
                    redisTemplate.delete(failKey);
                }
            }
        } catch (Exception ignored) {
            // Redis 不可用：跳过限流
        }
    }
}
