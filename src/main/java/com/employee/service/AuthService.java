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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

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

    private static final int MAX_FAIL = 5;
    private static final long FAIL_WINDOW_MIN = 10;
    private static final long LOCK_MIN = 30;

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

        // 1. 账号锁定校验（Redis 不可用时跳过，不阻断登录）
        if (isLocked(username)) {
            throw new BusinessException(423, "密码错误次数过多，账号已锁定30分钟");
        }

        // 2. 查询用户
        User user = userMapper.selectByUsername(username);
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            // 为避免账号枚举，统一提示；仍计入失败次数
            registerFail(username);
            throw new BusinessException(401, "用户名或密码错误");
        }

        // 3. 校验密码
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            registerFail(username);
            throw new BusinessException(401, "用户名或密码错误");
        }

        // 4. 登录成功：清除失败计数，签发 token
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

    // ---------- Redis 失败锁定（全部容错，Redis 异常时不阻断主流程）----------

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
            // Redis 不可用：跳过限流
        }
    }

    private void clearFail(String username) {
        try {
            redisTemplate.delete("login:fail:" + username);
        } catch (Exception ignored) {
        }
    }
}
