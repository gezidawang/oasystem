package com.employee.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.employee.dto.ScheduleVO;
import com.employee.entity.AttendanceSchedule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 部门排班 Mapper。
 */
@Mapper
public interface AttendanceScheduleMapper extends BaseMapper<AttendanceSchedule> {

    /**
     * 排班设置页列表：仅一级部门（parent_id 为 NULL）+ 其排班。
     * 不设按技术栈划分的二级组；若存在子部门也不单独排班、不在此列表呈现，
     * 其员工打卡时沿 parent_id 沿用所属一级部门的排班。
     * DATE_FORMAT 把 TIME 列直接格式化为 HH:mm 字符串，避免 LocalDateTime 序列化带秒的问题。
     */
    @Select("SELECT d.id AS departmentId, " +
            "       d.name AS departmentName, " +
            "       DATE_FORMAT(s.work_start_time, '%H:%i') AS workStartTime, " +
            "       DATE_FORMAT(s.work_end_time, '%H:%i') AS workEndTime, " +
            "       (s.id IS NOT NULL) AS hasSchedule " +
            "FROM sys_departments d " +
            "LEFT JOIN biz_attendance_schedules s ON s.department_id = d.id " +
            "WHERE d.status = 1 AND d.parent_id IS NULL " +
            "ORDER BY d.id")
    List<ScheduleVO> selectScheduleViews();
}
