package com.employee.controller;

import com.employee.annotation.AuditLog;
import com.employee.annotation.NoRepeatSubmit;
import com.employee.annotation.RequireWeight;
import com.employee.common.Result;
import com.employee.dto.AttendanceQueryDTO;
import com.employee.dto.ScheduleSaveDTO;
import com.employee.service.AttendanceService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 考勤接口：/api/attendance
 *
 * 权限码（RBAC，与前端菜单可见性一致）：
 *   attendance:check:own        打卡 / 查看本人今日打卡（hr、manager、employee；super_admin 已吊销——老板不是被考勤对象）
 *   attendance:read:own         查看本人考勤记录
 *   attendance:read:dept        查看本部门考勤记录（部门领导）
 *   attendance:read:all         查看全公司考勤记录（人事、超管）
 *   attendance:schedule:manage  按部门设置上下班时间（仅超级管理员）
 */
@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;

    public AttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    /**
     * 统一打卡（唯一打卡入口）：未打上班卡时打上班，已上班未打下班时打下班；
     * 下班前 1 小时锁死上班入口，已打卡者不受影响。
     * 注：用 isAuthenticated() 而非绑权限码，是为了让 Service 里的 assertPunchable()
     * 给出精确提示（「老板无需打卡」vs「无打卡权限」）；RBAC 校验集中在 Service，不破坏权限体系。
     */
    @PostMapping("/punch")
    @PreAuthorize("isAuthenticated()")
    @NoRepeatSubmit(message = "打卡请求正在处理，请勿重复提交")
    @AuditLog(action = "CREATE", resource = "attendance", description = "打卡")
    public Result<Map<String, Object>> punch() {
        return Result.success("打卡成功", attendanceService.punch());
    }

    /**
     * 打卡页数据：本人今日记录 + 部门排班。
     * 允许只有查看权限的用户调用（如超管），Service 内对老板角色返回 punchable=false 不适用标记，
     * 而不是直接 403，保证打卡页能展示友好提示而非半截数据报错。
     */
    @GetMapping("/today")
    @PreAuthorize("hasAnyAuthority('attendance:check:own','attendance:read:own','attendance:read:dept','attendance:read:all')")
    public Result<Map<String, Object>> today() {
        return Result.success(attendanceService.today());
    }

    /** 考勤记录列表：按部门/姓名/日期/状态查询，可见范围由权限码决定 */
    @GetMapping("/records")
    @PreAuthorize("hasAnyAuthority('attendance:read:own','attendance:read:dept','attendance:read:all')")
    public Result<Map<String, Object>> records(AttendanceQueryDTO query) {
        return Result.success(attendanceService.listRecords(query));
    }

    /** 考勤筛选用部门列表 */
    @GetMapping("/departments")
    @PreAuthorize("hasAnyAuthority('attendance:read:own','attendance:read:dept','attendance:read:all')")
    public Result<List<Map<String, Object>>> departments() {
        return Result.success(attendanceService.departments());
    }

    /** 排班设置列表（全部部门 + 当前上下班时间） */
    @GetMapping("/schedules")
    @PreAuthorize("hasAuthority('attendance:schedule:manage')")
    public Result<List<?>> schedules() {
        return Result.success(attendanceService.listSchedules());
    }

    /** 保存某部门的弹性上下班时间（有则更新，无则新增）。主管级以上可操作 */
    @PutMapping("/schedule")
    @PreAuthorize("hasAuthority('attendance:schedule:manage')")
    @RequireWeight(2)
    @AuditLog(action = "UPDATE", resource = "attendance-schedule", description = "保存排班")
    public Result<Void> saveSchedule(@Valid @RequestBody ScheduleSaveDTO dto) {
        attendanceService.saveSchedule(dto);
        return Result.success("排班保存成功", null);
    }
}
