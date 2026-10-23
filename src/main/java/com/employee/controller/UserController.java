package com.employee.controller;

import com.employee.annotation.AuditLog;
import com.employee.annotation.NoRepeatSubmit;
import com.employee.annotation.RequireWeight;
import com.employee.common.Result;
import com.employee.dto.AccountDTO;
import com.employee.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 员工账号管理接口（区域八 8-3，唯一建档入口）。
 *
 * 双闸门口径（08 文档 §三）：权限码 user:manage（已授 hr + super_admin）回答"能不能"，
 * @RequireWeight(3) 回答"级别够不够"——人事专员(W1)虽同持 hr 角色码也被职级门槛拦截，
 * 仅部门负责人级(W3，即人事总监)与总经理(W4/99)可建/停/重置账号，与"人事可管账号但不能改角色矩阵"铁律对齐。
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /** 账号列表（工号/姓名搜索 + 部门/状态筛选；手机号掩码，密码永不回传） */
    @GetMapping
    @PreAuthorize("hasAuthority('user:manage')")
    @RequireWeight(3)
    public Result<Map<String, Object>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer departmentId,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(userService.page(keyword, departmentId, status, page, Math.min(size, 100)));
    }

    /** 建档：同事务生成工号 + sys_users + biz_employees，返回一次性初始密码（审计不落明文） */
    @PostMapping
    @PreAuthorize("hasAuthority('user:manage')")
    @RequireWeight(3)
    @NoRepeatSubmit(message = "建档请求正在处理，请勿重复提交")
    @AuditLog(action = "CREATE", resource = "account", description = "员工建档")
    public Result<Map<String, Object>> create(@Valid @RequestBody AccountDTO dto) {
        return Result.success("建档成功，请将初始密码一次性告知本人", userService.createAccount(dto));
    }

    /** 受限编辑：仅个人信息（工号/部门/岗位/角色不在此改，调岗归区域四异动流程）；
     *  不走 @Valid：部门/岗位/角色的 NotNull 仅建档口径，编辑态不读这三个字段 */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('user:manage')")
    @RequireWeight(3)
    @AuditLog(action = "UPDATE", resource = "account", description = "编辑员工信息")
    public Result<Void> update(@PathVariable Integer id, @RequestBody AccountDTO dto) {
        userService.updateProfile(id, dto);
        return Result.success("员工信息已更新", null);
    }

    /** 启用/停用（离职禁用；停用后旧 token 立即进黑名单，联动 9-5） */
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('user:manage')")
    @RequireWeight(3)
    @AuditLog(action = "UPDATE", resource = "account", description = "账号启停")
    public Result<Void> updateStatus(@PathVariable Integer id, @RequestParam Integer status) {
        userService.updateStatus(id, status);
        return Result.success(status == 1 ? "账号已启用" : "账号已停用，其登录态即刻失效", null);
    }

    /** 重置初始密码：BCrypt 重加密 + 清登录锁，仅本次返回明文 */
    @PutMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority('user:manage')")
    @RequireWeight(3)
    @AuditLog(action = "UPDATE", resource = "account", description = "重置密码")
    public Result<Map<String, String>> resetPassword(@PathVariable Integer id) {
        return Result.success("已重置，请将新密码一次性告知本人",
                Map.of("initialPassword", userService.resetPassword(id)));
    }

    /** 解锁（帮助台口径：清除账号锁与失败计数，不动密码与启停状态） */
    @PutMapping("/{id}/unlock")
    @PreAuthorize("hasAuthority('user:manage')")
    @RequireWeight(3)
    @AuditLog(action = "UPDATE", resource = "account", description = "解锁账号")
    public Result<Void> unlock(@PathVariable Integer id) {
        userService.unlock(id);
        return Result.success("账号已解锁，可立即重新登录", null);
    }
}
