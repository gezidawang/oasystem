package com.employee.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.employee.entity.Employee;
import org.apache.ibatis.annotations.Mapper;

/**
 * 员工档案 Mapper（8-3 建档双表之二）。
 */
@Mapper
public interface EmployeeMapper extends BaseMapper<Employee> {
}
