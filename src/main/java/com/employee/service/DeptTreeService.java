package com.employee.service;

import com.employee.mapper.DepartmentMapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 部门子树缓存服务（Phase 0-8）。
 * 展开某部门「自身 + 全部后代」的 ID 集合，是所有 @DataScope 部门级查询的公共热点，
 * 部门树极少变动，按 rootId 缓存收益最大。独立成 bean 以避免同类自调用导致 @Cacheable 失效。
 * 部门增删改（区域八 8-1）时调用 {@link #evictAll()} 即时失效。
 */
@Service
public class DeptTreeService {

    private static final int MAX_DEPT_DEPTH = 10;

    private final DepartmentMapper departmentMapper;

    public DeptTreeService(DepartmentMapper departmentMapper) {
        this.departmentMapper = departmentMapper;
    }

    @Cacheable(value = "dept:subtree", key = "#rootId", unless = "#rootId == null")
    public Set<Integer> subtree(Integer rootId) {
        Set<Integer> result = new LinkedHashSet<>();
        if (rootId == null) {
            return result;
        }
        Deque<Integer> queue = new ArrayDeque<>();
        queue.add(rootId);
        result.add(rootId);
        Set<Integer> visited = new HashSet<>();
        visited.add(rootId);
        int guard = 0;
        while (!queue.isEmpty() && guard++ < MAX_DEPT_DEPTH * MAX_DEPT_DEPTH) {
            Integer current = queue.poll();
            for (Integer childId : departmentMapper.selectChildIds(current)) {
                if (visited.add(childId)) {
                    result.add(childId);
                    queue.add(childId);
                }
            }
        }
        return result;
    }

    /** 清空部门子树缓存（部门结构变更后调用） */
    @CacheEvict(value = "dept:subtree", allEntries = true)
    public void evictAll() {
        // 仅用于触发缓存失效
    }
}
