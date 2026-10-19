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

    /** 岗位等级 */
    private Integer level;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
