package com.employee.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 编制变更申请提交请求（二期-1）。
 * type=create：name/code 必填（parentId 可选）；
 * type=disable/update：targetDeptId 必填（update 另需 name/code）。
 */
@Data
public class DeptRequestDTO {

    @NotBlank(message = "申请类型不能为空")
    @Pattern(regexp = "^(create|disable|update)$", message = "申请类型仅支持 create/disable/update")
    private String type;

    /** 调整/撤编的目标部门 id（disable/update 必填） */
    private Integer targetDeptId;

    /** 拟设/拟改部门名 */
    private String name;

    /** 拟设/拟改部门字母（工号首字母） */
    @Pattern(regexp = "^$|^[A-Z]$", message = "部门字母必须为单个大写A-Z")
    private String code;

    /** 拟上级部门 id（NULL=一级部门） */
    private Integer parentId;

    @NotBlank(message = "申请理由不能为空")
    private String reason;
}
