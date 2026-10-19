package com.employee.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 角色-权限关联表 sys_role_permissions（无独立主键，用自定义 SQL）。
 */
@Mapper
public interface RolePermissionMapper {

    @Select("SELECT permission_id FROM sys_role_permissions WHERE role_id = #{roleId}")
    List<Integer> selectPermissionIdsByRoleId(Integer roleId);

    @Delete("DELETE FROM sys_role_permissions WHERE role_id = #{roleId}")
    int deleteByRoleId(Integer roleId);

    @Insert("INSERT INTO sys_role_permissions (role_id, permission_id) VALUES (#{roleId}, #{permissionId})")
    int insertOne(Integer roleId, Integer permissionId);
}
