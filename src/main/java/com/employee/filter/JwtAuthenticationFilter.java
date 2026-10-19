package com.employee.filter;

import com.employee.security.LoginUser;
import com.employee.service.PermissionCacheService;
import com.employee.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

    public JwtAuthenticationFilter(JwtUtil jwtUtil, PermissionCacheService permissionCacheService) {
        this.jwtUtil = jwtUtil;
        this.permissionCacheService = permissionCacheService;
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
