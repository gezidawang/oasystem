package com.employee.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 权限点，对应 sys_permissions。
 * perm_code 形如 resource:action:scope，例如 employee:read:all。
 */
@Data
@TableName("sys_permissions")
public class Permission {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String permCode;

    private String permName;

    private String resource;

    private String action;

    private String description;

    private LocalDateTime createdAt;
}
