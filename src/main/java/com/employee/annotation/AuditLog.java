package com.employee.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 审计日志注解：标注在 Service/Controller 方法上，方法成功返回后由
 * {@link com.employee.aspect.AuditLogAspect} 记录一条 sys_audit_logs。
 * action 取值：CREATE / READ / UPDATE / DELETE（与库内枚举一致）。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {

    /** 操作类型：CREATE/READ/UPDATE/DELETE */
    String action();

    /** 资源名，如 attendance / notice / employee */
    String resource();

    /** 可读描述，留空则用方法名兜底 */
    String description() default "";
}
