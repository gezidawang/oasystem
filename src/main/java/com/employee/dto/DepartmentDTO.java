package com.employee.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** 部门新增/修改请求 */
@Data
public class DepartmentDTO {

    @NotBlank(message = "部门名称不能为空")
    private String name;

    /** 部门字母 code（工号首字母），单大写字母 */
    @NotBlank(message = "部门字母不能为空")
    @Pattern(regexp = "^[A-Z]$", message = "部门字母必须为单个大写A-Z")
    private String code;

    /** 上级部门ID（NULL=一级部门） */
    private Integer parentId;

    /** 部门主管 user_id */
    private Integer managerId;
}
