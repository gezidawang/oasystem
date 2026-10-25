package com.employee.filter;

import com.employee.security.LoginUser;
import com.employee.service.PermissionCacheService;
import com.employee.service.UserService;
import com.employee.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器：解析 Bearer token，加载权限码，写入 SecurityContext。
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final PermissionCacheService permissionCacheService;
    private final StringRedisTemplate redisTemplate;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, PermissionCacheService permissionCacheService,
                                   StringRedisTemplate redisTemplate) {
        this.jwtUtil = jwtUtil;
        this.permissionCacheService = permissionCacheService;
        this.redisTemplate = redisTemplate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Claims claims = jwtUtil.parse(token);
                Integer userId = Integer.valueOf(claims.getSubject());
                // 停用/离职黑名单实查（8-3 联动 9-5）：账号被停用后旧 token 立即失效（user:off:{id}，TTL=token 有效期）；
                // 与登录锁定同口径：Redis 异常不阻断主流程（fail-open，token 自然到期兜底）
                boolean off = false;
                try {
                    off = Boolean.TRUE.equals(redisTemplate.hasKey(UserService.OFF_KEY_PREFIX + userId));
                } catch (Exception ignored) {
                }
                if (off) {
                    SecurityContextHolder.clearContext();
                    filterChain.doFilter(request, response);
                    return;
                }
                String username = claims.get("username", String.class);
                Integer roleId = claims.get("roleId", Integer.class);
                String roleCode = claims.get("roleCode", String.class);

                List<String> codes = permissionCacheService.getPermCodes(roleId);
                List<GrantedAuthority> authorities = codes.stream()
                        .map(c -> (GrantedAuthority) new SimpleGrantedAuthority(c))
                        .toList();
                LoginUser loginUser = new LoginUser(
                        userId, username, null, roleId, roleCode,
                        authorities,
                        true);

                // 滑动续期（热加载，2026-10-24）：活跃用户 token 剩余不足有效期 1/3 时重发新 token 随响应头下发，
                // 前端拦截器静默替换——正常使用永不踢线；闲置超过有效期不活跃才自然过期（安全窗口不变）。
                // 续期只在临期窗口触发，不会每个请求都重签（约每 10 分钟一次）
                long remainMs = claims.getExpiration().getTime() - System.currentTimeMillis();
                if (remainMs > 0 && remainMs < jwtUtil.getAccessTokenExpiration() / 3) {
                    response.setHeader("X-Renew-Token",
                            jwtUtil.generateToken(userId, username, roleId, roleCode));
                }

                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (Exception e) {
                // token 非法/过期：保持匿名，由后续的认证入口点返回 401
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
