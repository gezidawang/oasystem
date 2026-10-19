package com.employee.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.employee.dto.AttendanceQueryDTO;
import com.employee.dto.AttendanceRecordVO;
import com.employee.dto.UserScopeRef;
import com.employee.entity.Attendance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 考勤 Mapper：打卡记录 CRUD + 考勤记录多条件联合查询。
 */
@Mapper
public interface AttendanceMapper extends BaseMapper<Attendance> {

    /**
     * 考勤记录列表查询：打卡记录关联用户、部门、职位。
     * 部门口径：考勤只按一级部门核算展示——子部门人员的部门列显示其一级部门名
     * （COALESCE(pd.name, d.name)），二级项目组不作为独立考勤部门出现。
     * 部门过滤使用 Service 注入的 scopeDeptIds 集合（选中一级部门时已自动包含其全部子部门），
     * 不再按 department_id 精确单值匹配，避免选一级部门时漏掉子项目组人员。
     * DATE_FORMAT 直接在 SQL 层格式化为 yyyy-MM-dd HH:mm 字符串。
     */
    @Select("<script>" +
            "SELECT a.user_id AS userId, " +
            "       u.real_name AS realName, " +
            "       COALESCE(pd.name, d.name, '未分配部门') AS departmentName, " +
            "       p.name AS positionName, " +
            "       DATE_FORMAT(a.work_date, '%Y-%m-%d') AS workDate, " +
            "       DATE_FORMAT(a.check_in_time, '%Y-%m-%d %H:%i') AS checkInTime, " +
            "       DATE_FORMAT(a.check_out_time, '%Y-%m-%d %H:%i') AS checkOutTime, " +
            "       a.status AS status " +
            "FROM biz_attendance a " +
            "JOIN sys_users u ON u.id = a.user_id " +
            "LEFT JOIN sys_departments d ON d.id = u.department_id " +
            "LEFT JOIN sys_departments pd ON pd.id = d.parent_id " +
            "LEFT JOIN sys_positions p ON p.id = u.position_id " +
            "WHERE 1 = 1 " +
            "<if test='query.scopeUserId != null'> AND a.user_id = #{query.scopeUserId}</if>" +
            "<if test='query.scopeDeptIds != null'>" +
            "   <choose>" +
            "      <when test='query.scopeDeptIds.size() > 0'>" +
            "         AND u.department_id IN" +
            "         <foreach collection='query.scopeDeptIds' item='sid' open='(' separator=',' close=')'>#{sid}</foreach>" +
            "      </when>" +
            // fail-closed：集合非 null 但为空 = 无任何可见部门，绝不跳过过滤
            "      <otherwise> AND 1 = 0</otherwise>" +
            "   </choose></if>" +
            "<if test='query.keyword != null and query.keyword != \"\"'>" +
            "   AND u.real_name LIKE CONCAT('%', #{query.keyword}, '%')</if>" +
            "<if test='query.startDate != null and query.startDate != \"\"'>" +
            "   AND a.work_date &gt;= #{query.startDate}</if>" +
            "<if test='query.endDate != null and query.endDate != \"\"'>" +
            "   AND a.work_date &lt;= #{query.endDate}</if>" +
            "<if test='query.status != null and query.status != \"\"'>" +
            "   AND a.status = #{query.status}</if>" +
            "</script>")
    List<AttendanceRecordVO> selectRecordViews(@Param("query") AttendanceQueryDTO query);

    /**
     * 查询权限范围内的用户（含归口部门/职位），用于合成「缺勤/未打卡」记录：
     * 考勤列表不只是打卡后才有行——当天没打卡的人也要出现在结果里。
     * 部门列同样归口一级部门；deptIds 集合由 Service 按「一级部门含子部门」展开后传入。
     * 注意：排除 super_admin（老板）角色——老板不是被考勤对象，
     * 不能被任何人的考勤表合成为「未打卡/缺勤」，避免污染考勤统计与迟到率。
     */
    @Select("<script>" +
            "SELECT u.id AS userId, " +
            "       u.real_name AS realName, " +
            "       u.department_id AS departmentId, " +
            "       COALESCE(pd.name, d.name, '未分配部门') AS departmentName, " +
            "       p.name AS positionName " +
            "FROM sys_users u " +
            "JOIN sys_roles r ON r.id = u.role_id " +
            "LEFT JOIN sys_departments d ON d.id = u.department_id " +
            "LEFT JOIN sys_departments pd ON pd.id = d.parent_id " +
            "LEFT JOIN sys_positions p ON p.id = u.position_id " +
            "WHERE u.status = 1 AND r.role_code &lt;&gt; 'super_admin' " +
            "<if test='scopeUserId != null'> AND u.id = #{scopeUserId}</if>" +
            "<if test='deptIds != null'>" +
            "   <choose>" +
            "      <when test='deptIds.size() > 0'>" +
            "         AND u.department_id IN" +
            "         <foreach collection='deptIds' item='sid' open='(' separator=',' close=')'>#{sid}</foreach>" +
            "      </when>" +
            // fail-closed：空集合时不合成任何用户，防止退化成全公司合成
            "      <otherwise> AND 1 = 0</otherwise>" +
            "   </choose></if>" +
            "ORDER BY u.id" +
            "</script>")
    List<UserScopeRef> selectScopeUsers(@Param("scopeUserId") Integer scopeUserId,
                                        @Param("deptIds") List<Integer> deptIds);
}
