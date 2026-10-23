package com.employee.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.employee.entity.DeptChangeRequest;
import org.apache.ibatis.annotations.Mapper;

/**
 * 编制变更申请 Mapper（sys_dept_change_requests）。
 */
@Mapper
public interface DeptChangeRequestMapper extends BaseMapper<DeptChangeRequest> {
}
