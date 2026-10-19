package com.employee.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * 登录用户主体，作为 SecurityContext 的 principal。
 */
public class LoginUser implements UserDetails {

    private final Integer userId;
    private final String username;
    private final String realName;
    private final Integer roleId;
    private final String roleCode;
    private final Collection<GrantedAuthority> authorities;
    private final boolean enabled;

    public LoginUser(Integer userId, String username, String realName,
                     Integer roleId, String roleCode,
                     Collection<GrantedAuthority> authorities, boolean enabled) {
        this.userId = userId;
        this.username = username;
        this.realName = realName;
        this.roleId = roleId;
        this.roleCode = roleCode;
        this.authorities = authorities == null ? List.of() : authorities;
        this.enabled = enabled;
    }

    public Integer getUserId() {
        return userId;
    }

    public String getRealName() {
        return realName;
    }

    public Integer getRoleId() {
        return roleId;
    }

    public String getRoleCode() {
        return roleCode;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
