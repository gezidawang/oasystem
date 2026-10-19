package com.employee.dto;

import lombok.Data;

/**
 * 权限范围内的用户引用（考勤缺勤/未打卡记录合成用）。
 */
@Data
public class UserScopeRef {

    /** 用户ID */
    private Integer userId;

    /** 姓名 */
    private String realName;

    /** 部门ID */
    private Integer departmentId;

    /** 部门名称 */
    private String departmentName;

    /** 职位名称 */
    private String positionName;
}
