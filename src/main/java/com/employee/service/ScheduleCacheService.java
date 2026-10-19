package com.employee.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.employee.dto.WorkWindow;
import com.employee.entity.AttendanceSchedule;
import com.employee.entity.Department;
import com.employee.exception.BusinessException;
import com.employee.mapper.AttendanceScheduleMapper;
import com.employee.mapper.DepartmentMapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

/**
 * 排班基准缓存服务（Phase 0-8）。
 * 解析某部门的上下班时间基准：沿 parent_id 上溯到第一个已配置排班的部门（子部门→一级部门）。
 * 排班极少变动、且每次打卡/考勤查询都会走，按 departmentId 缓存。
 * 独立成 bean 以避免同类自调用导致 @Cacheable 失效；排班保存（4-2）时调用 {@link #evictAll()}。
 */
@Service
public class ScheduleCacheService {

    private static final int MAX_DEPT_DEPTH = 10;

    private final DepartmentMapper departmentMapper;
    private final AttendanceScheduleMapper scheduleMapper;

    public ScheduleCacheService(DepartmentMapper departmentMapper, AttendanceScheduleMapper scheduleMapper) {
        this.departmentMapper = departmentMapper;
        this.scheduleMapper = scheduleMapper;
    }

    @Cacheable(value = "sch:dept", key = "#departmentId", unless = "#departmentId == null")
    public WorkWindow resolve(Integer departmentId) {
        if (departmentId == null) {
            throw new BusinessException(400, "您尚未分配部门，无法确定考勤排班，请联系管理员");
        }
        Integer cursor = departmentId;
        String ownDeptName = null;
        Set<Integer> visited = new HashSet<>();
        int depth = 0;
        while (cursor != null && depth++ < MAX_DEPT_DEPTH && visited.add(cursor)) {
            Department dept = departmentMapper.selectById(cursor);
            if (dept == null) {
                break;
            }
            if (ownDeptName == null) {
                ownDeptName = dept.getName();
            }
            AttendanceSchedule s = scheduleMapper.selectOne(new QueryWrapper<AttendanceSchedule>()
                    .eq("department_id", cursor));
            if (s != null && s.getWorkStartTime() != null && s.getWorkEndTime() != null) {
                return new WorkWindow(s.getWorkStartTime(), s.getWorkEndTime(), dept.getId(), dept.getName());
            }
            cursor = dept.getParentId();
        }
        throw new BusinessException(400, "「" + (ownDeptName == null ? "所在部门" : ownDeptName)
                + "」及其上级部门均未配置考勤排班，请联系管理员在「排班设置」中配置");
    }

    /** 清空排班缓存（保存/修改排班后调用） */
    @CacheEvict(value = "sch:dept", allEntries = true)
    public void evictAll() {
        // 仅用于触发缓存失效
    }
}
