package com.employee.datascope;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

/**
 * 数据范围切面：拦截标注 {@link DataScope} 的方法，对参数中实现
 * {@link DataScopeFilter} 的对象自动注入当前登录人的可见范围。
 */
@Aspect
@Component
public class DataScopeAspect {

    private final DataScopeService dataScopeService;

    public DataScopeAspect(DataScopeService dataScopeService) {
        this.dataScopeService = dataScopeService;
    }

    @Before("@annotation(com.employee.datascope.DataScope)")
    public void doBefore(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        DataScope annotation = signature.getMethod().getAnnotation(DataScope.class);
        if (annotation == null) {
            return;
        }
        for (Object arg : joinPoint.getArgs()) {
            if (arg instanceof DataScopeFilter filter) {
                dataScopeService.apply(filter, annotation.allPerm(), annotation.deptPerm());
            }
        }
    }
}
