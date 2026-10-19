package com.employee.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 打卡考勤记录实体，对应 biz_attendance。
 * 一人一天最多一条记录（(user_id, work_date) 唯一键）。
 *
 * status 取值（数据库 enum）：
 *   normal       正常    —— 规定时间内上下班
 *   late         迟到    —— 上班打卡晚于部门设定上班时间
 *   early_leave  早退    —— 下班打卡早于部门设定下班时间
 *   absent       缺勤    —— 当天下班时间之前始终没有打上班卡
 *   leave        请假    —— 预留（后续审批流模块写入）
 *
 * 判定优先级：absent > late > early_leave > normal
 * （同时迟到又早退时记为迟到；未到下班时间且未打卡的当天展示为「未打卡」，不落库）
 */
@Data
@TableName("biz_attendance")
public class Attendance {

    @TableId(type = IdType.AUTO)
    private Integer id;

    /** 打卡用户 user_id */
    private Integer userId;

    /** 工作日期 */
    private LocalDate workDate;

    /** 上班打卡时间 */
    private LocalDateTime checkInTime;

    /** 下班打卡时间 */
    private LocalDateTime checkOutTime;

    /** 考勤状态：normal/late/early_leave/absent/leave */
    private String status;

    /** 备注 */
    private String remark;

    /** 创建时间（MyBatis-Plus 自动填充） */
    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private LocalDateTime createdAt;
}
