package com.employee.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 权重门槛注解（Phase 0-7，方案 A）。
 * 与权限码正交：权限码回答"能不能做这类操作"，本注解回答"级别够不够"。
 * 执行顺序：@PreAuthorize → @RequireWeight → @DataScope → 业务方法。
 * <p>
 * level 含义：1=员工, 2=主管, 3=部门负责人/人事, 99=总经理。
 * 当前用户 level 实时查库（sys_users.position_id → sys_positions.level），不取 JWT。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireWeight {

    /** 允许调用所需的最小 level（如 2=主管及以上，99=仅总经理） */
    int value();
}
