package com.employee.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** 岗位新增/修改请求 */
@Data
public class PositionDTO {

    @NotBlank(message = "岗位名称不能为空")
    private String name;

    /** 所属部门（NULL=全公司通用岗位） */
    private Integer departmentId;

    @NotNull(message = "岗位等级不能为空")
    @Min(value = 1, message = "等级最小为1")
    @Max(value = 99, message = "等级最大为99")
    private Integer level;

    /** 权重字母 A/G/E（仅总经理可改） */
    @Pattern(regexp = "^[AGE]$", message = "权重字母只能为 A/G/E")
    private String weightCode;
}
