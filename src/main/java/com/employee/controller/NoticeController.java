package com.employee.controller;

import com.employee.annotation.AuditLog;
import com.employee.annotation.NoRepeatSubmit;
import com.employee.common.Result;
import com.employee.entity.Notice;
import com.employee.service.NoticeService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 公告通知接口（区域二 2-1，分级发布 + 审批控制）。
 */
@RestController
@RequestMapping("/api/notices")
public class NoticeController {

    private final NoticeService noticeService;

    public NoticeController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    /** 公告列表 */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Result<Map<String, Object>> list(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer deptId,
            @RequestParam(required = false) Integer managed,
            @RequestParam(required = false) Integer manage,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(noticeService.list(type, status, keyword, deptId, managed, manage, page, Math.min(size, 100)));
    }

    /** 公告详情（自动标记已读） */
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<Notice> detail(@PathVariable Integer id) {
        return Result.success(noticeService.detail(id));
    }

    /** 未读公告数 */
    @GetMapping("/unread-count")
    @PreAuthorize("isAuthenticated()")
    public Result<Integer> unreadCount() {
        return Result.success(noticeService.unreadCount());
    }

    /** 查询当前用户发布/置顶能力（前端按钮形态用，后端按真实部门判定） */
    @GetMapping("/publish-permission")
    @PreAuthorize("isAuthenticated()")
    public Result<Map<String, Object>> publishPermission() {
        return Result.success(noticeService.publishPermission());
    }

    /** 创建公告（自动判断直接发布 or 提交申请；draft=true 存草稿） */
    @PostMapping
    @PreAuthorize("hasAuthority('notice:create') or hasAuthority('notice:submit:dept')")
    @NoRepeatSubmit(message = "公告正在提交，请勿重复")
    @AuditLog(action = "CREATE", resource = "notice", description = "创建公告")
    public Result<Notice> create(@Valid @RequestBody Notice notice,
                                 @RequestParam(defaultValue = "false") boolean draft) {
        Notice created = noticeService.create(notice, draft);
        String msg = "draft".equals(created.getStatus()) ? "已保存草稿"
                : "published".equals(created.getStatus()) ? "公告发布成功"
                : "已提交审批申请，请等待审批";
        return Result.success(msg, created);
    }

    /** 编辑公告（已发布状态需4小时内） */
    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @AuditLog(action = "UPDATE", resource = "notice", description = "编辑公告")
    public Result<Void> update(@PathVariable Integer id, @Valid @RequestBody Notice notice) {
        noticeService.update(id, notice);
        return Result.success("公告更新成功", null);
    }

    /** 审批通过（路由在 Service 判定：全员申请→行政；部门申请→本部门负责人/老板兜底） */
    @PutMapping("/{id}/approve")
    @PreAuthorize("isAuthenticated()")
    @AuditLog(action = "UPDATE", resource = "notice", description = "审批通过公告")
    public Result<Void> approve(@PathVariable Integer id) {
        noticeService.approve(id);
        return Result.success("审批通过，公告已发布", null);
    }

    /** 审批驳回（路由同 approve） */
    @PutMapping("/{id}/reject")
    @PreAuthorize("isAuthenticated()")
    @AuditLog(action = "UPDATE", resource = "notice", description = "驳回公告")
    public Result<Void> reject(@PathVariable Integer id, @RequestParam String reason) {
        noticeService.reject(id, reason);
        return Result.success("已驳回", null);
    }

    /** 撤回公告 */
    @PutMapping("/{id}/revoke")
    @PreAuthorize("isAuthenticated()")
    @AuditLog(action = "UPDATE", resource = "notice", description = "撤回公告")
    public Result<Void> revoke(@PathVariable Integer id) {
        noticeService.revoke(id);
        return Result.success("已撤回，公告退回发布人草稿箱", null);
    }

    /** 置顶/取消置顶（独立管理动作，需对应权限） */
    @PutMapping("/{id}/top")
    @PreAuthorize("hasAuthority('notice:manage:all') or hasAuthority('notice:publish:dept')")
    @AuditLog(action = "UPDATE", resource = "notice", description = "置顶操作")
    public Result<Void> setTop(@PathVariable Integer id, @RequestParam Integer top) {
        noticeService.setTop(id, top);
        return Result.success(top == 1 ? "已置顶" : "已取消置顶", null);
    }

    /** 删除公告 */
    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @AuditLog(action = "DELETE", resource = "notice", description = "删除公告")
    public Result<Void> delete(@PathVariable Integer id) {
        noticeService.delete(id);
        return Result.success("公告已删除", null);
    }
}
