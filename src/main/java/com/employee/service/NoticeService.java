package com.employee.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.employee.entity.Notice;
import com.employee.entity.NoticeRead;
import com.employee.entity.Position;
import com.employee.entity.Department;
import com.employee.entity.User;
import com.employee.exception.BusinessException;
import com.employee.mapper.DepartmentMapper;
import com.employee.mapper.NoticeMapper;
import com.employee.mapper.NoticeReadMapper;
import com.employee.mapper.PositionMapper;
import com.employee.mapper.UserMapper;
import com.employee.security.LoginUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 公告通知服务（区域二 2-1，分级发布 + 审批控制）。
 *
 * 权限分级：
 * - 老板/行政：可直接发布全员公告 + 部门公告。
 * - 部门负责人(W2/W3，含人事总监)：可直接发布本部门公告；发全员公告需提交申请。
 * - 普通员工(W1)：只能申请发本部门公告。
 *
 * 状态流转：draft → pending → published / rejected; 或直接 draft → published。
 * 编辑窗口：发布后（或审批通过后）4小时内可二次编辑。
 * 草稿生命周期：10天自动清除（定时任务 Phase 7）。
 */
@Service
public class NoticeService {

    private static final int EDIT_WINDOW_HOURS = 4;
    private static final int DRAFT_RETENTION_DAYS = 10;
    /** 行政治理窗口定案（2026-10-21）：行政借治理权删/撤他人仅限发布后 2 小时内且限发布当日；本人/链内领导层不受限 */
    private static final int GOVERN_DELETE_WINDOW_HOURS = 2;

    private final NoticeMapper noticeMapper;
    private final NoticeReadMapper noticeReadMapper;
    private final UserMapper userMapper;
    private final PositionMapper positionMapper;
    private final DepartmentMapper departmentMapper;
    private final DeptTreeService deptTreeService;
    private final PositionScopeService positionScopeService;

    public NoticeService(NoticeMapper noticeMapper, NoticeReadMapper noticeReadMapper,
                         UserMapper userMapper, PositionMapper positionMapper,
                         DepartmentMapper departmentMapper, DeptTreeService deptTreeService,
                         PositionScopeService positionScopeService) {
        this.noticeMapper = noticeMapper;
        this.noticeReadMapper = noticeReadMapper;
        this.userMapper = userMapper;
        this.positionMapper = positionMapper;
        this.departmentMapper = departmentMapper;
        this.deptTreeService = deptTreeService;
        this.positionScopeService = positionScopeService;
    }

    /** 当前用户发布/置顶能力汇总（前端按钮形态依据，按真实部门判定） */
    public Map<String, Object> publishPermission() {
        LoginUser user = currentUser();
        User dbUser = userMapper.selectById(user.getUserId());
        Integer userDeptId = dbUser != null ? dbUser.getDepartmentId() : null;
        Map<String, Object> perm = new LinkedHashMap<>();
        perm.put("canDirectCompany", canDirectPublish("all", userDeptId));
        perm.put("canDirectDept", canDirectPublish("department", userDeptId));
        perm.put("canPinCompany", canPin("all", null, userDeptId));
        perm.put("canPinDept", canPin("department", userDeptId, userDeptId));
        perm.put("manageOverride", hasManageOverride());
        // 部门管理域（直发/审批/删撤本部门公告）：除 publish:dept 码外还需实时职级≥2（主管/负责人），
        // 防止角色码泄漏给部门内 W1 普通成员（如 hr 角色下的人事专员）
        perm.put("canManageDeptScope", isDeptLeaderGrade());
        // 全员公告直发/审批能力（前端待审批 Tab 与按钮以本字段为准）
        perm.put("canApproveCompany", isCompanyApprover());
        // 部门内审审批能力（分级审批 2026-10-24：W3 链头 或 任一节点负责人（含 W2 组长））：前端待审批 Tab 以此为入口依据
        perm.put("canApproveDept", isDeptApprover() || !headDeptIds().isEmpty());
        return perm;
    }

    /** 当前登录人职级（实时查库，查不到返 0） */
    private int currentLevel() {
        LoginUser user = currentUser();
        User dbUser = userMapper.selectById(user.getUserId());
        if (dbUser == null || dbUser.getPositionId() == null) {
            return 0;
        }
        Position position = positionMapper.selectById(dbUser.getPositionId());
        return position != null && position.getLevel() != null ? position.getLevel() : 0;
    }

    /** 部门领导层判定：持 notice:publish:dept 码 且 实时职级≥2（主管/负责人/总监）——直发/删撤/置顶等执行层能力 */
    private boolean isDeptLeaderGrade() {
        return hasAuthority("notice:publish:dept") && currentLevel() >= 2;
    }

    /**
     * 部门审批人判定（2026-10-22 定案，正规企业 DOA 口径）：公告内审审批权收归 W3（部门负责人/总监）。
     * 业务原因：审批=拍板=承担责任，权力与责任对齐；主管(W2)是执行层（初审转呈），无拍板权；
     * 缺位场景由老板兜底（上提一级）覆盖，不靠向主管摊薄审批权。
     */
    private boolean isDeptApprover() {
        return hasAuthority("notice:publish:dept") && currentLevel() >= 3;
    }

    /**
     * 行政管理层判定：持 notice:publish:company 码 且（老板 或 实时职级≥3，与 hasManageOverride 同一套 W3 闸门）。
     * 业务原因：全员公告的直发与审批属行政管理层（W3+）治理权，权限码 alone 不足以放行，
     * 必须叠加职级闸门，防止 W1 专员（如 admin 角色下的吴敏）经由角色授权越权到管理层级。
     */
    private boolean isCompanyApprover() {
        return hasAuthority("notice:publish:company")
                && (isSuperAdmin() || currentLevel() >= 3);
    }

