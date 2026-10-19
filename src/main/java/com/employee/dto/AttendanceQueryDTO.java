package com.employee.dto;

import com.employee.datascope.DataScopeFilter;
import lombok.Data;

import java.util.List;

/**
 * 考勤记录查询条件。
 * deptId / keyword / startDate / endDate / status 来自前端筛选栏；
 * scopeUserId / scopeDeptIds 由后端按登录人权限与部门归口口径注入，前端传了也会被覆盖。
 * 部门口径：考勤只按一级部门核算，选中一级部门时自动包含其下所有二级项目组；
 * 二级项目组不作为独立查询主体。
 */
@Data
public class AttendanceQueryDTO implements DataScopeFilter {

    /** 按部门筛选（前端传入的部门ID，可以是任意层级；Service 会展开为归口后的部门集合） */
    private Integer deptId;

    /** 按姓名模糊筛选 */
    private String keyword;

    /** 起始日期 yyyy-MM-dd，空则默认今天 */
    private String startDate;

    /** 结束日期 yyyy-MM-dd，空则默认今天 */
    private String endDate;

    /** 考勤状态筛选：normal/late/early_leave/absent/pending */
    private String status;

    /** 当前页码，默认 1 */
    private Integer page = 1;

    /** 每页条数，默认 10 */
    private Integer size = 10;

    /** 内部字段：本人域查询时由 Service 注入，禁止前端指定他人 */
    private Integer scopeUserId;

    /**
     * 内部字段：本次查询生效的部门ID集合（已按「一级部门含其全部子部门」展开，
     * 并与登录人可见边界取交集）。null=不限部门；由 Service 计算，不接受前端直传。
     */
    private List<Integer> scopeDeptIds;
}
