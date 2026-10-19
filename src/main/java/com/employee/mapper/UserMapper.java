package com.employee.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.employee.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 用户 Mapper。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /** 按登录名（工号）查询用户 */
    @Select("SELECT * FROM sys_users WHERE username = #{username} LIMIT 1")
    User selectByUsername(String username);
}
