package com.employee.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 排班保存参数（超级管理员按部门设定弹性上下班时间）。
 */
/**
 * Lombok的@Data注解，会自动为类生成以下方法：
 * 1. getter() 和 setter() 方法
 * 2. toString() 方法
 * 3. equals() 和 hashCode() 方法
 * 4. 内部类的构造器（如果有的话）
 */
@Data
public class ScheduleSaveDTO {

    /** 部门ID */
    @NotNull(message = "部门不能为空")
    private Integer departmentId;

    /** 上班时间 HH:mm */
    @NotBlank(message = "上班时间不能为空")
    private String workStartTime;

    /** 下班时间 HH:mm */
    @NotBlank(message = "下班时间不能为空")
    private String workEndTime;
}
