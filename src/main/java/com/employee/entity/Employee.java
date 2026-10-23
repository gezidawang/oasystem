package com.employee.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.employee.typehandler.EncryptedStringTypeHandler;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 员工档案（区域八 8-3 建档双表之二，表 biz_employees）。
 *
 * 定案口径（08 文档 问题2）：唯一建档入口在 8-3——人事录入时同一事务内写 sys_users + biz_employees
 * 并生成工号；区域四（员工档案）只提供查询与受限编辑，不得另行创建档案避免发号器分叉。
 * 关联：user_id 指向 sys_users.id（历史业务数据一律按 user_id 关联，工号换发不影响归属）。
 * phone 字段级 AES-256/GCM 加密（与 sys_users.phone 同一 TypeHandler，建档即密文）。
 */
@Data
@TableName(value = "biz_employees", autoResultMap = true)
public class Employee {

    @TableId(type = IdType.AUTO)
    private Integer id;

    /** 关联账号 id（sys_users.id，一一对应） */
    private Integer userId;

    private String realName;

    /** 性别：男/女/未设置（表为枚举列，空值按"未设置"落库） */
    private String gender;

    private Integer age;

    /** 手机号：AES 加密存储，TypeHandler 自动加解密，列表展示层做掩码 */
    @TableField(typeHandler = EncryptedStringTypeHandler.class)
    private String phone;

    private String photoUrl;

    private Long avatarFileId;

    private Integer departmentId;

    /** 岗位名快照（文本）：权威岗位在 sys_users.position_id，调岗留痕区域四维护，本字段仅档案展示 */
    private String position;

    private LocalDate hireDate;

    /** 在职状态：试用/在职/离职（离职联动 sys_users.status=0 禁用账号） */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
