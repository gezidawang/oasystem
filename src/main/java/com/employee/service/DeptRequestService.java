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
        return hasAuthority("org:manage");
    }

    /** 当前登录人是否持有指定权限码（服务端权威判定，不依赖前端隐藏） */
    private boolean hasAuthority(String code) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> code.equals(a.getAuthority()));
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
        // 能力位改按新审批码判定（2026-10-24 权限收敛：老板剥 request/org:manage、只持 dept:manage:approve）；
        // 前端按钮显隐仅体验层，真正的门槛在 Controller 码+职级双闸门
        result.put("canApprove", hasAuthority("dept:manage:approve"));
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
            // 撤编提交即预检（与 DepartmentService.disable 硬约束同一套口径，2026-10-24 DOA 修补）：
            // 业务原因：防止"申请能提、老板能批、执行必败"的死单——在职员工/子部门未安置前根本不该进入审批流；
            // 正规路径：先走异动调岗或离职安置（8-3 离职联动停用账号），或改申"调整"把人员并入其他部门后再撤。
            Department target = departmentMapper.selectById(dto.getTargetDeptId());
            List<Integer> children = departmentMapper.selectChildIds(dto.getTargetDeptId());
            if (!children.isEmpty()) {
                throw new BusinessException(400, "该部门下还有 " + children.size() + " 个子部门，请先撤销/并入子部门再申请撤编");
            }
            Long activeUsers = userMapper.selectCount(new QueryWrapper<User>()
                    .eq("department_id", dto.getTargetDeptId()).eq("status", 1));
            if (activeUsers > 0) {
                throw new BusinessException(400, "该部门下还有 " + activeUsers + " 名在职员工，无法申请撤编：请先通过异动调岗/离职安置全部人员（离职=账号停用后不再计入在职）");
            }
            if (target.getStatus() != null && target.getStatus() == 0) {
                throw new BusinessException(400, "该部门已是停用状态，无需重复申请撤编（删除请走部门管理 30 天冷却期通道）");
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
            // 调整提交即预检字母占用（排除自身）：与 create 的 code 唯一校验同口径，消灭"执行时才撞车"窗口
            if (departmentMapper.selectCount(new QueryWrapper<Department>()
                    .eq("code", dto.getCode()).ne("id", dto.getTargetDeptId())) > 0) {
                throw new BusinessException(400, "部门字母「" + dto.getCode() + "」已被其他部门占用，请更换");
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

    /**
     * 审批通过（DOA 职责分离硬判定，2026-10-24）：审批人不得是申请人本人——
     * 业务原因：提案权与拍板权必须分属不同人，否则"自审自批闭环"使三段式形同虚设；
     * 老板的建制需求由人事/部门代为提案（不给任何人开特例通道，缺位靠上提不靠摊薄）。
     * failed 态允许重新审批（整改后重试，配合执行失败回退机制消灭死单）。
     */
    public void approve(Integer id) {
        DeptChangeRequest req = getOr404(id);
        if (!"pending".equals(req.getStatus()) && !"failed".equals(req.getStatus())) {
            throw new BusinessException(400, "只有待审批或执行失败的申请才能审批");
        }
        requireNotSelfInvolved(req, "审批");
        req.setStatus("approved");
        req.setFailReason(null);
        req.setApproverId(currentUser().getUserId());
        req.setDecidedAt(LocalDateTime.now());
        requestMapper.updateById(req);
    }

    /** 驳回：同样受职责分离约束（申请人不能自行驳回自己的提案）；approved 态可撤回终止（撤单归审批方） */
    public void reject(Integer id, String reason) {
        DeptChangeRequest req = getOr404(id);
        if (!List.of("pending", "approved", "failed").contains(req.getStatus())) {
            throw new BusinessException(400, "只有待审批/待执行/执行失败的申请才能驳回终止");
        }
        // 驳回原因必填（与公告同口径）：空原因让申请人无法定位问题
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(400, "驳回原因不能为空");
        }
        requireNotSelfInvolved(req, "驳回");
        req.setStatus("rejected");
        req.setRejectReason(reason.trim());
        req.setApproverId(currentUser().getUserId());
        req.setDecidedAt(LocalDateTime.now());
        requestMapper.updateById(req);
    }

    /**
     * 申请人撤回自己的提案（2026-10-24 口径补全）：撤回=收回提案，是提案权的延伸而非审批动作，
     * 不受"审批人≠申请人"限制；否则申请人自提的在途单既无人能批（SoD 拦截）又无人能终止，成为新死单。
     * 仅限 pending/approved（未执行）状态；执行后 done 不可撤回。复用 rejected 态归档（原因固定为主动撤回，
     * 可见性仍按"申请人/审批人双方"脱敏口径）。
     */
    public void withdraw(Integer id) {
        DeptChangeRequest req = getOr404(id);
        if (!currentUser().getUserId().equals(req.getApplicantId())) {
            throw new BusinessException(403, "只能撤回自己提交的申请（终止他人提案请用驳回，归审批方）");
        }
        if (!List.of("pending", "approved").contains(req.getStatus())) {
            throw new BusinessException(400, "只有待审批/待执行的提案可以撤回");
        }
        req.setStatus("rejected");
        req.setRejectReason("申请人主动撤回");
        req.setDecidedAt(LocalDateTime.now());
        requestMapper.updateById(req);
    }

    // ==================== 执行建制（人事 org:manage + W3，Controller 门槛） ====================

    /**
     * 执行建制（三权分立第三段，2026-10-24）：执行人不得是申请人、也不得是本单审批人——
     * 提案/拍板/经手三段必须分属不同人；人事自己的编制调整需由需求部门或行政提案。
     * 执行失败回退：落地撞硬约束（如字母被并发占用）时单据回退 failed 态并留痕 fail_reason，
     * 老板可整改后重新审批或驳回终止，杜绝"永远关不掉的死单"。
     * 注：本方法不加 @Transactional——落地调用各自独立成事务，失败不连坐单据更新（否则回滚标记会吞掉 failed 留痕）。
     */
    public void execute(Integer id) {
        DeptChangeRequest req = getOr404(id);
        if (!"approved".equals(req.getStatus())) {
            throw new BusinessException(400, "只有审批通过的申请才能执行建制");
        }
        Integer me = currentUser().getUserId();
        if (me.equals(req.getApplicantId())) {
            throw new BusinessException(403, "执行人不得是申请人本人（三权分立：提案/拍板/经手分属不同人）");
        }
        if (me.equals(req.getApproverId())) {
            throw new BusinessException(403, "执行人不得是本单审批人（审批与经手分离）");
        }
        try {
            applyChange(req);
        } catch (BusinessException e) {
            // 执行失败：单据回退 failed + 留痕（系统校验信息，不脱敏），原异常继续上抛给操作人
            req.setStatus("failed");
            req.setFailReason(e.getMessage());
            requestMapper.updateById(req);
            throw e;
        }
        req.setStatus("done");
        req.setDoneAt(LocalDateTime.now());
        requestMapper.updateById(req);
    }

    /** 落地建制：按类型调部门管理同一套 Service（执行段零另起炉灶，与直办通道同一校验链） */
    private void applyChange(DeptChangeRequest req) {
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
    }

    // ==================== 私有 ====================

    /** 职责分离硬判定：审批/驳回段不得由申请人本人经手（自审自批一律 403，DOA 铁律） */
    private void requireNotSelfInvolved(DeptChangeRequest req, String action) {
        if (currentUser().getUserId().equals(req.getApplicantId())) {
            throw new BusinessException(403, "不能" + action + "自己提交的申请（提案与拍板职责分离）");
        }
    }

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
