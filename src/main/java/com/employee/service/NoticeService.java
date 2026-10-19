package com.employee.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.employee.entity.Notice;
import com.employee.entity.NoticeRead;
import com.employee.entity.User;
import com.employee.exception.BusinessException;
import com.employee.mapper.NoticeMapper;
import com.employee.mapper.NoticeReadMapper;
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
 * - 老板/行政/人事：可直接发布全员公告 + 部门公告。
 * - 部门负责人(W2/W3)：可直接发布本部门公告；发全员公告需提交申请。
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

    private final NoticeMapper noticeMapper;
    private final NoticeReadMapper noticeReadMapper;
    private final UserMapper userMapper;

    public NoticeService(NoticeMapper noticeMapper, NoticeReadMapper noticeReadMapper, UserMapper userMapper) {
        this.noticeMapper = noticeMapper;
        this.noticeReadMapper = noticeReadMapper;
        this.userMapper = userMapper;
    }

    // ==================== 列表 ====================

    public Map<String, Object> list(String type, String status, String keyword, Integer filterDeptId, int page, int size) {
        LoginUser user = currentUser();
        User dbUser = userMapper.selectById(user.getUserId());
        Integer userDeptId = dbUser != null ? dbUser.getDepartmentId() : null;
        boolean canSeeAllDepts = hasAuthority("notice:publish:company"); // 行政/老板可看任意部门

        QueryWrapper<Notice> qw = new QueryWrapper<>();

        if ("draft".equals(status)) {
            qw.eq("status", "draft").eq("publisher_id", user.getUserId());
        } else if ("pending_approval".equals(status)) {
            // 待审批列表：按角色分级可见范围
            qw.eq("status", "pending");
            if (canSeeAllDepts) {
                // 行政/老板：看所有待审批
            } else if (hasAuthority("notice:publish:dept")) {
                // 部门负责人：只看本部门的待审批
                if (userDeptId != null) {
                    qw.eq("department_id", userDeptId);
                } else {
                    qw.eq("publisher_id", user.getUserId()); // 无部门降级为自己
                }
            } else {
                // 普通员工：只看自己提交的
                qw.eq("publisher_id", user.getUserId());
            }
        } else if ("rejected".equals(status)) {
            // 未通过列表：同样按角色分级
            qw.eq("status", "rejected");
            if (canSeeAllDepts) {
                // 行政/老板：看所有未通过
            } else if (hasAuthority("notice:publish:dept")) {
                // 部门负责人：本部门发起的 + 自己提交的
                if (userDeptId != null) {
                    qw.and(w -> w.eq("department_id", userDeptId).or().eq("publisher_id", user.getUserId()));
                } else {
                    qw.eq("publisher_id", user.getUserId());
                }
            } else {
                // 普通员工：只看自己提交的
                qw.eq("publisher_id", user.getUserId());
            }
        } else if ("pending".equals(status)) {
            // “我提交的申请” Tab：只看自己提交的
            qw.eq("status", "pending").eq("publisher_id", user.getUserId());
        } else {
            // 默认：已发布，对读者可见
            qw.eq("status", "published");
            if (canSeeAllDepts) {
                // 行政/老板：可看全部
            } else if (userDeptId != null) {
                qw.and(w -> w.eq("type", "all").or().eq("department_id", userDeptId));
            } else {
                qw.eq("type", "all");
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
        return notice;
    }

    // ==================== 未读数 ====================

    public int unreadCount() {
        LoginUser user = currentUser();
        User dbUser = userMapper.selectById(user.getUserId());
        Integer deptId = dbUser != null ? dbUser.getDepartmentId() : null;
        return noticeMapper.countUnread(user.getUserId(), deptId);
    }

    /** 置顶权限判定：谁能置顶什么 */
    public boolean canPin(String noticeType, Integer noticeDeptId, Integer userDeptId) {
        if ("all".equals(noticeType)) {
            // 置顶全员公告：仅行政/老板
            return hasAuthority("notice:publish:company");
        }
        // 置顶部门公告：行政/老板 或 本部门负责人
        return hasAuthority("notice:publish:company")
                || (hasAuthority("notice:publish:dept") && userDeptId != null && userDeptId.equals(noticeDeptId));
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
        // 权限校验
        if (top == 1 && !canPin(notice.getType(), notice.getDepartmentId(), userDeptId)) {
            throw new BusinessException(403, "无权置顶该公告");
        }
        if (top == 0 && !canPin(notice.getType(), notice.getDepartmentId(), userDeptId)) {
            throw new BusinessException(403, "无权取消置顶");
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

        if ("department".equals(notice.getType()) && notice.getDepartmentId() == null) {
            notice.setDepartmentId(userDeptId);
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
            notice.setPublishAt(LocalDateTime.now());
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

        existing.setTitle(dto.getTitle());
        existing.setContent(dto.getContent());
        existing.setType(dto.getType());
        existing.setDepartmentId(dto.getDepartmentId());
        existing.setTop(dto.getTop());
        existing.setPublishAt(dto.getPublishAt());
        // 置顶权限兜底
        LoginUser lu = currentUser();
        User lu2 = userMapper.selectById(lu.getUserId());
        enforceTopPermission(existing, lu2 != null ? lu2.getDepartmentId() : null);

        // 驳回后重新提交 → 走申请流程
        if ("rejected".equals(existing.getStatus())) {
            existing.setStatus("pending");
            existing.setSubmittedAt(LocalDateTime.now());
            existing.setRejectReason(null);
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
        LoginUser user = currentUser();
        notice.setStatus("rejected");
        notice.setRejectReason(reason);
        notice.setApproverId(user.getUserId());
        noticeMapper.updateById(notice);
    }

    // ==================== 撤回 ====================

    public void revoke(Integer id) {
        Notice notice = noticeMapper.selectById(id);
        if (notice == null) {
            throw new BusinessException(404, "公告不存在");
        }
        LoginUser user = currentUser();
        if (!notice.getPublisherId().equals(user.getUserId()) && !hasAuthority("notice:manage:all")) {
            throw new BusinessException(403, "只能撤回自己发布的公告");
        }
        notice.setStatus("archived");
        noticeMapper.updateById(notice);
    }

    // ==================== 删除 ====================

    public void delete(Integer id) {
        Notice notice = noticeMapper.selectById(id);
        if (notice == null) {
            throw new BusinessException(404, "公告不存在");
        }
        LoginUser user = currentUser();
        if (!notice.getPublisherId().equals(user.getUserId())) {
            throw new BusinessException(403, "只能删除自己的公告");
        }
        noticeMapper.deleteById(id);
    }

    // ==================== 权限判定 ====================

    /**
     * 是否可以直接发布（无需审批）：
     * - 全员公告：需要 notice:publish:company 权限（行政/人事/老板）
     * - 部门公告：需要 notice:publish:dept 权限 或 是部门负责人
     */
    public boolean canDirectPublish(String type, Integer userDeptId) {
        if ("all".equals(type)) {
            return hasAuthority("notice:publish:company");
        }
        // 部门公告：有 notice:create 的人（hr/super_admin）直接发；
        // 部门负责人有 notice:publish:dept 直接发本部门
        return hasAuthority("notice:publish:company") || hasAuthority("notice:publish:dept");
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
