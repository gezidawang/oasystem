package com.employee.datascope;

import com.employee.entity.User;
import com.employee.exception.BusinessException;
import com.employee.mapper.UserMapper;
import com.employee.security.LoginUser;
import com.employee.service.DeptTreeService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 通用数据范围解析器。集中实现 own/dept/all 三档范围与部门子树展开、
 * 越权取交集、fail-closed 等硬规则（见 00 §三）。
 * 铁律：空集合必须 fail-closed；越权取交集而非放开；绝不退化为全量查询。
 * 部门子树展开委托 {@link DeptTreeService}（带缓存）。
 */
@Component
public class DataScopeService {

    private final DeptTreeService deptTreeService;
    private final UserMapper userMapper;

    public DataScopeService(DeptTreeService deptTreeService, UserMapper userMapper) {
        this.deptTreeService = deptTreeService;
        this.userMapper = userMapper;
    }

    /**
     * 依据当前登录人权限，把可见范围注入到 filter。
     * 逻辑与考勤原有实现逐条等价。
     */
    public void apply(DataScopeFilter filter, String allPerm, String deptPerm) {
        LoginUser user = currentUser();

        // 全公司域
        if (!allPerm.isEmpty() && hasAuthority(allPerm)) {
            filter.setScopeUserId(null);
            filter.setScopeDeptIds(filter.getDeptId() == null
                    ? null
                    : new ArrayList<>(deptTreeService.subtree(filter.getDeptId())));
            return;
        }

        // 部门子树域
        if (!deptPerm.isEmpty() && hasAuthority(deptPerm)) {
            List<Integer> allowed = new ArrayList<>(deptTreeService.subtree(currentDepartmentId(user)));
            if (allowed.isEmpty()) {
                // 未分配部门：降级为仅本人，绝不让空集合流入 SQL 导致部门过滤被跳过
                filter.setScopeUserId(user.getUserId());
                filter.setScopeDeptIds(null);
                filter.setDeptId(null);
                return;
            }
            if (filter.getDeptId() != null) {
                // 指定部门与自身可见边界取交集，交集为空即越权，绝不退化为全量
                List<Integer> requested = new ArrayList<>(deptTreeService.subtree(filter.getDeptId()));
                requested.retainAll(allowed);
                if (requested.isEmpty()) {
                    throw new BusinessException(403, "无权查看该范围内的数据");
                }
                filter.setScopeDeptIds(requested);
            } else {
                filter.setScopeDeptIds(allowed);
            }
            filter.setScopeUserId(null);
            return;
        }

        // 仅本人域
        filter.setScopeUserId(user.getUserId());
        filter.setScopeDeptIds(null);
        filter.setDeptId(null);
    }

    /** 从 SecurityContext 取当前登录用户 */
    private LoginUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof LoginUser loginUser)) {
            throw new BusinessException(401, "未登录");
        }
        return loginUser;
    }

    /** 当前登录用户是否持有某权限码 */
    private boolean hasAuthority(String code) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(code::equals);
    }

    /** 实时查库取当前用户部门（不依赖 JWT，避免部门调整后 token 过期） */
    private Integer currentDepartmentId(LoginUser user) {
        User dbUser = userMapper.selectById(user.getUserId());
        return dbUser == null ? null : dbUser.getDepartmentId();
    }
}
