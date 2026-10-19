package com.employee.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色，对应 sys_roles。
 */
@Data
@TableName("sys_roles")
public class Role {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String roleCode;

    private String roleName;

    private String description;

    /** 1=系统内置角色 */
    private Integer isSystem;

    private LocalDateTime createdAt;
}