    // ==================== 列表 ====================

    public Map<String, Object> list(String type, String status, String keyword, Integer filterDeptId, Integer managed, Integer manage, int page, int size) {
        LoginUser user = currentUser();
        User dbUser = userMapper.selectById(user.getUserId());
        Integer userDeptId = dbUser != null ? dbUser.getDepartmentId() : null;
        boolean canSeeAllDepts = hasAuthority("notice:publish:company"); // 行政/老板可看任意部门

        QueryWrapper<Notice> qw = new QueryWrapper<>();

        if ("draft".equals(status)) {
            qw.eq("status", "draft").eq("publisher_id", user.getUserId());
        } else if ("pending_approval".equals(status)) {
            // 待审批队列按直属汇报线分级（正规 DOA，2026-10-24）：组长审本组、部门负责人审全链、行政审全员、老板兜底；
            // 自己提交的申请不进自己的审批队列（禁自审自批，组长自提单自动升部门头）
            qw.eq("status", "pending");
            if (isSuperAdmin()) {
                // 老板：全部（含保密部门无负责人时的兜底审批）
            } else if (isCompanyApprover()) {
                // 行政管理层（publish:company+W3，权限码+职级双闸门）：只审全员公告申请，不参与其他部门内部公告审核；
                // 例外：自己兼任某部门负责人/组长时，管辖范围内的内审申请也要进自己的队列
                java.util.Set<Integer> myManaged = new java.util.HashSet<>(headDeptIds());
                if (isDeptApprover() && userDeptId != null) {
                    myManaged.addAll(deptTreeService.subtree(userDeptId));
                }
                if (myManaged.isEmpty()) {
                    qw.eq("type", "all");
                } else {
                    final java.util.Set<Integer> m = myManaged;
                    qw.and(w -> w.eq("type", "all").or().in("department_id", m));
                }
                qw.ne("publisher_id", user.getUserId());
            } else if (isDeptApprover()) {
                // 部门负责人(W3)：看本链（本部门+各组）的待审批（链内一体），另加自己兼任组长的其他节点；不含自己提的
                java.util.Set<Integer> myChain2 = new java.util.HashSet<>(headDeptIds());
                if (userDeptId != null) {
                    myChain2.addAll(deptTreeService.subtree(userDeptId));
                }
                if (myChain2.isEmpty()) {
                    qw.eq("publisher_id", user.getUserId());
                } else {
                    qw.in("department_id", myChain2);
                }
                qw.ne("publisher_id", user.getUserId());
            } else if (isDeptLeaderGrade() && !headDeptIds().isEmpty()) {
                // 组长/节点负责人（W2，manager_id=自己）：审自己负责节点内的申请（直属上级第一审批人）；不含自己提的
                qw.in("department_id", headDeptIds()).ne("publisher_id", user.getUserId());
            } else {
                // 普通员工：只看自己提交的
                qw.eq("publisher_id", user.getUserId());
            }
        } else if ("rejected".equals(status)) {
            // 未通过列表口径（2026-10-21 定案）：只显示①自己提交/发布且被驳回的 ②自己作为审批人驳回的；
            // 其他人的驳回记录一律不显示（含行政/老板）。业务原因：驳回原因属敏感信息仅相关双方可见，
            // 列表范围同步收窄为"与我相关"，避免无关人员借列表窥探他人被驳回记录的存在与内容。
            qw.eq("status", "rejected")
                    .and(w -> w.eq("publisher_id", user.getUserId()).or().eq("approver_id", user.getUserId()));
        } else if ("pending".equals(status)) {
            // “我提交的申请” Tab：只看自己提交的
            qw.eq("status", "pending").eq("publisher_id", user.getUserId());
        } else {
            // 默认：已发布，对读者可见（2026-10-22 数据范围岗位化定案）。
            // 统一谓词 = 自己提交/发布的（无论类型）∪ 全员公告（恒可见基础层）∪ 岗位数据范围内的部门公告；
            // 范围由岗位决定（sys_positions.data_scope → PositionScopeService 解析），谁坐位子适用同一套规则；
            // 保密 overlay：保密部门严格本部门不外溢；老板含保密全范围（只看不动）。
            qw.eq("status", "published");
            if (managed != null && managed == 1 && hasManageOverride()) {
                // 治理视图（读管分离）：行政治理层（notice:manage:all+W3）的跨部门治理入口，
                // 返回非保密全范围可操作行——消除阅读干扰的同时不削弱删/撤/置顶三层口径的治理能力；
                // 无治理权者即使带上参数也回落到普通岗位范围（后端拦截，不依赖前端隐藏）。
                java.util.Set<Integer> conf = confidentialDeptIds();
                if (!conf.isEmpty()) {
                    qw.not(w -> w.eq("type", "department").in("department_id", conf));
                }
            } else if (manage != null && manage == 1) {
                // 管理工作台口径（方案 A 定案 2026-10-23）：公告管理的"已发布"Tab 只显示与己治理相关——
                //   ①自己发布/提交的（无论类型，含自己发的全员）②岗位管辖链内的部门公告（链内一体，保密剥离已含）；
                // 他人发布的全员公告属阅读侧内容，去"公告中心"看，不再混入管理页造成"跨部门可见"的错觉；
                // 后端谓词为准，不依赖前端隐藏；公告中心不带此参，读者口径零影响。
                java.util.Set<Integer> visible = positionScopeService.currentVisibleDepts();
                if (visible.isEmpty()) {
                    qw.eq("publisher_id", user.getUserId());
                } else {
                    qw.and(w -> w.eq("publisher_id", user.getUserId())
                            .or().eq("type", "department").in("department_id", visible));
                }
            } else {
                java.util.Set<Integer> visible = positionScopeService.currentVisibleDepts();
                if (visible.isEmpty()) {
                    // own 级（或无部门）：仅本人相关 + 全员公告
                    qw.and(w -> w.eq("publisher_id", user.getUserId()).or().eq("type", "all"));
                } else {
                    qw.and(w -> w.eq("publisher_id", user.getUserId())
                            .or().eq("type", "all")
                            .or(x -> x.eq("type", "department").in("department_id", visible)));
                }
            }
        }
        if (type != null && !type.isBlank()) {
            qw.eq("type", type);
        }
        // 部门筛选：非管理员强制限定为自己部门
        if (filterDeptId != null) {
            if (!canSeeAllDepts) {
                // 越权检查：非管理员只能筛自己部门
                if (!filterDeptId.equals(userDeptId)) {
                    throw new BusinessException(403, "无权查看其他部门的公告");
                }
            }
            qw.eq("department_id", filterDeptId);
        }
        if (keyword != null && !keyword.isBlank()) {
            qw.like("title", keyword);
        }
        // 分层排序：置顶全员 > 置顶部门 > 未置顶（按发布时间倒序）
        qw.last("ORDER BY CASE WHEN top=1 AND type='all' THEN 0 WHEN top=1 AND type='department' THEN 1 ELSE 2 END ASC, publish_at DESC");

        Page<Notice> p = noticeMapper.selectPage(new Page<>(page, size), qw);
        fillPublisherNames(p.getRecords());
        fillReadFlags(p.getRecords(), user.getUserId());
        // 驳回原因脱敏：列表返回前逐条过滤，仅相关双方保留原因内容
        p.getRecords().forEach(n -> maskRejectReason(n, user.getUserId()));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", p.getRecords());
        result.put("total", p.getTotal());
        result.put("page", p.getCurrent());
        result.put("size", p.getSize());
        return result;
    }

