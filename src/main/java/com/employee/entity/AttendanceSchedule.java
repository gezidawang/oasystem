package com.employee.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 部门考勤排班实体，对应 biz_attendance_schedules。
 * 每个部门最多一条排班记录（department_id 唯一键），
 * 由超级管理员在「排班设置」页面配置弹性上下班时间。
 * 部门没有排班记录时，后端按默认 09:00-18:00 兜底。
 */
@Data
@TableName("biz_attendance_schedules")
public class AttendanceSchedule {

    @TableId(type = IdType.AUTO)
    private Integer id;

    /** 部门ID */
    private Integer departmentId;

    /** 上班时间（如 09:00） */
    private LocalTime workStartTime;

    /** 下班时间（如 18:00） */
    private LocalTime workEndTime;

    /** 最后修改人 user_id */
    private Integer updatedBy;

    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
