package com.employee.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 编制变更申请（二期-1 编制申请流），对应 sys_dept_change_requests。
 * 三段式治理：部门负责人/主管申请 → 老板(W4)审批 → 人事(org:manage)执行建制。
 * 状态机：pending → approved / rejected；approved → done。
 */
@Data
@TableName("sys_dept_change_requests")
public class DeptChangeRequest {

    @TableId(type = IdType.AUTO)
    private Integer id;

    /** 申请类型：create=增设部门 / disable=撤编停用 / update=调整（改名/上级） */
    private String type;

    /** 调整/撤编的目标部门 id（type=disable/update 时必填） */
    private Integer targetDeptId;

    /** 拟设/拟改部门名 */
    private String name;

    /** 拟设/拟改部门字母（工号首字母，单大写 A-Z） */
    private String code;

    /** 拟上级部门 id（NULL=一级部门） */
    private Integer parentId;

    /** 申请理由（必填） */
    private String reason;

    /** 申请人 user_id */
    private Integer applicantId;

    /** 状态：pending/approved/rejected/done */
    private String status;

    /** 审批人 user_id（老板） */
    private Integer approverId;

    /** 驳回原因（仅申请人/审批人可见，其余人查询时脱敏置空） */
    private String rejectReason;

    /** 执行失败原因（DOA 三段式兼容口径：人事执行撞硬约束时单据回退 failed 态并留痕，
     *  老板可整改后重新审批或驳回终止，杜绝"永远关不掉的死单"；属系统校验信息不涉人敏感评价，不脱敏） */
    private String failReason;

    /** 执行建制后产生的部门 id（type=create） */
    private Integer resultDeptId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 审批时间 */
    private LocalDateTime decidedAt;

    /** 执行完成时间 */
    private LocalDateTime doneAt;

    // ===== 展示用瞬态字段 =====

    /** 申请人姓名 */
    @TableField(exist = false)
    private String applicantName;

    /** 审批人姓名 */
    @TableField(exist = false)
    private String approverName;

    /** 目标/结果部门名（展示用） */
    @TableField(exist = false)
    private String deptName;
}
