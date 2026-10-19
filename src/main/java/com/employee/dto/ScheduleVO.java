package com.employee.dto;

import lombok.Data;

/**
 * 排班设置行：部门 + 该部门的上下班时间。
 * hasSchedule=false 表示该部门尚未配置排班，打卡时按默认 09:00-18:00 判定。
 */
@Data
public class ScheduleVO {

    /** 部门ID */
    private Integer departmentId;

    /** 部门名称 */
    private String departmentName;

    /** 上班时间 HH:mm */
    private String workStartTime;

    /** 下班时间 HH:mm */
    private String workEndTime;

    /** 是否已配置排班 */
    private Boolean hasSchedule;
}
