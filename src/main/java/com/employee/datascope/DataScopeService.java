package com.employee.datascope;

import com.employee.exception.BusinessException;
import com.employee.security.LoginUser;
import com.employee.service.DeptTreeService;
import com.employee.service.PositionScopeService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 通用数据范围解析器。集中实现范围判定、部门子树展开、越权取交集、fail-closed 等硬规则（见 00 §三）。
 * 铁律：空集合必须 fail-closed；越权取交集而非放开；绝不退化为全量查询。
 *
 * 2026-10-22 数据范围合流定案：
 *   权限码（allPerm/deptPerm）= 职能天花板（这个功能允许看多宽）；
 *   岗位 data_scope（{@link PositionScopeService}）= 结构范围（这个位子能看哪些部门行）；
 *   有效范围 = 两者取交集（越权取窄，绝不放开）；保密部门 overlay 由 PositionScopeService 统一处理。
 * 业务原因：范围是岗位属性而非角色特批，所有模块共用同一套范围定义，杜绝各模块自搞一套判定链。
 * 部门子树展开委托 {@link DeptTreeService}（带缓存）。
 */
@Component
public class DataScopeService {

    private final DeptTreeService deptTreeService;
    private final PositionScopeService positionScopeService;

    public DataScopeService(DeptTreeService deptTreeService,
                            PositionScopeService positionScopeService) {
        this.deptTreeService = deptTreeService;
        this.positionScopeService = positionScopeService;
    }

    /**
     * 依据当前登录人权限，把可见范围注入到 filter。
     * 有效范围 = 职能天花板 ∩ 岗位结构范围（取窄）；空集合 fail-closed。
     */
    public void apply(DataScopeFilter filter, String allPerm, String deptPerm) {
        LoginUser user = currentUser();

        // 职能天花板：allPerm → 全公司域；deptPerm → 部门域；否则仅本人
        boolean ceilingAll = !allPerm.isEmpty() && hasAuthority(allPerm);
        boolean ceilingDept = !deptPerm.isEmpty() && hasAuthority(deptPerm);
        if (!ceilingAll && !ceilingDept) {
            // 仅本人域（fail-closed）
            filter.setScopeUserId(user.getUserId());
            filter.setScopeDeptIds(null);
            filter.setDeptId(null);
            return;
        }

        // 岗位结构范围：own → 仅本人；其余 → 可见部门集合（含保密 overlay：保密部门严格本部门）
        String scope = positionScopeService.currentScope();
        Set<Integer> visible = positionScopeService.currentVisibleDepts();
        if ("own".equals(scope) || visible.isEmpty()) {
            // 未分配部门/仅本人岗：降级为仅本人，绝不让空集合流入 SQL 导致部门过滤被跳过
            filter.setScopeUserId(user.getUserId());
            filter.setScopeDeptIds(null);
            filter.setDeptId(null);
            return;
        }

        // 天花板与岗位均为全范围 → 原全量语义（含指定部门子树查询）
        if (ceilingAll && "all".equals(scope)) {
            filter.setScopeUserId(null);
            filter.setScopeDeptIds(filter.getDeptId() == null
                    ? null
                    : new ArrayList<>(deptTreeService.subtree(filter.getDeptId())));
            return;
        }

        // 部门域：有效范围 = 岗位可见部门；指定部门时取交集，为空即越权，绝不退化为全量
        List<Integer> allowed = new ArrayList<>(visible);
        if (filter.getDeptId() != null) {
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
}
