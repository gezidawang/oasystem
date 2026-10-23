package com.employee.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
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

    /** 发布人姓名（非表字段，查询时联查填充） */
    @TableField(exist = false)
    private String publisherName;

    /** 发布人所在部门名（非表字段；全员公告展示"姓名/部门"用） */
    @TableField(exist = false)
    private String publisherDeptName;

    /** 当前登录用户是否已读（非表字段，列表查询时填充） */
    @TableField(exist = false)
    private Boolean isRead;

    /** draft=草稿 / published=已发布 / archived=已撤回 */
    private String status;

    /** 是否置顶 */
    private Integer top;

    /** 发布时间（直发/审批通过时写入；撤回/重提时清空） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime publishAt;

    /** 提交申请时间（撤回/重提清空重算） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime submittedAt;

    /** 审批通过时间（编辑窗口起算；撤回/重提清空） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime approvedAt;

    /** 驳回原因（仅驳回人/申请人可见；重新提交时清空） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String rejectReason;

    /** 驳回时间（"未通过"列表时间列展示用；重新提交/撤回时清空重算） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime rejectedAt;

    /** 审批人 user_id（撤回/重提时清空） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer approverId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 逻辑删除标记（全局配置 logic-delete-field=deleted，deleteById 自动转 UPDATE） */
    private Integer deleted;
}
