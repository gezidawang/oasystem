package com.employee.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDate;

/**
 * 员工建档/编辑入参（8-3 唯一建档入口）。
 *
 * 字段口径：
 * - 建档必填：姓名/部门/岗位/角色；手机号 11 位数字（格式校验前后端同一口径）；
 * - 工号不接受前端传入——唯一来源是服务端发号器（防分叉）；
 * - 密码不接受前端传入——服务端随机生成一次性初始密码，BCrypt 入库；
 * - 编辑（受限）只允许个人信息字段：部门/岗位/角色不在此列（调岗归区域四异动流程，
 *   防止绕过"换首字母、数字终身不变"的工号规则）。
 */
@Data
public class AccountDTO {

    @NotBlank(message = "姓名不能为空")
    private String realName;

    /** 男/女/未设置，缺省按"未设置" */
    private String gender;

    private Integer age;

    @Pattern(regexp = "^\\d{11}$", message = "手机号须为11位数字")
    private String phone;

    private String email;

    @NotNull(message = "请选择部门")
    private Integer departmentId;

    @NotNull(message = "请选择岗位")
    private Integer positionId;

    @NotNull(message = "请选择角色")
    private Integer roleId;

    /** 入职日期，缺省当天 */
    private LocalDate hireDate;

    /** 在职状态：试用/在职，缺省试用 */
    private String employeeStatus;
}
