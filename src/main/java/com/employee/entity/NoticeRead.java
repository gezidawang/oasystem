package com.employee.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 公告阅读记录，对应 sys_notice_reads。
 */
@Data
@TableName("sys_notice_reads")
public class NoticeRead {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer noticeId;

    private Integer userId;

    private LocalDateTime readAt;
}
