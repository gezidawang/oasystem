package com.employee.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.employee.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 用户 Mapper。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /** 按登录名（工号）查询用户 */
    @Select("SELECT * FROM sys_users WHERE username = #{username} LIMIT 1")
    User selectByUsername(String username);

    /**
     * 工号全局流水发号初值（仅供序列表 sys_employee_no_seq 初始化对齐；
     * 运行期发号已改用序列表原子取号，见 nextEmployeeSerial/lastEmployeeSerial）。
     */
    @Select("SELECT MAX(CAST(RIGHT(employee_no, 5) AS UNSIGNED)) FROM sys_users " +
            "WHERE employee_no REGEXP '^[A-Z][AGE][0-9]{5}$'")
    Integer maxEmployeeSerial();

    /**
     * 序列表原子取号（8-3 建档并发安全核心，db/account_scheme.sql）：
     * UPDATE 行锁使并发请求排队递增，LAST_INSERT_ID() 按连接记录本次值——同事务内紧接着读取即得独占流水；
     * 业务事务回滚时递增值一并回滚，不重号不废号。
     */
    @Update("UPDATE sys_employee_no_seq SET val = LAST_INSERT_ID(val + 1) WHERE id = 1")
    int nextEmployeeSerial();

    /** 读取本连接最近一次 LAST_INSERT_ID（须与 nextEmployeeSerial 同一事务/连接） */
    @Select("SELECT LAST_INSERT_ID()")
    int lastEmployeeSerial();
}
