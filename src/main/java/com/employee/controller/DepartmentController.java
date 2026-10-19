package com.employee.controller;

import com.employee.annotation.AuditLog;
import com.employee.annotation.RequireWeight;
import com.employee.common.Result;
import com.employee.dto.DepartmentDTO;
import com.employee.dto.DepartmentTreeVO;
import com.employee.service.DepartmentService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 部门管理接口（Phase 1, 8-1）。
 * 读：登录即可（通讯录用）；写：org:manage + W3 门槛。
 */
@RestController
@RequestMapping("/api/departments")
public class DepartmentController {

    private final DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    /** 部门树（含停用，管理视角） */
    @GetMapping("/tree")
    @PreAuthorize("isAuthenticated()")
    public Result<List<DepartmentTreeVO>> tree() {
        return Result.success(departmentService.getTree());
    }

    /** 新增部门 */
    @PostMapping
    @PreAuthorize("hasAuthority('org:manage')")
    @RequireWeight(3)
    @AuditLog(action = "CREATE", resource = "department", description = "新增部门")
    public Result<Void> create(@Valid @RequestBody DepartmentDTO dto) {
        departmentService.create(dto);
        return Result.success("部门创建成功", null);
    }

    /** 修改部门 */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('org:manage')")
    @RequireWeight(3)
    @AuditLog(action = "UPDATE", resource = "department", description = "修改部门")
    public Result<Void> update(@PathVariable Integer id, @Valid @RequestBody DepartmentDTO dto) {
        departmentService.update(id, dto);
        return Result.success("部门修改成功", null);
    }

    /** 停用部门 */
    @PutMapping("/{id}/disable")
    @PreAuthorize("hasAuthority('org:manage')")
    @RequireWeight(3)
    @AuditLog(action = "UPDATE", resource = "department", description = "停用部门")
    public Result<Void> disable(@PathVariable Integer id) {
        departmentService.disable(id);
        return Result.success("部门已停用", null);
    }

    /** 启用部门（重新激活，清空删除计时） */
    @PutMapping("/{id}/enable")
    @PreAuthorize("hasAuthority('org:manage')")
    @RequireWeight(3)
    @AuditLog(action = "UPDATE", resource = "department", description = "启用部门")
    public Result<Void> enable(@PathVariable Integer id) {
        departmentService.enable(id);
        return Result.success("部门已启用", null);
    }

    /** 删除部门（停用超过一个月后才允许） */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('org:manage')")
    @RequireWeight(3)
    @AuditLog(action = "DELETE", resource = "department", description = "删除部门")
    public Result<Void> delete(@PathVariable Integer id) {
        departmentService.delete(id);
        return Result.success("部门已删除", null);
    }
}
