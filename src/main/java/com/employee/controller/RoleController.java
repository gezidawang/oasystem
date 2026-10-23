package com.employee.controller;

import com.employee.annotation.RequireWeight;
import com.employee.common.Result;
import com.employee.dto.RoleDTO;
import com.employee.service.RoleService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 角色权限管理接口（Phase 1, 8-4）。
 * 铁律：全部要求 role:manage + W4（@RequireWeight(99)）——仅总经理可操作系统角色与权限矩阵。
 * 敏感写操作的前后快照审计在 Service 内完成（矩阵变更需记录改前/改后集合）。
 */
@RestController
@RequestMapping("/api/roles")
@PreAuthorize("hasAuthority('role:manage')")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    /** 角色列表（含账号数/权限数） */
    @GetMapping
    public Result<List<Map<String, Object>>> list() {
        return Result.success(roleService.list());
    }

    /** 新增角色 */
    @PostMapping
    @RequireWeight(99)
    public Result<Void> create(@Valid @RequestBody RoleDTO dto) {
        roleService.create(dto);
        return Result.success("角色创建成功", null);
    }

    /** 修改角色（名称/描述） */
    @PutMapping("/{id}")
    @RequireWeight(99)
    public Result<Void> update(@PathVariable Integer id, @Valid @RequestBody RoleDTO dto) {
        roleService.update(id, dto);
        return Result.success("角色更新成功", null);
    }

    /** 删除角色（内置角色与有账号的角色禁删） */
    @DeleteMapping("/{id}")
    @RequireWeight(99)
    public Result<Void> delete(@PathVariable Integer id) {
        roleService.delete(id);
        return Result.success("角色已删除", null);
    }

    /** 全部权限码清单（矩阵数据源） */
    @GetMapping("/permissions")
    public Result<List<Map<String, Object>>> permissions() {
        return Result.success(roleService.listPermissions());
    }

    /** 某角色已有权限 ID 集 */
    @GetMapping("/{id}/permissions")
    public Result<List<Integer>> rolePermissions(@PathVariable Integer id) {
        return Result.success(roleService.getRolePermissionIds(id));
    }

    /** 保存角色权限矩阵（先删后插+前后审计+缓存失效，立即生效） */
    @PutMapping("/{id}/permissions")
    @RequireWeight(99)
    public Result<Void> saveRolePermissions(@PathVariable Integer id, @RequestBody List<Integer> permIds) {
        roleService.saveRolePermissions(id, permIds);
        return Result.success("权限矩阵已保存并即时生效", null);
    }
}
