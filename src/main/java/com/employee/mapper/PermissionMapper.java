package com.employee.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.employee.entity.Permission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 权限 Mapper。
 */
@Mapper
public interface PermissionMapper extends BaseMapper<Permission> {

    /** 查询某角色拥有的全部权限编码 */
    @Select("SELECT p.perm_code FROM sys_permissions p " +
            "JOIN sys_role_permissions rp ON rp.permission_id = p.id " +
            "WHERE rp.role_id = #{roleId}")
    List<String> selectPermCodesByRoleId(Integer roleId);
}
