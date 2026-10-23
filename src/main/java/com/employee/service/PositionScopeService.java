package com.employee.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.employee.entity.Department;
import com.employee.entity.Position;
import com.employee.entity.User;
import com.employee.mapper.DepartmentMapper;
import com.employee.mapper.PositionMapper;
import com.employee.mapper.UserMapper;
import com.employee.security.LoginUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 岗位数据范围解析（数据范围岗位化，2026-10-22 定案）。
 *
 * 三正交干净切分：角色=职能域（能做什么操作），岗位=数据范围（能看哪些行），
 * 权重=操作门槛（W1-W4），部门属性=保密例外（confidential 严格本部门不外溢）。
 *
 * 数据范围是岗位属性（sys_positions.data_scope），谁坐这个位子适用同一套规则，杜绝人治特例：
 *   own=仅本人相关行；dept=本部门；dept_subtree=本部门+子部门（平铺期无子部门=退化为本部门）；
 *   all=全公司。
 *
 * 保密 overlay（与查看范围正交，任何等级都适用）：
 *   保密隔离部门的行，只有严格本部门成员可见（不外溢子树）；老板（super_admin）含保密全范围（只看不动）。
 */
@Service
public class PositionScopeService {

    private final UserMapper userMapper;
    private final PositionMapper positionMapper;
    private final DepartmentMapper departmentMapper;
    private final DeptTreeService deptTreeService;

    public PositionScopeService(UserMapper userMapper, PositionMapper positionMapper,
                                DepartmentMapper departmentMapper, DeptTreeService deptTreeService) {
        this.userMapper = userMapper;
        this.positionMapper = positionMapper;
        this.departmentMapper = departmentMapper;
        this.deptTreeService = deptTreeService;
    }

    /** 当前登录人（未登录返回 null） */
    private LoginUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof LoginUser loginUser)) {
            return null;
        }
        return loginUser;
    }

    /** 当前登录人所属部门 id（查不到返回 null） */
    private Integer currentDeptId() {
        LoginUser user = currentUser();
        if (user == null) {
            return null;
        }
        User dbUser = userMapper.selectById(user.getUserId());
        return dbUser != null ? dbUser.getDepartmentId() : null;
    }

    /**
     * 当前登录人岗位的 data_scope（fail-closed：查不到岗位/取值非法 → 按最窄的 dept 处理）。
     */
    public String currentScope() {
        LoginUser user = currentUser();
        if (user == null) {
            return "own";
        }
        User dbUser = userMapper.selectById(user.getUserId());
        if (dbUser == null || dbUser.getPositionId() == null) {
            return "own";
        }
        Position position = positionMapper.selectById(dbUser.getPositionId());
        String scope = position != null ? position.getDataScope() : null;
        if (scope == null || !(scope.equals("own") || scope.equals("dept")
                || scope.equals("dept_subtree") || scope.equals("all"))) {
            return "dept";
        }
        return scope;
    }

    /**
     * 当前登录人可见的部门 id 集合（按岗位 data_scope 展开 + 祖先链 + 保密 overlay）。
     * 链内一体原则（2026-10-22 定案）：组是部门的一部分——
     *   可见节点 = 自己节点的祖先链（部门公告向下穿透到组）∪ 自己节点 ∪（dept_subtree 级另加本链子树）；
     *   组公告仍组内私有（兄弟组/部门直属人员不可见，精准投放）。
     * 保密 overlay：保密部门仅对"链外"剥离（链内上级/本组可见），老板含保密全范围（只看不动）。
     * 返回空集合表示"仅本人相关行 + 全员公告"（own 级），调用方据此拼统一谓词：
     *   publisher_id=我 OR type='all' OR (type='department' AND department_id ∈ 本集合)
     */
    public Set<Integer> currentVisibleDepts() {
        LoginUser user = currentUser();
        Set<Integer> result = new LinkedHashSet<>();
        if (user == null) {
            return result;
        }
        Integer myDept = currentDeptId();
        String scope = currentScope();
        boolean boss = "super_admin".equals(user.getRoleCode());

        if ("own".equals(scope)) {
            return result; // 空集=仅本人相关
        }

        // 我的链（用于保密 overlay 的"链内"判定）：祖先链 + 自己 + 本链子树
        Set<Integer> myChain = new LinkedHashSet<>();
        if (myDept != null) {
            myChain.addAll(ancestorIds(myDept));
            myChain.add(myDept);
            myChain.addAll(deptTreeService.subtree(myDept));
        }

        // 展开基础集合：祖先链（部门公告穿透到组）∪ 自己节点 ∪（dept_subtree 另加子树）
        if ("all".equals(scope)) {
            departmentMapper.selectList(new QueryWrapper<Department>())
                    .forEach(d -> result.add(d.getId()));
        } else {
            if (myDept != null) {
                result.addAll(ancestorIds(myDept)); // 组员可见本部门及上级部门的公告
                result.add(myDept);
                if ("dept_subtree".equals(scope)) {
                    result.addAll(deptTreeService.subtree(myDept)); // 负责人管全链（含各组）
                }
            }
        }

        // 保密 overlay：保密部门仅对链外剥离（链内上级/本组可见）；老板豁免（含保密，只看不动）
        if (!boss) {
            Set<Integer> conf = new LinkedHashSet<>();
            departmentMapper.selectList(new QueryWrapper<Department>().eq("confidential", 1))
                    .forEach(d -> conf.add(d.getId()));
            if (!conf.isEmpty()) {
                result.removeIf(id -> conf.contains(id) && !myChain.contains(id));
            }
        }
        return result;
    }

    /** 祖先链 id 集合（沿 parent_id 向上，含各级上级但不含自己）；防环：最多上溯 10 层 */
    private Set<Integer> ancestorIds(Integer deptId) {
        Set<Integer> result = new LinkedHashSet<>();
        Integer current = deptId;
        for (int i = 0; i < 10 && current != null; i++) {
            Department d = departmentMapper.selectById(current);
            if (d == null || d.getParentId() == null) {
                break;
            }
            result.add(d.getParentId());
            current = d.getParentId();
        }
        return result;
    }
}