    // ==================== 详情 ====================

    public Notice detail(Integer id) {
        Notice notice = noticeMapper.selectById(id);
        if (notice == null) {
            throw new BusinessException(404, "公告不存在");
        }
        // 可见性校验（防 IDOR 按 id 偷看）：统一返 404，不暴露"存在但无权"的信息
        LoginUser viewer = currentUser();
        User dbViewer = userMapper.selectById(viewer.getUserId());
        Integer viewerDeptId = dbViewer != null ? dbViewer.getDepartmentId() : null;
        boolean isOwner = notice.getPublisherId().equals(viewer.getUserId());
        boolean superAdmin = isSuperAdmin();
        boolean confDept = "department".equals(notice.getType()) && isConfidentialDept(notice.getDepartmentId());
        // 链内领导（链内一体，2026-10-22）：公告部门∈查看者本链子树（含各组）；
        // W2 主管可看（初审转呈），W3 负责人可看可批；与待审批队列同一套链判定，保证"队列看得到=详情打得开"
        boolean chainLeader = isDeptLeaderGrade()
                && viewerDeptId != null
                && deptTreeService.subtree(viewerDeptId).contains(notice.getDepartmentId());
        // 行政视角（manageOverride / publish:company）：保密隔离部门对其无效
        boolean adminVisible = !confDept && (hasManageOverride() || hasAuthority("notice:publish:company"));

        if ("draft".equals(notice.getStatus())) {
            // 草稿=未提交的个人工作（2026-10-23 隐私修复）：仅本人可见，审批人/行政/老板在提交前一律不可打开
            if (!isOwner) {
                throw new BusinessException(404, "公告不存在");
            }
        } else if (!"published".equals(notice.getStatus())) {
            // 审批中/未通过：本人、老板（兜底审批）、链内领导（初审/审批）、非保密部门另加行政
            if (!isOwner && !superAdmin && !chainLeader && !adminVisible) {
                throw new BusinessException(404, "公告不存在");
            }
        } else if ("department".equals(notice.getType())) {
            // 已发布部门公告：链内可见（本节点+祖先链，部门公告穿透到组）/ 老板（只看）/ 非保密部门的行政视角
            boolean chainVisible = positionScopeService.currentVisibleDepts().contains(notice.getDepartmentId());
            if (!chainVisible && !superAdmin && !adminVisible) {
                throw new BusinessException(404, "公告不存在");
            }
        }
        // 填充发布人姓名与部门
        User publisher = userMapper.selectById(notice.getPublisherId());
        if (publisher != null) {
            notice.setPublisherName(publisher.getRealName());
            if (publisher.getDepartmentId() != null) {
                com.employee.entity.Department dept = departmentMapper.selectById(publisher.getDepartmentId());
                if (dept != null) {
                    notice.setPublisherDeptName(dept.getName());
                }
            }
        }
        // 自动标记已读（只对已发布状态）
        if ("published".equals(notice.getStatus())) {
            LoginUser user = currentUser();
            Long exists = noticeReadMapper.selectCount(
                    new QueryWrapper<NoticeRead>().eq("notice_id", id).eq("user_id", user.getUserId()));
            if (exists == 0) {
                NoticeRead read = new NoticeRead();
                read.setNoticeId(id);
                read.setUserId(user.getUserId());
                read.setReadAt(LocalDateTime.now());
                noticeReadMapper.insert(read);
            }
        }
        // 驳回原因脱敏（仅驳回人与申请/发布人可见），在返回前统一处理
        maskRejectReason(notice, viewer.getUserId());
        return notice;
    }

    // ==================== 未读数 ====================

    public int unreadCount() {
        LoginUser user = currentUser();
        User dbUser = userMapper.selectById(user.getUserId());
        Integer deptId = dbUser != null ? dbUser.getDepartmentId() : null;
        return noticeMapper.countUnread(user.getUserId(), deptId);
    }

