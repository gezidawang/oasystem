package com.employee.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 部门，对应 sys_departments。树形结构（parent_id 自关联）。
 */
@Data
@TableName("sys_departments")
public class Department {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String name;

    /** 部门字母（工号首字母来源，唯一，A-Z） */
    private String code;

    /** 上级部门，NULL=一级部门 */
    private Integer parentId;

    /** 部门主管（sys_users.id） */
    private Integer managerId;

    /** 1=启用 0=停用 */
    private Integer status;

    /** 保密隔离部门：1=其内部公告仅本部门+老板可见可操作，行政不可见 */
    private Integer confidential;

    /** 停用时间（删除冷却期判断） */
    private LocalDateTime disabledAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
