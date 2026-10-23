package com.employee.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 岗位，对应 sys_positions。
 */
@Data
@TableName("sys_positions")
public class Position {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String name;

    private Integer departmentId;

    /** 岗位等级（1员工/2主管/3部门负责人/99总经理，@RequireWeight 门槛基准） */
    private Integer level;

    /** 权重字母：A=总经理 G=主管/负责人 E=员工（工号第二字母来源，仅 W4 可维护） */
    private String weightCode;

    /** 数据范围（岗位属性）：own/dept/dept_subtree/all——决定"能看哪些行"，谁坐这个位子适用同一套规则 */
    private String dataScope;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