    /**
     * 置顶权限判定（链内一体终版，2026-10-23）：
     * - 全员公告置顶：仅行政管理层（老板不再拥有）；保密隔离部门成员不得顶全员。
     * - 部门公告置顶：本链领导层（publish:dept 且 W2+，公告部门∈本人链子树——含各组，保密子组同）；
     *   非保密部门另可被行政管理层跨部门顶（治理口径）；保密部门对行政无效（链外死隔离不变）。
     */
    public boolean canPin(String noticeType, Integer noticeDeptId, Integer userDeptId) {
        if ("all".equals(noticeType)) {
            // 置顶全员公告：仅行政管理层（老板不再拥有）；保密隔离成员不得顶全员
            return hasManageOverride() && !isConfidentialDept(userDeptId);
        }
        if ("department".equals(noticeType)) {
            // 链内领导层判定：公告部门∈本人链子树（本部门+各组；兄弟组/他链天然不在集内）
            boolean chainLeader = isDeptLeaderGrade()
                    && userDeptId != null && noticeDeptId != null
                    && deptTreeService.subtree(userDeptId).contains(noticeDeptId);
            // 保密隔离部门：只有本链领导层可置顶，行政管理层无效
            if (isConfidentialDept(noticeDeptId)) {
                return chainLeader;
            }
            // 普通部门公告：行政管理层（跨部门治理） 或 本链领导层
            return hasManageOverride() || chainLeader;
        }
        return false;
    }

