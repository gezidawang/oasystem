package com.employee.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 角色新增/修改请求 */
@Data
public class RoleDTO {

    /** 角色编码（仅新增时可指定，创建后不可变） */
    private String roleCode;

    @NotBlank(message = "角色名称不能为空")
    private String roleName;

    private String description;
}
