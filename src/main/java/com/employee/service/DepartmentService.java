package com.employee.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.employee.dto.DepartmentDTO;
import com.employee.dto.DepartmentTreeVO;
import com.employee.entity.Department;
import com.employee.entity.User;
import com.employee.exception.BusinessException;
import com.employee.mapper.DepartmentMapper;
import com.employee.mapper.UserMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 部门管理服务（Phase 1, 8-1）。
 * 提供树查询、新增、修改、停用；操作后清部门子树缓存。
 */
@Service
public class DepartmentService {

    private final DepartmentMapper departmentMapper;
    private final UserMapper userMapper;
    private final DeptTreeService deptTreeService;
    private final NoticeService noticeService;

    public DepartmentService(DepartmentMapper departmentMapper, UserMapper userMapper,
                             DeptTreeService deptTreeService, NoticeService noticeService) {
        this.departmentMapper = departmentMapper;
        this.userMapper = userMapper;
        this.deptTreeService = deptTreeService;
        this.noticeService = noticeService;
    }

    // ==================== 查询 ====================

    /** 获取完整部门树（含停用，管理视角） */
    public List<DepartmentTreeVO> getTree() {
        List<Department> all = departmentMapper.selectList(
                new QueryWrapper<Department>().orderByAsc("id"));
        // 批量查 manager 姓名
        Set<Integer> managerIds = all.stream()
                .map(Department::getManagerId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Integer, String> nameMap = new HashMap<>();
        if (!managerIds.isEmpty()) {
            List<User> managers = userMapper.selectBatchIds(managerIds);
            managers.forEach(u -> nameMap.put(u.getId(), u.getRealName()));
        }
        // 构建树
        Map<Integer, DepartmentTreeVO> voMap = new LinkedHashMap<>();
        for (Department d : all) {
            DepartmentTreeVO vo = toVO(d, nameMap);
            vo.setChildren(new ArrayList<>());
            voMap.put(d.getId(), vo);
        }
        List<DepartmentTreeVO> roots = new ArrayList<>();
        for (DepartmentTreeVO vo : voMap.values()) {
            if (vo.getParentId() == null || !voMap.containsKey(vo.getParentId())) {
                roots.add(vo);
            } else {
                voMap.get(vo.getParentId()).getChildren().add(vo);
            }
        }
        return roots;
    }

    // ==================== 新增 ====================

    public void create(DepartmentDTO dto) {
        validateCodeUnique(dto.getCode(), null);
        validateParentExists(dto.getParentId());
        Department dept = new Department();
        dept.setName(dto.getName());
        dept.setCode(dto.getCode());
        dept.setParentId(dto.getParentId());
        dept.setManagerId(dto.getManagerId());
        dept.setConfidential(resolveConfidential(dto.getConfidential(), 0));
        dept.setStatus(1);
        departmentMapper.insert(dept);
        deptTreeService.evictAll();
    }

    // ==================== 修改 ====================

    public void update(Integer id, DepartmentDTO dto) {
        Department existing = departmentMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "部门不存在");
        }
        validateCodeUnique(dto.getCode(), id);
        validateParentExists(dto.getParentId());
        // 防止自己是自己的父级
        if (Objects.equals(dto.getParentId(), id)) {
            throw new BusinessException(400, "上级部门不能是自己");
        }
        // 防环：上级部门不能是自己的子孙部门（否则部门树成环，subtree 范围解析会无限递归）
        if (dto.getParentId() != null && deptTreeService.subtree(id).contains(dto.getParentId())) {
            throw new BusinessException(400, "上级部门不能是自己的下级部门");
        }
        existing.setName(dto.getName());
        existing.setCode(dto.getCode());
        existing.setParentId(dto.getParentId());
        existing.setManagerId(dto.getManagerId());
        existing.setConfidential(resolveConfidential(dto.getConfidential(), existing.getConfidential()));
        departmentMapper.updateById(existing);
        deptTreeService.evictAll();
    }

    /**
     * 保密隔离标志变更拦截：隔离是治理决策，仅老板或行政管理层（notice:manage:all + W3）可设置；
     * 其他角色提交不同值直接 403，提交相同/缺省值保持原样。
     */
    private Integer resolveConfidential(Integer requested, Integer current) {
        int cur = current == null ? 0 : current;
        if (requested == null || requested.equals(cur)) {
            return cur;
        }
        if (!noticeService.isSuperAdmin() && !noticeService.hasManageOverride()) {
            throw new BusinessException(403, "保密隔离标志仅老板/行政管理层可设置");
        }
        return requested == 1 ? 1 : 0;
    }

    // ==================== 停用 ====================

    public void disable(Integer id) {
        Department dept = departmentMapper.selectById(id);
        if (dept == null) {
            throw new BusinessException(404, "部门不存在");
        }
        // 有子部门不允许停用
        List<Integer> children = departmentMapper.selectChildIds(id);
        if (!children.isEmpty()) {
            throw new BusinessException(400, "该部门下有子部门，请先处理子部门");
        }
        // 有在职员工不允许停用
        Long userCount = userMapper.selectCount(
                new QueryWrapper<User>().eq("department_id", id).eq("status", 1));
        if (userCount > 0) {
            throw new BusinessException(400, "该部门下还有 " + userCount + " 名在职员工，无法停用");
        }
        dept.setStatus(0);
        dept.setDisabledAt(LocalDateTime.now());
        departmentMapper.updateById(dept);
        deptTreeService.evictAll();
    }

    // ==================== 启用（重新激活，清空删除计时） ====================

    public void enable(Integer id) {
        Department dept = departmentMapper.selectById(id);
        if (dept == null) {
            throw new BusinessException(404, "部门不存在");
        }
        if (dept.getStatus() == 1) {
            throw new BusinessException(400, "该部门已经是启用状态");
        }
        dept.setStatus(1);
        dept.setDisabledAt(null); // 清空停用时间，重置删除冷却计时
        departmentMapper.updateById(dept);
        deptTreeService.evictAll();
    }

    // ==================== 删除（停用超过一个月才可） ====================

    public void delete(Integer id) {
        Department dept = departmentMapper.selectById(id);
        if (dept == null) {
            throw new BusinessException(404, "部门不存在");
        }
        if (dept.getStatus() != 0) {
            throw new BusinessException(400, "只能删除已停用的部门，请先停用再删除");
        }
        if (dept.getDisabledAt() == null) {
            throw new BusinessException(400, "该部门停用时间未记录，无法判断冷却期，请联系管理员");
        }
        // 停用超过 30 天才允许删除
        LocalDateTime oneMonthAgo = LocalDateTime.now().minusDays(30);
        if (dept.getDisabledAt().isAfter(oneMonthAgo)) {
            long daysSinceDisabled = java.time.Duration.between(dept.getDisabledAt(), LocalDateTime.now()).toDays();
            long remainingDays = 30 - daysSinceDisabled;
            throw new BusinessException(400, "停用未满一个月，还需等待约 " + remainingDays + " 天才可删除");
        }
        // 确认无子部门
        List<Integer> children = departmentMapper.selectChildIds(id);
        if (!children.isEmpty()) {
            throw new BusinessException(400, "该部门下仍有子部门，无法删除");
        }
        // 确认无任何员工关联
        Long userCount = userMapper.selectCount(
                new QueryWrapper<User>().eq("department_id", id));
        if (userCount > 0) {
            throw new BusinessException(400, "该部门下仍有 " + userCount + " 名员工记录，无法删除");
        }
        departmentMapper.deleteById(id);
        deptTreeService.evictAll();
    }

    // ==================== 私有校验 ====================

    private void validateCodeUnique(String code, Integer excludeId) {
        QueryWrapper<Department> qw = new QueryWrapper<Department>().eq("code", code);
        if (excludeId != null) {
            qw.ne("id", excludeId);
        }
        if (departmentMapper.selectCount(qw) > 0) {
            throw new BusinessException(400, "部门字母「" + code + "」已被占用");
        }
    }

    private void validateParentExists(Integer parentId) {
        if (parentId != null && departmentMapper.selectById(parentId) == null) {
            throw new BusinessException(400, "上级部门不存在");
        }
    }

    private DepartmentTreeVO toVO(Department d, Map<Integer, String> nameMap) {
        DepartmentTreeVO vo = new DepartmentTreeVO();
        vo.setId(d.getId());
        vo.setName(d.getName());
        vo.setCode(d.getCode());
        vo.setParentId(d.getParentId());
        vo.setManagerId(d.getManagerId());
        vo.setManagerName(d.getManagerId() != null ? nameMap.get(d.getManagerId()) : null);
        vo.setStatus(d.getStatus());
        vo.setConfidential(d.getConfidential());
        return vo;
    }
}
