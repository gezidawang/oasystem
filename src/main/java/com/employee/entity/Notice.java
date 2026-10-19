package com.employee.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 公告通知，对应 sys_notices。
 */
@Data
@TableName("sys_notices")
public class Notice {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String title;

    private String content;

    /** all=全员公告 / department=部门公告 */
    private String type;

    /** 部门公告对应的部门ID（全员为NULL） */
    private Integer departmentId;

    /** 发布人 user_id */
    private Integer publisherId;

    /** draft=草稿 / published=已发布 / archived=已撤回 */
    private String status;

    /** 是否置顶 */
    private Integer top;

    /** 发布时间（NULL=立即） */
    private LocalDateTime publishAt;

    /** 提交申请时间 */
    private LocalDateTime submittedAt;

    /** 审批通过时间（编辑窗口起算） */
    private LocalDateTime approvedAt;

    /** 驳回原因 */
    private String rejectReason;

    /** 审批人 user_id */
    private Integer approverId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
