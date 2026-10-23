package com.employee.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.employee.dto.RoleDTO;
import com.employee.entity.AuditLog;
import com.employee.entity.Permission;
import com.employee.entity.Role;
import com.employee.entity.User;
import com.employee.exception.BusinessException;
import com.employee.mapper.PermissionMapper;
import com.employee.mapper.RoleMapper;
import com.employee.mapper.RolePermissionMapper;
import com.employee.mapper.UserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 角色权限管理服务（Phase 1, 8-4）。
 * 铁律：role:manage 仅总经理（W4）——人事可管账号但不能改角色权限矩阵，防止"自己给自己提权"。
 * 权限矩阵保存 = 整体事务 + 前后快照审计 + 角色权限缓存失效（PermissionCacheService.evictAll）。
 */
@Service
public class RoleService {

    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final UserMapper userMapper;
    private final AuditLogService auditLogService;
    private final PermissionCacheService permissionCacheService;
    private final ObjectMapper objectMapper;

    public RoleService(RoleMapper roleMapper, PermissionMapper permissionMapper,
                       RolePermissionMapper rolePermissionMapper, UserMapper userMapper,
                       AuditLogService auditLogService, PermissionCacheService permissionCacheService,
                       ObjectMapper objectMapper) {
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.userMapper = userMapper;
        this.auditLogService = auditLogService;
        this.permissionCacheService = permissionCacheService;
        this.objectMapper = objectMapper;
    }

    // ==================== 角色列表 ====================

    public List<Map<String, Object>> list() {
        List<Role> roles = roleMapper.selectList(new QueryWrapper<Role>().orderByAsc("id"));
        return roles.stream().map(r -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", r.getId());
            row.put("roleCode", r.getRoleCode());
            row.put("roleName", r.getRoleName());
            row.put("description", r.getDescription());
            row.put("isSystem", r.getIsSystem());
            row.put("userCount", userMapper.selectCount(new QueryWrapper<User>().eq("role_id", r.getId())));
            row.put("permCount", rolePermissionMapper.selectPermissionIdsByRoleId(r.getId()).size());
            return row;
        }).toList();
    }

    // ==================== 新增角色 ====================

    public void create(RoleDTO dto) {
        if (dto.getRoleCode() == null || !dto.getRoleCode().matches("^[a-z][a-z0-9_]{1,29}$")) {
            throw new BusinessException(400, "角色编码仅允许小写字母开头的字母/数字/下划线（2-30位）");
        }
        if (roleMapper.selectCount(new QueryWrapper<Role>().eq("role_code", dto.getRoleCode())) > 0) {
            throw new BusinessException(400, "角色编码「" + dto.getRoleCode() + "」已存在");
        }
        Role role = new Role();
        role.setRoleCode(dto.getRoleCode());
        role.setRoleName(dto.getRoleName());
        role.setDescription(dto.getDescription());
        role.setIsSystem(0);
        roleMapper.insert(role);
    }

    // ==================== 修改角色（roleCode 不可变） ====================

    public void update(Integer id, RoleDTO dto) {
        Role role = getOr404(id);
        role.setRoleName(dto.getRoleName());
        role.setDescription(dto.getDescription());
        roleMapper.updateById(role);
    }

    // ==================== 删除角色 ====================

    public void delete(Integer id) {
        Role role = getOr404(id);
        if (role.getIsSystem() != null && role.getIsSystem() == 1) {
            throw new BusinessException(400, "系统内置角色不可删除");
        }
        Long users = userMapper.selectCount(new QueryWrapper<User>().eq("role_id", id));
        if (users > 0) {
            throw new BusinessException(400, "该角色下还有 " + users + " 个账号，无法删除");
        }
        rolePermissionMapper.deleteByRoleId(id);
        roleMapper.deleteById(id);
        permissionCacheService.evictAll();
    }

    // ==================== 权限清单（矩阵数据源） ====================

    public List<Map<String, Object>> listPermissions() {
        List<Permission> perms = permissionMapper.selectList(
                new QueryWrapper<Permission>().orderByAsc("resource", "id"));
        return perms.stream().map(p -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", p.getId());
            row.put("permCode", p.getPermCode());
            row.put("permName", p.getPermName());
            row.put("resource", p.getResource());
            return row;
        }).toList();
    }

    public List<Integer> getRolePermissionIds(Integer roleId) {
        getOr404(roleId);
        return rolePermissionMapper.selectPermissionIdsByRoleId(roleId);
    }

    // ==================== 权限矩阵保存（核心敏感操作） ====================

    @Transactional
    public void saveRolePermissions(Integer roleId, List<Integer> permIds) {
        Role role = getOr404(roleId);
        List<Integer> before = rolePermissionMapper.selectPermissionIdsByRoleId(roleId);

        // 自锁保护：super_admin 必须始终持有 role:manage，否则全公司再无人能管理角色
        if ("super_admin".equals(role.getRoleCode())) {
            Permission rm = permissionMapper.selectOne(new QueryWrapper<Permission>().eq("perm_code", "role:manage"));
            if (rm != null && !permIds.contains(rm.getId())) {
                throw new BusinessException(400, "不允许移除超级管理员的 role:manage 权限（防止角色管理体系自锁）");
            }
        }

        // 先删后插（整体事务）
        rolePermissionMapper.deleteByRoleId(roleId);
        if (permIds != null) {
            for (Integer pid : permIds) {
                rolePermissionMapper.insertOne(roleId, pid);
            }
        }

        // 审计：改前/改后权限集合快照
        AuditLog audit = new AuditLog();
        audit.setAction("UPDATE");
        audit.setResource("role-permission");
        audit.setDescription("角色权限矩阵变更：" + role.getRoleCode());
        audit.setOldValue(toJson(before));
        audit.setNewValue(toJson(permIds));
        audit.setResult("success");
        auditLogService.record(audit);

        // 缓存失效：下一次请求即拿到新权限（10min TTL 兜底）
        permissionCacheService.evictAll();
    }

    // ==================== 私有 ====================

    private Role getOr404(Integer id) {
        Role role = roleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException(404, "角色不存在");
        }
        return role;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "[]";
        }
    }
}
