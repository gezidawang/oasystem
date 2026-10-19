package com.employee.dto;

import java.time.LocalTime;

/**
 * 打卡时间基准（含排班归属部门，便于提示与展示）。
 * 由 AttendanceService 与 ScheduleCacheService 共用。
 */
public record WorkWindow(LocalTime start, LocalTime end, Integer ownerDepartmentId, String ownerDepartmentName) {
}
