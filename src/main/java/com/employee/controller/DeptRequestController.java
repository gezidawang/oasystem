package com.employee.controller;

import com.employee.annotation.AuditLog;
import com.employee.annotation.RequireWeight;
import com.employee.common.Result;
import com.employee.dto.DeptRequestDTO;
import com.employee.service.DeptRequestService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 编制变更申请接口（二期-1 编制申请流）。
 * 提交：dept:manage:request + W2（部门负责人/主管）；审批：W4（老板）；执行建制：org:manage + W3（人事）。
 * 业务原因：编制权三段式分离（申请/审批/建制），防止建制权稀释数据范围子树。
 */
@RestController
@RequestMapping("/api/dept-requests")
public class DeptRequestController {

    private final DeptRequestService deptRequestService;

    public DeptRequestController(DeptRequestService deptRequestService) {
        this.deptRequestService = deptRequestService;
    }

    /** 申请列表（申请人看自己；老板/人事看全部，含 canApprove/canExecute 能力位） */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Result<Map<String, Object>> list(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "10") int size) {
        return Result.success(deptRequestService.list(page, Math.min(size, 100)));
    }

    /** 提交编制变更申请（部门负责人/主管，W2 门槛） */
    @PostMapping
    @PreAuthorize("hasAuthority('dept:manage:request')")
    @RequireWeight(2)
    @AuditLog(action = "CREATE", resource = "dept-request", description = "提交编制变更申请")
    public Result<Void> submit(@Valid @RequestBody DeptRequestDTO dto) {
        deptRequestService.submit(dto);
        return Result.success("编制申请已提交，等待总经理审批", null);
    }

    /** 审批通过（仅老板 W4） */
    @PutMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('dept:manage:request')")
    @RequireWeight(99)
    @AuditLog(action = "UPDATE", resource = "dept-request", description = "编制申请审批通过")
    public Result<Void> approve(@PathVariable Integer id) {
        deptRequestService.approve(id);
        return Result.success("已审批通过，待人事执行建制", null);
    }

    /** 审批驳回（仅老板 W4；驳回原因必填、仅双方可见） */
    @PutMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('dept:manage:request')")
    @RequireWeight(99)
    @AuditLog(action = "UPDATE", resource = "dept-request", description = "编制申请驳回")
    public Result<Void> reject(@PathVariable Integer id, @RequestParam String reason) {
        deptRequestService.reject(id, reason);
        return Result.success("已驳回", null);
    }

    /** 执行建制（人事 org:manage + W3） */
    @PutMapping("/{id}/execute")
    @PreAuthorize("hasAuthority('org:manage')")
    @RequireWeight(3)
    @AuditLog(action = "UPDATE", resource = "dept-request", description = "执行编制建制")
    public Result<Void> execute(@PathVariable Integer id) {
        deptRequestService.execute(id);
        return Result.success("建制执行完成", null);
    }
}
