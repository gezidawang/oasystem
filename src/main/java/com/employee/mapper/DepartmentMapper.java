package com.employee.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.employee.entity.Department;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 部门 Mapper。既用于基础数据管理（CRUD），也供考勤模块（归口/子树展开）使用。
 */
@Mapper
public interface DepartmentMapper extends BaseMapper<Department> {

    /** 查询启用状态的部门列表（考勤归口后只向一级部门提供筛选/排班） */
    @Select("SELECT * FROM sys_departments WHERE status = 1 ORDER BY id")
    List<Department> selectEnabledDepartments();

    /** 查询某部门的直接子部门ID（用于考勤部门查询范围沿 parent_id 展开子树） */
    @Select("SELECT id FROM sys_departments WHERE parent_id = #{parentId}")
    List<Integer> selectChildIds(Integer parentId);
}
