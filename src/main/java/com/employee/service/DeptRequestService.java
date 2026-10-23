package com.employee.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.employee.dto.DepartmentDTO;
import com.employee.dto.DeptRequestDTO;
import com.employee.entity.Department;
import com.employee.entity.DeptChangeRequest;
import com.employee.entity.User;
import com.employee.exception.BusinessException;
import com.employee.mapper.DepartmentMapper;
import com.employee.mapper.DeptChangeRequestMapper;
import com.employee.mapper.UserMapper;
import com.employee.security.LoginUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 编制变更申请服务（二期-1 编制申请流）。
 * 三段式治理：部门负责人/主管申请（dept:manage:request + W2）→ 老板审批（W4）→ 人事执行建制（org:manage + W3）。
 * 业务原因：编制变更是公司治理事项（动预算/汇报线），建制权与申请权分离，杜绝"谁都能建部门"稀释数据范围子树。
 * 驳回原因可见性与公告同口径：仅申请人/审批人可见，其余人查询时脱敏置空。
 */
@Service
public class DeptRequestService {

    private final DeptChangeRequestMapper requestMapper;
    private final UserMapper userMapper;
    private final DepartmentMapper departmentMapper;
    private final DepartmentService departmentService;
    private final PositionScopeService positionScopeService;

    public DeptRequestService(DeptChangeRequestMapper requestMapper, UserMapper userMapper,
                              DepartmentMapper departmentMapper, DepartmentService departmentService,
                              PositionScopeService positionScopeService) {
        this.requestMapper = requestMapper;
        this.userMapper = userMapper;
        this.departmentMapper = departmentMapper;
        this.departmentService = departmentService;
        this.positionScopeService = positionScopeService;
    }