    /** 当前登录人是否老板（super_admin 角色）。公开供 DepartmentService 复用 */
    public boolean isSuperAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof LoginUser lu
                && "super_admin".equals(lu.getRoleCode());
    }

    /** 保密隔离部门 ID 集合 */
    private java.util.Set<Integer> confidentialDeptIds() {
        return departmentMapper.selectList(new QueryWrapper<Department>().eq("confidential", 1))
                .stream().map(Department::getId).collect(java.util.stream.Collectors.toSet());
    }

    /** 某部门是否保密隔离 */
    private boolean isConfidentialDept(Integer deptId) {
        return deptId != null && confidentialDeptIds().contains(deptId);
    }

    /**
     * 管理越权判定：持有 notice:manage:all 且职位 level>=3（总监级）。
     * 人事专员（W1）虽同持 hr 角色但无越权；老板无 notice:manage:all 也无越权。
     */
    public boolean hasManageOverride() {
        if (!hasAuthority("notice:manage:all")) {
            return false;
        }
        LoginUser user = currentUser();
        User dbUser = userMapper.selectById(user.getUserId());
        if (dbUser == null || dbUser.getPositionId() == null) {
            return false;
        }
        Position position = positionMapper.selectById(dbUser.getPositionId());
        return position != null && position.getLevel() != null && position.getLevel() >= 3;
    }

    /** 校验并强制 top 字段（后端兜底） */
    private void enforceTopPermission(Notice notice, Integer userDeptId) {
        if (notice.getTop() != null && notice.getTop() == 1) {
            Integer effectiveDeptId = notice.getDepartmentId() != null ? notice.getDepartmentId() : userDeptId;
            if (!canPin(notice.getType(), effectiveDeptId, userDeptId)) {
                notice.setTop(0); // 无权置顶 → 强制取消
            }
        }
    }

    // ==================== 置顶/取消置顶（独立管理动作） ====================

    public void setTop(Integer id, Integer top) {
        Notice notice = noticeMapper.selectById(id);
        if (notice == null) {
            throw new BusinessException(404, "公告不存在");
        }
        LoginUser user = currentUser();
        User dbUser = userMapper.selectById(user.getUserId());
        Integer userDeptId = dbUser != null ? dbUser.getDepartmentId() : null;
        if (!canPin(notice.getType(), notice.getDepartmentId(), userDeptId)) {
            throw new BusinessException(403, top == 1 ? "无权置顶该公告" : "无权取消置顶");
        }
        notice.setTop(top);
        noticeMapper.updateById(notice);
    }

    // ==================== 创建（分级发布核心逻辑） ====================

    public Notice create(Notice notice, boolean saveAsDraft) {
        LoginUser user = currentUser();
        User dbUser = userMapper.selectById(user.getUserId());
        Integer userDeptId = dbUser != null ? dbUser.getDepartmentId() : null;
        notice.setPublisherId(user.getUserId());

        // 白名单校验（后端唯一权威）：类型只认 all/department
        if (!"all".equals(notice.getType()) && !"department".equals(notice.getType())) {
            throw new BusinessException(400, "公告类型不合法");
        }
        // XSS 清洗：正文服务端白名单净化（前端富文本仅第一道；附件以正文 data-fid 标记承载，无需单独字段）
        notice.setContent(sanitize(notice.getContent()));
        // 定时发布上限：最多未来 4 小时（防远期占位）
        if (notice.getPublishAt() != null
                && notice.getPublishAt().isAfter(LocalDateTime.now().plusHours(EDIT_WINDOW_HOURS))) {
            throw new BusinessException(400, "定时发布时间最多设置在未来" + EDIT_WINDOW_HOURS + "小时内");
        }

        // 后端兜底：无 notice:create 的人（普通员工）不能发也不能申请全员公告
        if ("all".equals(notice.getType()) && !hasAuthority("notice:create")) {
            throw new BusinessException(403, "普通员工只能发布本部门公告（需申请）");
        }

        if ("department".equals(notice.getType()) && notice.getDepartmentId() == null) {
            // 缺省发到自己所在节点（组员=本组，负责人=本部门；链穿透由阅读范围谓词保证）
            notice.setDepartmentId(userDeptId);
        }
        if ("department".equals(notice.getType())) {
            Integer target = notice.getDepartmentId();
            // 保密隔离（链内一体）：保密目标部门仅本链人员可发/申请（含链头向下属组代发），链外死隔离（行政/老板不得代笔）
            if (isConfidentialDept(target) && !target.equals(userDeptId)
                    && !(userDeptId != null && deptTreeService.subtree(userDeptId).contains(target))) {
                throw new BusinessException(403, "保密隔离部门的公告仅本链人员可发布");
            }
            // 跨节点发布限定：目标非本部门时，需本链治理（链子树内含下属组）或行政代发权（publish:company/manage:all）
            if (!target.equals(userDeptId)
                    && (userDeptId == null || !deptTreeService.subtree(userDeptId).contains(target))
                    && !hasAuthority("notice:publish:company") && !hasManageOverride()) {
                throw new BusinessException(403, "只能发布本部门（或所辖下属组）的公告");
            }
            // dept 范围岗位（主管/员工）不得代发下级组公告：目标须落在自己的可见范围内
            if ("dept".equals(positionScopeService.currentScope())
                    && !positionScopeService.currentVisibleDepts().contains(target)) {
                throw new BusinessException(403, "主管/员工仅可发布自己可见范围的部门公告");
            }
        }
        // 置顶权限兜底
        enforceTopPermission(notice, userDeptId);

        if (saveAsDraft) {
            notice.setStatus("draft");
            noticeMapper.insert(notice);
            return notice;
        }

        // 判断是否可以直接发布
        if (canDirectPublish(notice.getType(), userDeptId)) {
            notice.setStatus("published");
            // 定时发布：不晚于当前则立即生效，否则保留未来时刻（≤now+4h 已校验）
            LocalDateTime sched = notice.getPublishAt();
            notice.setPublishAt(sched != null && sched.isAfter(LocalDateTime.now()) ? sched : LocalDateTime.now());
        } else {
            notice.setStatus("pending");
            notice.setSubmittedAt(LocalDateTime.now());
        }
        noticeMapper.insert(notice);
        return notice;
    }

    // ==================== 编辑（含4小时窗口校验） ====================

    public void update(Integer id, Notice dto) {
        Notice existing = noticeMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "公告不存在");
        }
        LoginUser user = currentUser();
        if (!existing.getPublisherId().equals(user.getUserId())) {
            throw new BusinessException(403, "只能编辑自己发布的公告");
        }

        // 已发布状态：检查4小时编辑窗口
        if ("published".equals(existing.getStatus())) {
            LocalDateTime editDeadline = getEditDeadline(existing);
            if (editDeadline != null && LocalDateTime.now().isAfter(editDeadline)) {
                throw new BusinessException(400, "已超过发布后 " + EDIT_WINDOW_HOURS + " 小时编辑窗口，无法修改");
            }
        }
        // pending/rejected/draft 状态允许编辑

        // 字段级权限（定稿口径）：部分更新（如仅传 top）不得清空原标题/正文；
        // 类型/目标部门在已发布/审批中状态禁止调整（防止部门公告发布后改成全员公告）
        existing.setTitle(dto.getTitle() == null || dto.getTitle().isBlank() ? existing.getTitle() : dto.getTitle());
        if (dto.getContent() != null && !dto.getContent().isBlank()) {
            // 富文本 XSS 白名单净化（后端为准，前端仅第一道防线）
            existing.setContent(sanitize(dto.getContent()));
        }
        if (!"published".equals(existing.getStatus()) && !"pending".equals(existing.getStatus())) {
            // 草稿/未通过：类型与目标部门可调整（类型只认白名单）
            if (dto.getType() != null && ("all".equals(dto.getType()) || "department".equals(dto.getType()))) {
                existing.setType(dto.getType());
            }
            if (dto.getDepartmentId() != null) {
                existing.setDepartmentId(dto.getDepartmentId());
            }
        }
        // 定时发布调整：最多未来 4 小时；已发布状态不再改发布时间
        if (dto.getPublishAt() != null) {
            if (dto.getPublishAt().isAfter(LocalDateTime.now().plusHours(EDIT_WINDOW_HOURS))) {
                throw new BusinessException(400, "定时发布时间最多设置在未来" + EDIT_WINDOW_HOURS + "小时内");
            }
            if (!"published".equals(existing.getStatus())) {
                existing.setPublishAt(dto.getPublishAt());
            }
        }
        // top 调整：按 canPin 链内口径判定（置顶/取消同权）；未变化时静默保留原值（支持仅传 top 的部分更新）
        if (dto.getTop() != null && !dto.getTop().equals(existing.getTop())) {
            User dbUpdater = userMapper.selectById(user.getUserId());
            Integer updaterDeptId = dbUpdater != null ? dbUpdater.getDepartmentId() : null;
            if (!canPin(existing.getType(), existing.getDepartmentId(), updaterDeptId)) {
                throw new BusinessException(403, "无权置顶该公告（仅本链领导层或行政管理层可调整置顶）");
            }
            existing.setTop(dto.getTop());
        }
        // 审批中状态：定时时刻已过期则清理（不影响内容编辑）
        if ("pending".equals(existing.getStatus()) && existing.getPublishAt() != null
                && existing.getPublishAt().isBefore(LocalDateTime.now())) {
            existing.setPublishAt(null);
        }

        // 驳回后重新提交 → 走申请流程（清空驳回痕迹，驳回时间/原因一并重置）
        if ("rejected".equals(existing.getStatus())) {
            existing.setStatus("pending");
            existing.setSubmittedAt(LocalDateTime.now());
            existing.setRejectReason(null);
            existing.setRejectedAt(null);
        }
        noticeMapper.updateById(existing);
    }

    // ==================== 审批通过 ====================

    public void approve(Integer id) {
        Notice notice = noticeMapper.selectById(id);
        if (notice == null) {
            throw new BusinessException(404, "公告不存在");
        }
        if (!"pending".equals(notice.getStatus())) {
            throw new BusinessException(400, "只有审批中的公告才能审批");
        }
        checkApprovePermission(notice);
        LoginUser user = currentUser();
        notice.setStatus("published");
        notice.setPublishAt(LocalDateTime.now());
        notice.setApprovedAt(LocalDateTime.now());
        notice.setApproverId(user.getUserId());
        noticeMapper.updateById(notice);
    }

    // ==================== 审批驳回 ====================

    public void reject(Integer id, String reason) {
        Notice notice = noticeMapper.selectById(id);
        if (notice == null) {
            throw new BusinessException(404, "公告不存在");
        }
        if (!"pending".equals(notice.getStatus())) {
            throw new BusinessException(400, "只有审批中的公告才能驳回");
        }
        // 驳回原因必填（后端同步校验，前端弹窗已做空值拦截）：空原因会让申请人无法定位问题，驳回即失去意义
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(400, "驳回原因不能为空");
        }
        checkApprovePermission(notice);
        LoginUser user = currentUser();
        notice.setStatus("rejected");
        notice.setRejectReason(reason.trim());
        // 驳回时间落库："未通过"列表时间列展示驳回时刻（不是申请时刻）
        notice.setRejectedAt(LocalDateTime.now());
        notice.setApproverId(user.getUserId());
        noticeMapper.updateById(notice);
    }

    /**
     * 审批路由（正规企业分级审批，2026-10-24 定案）：审批跟直属汇报线走——
     *   组长（节点负责人，publish:dept 且 W2+，sys_departments.manager_id=自己）可拍板本节点申请；
     *   部门负责人（W3）覆盖整条链（部本级申请+各组的升提兜底）；
     *   全员申请→行政管理层；老板全局兜底；行政不参与他部门内审，保密部门对行政彻底隔离；
     *   任何人不得审批自己提交的申请（组长自提本组单→自动升部门负责人，杜绝自审自批）。
     * 与旧"审批收 W3"口径的关系：拍板层从"只设在部一级"修正为"节点负责人即第一审批人"，
     * 仍不突破"权限码+职级双闸门"与"缺位靠上提不靠摊薄"两条铁律。
     */
    private void checkApprovePermission(Notice notice) {
        LoginUser user = currentUser();
        if ("all".equals(notice.getType())) {
            // 全员申请仅行政管理层可审（权限码+职级双闸门，防止 W1 专员越权审批）；自提自批一律拦截
            if (!isCompanyApprover()) {
                throw new BusinessException(403, "全员公告由行政审批（部门领导/人事总监仅可申请）");
            }
            if (notice.getPublisherId().equals(user.getUserId())) {
                throw new BusinessException(403, "不能审批自己提交的申请（职责分离）");
            }
            return;
        }
        if (notice.getPublisherId().equals(user.getUserId())) {
            throw new BusinessException(403, "不能审批自己提交的申请，由上级负责人拍板（禁自审自批）");
        }
        User dbUser = userMapper.selectById(user.getUserId());
        Integer userDeptId = dbUser != null ? dbUser.getDepartmentId() : null;
        // ① 节点负责人（组长 W2+ 即可）：本节点申请由直属上级第一审批
        boolean nodeHead = isNodeHeadOf(notice.getDepartmentId());
        // ② 链头（W3）：本部门及下属各组全覆盖（部本级申请、组升提兜底）
        boolean chainHead = isDeptApprover()
                && userDeptId != null
                && deptTreeService.subtree(userDeptId).contains(notice.getDepartmentId());
        if (!nodeHead && !chainHead && !isSuperAdmin()) {
            throw new BusinessException(403, "部门公告由本节点负责人或上级部门负责人审批（非负责人仅可初审转呈）");
        }
    }

    /** 节点负责人判定（组长档，W2+）：持 publish:dept 码且该部门 manager_id=自己——正规 DOA 里直属上级就是第一审批人 */
    private boolean isNodeHeadOf(Integer deptId) {
        if (deptId == null || !hasAuthority("notice:publish:dept") || currentLevel() < 2) {
            return false;
        }
        Department dept = departmentMapper.selectById(deptId);
        return dept != null && dept.getManagerId() != null
                && dept.getManagerId().equals(currentUser().getUserId());
    }

    /** 自己作为负责人（manager_id）的全部部门节点 id（组长本组/部长本部）：驱动审批队列与 canApproveDept 能力位 */
    private java.util.Set<Integer> headDeptIds() {
        if (!hasAuthority("notice:publish:dept") || currentLevel() < 2) {
            return java.util.Collections.emptySet();
        }
        LoginUser user = currentUser();
        return departmentMapper.selectList(new QueryWrapper<Department>().eq("manager_id", user.getUserId()))
                .stream().map(Department::getId).collect(java.util.stream.Collectors.toSet());
    }

    // ==================== 撤回（退回发布人草稿） ====================

    public void revoke(Integer id) {
        Notice notice = noticeMapper.selectById(id);
        if (notice == null) {
            throw new BusinessException(404, "公告不存在");
        }
        if (!"published".equals(notice.getStatus())) {
            throw new BusinessException(400, "只有已发布的公告才能撤回");
        }
        LoginUser user = currentUser();
        User dbUser = userMapper.selectById(user.getUserId());
        Integer userDeptId = dbUser != null ? dbUser.getDepartmentId() : null;
        // 三层口径：本人撤自己的；行政可撤非保密范围内全部；部门负责人可撤本部门/子部门；保密部门仅限本部门负责人；老板无操作权
        boolean isOwner = notice.getPublisherId().equals(user.getUserId());
        boolean byChainLeader = deptHeadScope(notice, userDeptId)
                || ("department".equals(notice.getType()) && isConfidentialDept(notice.getDepartmentId())
                    && sameDeptHead(notice.getDepartmentId(), userDeptId));
        if (isOwner) {
            // 本人可撤（退回自己草稿箱）
        } else if ("department".equals(notice.getType()) && isConfidentialDept(notice.getDepartmentId())) {
            if (!sameDeptHead(notice.getDepartmentId(), userDeptId)) {
                throw new BusinessException(403, "保密隔离部门的公告仅本链负责人可操作");
            }
        } else if (!hasManageOverride() && !deptHeadScope(notice, userDeptId)) {
            throw new BusinessException(403, "只能撤回自己发布的公告（本部门负责人可撤本部门及子部门，行政可撤全范围）");
        }
        // 行政治理撤回（非本人且非链内领导身份）：仅发布后 2 小时内且限当日
        if (!isOwner && !byChainLeader) {
            assertGovernWindow(notice);
        }
        // 退回发布人草稿箱（publisher 不变），发布人可改后重发或删除；置顶标记一并重置
        notice.setStatus("draft");
        notice.setTop(0);
        notice.setPublishAt(null);
        notice.setSubmittedAt(null);
        notice.setApprovedAt(null);
        notice.setRejectReason(null);
        notice.setRejectedAt(null);
        notice.setApproverId(null);
        noticeMapper.updateById(notice);
    }

    // ==================== 删除 ====================

    public void delete(Integer id) {
        Notice notice = noticeMapper.selectById(id);
        if (notice == null) {
            throw new BusinessException(404, "公告不存在");
        }
        LoginUser user = currentUser();
        User dbUser = userMapper.selectById(user.getUserId());
        Integer userDeptId = dbUser != null ? dbUser.getDepartmentId() : null;
        boolean isOwner = notice.getPublisherId().equals(user.getUserId());
        // 三层口径：本人限草稿/未通过；行政可直删非保密全部；本链领导层可删链内；保密部门归本链；老板无操作权
        boolean byChainLeader = deptHeadScope(notice, userDeptId)
                || ("department".equals(notice.getType()) && isConfidentialDept(notice.getDepartmentId())
                    && sameDeptHead(notice.getDepartmentId(), userDeptId));
        if (isOwner) {
            if (!"draft".equals(notice.getStatus()) && !"rejected".equals(notice.getStatus())) {
                throw new BusinessException(400, "已发布的公告请先撤回再删除");
            }
        } else if ("department".equals(notice.getType()) && isConfidentialDept(notice.getDepartmentId())) {
            if (!sameDeptHead(notice.getDepartmentId(), userDeptId)) {
                throw new BusinessException(403, "保密隔离部门的公告仅本链负责人可操作");
            }
        } else if (!hasManageOverride() && !deptHeadScope(notice, userDeptId)) {
            throw new BusinessException(403, "无权删除该公告（本部门负责人可删本部门及子部门，行政可删全范围）");
        }
        // 行政治理删除（非本人且非链内领导身份）：仅发布后 2 小时内且限当日
        if (!isOwner && !byChainLeader) {
            assertGovernWindow(notice);
        }
        noticeMapper.deleteById(id);
    }

    /**
     * 行政治理窗口校验：仅可操作发布后 2 小时内且发布当日的公告（定案）；
     * 已撤回的公告 publish_at 已清空，回退按创建时间兼容链头删草稿的场景。
     * 业务原因：防止治理方无限追溯撤改他人历史公告；本人/链内领导层不受本窗口限制。
     */
    private void assertGovernWindow(Notice notice) {
        LocalDateTime pub = notice.getPublishAt() != null ? notice.getPublishAt() : notice.getCreatedAt();
        if (pub == null) {
            throw new BusinessException(400, "公告发布时间缺失，无法执行治理操作");
        }
        if (!java.time.LocalDate.now().isEqual(pub.toLocalDate())) {
            throw new BusinessException(400, "仅可治理操作当日发布的公告（跨日不再开放删除/撤回）");
        }
        if (java.time.LocalTime.now().isBefore(pub.toLocalTime())
                || java.time.LocalTime.now().isAfter(pub.toLocalTime().plusHours(GOVERN_DELETE_WINDOW_HOURS))) {
            throw new BusinessException(400, "发布 " + GOVERN_DELETE_WINDOW_HOURS + " 小时后至当日 24 点期间不能删除/撤回（发布后两小时内为唯一可治理窗口）");
        }
    }

    // ==================== 删除/撤回三层口径判定 ====================

    /**
     * 口径一：发布人本人（在各方法内直接判 isOwner）——撤回自己的已发布、删除仅限自己的草稿/未通过。
     * 口径二：行政（notice:manage:all + W3，hasManageOverride）——全范围不受部门归属限制，但保密部门无效（调用方先行拦截）。
     * 口径三：部门负责人（notice:publish:dept）——可删/撤 type=department 且公告部门∈本部门或子部门（缓存子树），
     *         不依赖 manage:all，严禁跨部门；保密部门不走本方法（由 sameDeptHead 严格同部门判定）。
     */
    private boolean deptHeadScope(Notice notice, Integer userDeptId) {
        return "department".equals(notice.getType())
                && notice.getDepartmentId() != null
                && isDeptLeaderGrade()
                && !isConfidentialDept(notice.getDepartmentId())
                && deptTreeService.subtree(userDeptId).contains(notice.getDepartmentId());
    }

    /**
     * 保密部门链内领导判定（链内一体，2026-10-23）：保密公告的删/撤/顶归本链领导层
     * （publish:dept + W2+，公告部门∈本人链子树）；链外（行政/兄弟组/他链）仍死隔离——
     * 不外溢口径不变，只是"部门"按组织链展开（组员/子组的公告链头可治理）。
     */
    private boolean sameDeptHead(Integer noticeDeptId, Integer userDeptId) {
        return isDeptLeaderGrade()
                && noticeDeptId != null && userDeptId != null
                && deptTreeService.subtree(userDeptId).contains(noticeDeptId);
    }

    /**
     * 驳回原因脱敏（2026-10-21 定案）：只有驳回人本人（approverId）与申请/发布人本人（publisherId）
     * 可见；其他任何人（含其他领导/行政/老板）一律置空不返回。业务原因：驳回意见可能包含对申请人
     * 的评价，属敏感人事信息，越权展示易引发纠纷，故在后端返回前统一置空（前端无需二次过滤）。
     */
    private void maskRejectReason(Notice notice, Integer viewerId) {
        if (notice == null || notice.getRejectReason() == null || viewerId == null) {
            return;
        }
        boolean isPublisher = viewerId.equals(notice.getPublisherId());
        boolean isApprover = viewerId.equals(notice.getApproverId());
        if (!isPublisher && !isApprover) {
            notice.setRejectReason(null);
        }
    }

    // ==================== 权限判定 ====================

    /**
     * 是否可以直接发布（无需审批）：
     * - 全员公告：需要 notice:publish:company 权限（仅行政/老板；人事总监同部门领导口径，只能申请）
     * - 部门公告：需要 notice:publish:dept 权限 或 是部门负责人
     */
    public boolean canDirectPublish(String type, Integer userDeptId) {
        if ("all".equals(type)) {
            // 全员直发属行政管理层治理权（权限码+职级双闸门，W1 专员只能申请）
            return isCompanyApprover();
        }
        // 部门公告：行政管理层（publish:company+W3，代发任意非保密部门）；
        // 部门领导层（publish:dept + 职级≥2）直发本部门；部门内 W1 普通成员（如同持 hr 角色的专员）只能申请
        return isCompanyApprover()
                || (hasAuthority("notice:publish:dept") && currentLevel() >= 2);
    }

    /** 获取编辑截止时间 */
    private LocalDateTime getEditDeadline(Notice notice) {
        if (notice.getApprovedAt() != null) {
            return notice.getApprovedAt().plusHours(EDIT_WINDOW_HOURS);
        }
        if (notice.getPublishAt() != null) {
            return notice.getPublishAt().plusHours(EDIT_WINDOW_HOURS);
        }
        return null;
    }

    // ==================== 私有工具 ====================

    /** 批量填充当前用户已读标记（一次查库，驱动前端未读加粗） */
    private void fillReadFlags(java.util.Collection<Notice> notices, Integer userId) {
        if (notices == null || notices.isEmpty() || userId == null) {
            return;
        }
        java.util.Set<Integer> ids = notices.stream().map(Notice::getId)
                .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        if (ids.isEmpty()) {
            return;
        }
        java.util.Set<Integer> readIds = noticeReadMapper.selectList(
                        new QueryWrapper<NoticeRead>().eq("user_id", userId).in("notice_id", ids))
                .stream().map(NoticeRead::getNoticeId).collect(java.util.stream.Collectors.toSet());
        notices.forEach(n -> n.setIsRead(readIds.contains(n.getId())));
    }

    /** 批量填充发布人姓名与部门名（两次查库，避免 N+1） */
    private void fillPublisherNames(java.util.Collection<Notice> notices) {
        if (notices == null || notices.isEmpty()) {
            return;
        }
        java.util.Set<Integer> ids = notices.stream()
                .map(Notice::getPublisherId).filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        if (ids.isEmpty()) {
            return;
        }
        Map<Integer, String> nameMap = new LinkedHashMap<>();
        Map<Integer, Integer> userDeptMap = new LinkedHashMap<>();
        userMapper.selectBatchIds(ids).forEach(u -> {
            nameMap.put(u.getId(), u.getRealName());
            if (u.getDepartmentId() != null) {
                userDeptMap.put(u.getId(), u.getDepartmentId());
            }
        });
        Map<Integer, String> deptNameMap = new LinkedHashMap<>();
        if (!userDeptMap.isEmpty()) {
            departmentMapper.selectBatchIds(new java.util.HashSet<>(userDeptMap.values()))
                    .forEach(d -> deptNameMap.put(d.getId(), d.getName()));
        }
        notices.forEach(n -> {
            n.setPublisherName(nameMap.get(n.getPublisherId()));
            Integer deptId = userDeptMap.get(n.getPublisherId());
            if (deptId != null) {
                n.setPublisherDeptName(deptNameMap.get(deptId));
            }
        });
    }

    /**
     * 富文本 XSS 白名单净化（后端为准，前端仅第一道防线）：
     * 剥离 script/style 标签、on* 事件属性与 javascript: 协议，保留常规排版标签与附件 data-fid 标记。
     */
    private String sanitize(String html) {
        if (html == null) {
            return null;
        }
        return org.jsoup.Jsoup.clean(html,
                org.jsoup.safety.Safelist.relaxed()
                        .addTags("span", "u", "s", "img")
                        .addAttributes("span", "style")
                        .addAttributes("img", "src", "data-fid", "alt"));
    }

    private LoginUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof LoginUser loginUser)) {
            throw new BusinessException(401, "未登录");
        }
        return loginUser;
    }

    private boolean hasAuthority(String code) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(code::equals);
    }
}
