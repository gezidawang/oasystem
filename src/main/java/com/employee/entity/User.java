package com.employee.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.employee.typehandler.EncryptedStringTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统用户（登录账号），对应 sys_users。
 * phone / email 为 AES-256/GCM 透明加密列（由 EncryptedStringTypeHandler 自动加/解密）。
 */
@Data
@TableName(value = "sys_users", autoResultMap = true)
public class User {

    @TableId(type = IdType.AUTO)
    private Integer id;

    /** 登录名（工号） */
    private String username;

    /** BCrypt 密码哈希 */
    private String password;

    private String realName;

    private Integer roleId;

    private Integer departmentId;

    private Integer positionId;

    private String employeeNo;

    /** AES-256/GCM 加密存储 */
    @TableField(typeHandler = EncryptedStringTypeHandler.class)
    private String phone;

    /** AES-256/GCM 加密存储 */
    @TableField(typeHandler = EncryptedStringTypeHandler.class)
    private String email;

    private String avatar;

    private Long avatarFileId;

    /** 1=启用 0=停用 */
    private Integer status;

    private LocalDateTime lastLoginAt;

    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