    private LoginUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof LoginUser loginUser)) {
            throw new BusinessException(401, "未登录");
        }
        return loginUser;
    }

    /** 全量视图：老板（super_admin）或建制执行人（org:manage，即人事）可看全部申请 */
    private boolean canSeeAll() {
        LoginUser user = currentUser();
        return "super_admin".equals(user.getRoleCode()) || hasOrgManage();
    }

    /** 执行建制权（org:manage）判定 */
    private boolean hasOrgManage() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "org:manage".equals(a.getAuthority()));
    }

    // ==================== 列表（申请人看自己；老板/人事看全部） ====================

    public Map<String, Object> list(int page, int size) {
        LoginUser user = currentUser();
        QueryWrapper<DeptChangeRequest> qw = new QueryWrapper<>();
        if (!canSeeAll()) {
            qw.eq("applicant_id", user.getUserId());
        }
        qw.orderByDesc("created_at");
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<DeptChangeRequest> p =
                requestMapper.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size), qw);
        List<DeptChangeRequest> rows = p.getRecords();
        fillNames(rows);
        // 驳回原因脱敏：仅申请人/审批人可见
        rows.forEach(r -> maskRejectReason(r, user.getUserId()));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", rows);
        result.put("total", p.getTotal());
        result.put("canApprove", "super_admin".equals(user.getRoleCode()));
        result.put("canExecute", hasOrgManage());
        // 申请范围=岗位数据范围（2026-10-22 定案）：前端下拉只列可申请的部门，与后端校验同一套边界
        java.util.Set<Integer> scopeIds = positionScopeService.currentVisibleDepts();
        boolean scopeAll = "all".equals(positionScopeService.currentScope()) || hasOrgManage();
        List<Map<String, Object>> deptOptions = new java.util.ArrayList<>();
        List<Department> depts = scopeAll
                ? departmentMapper.selectList(new QueryWrapper<Department>())
                : (scopeIds.isEmpty() ? List.of() : departmentMapper.selectBatchIds(scopeIds));
        for (Department d : depts) {
            Map<String, Object> opt = new LinkedHashMap<>();
            opt.put("id", d.getId());
            opt.put("name", d.getName());
            deptOptions.add(opt);
        }
        result.put("deptOptions", deptOptions);
        result.put("canScopeAll", scopeAll);
        return result;
    }

    // ==================== 提交申请 ====================

    public void submit(DeptRequestDTO dto) {
        LoginUser user = currentUser();
        String type = dto.getType();
        // 申请范围=岗位数据范围（2026-10-22 定案）：只能申请自己管辖范围内的编制变更，
        // 防止部门负责人越权提案调整其他部门；一级部门设立=公司级事项，仅总经理/人事可提。
        java.util.Set<Integer> scopeIds = positionScopeService.currentVisibleDepts();
        boolean scopeAll = "all".equals(positionScopeService.currentScope()) || hasOrgManage();
        if ("create".equals(type)) {
            if (isBlank(dto.getName()) || isBlank(dto.getCode())) {
                throw new BusinessException(400, "增设部门需填写部门名称与字母代码");
            }
            if (dto.getParentId() == null && !scopeAll) {
                throw new BusinessException(403, "设立一级部门是公司级事项，仅总经理/人事可申请");
            }
            if (dto.getParentId() != null && !scopeAll && !scopeIds.contains(dto.getParentId())) {
                throw new BusinessException(403, "只能申请自己管辖范围内的部门变更");
            }
            if (departmentMapper.selectCount(new QueryWrapper<Department>().eq("code", dto.getCode())) > 0) {
                throw new BusinessException(400, "部门字母「" + dto.getCode() + "」已被占用，请更换");
            }
            if (dto.getParentId() != null && departmentMapper.selectById(dto.getParentId()) == null) {
                throw new BusinessException(400, "上级部门不存在");
            }
        } else if ("disable".equals(type)) {
            if (dto.getTargetDeptId() == null || departmentMapper.selectById(dto.getTargetDeptId()) == null) {
                throw new BusinessException(400, "撤编申请需指定存在的目标部门");
            }
            if (!scopeAll && !scopeIds.contains(dto.getTargetDeptId())) {
                throw new BusinessException(403, "只能申请自己管辖范围内的部门变更");
            }
        } else { // update
            if (dto.getTargetDeptId() == null || departmentMapper.selectById(dto.getTargetDeptId()) == null) {
                throw new BusinessException(400, "调整申请需指定存在的目标部门");
            }
            if (!scopeAll && !scopeIds.contains(dto.getTargetDeptId())) {
                throw new BusinessException(403, "只能申请自己管辖范围内的部门变更");
            }
            if (isBlank(dto.getName()) || isBlank(dto.getCode())) {
                throw new BusinessException(400, "调整申请需填写新的部门名称与字母代码");
            }
        }

        DeptChangeRequest req = new DeptChangeRequest();
        req.setType(type);
        req.setTargetDeptId(dto.getTargetDeptId());
        req.setName(dto.getName());
        req.setCode(dto.getCode());
        req.setParentId(dto.getParentId());
        req.setReason(dto.getReason().trim());
        req.setApplicantId(user.getUserId());
        req.setStatus("pending");
        requestMapper.insert(req);
    }

    // ==================== 审批（老板 W4，Controller 门槛） ====================

    public void approve(Integer id) {
        DeptChangeRequest req = getOr404(id);
        if (!"pending".equals(req.getStatus())) {
            throw new BusinessException(400, "只有待审批的申请才能审批");
        }
        req.setStatus("approved");
        req.setApproverId(currentUser().getUserId());
        req.setDecidedAt(LocalDateTime.now());
        requestMapper.updateById(req);
    }

    public void reject(Integer id, String reason) {
        DeptChangeRequest req = getOr404(id);
        if (!"pending".equals(req.getStatus())) {
            throw new BusinessException(400, "只有待审批的申请才能驳回");
        }
        // 驳回原因必填（与公告同口径）：空原因让申请人无法定位问题
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(400, "驳回原因不能为空");
        }
        req.setStatus("rejected");
        req.setRejectReason(reason.trim());
        req.setApproverId(currentUser().getUserId());
        req.setDecidedAt(LocalDateTime.now());
        requestMapper.updateById(req);
    }

    // ==================== 执行建制（人事 org:manage + W3，Controller 门槛） ====================

    @Transactional
    public void execute(Integer id) {
        DeptChangeRequest req = getOr404(id);
        if (!"approved".equals(req.getStatus())) {
            throw new BusinessException(400, "只有审批通过的申请才能执行建制");
        }
        if ("create".equals(req.getType())) {
            DepartmentDTO dto = new DepartmentDTO();
            dto.setName(req.getName());
            dto.setCode(req.getCode());
            dto.setParentId(req.getParentId());
            departmentService.create(dto);
            // code 全局唯一，建制后按 code 回查新部门 id 留档
            Department created = departmentMapper.selectOne(new QueryWrapper<Department>().eq("code", req.getCode()));
            req.setResultDeptId(created != null ? created.getId() : null);
        } else if ("disable".equals(req.getType())) {
            departmentService.disable(req.getTargetDeptId());
        } else { // update：仅更新名称/字母/上级，负责人与保密标志保持原值
            Department target = departmentMapper.selectById(req.getTargetDeptId());
            DepartmentDTO dto = new DepartmentDTO();
            dto.setName(req.getName());
            dto.setCode(req.getCode());
            dto.setParentId(req.getParentId() != null ? req.getParentId() : target.getParentId());
            dto.setManagerId(target.getManagerId());
            dto.setConfidential(target.getConfidential());
            departmentService.update(req.getTargetDeptId(), dto);
        }
        req.setStatus("done");
        req.setDoneAt(LocalDateTime.now());
        requestMapper.updateById(req);
    }

    // ==================== 私有 ====================

    private DeptChangeRequest getOr404(Integer id) {
        DeptChangeRequest req = requestMapper.selectById(id);
        if (req == null) {
            throw new BusinessException(404, "申请不存在");
        }
        return req;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** 填充申请人/审批人/部门展示名（批量查库防 N+1） */
    private void fillNames(List<DeptChangeRequest> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Set<Integer> userIds = rows.stream()
                .map(r -> r.getApplicantId() != null ? r.getApplicantId() : r.getApproverId())
                .filter(Objects::nonNull).collect(Collectors.toSet());
        rows.stream().map(DeptChangeRequest::getApproverId).filter(Objects::nonNull).forEach(userIds::add);
        Map<Integer, String> userNames = new LinkedHashMap<>();
        if (!userIds.isEmpty()) {
            userMapper.selectBatchIds(userIds).forEach(u -> userNames.put(u.getId(), u.getRealName()));
        }
        Set<Integer> deptIds = rows.stream()
                .map(r -> r.getResultDeptId() != null ? r.getResultDeptId() : r.getTargetDeptId())
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Integer, String> deptNames = new LinkedHashMap<>();
        if (!deptIds.isEmpty()) {
            departmentMapper.selectBatchIds(deptIds).forEach(d -> deptNames.put(d.getId(), d.getName()));
        }
        for (DeptChangeRequest r : rows) {
            r.setApplicantName(userNames.get(r.getApplicantId()));
            r.setApproverName(r.getApproverId() != null ? userNames.get(r.getApproverId()) : null);
            Integer deptId = r.getResultDeptId() != null ? r.getResultDeptId() : r.getTargetDeptId();
            r.setDeptName(deptId != null ? deptNames.get(deptId) : null);
        }
    }

    /** 驳回原因脱敏（与公告同口径）：仅申请人本人与审批人本人可见 */
    private void maskRejectReason(DeptChangeRequest req, Integer viewerId) {
        if (req == null || req.getRejectReason() == null || viewerId == null) {
            return;
        }
        boolean isApplicant = viewerId.equals(req.getApplicantId());
        boolean isApprover = viewerId.equals(req.getApproverId());
        if (!isApplicant && !isApprover) {
            req.setRejectReason(null);
        }
    }
}
