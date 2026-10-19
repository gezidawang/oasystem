package com.employee.service;

import com.employee.mapper.PermissionMapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 权限码缓存服务（Phase 0-8 P0）。
 * JwtAuthenticationFilter 每请求都要拿角色权限码，直查 DB 是最大热点；此处按 roleId 缓存。
 * 角色权限变更（区域八 8-4 保存角色权限矩阵）时调用 {@link #evictAll()} 即时失效，10 分钟 TTL 兜底。
 * 权限按 roleId 维度缓存，天然不含用户私有数据，无越权风险。
 */
@Service
public class PermissionCacheService {

    private final PermissionMapper permissionMapper;

    public PermissionCacheService(PermissionMapper permissionMapper) {
        this.permissionMapper = permissionMapper;
    }

    @Cacheable(value = "perm:role", key = "#roleId")
    public List<String> getPermCodes(Integer roleId) {
        return permissionMapper.selectPermCodesByRoleId(roleId);
    }

    /** 清空全部角色权限缓存（角色权限矩阵变更后调用） */
    @CacheEvict(value = "perm:role", allEntries = true)
    public void evictAll() {
        // 仅用于触发缓存失效
    }
}
