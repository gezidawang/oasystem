package com.employee.controller;

import com.employee.annotation.AuditLog;
import com.employee.annotation.RequireWeight;
import com.employee.common.Result;
import com.employee.dto.PositionDTO;
import com.employee.service.PositionService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 岗位管理接口（Phase 1, 8-2）。
 * 读：登录即可（通讯录/组织架构）；写：org:manage + W3；
 * weight_code 变更与删除在 Service 内强制 W4（8-2-2 铁律）。
 */
@RestController
@RequestMapping("/api/positions")
public class PositionController {

    private final PositionService positionService;

    public PositionController(PositionService positionService) {
        this.positionService = positionService;
    }

    /** 岗位列表（含在岗人数） */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Result<List<Map<String, Object>>> list() {
        return Result.success(positionService.list());
    }

    /** 新增岗位 */
    @PostMapping
    @PreAuthorize("hasAuthority('org:manage')")
    @RequireWeight(3)
    @AuditLog(action = "CREATE", resource = "position", description = "新增岗位")
    public Result<Void> create(@Valid @RequestBody PositionDTO dto) {
        positionService.create(dto);
        return Result.success("岗位创建成功", null);
    }

    /** 修改岗位（weight_code 变更仅总经理） */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('org:manage')")
    @RequireWeight(3)
    @AuditLog(action = "UPDATE", resource = "position", description = "修改岗位")
    public Result<Void> update(@PathVariable Integer id, @Valid @RequestBody PositionDTO dto) {
        positionService.update(id, dto);
        return Result.success("岗位更新成功", null);
    }

    /** 删除岗位（无人在岗；weight 体系敏感，仅总经理） */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('org:manage')")
    @RequireWeight(99)
    @AuditLog(action = "DELETE", resource = "position", description = "删除岗位")
    public Result<Void> delete(@PathVariable Integer id) {
        positionService.delete(id);
        return Result.success("岗位已删除", null);
    }
}
