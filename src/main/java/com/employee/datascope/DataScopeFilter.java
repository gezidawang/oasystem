package com.employee.datascope;

import java.util.List;

/**
 * 数据范围过滤契约：查询条件对象实现本接口后，可被 {@link DataScopeAspect} 自动注入
 * 当前登录人的可见范围（own / dept / all）。
 * scopeUserId / scopeDeptIds 为后端内部字段，前端传入一律被覆盖，禁止用于越权。
 */
public interface DataScopeFilter {

    /** 前端指定的部门筛选（可为任意层级，可为空） */
    Integer getDeptId();

    void setDeptId(Integer deptId);

    /** 本人域：仅可见该用户ID的数据 */
    Integer getScopeUserId();

    void setScopeUserId(Integer scopeUserId);

    /** 部门域：可见的部门ID集合（已展开子树；null=不限；空集合=fail-closed 不可见任何） */
    List<Integer> getScopeDeptIds();

    void setScopeDeptIds(List<Integer> scopeDeptIds);
}
