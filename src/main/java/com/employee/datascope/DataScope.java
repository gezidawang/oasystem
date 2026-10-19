package com.employee.datascope;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 通用行级数据范围注解。标注在 Service 查询方法上，方法参数中实现
 * {@link DataScopeFilter} 的对象会被 {@link DataScopeAspect} 自动注入可见范围。
 *
 * 范围规则（与 RBAC 三维度一致）：
 *   持有 allPerm            → 全公司（可再按前端 deptId 子树过滤）
 *   持有 deptPerm           → 本部门子树 ∩ 前端指定部门（越权直接拒绝，空边界降级本人）
 *   二者都不持有            → 仅本人
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DataScope {

    /** 授予「查看全部」的权限码，空串表示该模块无 all 级 */
    String allPerm() default "";

    /** 授予「查看本部门子树」的权限码，空串表示该模块无 dept 级 */
    String deptPerm() default "";
}
