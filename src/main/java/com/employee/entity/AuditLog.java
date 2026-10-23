package com.employee.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审计日志，对应 sys_audit_logs。本模块只读查询，写入由后续 AOP 切面负责。
 */
@Data
@TableName("sys_audit_logs")
public class AuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Integer userId;

    /** CREATE/READ/UPDATE/DELETE */
    private String action;

    private String resource;

    private Integer targetId;

    /** JSON 文本 */
    private String oldValue;

    /** JSON 文本 */
    private String newValue;

    private String ipAddress;

    private String userAgent;

    private String description;

    /** success=成功 / fail=失败（含越权被拒尝试） */
    private String result;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
