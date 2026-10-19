package com.employee.dto;

import lombok.Data;

/**
 * 考勤记录展示对象。
 * 对应需求展示格式：部门 - 姓名 - 职位 - 打卡日期时间（2026-08-12 08:01）- 考勤情况。
 * 时间字段统一为格式化后的字符串，前端无需再做日期处理。
 */
@Data
public class AttendanceRecordVO {

    /** 用户ID（列表定位/操作用） */
    private Integer userId;

    /** 姓名 */
    private String realName;

    /** 部门名称 */
    private String departmentName;

    /** 职位名称（总经理/部门负责人/主管/员工） */
    private String positionName;

    /** 工作日期 yyyy-MM-dd */
    private String workDate;

    /** 上班打卡 yyyy-MM-dd HH:mm（未打为空） */
    private String checkInTime;

    /** 下班打卡 yyyy-MM-dd HH:mm（未打为空） */
    private String checkOutTime;

    /** 状态码：normal/late/early_leave/absent/leave/pending */
    private String status;

    /** 状态中文名：正常/迟到/早退/缺勤/请假/未打卡（Service 统一翻译） */
    private String statusText;
}
