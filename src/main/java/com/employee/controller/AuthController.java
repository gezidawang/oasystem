package com.employee.controller;

import com.employee.annotation.AuditLog;
import com.employee.common.Result;
import com.employee.dto.LoginRequest;
import com.employee.security.LoginUser;
import com.employee.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 认证接口：/api/auth
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** 登录 */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        return Result.success("登录成功", authService.login(request));
    }

    /** 获取当前登录用户信息与权限 */
    @GetMapping("/me")
    public Result<Map<String, Object>> me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        LoginUser loginUser = (LoginUser) auth.getPrincipal();
        return Result.success(authService.getMe(loginUser.getUserId()));
    }

    /** 登出（无状态 JWT，由前端清除 token；此处返回成功） */
    @PostMapping("/logout")
    public Result<Void> logout() {
        return Result.success("已退出登录", null);
    }

    /** 管理员解锁被锁定的登录账号（传统企业“帮助台解锁”，需 user:manage） */
    @PutMapping("/unlock/{username}")
    @PreAuthorize("hasAuthority('user:manage')")
    @AuditLog(action = "UPDATE", resource = "auth", description = "解锁登录账号")
    public Result<Void> unlock(@PathVariable String username) {
        authService.unlockAccount(username);
        return Result.success("已解除 " + username + " 的登录锁定", null);
    }
}
