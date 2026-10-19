package com.employee.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.employee.entity.Notice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface NoticeMapper extends BaseMapper<Notice> {

    /** 某用户未读公告数（已发布 + 全员或用户所在部门 + 未读） */
    @Select("SELECT COUNT(*) FROM sys_notices n " +
            "WHERE n.status = 'published' " +
            "AND (n.type = 'all' OR n.department_id IN " +
            "  (SELECT id FROM sys_departments WHERE id = #{deptId} OR parent_id = #{deptId})) " +
            "AND NOT EXISTS (SELECT 1 FROM sys_notice_reads r WHERE r.notice_id = n.id AND r.user_id = #{userId})")
    int countUnread(Integer userId, Integer deptId);
}
