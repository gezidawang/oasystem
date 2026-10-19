package com.employee.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.employee.dto.AttendanceQueryDTO;
import com.employee.dto.AttendanceRecordVO;
import com.employee.dto.ScheduleSaveDTO;
import com.employee.dto.ScheduleVO;
import com.employee.dto.UserScopeRef;
import com.employee.dto.WorkWindow;
import com.employee.entity.Attendance;
import com.employee.entity.AttendanceSchedule;
import com.employee.entity.Department;
import com.employee.exception.BusinessException;
import com.employee.mapper.AttendanceMapper;
import com.employee.mapper.AttendanceScheduleMapper;
import com.employee.mapper.DepartmentMapper;
import com.employee.mapper.UserMapper;
import com.employee.datascope.DataScope;
import com.employee.security.LoginUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 考勤服务：上下班打卡、迟到/早退/缺勤判定、按部门排班管理、考勤记录多条件查询。
 *
 * 考勤状态判定规则（与需求一一对应）：
 *   1. 上班打卡时间 > 部门设定上班时间            → 迟到（late）
 *   2. 下班打卡时间 < 部门设定下班时间            → 早退（early_leave）
 *   3. 规定时间内正常上下班                      → 正常（normal）
 *   4. 当天下班时间之前始终没有打上班卡          → 缺勤（absent）
 * 优先级：缺勤 > 迟到 > 早退 > 正常（同时迟到又早退记为迟到）。
 * 当天尚未到下班时间且未打卡的展示为「未打卡」（pending），不落库，
 * 一旦过了下班时间自然按规则 4 显示缺勤 —— 无需定时任务扫描。
 *
 * 排班归属口径：只有「一级部门」（parent_id 为 NULL）可配置排班；
 * 不设按技术栈划分的二级组（前端组/后端组等），若将来存在子部门，
 * 也不是独立核算主体，不单独排班，打卡时间基准沿 parent_id 向上追溯到
 * 第一个已配置排班的部门（即其所属一级部门）；整条链都没有排班时抛可读的业务提示，
 * 绝不静默退化为硬编码默认时间。
 */
@Service
public class AttendanceService {

    /** 部门排班向上追溯的最大层级（防止脏数据形成环时死循环） */
    private static final int MAX_DEPT_DEPTH = 10;

    /** 查询日期范围上限（防止全表扫描式的超大范围查询） */
    private static final int MAX_RANGE_DAYS = 62;

    /**
     * 最迟上班打卡截止点 = 部门下班时间 - 该分钟数。
     * 到达截止点仍无上班卡 → 锁定当天打卡入口并判缺勤（缺勤由查询时合成，无需定时任务）。
     * 后续可挪入 biz_attendance_schedules 按部门配置，先以常量统一。
     */
    private static final int PUNCH_CUTOFF_MINUTES = 60;

    /**
     * 超级管理员（老板）角色码。
     * 业务定位：老板是考勤的「管理者」（排班/全公司查看），不是「被考勤对象」，
     * 因此不参与打卡，也不参与缺勤/未打卡合成。
     * 判定依据使用 JWT 中的 roleCode（LoginUser.getRoleCode），不写死用户 ID。
     */
    private static final String ROLE_SUPER_ADMIN = "super_admin";

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_TIME_MIN_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 状态码 → 中文名 */
    private static final Map<String, String> STATUS_TEXT = Map.of(
            "normal", "正常",
            "late", "迟到",
            "early_leave", "早退",
            "absent", "缺勤",
            "leave", "请假",
            "pending", "未打卡");

    private final AttendanceMapper attendanceMapper;
    private final AttendanceScheduleMapper scheduleMapper;
    private final DepartmentMapper departmentMapper;
    private final UserMapper userMapper;
    private final ScheduleCacheService scheduleCacheService;

    public AttendanceService(AttendanceMapper attendanceMapper,
                             AttendanceScheduleMapper scheduleMapper,
                             DepartmentMapper departmentMapper,
                             UserMapper userMapper,
                             ScheduleCacheService scheduleCacheService) {
        this.attendanceMapper = attendanceMapper;
        this.scheduleMapper = scheduleMapper;
        this.departmentMapper = departmentMapper;
        this.userMapper = userMapper;
        this.scheduleCacheService = scheduleCacheService;
    }

    // ==================== 打卡 ====================

    /**
     * 打卡前置校验：
     *   1. 角色级豁免：super_admin（老板）不是被考勤对象，直接拒绝；
     *   2. 权限级拦截：其余角色必须持有 attendance:check:own（hr/manager/employee 均有）。
     * 抛 BusinessException 经 GlobalExceptionHandler 统一包装为 Result（HTTP 200 + success=false），
     * 不会出现 500。
     */
    private void assertPunchable(LoginUser user) {
        if (isSuperAdmin(user)) {
            throw new BusinessException(403, "超级管理员（老板）无需打卡");
        }
        if (!hasAuthority("attendance:check:own")) {
            throw new BusinessException(403, "当前角色无打卡权限");
        }
    }

    /** 是否老板角色（仅豁免 super_admin 一个角色） */
    private boolean isSuperAdmin(LoginUser user) {
        return ROLE_SUPER_ADMIN.equals(user.getRoleCode());
    }

    /** 上班打卡：创建当日记录并按部门上班时间判定是否迟到 */
    public Map<String, Object> checkIn() {
        LoginUser user = currentUser();
        assertPunchable(user);
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now().withSecond(0).withNano(0);

        Attendance exist = todayRecord(user.getUserId(), today);
        if (exist != null && exist.getCheckInTime() != null) {
            throw new BusinessException(400, "今日已于 " +
                    exist.getCheckInTime().format(DATE_TIME_MIN_FMT) + " 上班打卡，请勿重复打卡");
        }

        WorkWindow window = resolveWorkWindow(currentDepartmentId(user));
        // 已过最迟上班打卡截止点（下班前 PUNCH_CUTOFF_MINUTES 分钟）且当天无上班卡 → 锁定，不得补打卡
        LocalTime cutoff = window.end().minusMinutes(PUNCH_CUTOFF_MINUTES);
        if (!now.isBefore(cutoff)) {
            throw new BusinessException(400, "已过最迟上班打卡时间（" + cutoff.format(TIME_FMT)
                    + "），今日按缺勤登记，无法补打卡");
        }
        LocalTime workStart = window.start();
        boolean late = now.isAfter(workStart);
        String status = late ? "late" : "normal";

        Attendance record = exist != null ? exist : new Attendance();
        record.setUserId(user.getUserId());
        record.setWorkDate(today);
        record.setCheckInTime(LocalDateTime.of(today, now));
        record.setStatus(status);
        if (record.getId() == null) {
            attendanceMapper.insert(record);
        } else {
            attendanceMapper.updateById(record);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("checkInTime", today + " " + now.format(TIME_FMT));
        data.put("status", status);
        data.put("statusText", STATUS_TEXT.get(status));
        data.put("late", late);
        data.put("message", late
                ? "打卡成功，已超过上班时间 " + workStart.format(TIME_FMT) + "，记为迟到"
                : "打卡成功，上班时间 " + workStart.format(TIME_FMT));
        return data;
    }

    /** 下班打卡：校验已有上班卡后记录下班时间，早于部门下班时间记早退 */
    public Map<String, Object> checkOut() {
        LoginUser user = currentUser();
        assertPunchable(user);
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now().withSecond(0).withNano(0);

        WorkWindow window = resolveWorkWindow(currentDepartmentId(user));
        Attendance record = todayRecord(user.getUserId(), today);
        if (record == null || record.getCheckInTime() == null) {
            LocalTime cutoff = window.end().minusMinutes(PUNCH_CUTOFF_MINUTES);
            if (!now.isBefore(cutoff)) {
                throw new BusinessException(400, "已过最迟上班打卡时间（" + cutoff.format(TIME_FMT) + "），今日按缺勤登记");
            }
            throw new BusinessException(400, "今日尚未上班打卡，请先打上班卡");
        }
        if (record.getCheckOutTime() != null) {
            throw new BusinessException(400, "今日已于 " +
                    record.getCheckOutTime().format(DATE_TIME_MIN_FMT) + " 下班打卡，请勿重复打卡");
        }

        LocalTime workEnd = window.end();
        boolean early = now.isBefore(workEnd);
        // 迟到优先级高于早退：上午已迟到的，下午早退仍保持「迟到」状态
        String status = ("late".equals(record.getStatus()) || !early) ? record.getStatus() : "early_leave";
        if (!early && !"late".equals(status)) {
            status = "normal";
        }

        record.setCheckOutTime(LocalDateTime.of(today, now));
        record.setStatus(status);
        attendanceMapper.updateById(record);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("checkOutTime", today + " " + now.format(TIME_FMT));
        data.put("status", status);
        data.put("statusText", STATUS_TEXT.get(status));
        // 消息与最终状态保持一致：迟到优先于早退，已迟到时不再提示早退
        String message;
        if ("late".equals(status)) {
            message = "打卡成功，今日已迟到，最终考勤：迟到";
        } else if (early) {
            message = "打卡成功，早于下班时间 " + workEnd.format(TIME_FMT) + "，记为早退";
        } else {
            message = "打卡成功，辛苦了！";
        }
        data.put("message", message);
        return data;
    }

    /**
     * 统一打卡（上下班合一）：未打上班卡→打上班；已上班未打下班→打下班；均已完成→拒绝。
     * 上班打卡受“下班前 PUNCH_CUTOFF_MINUTES 分钟”截止点约束（在 checkIn 内），
     * 过点未打即锁定并判缺勤；已打过卡的人不受影响（仍可打下班）。
     */
    public Map<String, Object> punch() {
        LoginUser user = currentUser();
        assertPunchable(user);
        Attendance record = todayRecord(user.getUserId(), LocalDate.now());
        boolean hasIn = record != null && record.getCheckInTime() != null;
        boolean hasOut = record != null && record.getCheckOutTime() != null;
        if (hasOut) {
            throw new BusinessException(400, "今日上下班均已打卡，无需重复");
        }
        Map<String, Object> result = hasIn ? checkOut() : checkIn();
        result.put("punchType", hasIn ? "out" : "in");
        return result;
    }

    /** 打卡页初始数据：本人今日记录 + 部门排班 + 服务器时间；老板角色返回 punchable=false 不适用标记 */
    public Map<String, Object> today() {
        LoginUser user = currentUser();
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        // 角色兑底：老板进打卡页时不报错，返回明确的「不适用」标记供前端展示友好提示
        if (isSuperAdmin(user)) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("punchable", false);
            data.put("punchMessage", "超级管理员（老板）无需打卡");
            data.put("serverTime", now.format(DATE_TIME_FMT));
            data.put("workDate", today.toString());
            data.put("record", null);
            return data;
        }

        Integer deptId = currentDepartmentId(user);
        Department dept = deptId == null ? null : departmentMapper.selectById(deptId);

        // 排班基准沿 parent_id 上溯解析（若存在子部门则归口到其一级部门，如研发部）；
        // 追溯失败时不报错，返回 punchable=false + 可读提示，打卡页展示友好信息
        WorkWindow window;
        try {
            window = resolveWorkWindow(deptId);
        } catch (BusinessException e) {
            Map<String, Object> unavailable = new LinkedHashMap<>();
            unavailable.put("punchable", false);
            unavailable.put("punchMessage", e.getMessage());
            unavailable.put("serverTime", now.format(DATE_TIME_FMT));
            unavailable.put("workDate", today.toString());
            unavailable.put("departmentName", dept == null ? "未分配部门" : dept.getName());
            unavailable.put("record", null);
            return unavailable;
        }

        Attendance record = todayRecord(user.getUserId(), today);

        // 锁定态：已过最迟上班打卡截止点且当天无上班卡 → 不可再打卡，前端展示缺勤提示
        LocalTime cutoff = window.end().minusMinutes(PUNCH_CUTOFF_MINUTES);
        boolean noCheckIn = (record == null || record.getCheckInTime() == null);
        if (noCheckIn && !now.toLocalTime().isBefore(cutoff)) {
            Map<String, Object> locked = new LinkedHashMap<>();
            locked.put("punchable", false);
            locked.put("punchMessage", "已过最迟上班打卡时间（" + cutoff.format(TIME_FMT) + "），今日登记为缺勤");
            locked.put("serverTime", now.format(DATE_TIME_FMT));
            locked.put("workDate", today.toString());
            locked.put("departmentName", primaryDeptName(dept));
            locked.put("record", null);
            return locked;
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("punchable", true);
        data.put("serverTime", now.format(DATE_TIME_FMT));
        data.put("workDate", today.toString());
        data.put("departmentName", primaryDeptName(dept));
        data.put("scheduleDeptName", window.ownerDepartmentName());
        data.put("workStartTime", window.start().format(TIME_FMT));
        data.put("workEndTime", window.end().format(TIME_FMT));
        data.put("hasSchedule", true);

        if (record == null) {
            data.put("record", null);
        } else {
            Map<String, Object> rec = new LinkedHashMap<>();
            rec.put("checkInTime", record.getCheckInTime() == null ? null
                    : record.getCheckInTime().format(DATE_TIME_MIN_FMT));
            rec.put("checkOutTime", record.getCheckOutTime() == null ? null
                    : record.getCheckOutTime().format(DATE_TIME_MIN_FMT));
            rec.put("status", record.getStatus());
            rec.put("statusText", STATUS_TEXT.getOrDefault(record.getStatus(), record.getStatus()));
            data.put("record", rec);
        }
        return data;
    }

    // ==================== 考勤记录查询 ====================

    /**
     * 多条件查询考勤记录，可见范围由权限码决定：
     *   attendance:read:all  → 全公司（超管/人事）
     *   attendance:read:dept → 本部门子树（部门领导；领导挂在项目组时即该项目组范围）
     *   其余                  → 仅本人
     * 部门口径：考勤只按一级部门核算——选中一级部门时自动包含其下全部二级项目组人员；
     * 二级项目组不作为独立查询主体。部门范围先于姓名/日期/状态等条件生效（SQL 内 AND 叠加）。
     * 对「没有打卡记录」的（人 × 日期）组合合成缺勤/未打卡行，保证考勤表完整。
     */
    @DataScope(allPerm = "attendance:read:all", deptPerm = "attendance:read:dept")
    public Map<String, Object> listRecords(AttendanceQueryDTO query) {
        // ---------- 1. 权限域 + 部门归口范围由 @DataScope 切面自动注入 ----------
        // own/dept/all 三档范围、子树展开、越权取交集、fail-closed 均由通用机制完成。
        Integer scopeUserId = query.getScopeUserId();
        List<Integer> scopeDeptIds = query.getScopeDeptIds();

        // ---------- 2. 日期归一化：默认查今天，范围上限 MAX_RANGE_DAYS 天 ----------
        LocalDate today = LocalDate.now();
        LocalDate start = parseDate(query.getStartDate(), today);
        LocalDate end = parseDate(query.getEndDate(), today);
        if (start.isAfter(end)) {
            throw new BusinessException(400, "起始日期不能晚于结束日期");
        }
        if (ChronoUnit.DAYS.between(start, end) > MAX_RANGE_DAYS) {
            throw new BusinessException(400, "查询范围不能超过 " + MAX_RANGE_DAYS + " 天");
        }
        query.setStartDate(start.toString());
        query.setEndDate(end.toString());

        // ---------- 3. 已落库的打卡记录 ----------
        List<AttendanceRecordVO> persisted = attendanceMapper.selectRecordViews(query);
        persisted.forEach(v -> v.setStatusText(STATUS_TEXT.getOrDefault(v.getStatus(), v.getStatus())));

        // ---------- 4. 合成缺勤/未打卡行 ----------
        Map<Integer, WorkWindow> deptWindowCache = new HashMap<>();
        Set<String> punchedKeys = persisted.stream()
                .map(v -> v.getUserId() + "#" + v.getWorkDate())
                .collect(Collectors.toSet());
        List<UserScopeRef> users = attendanceMapper.selectScopeUsers(scopeUserId, scopeDeptIds).stream()
                .filter(u -> query.getKeyword() == null || query.getKeyword().isEmpty()
                        || (u.getRealName() != null && u.getRealName().contains(query.getKeyword())))
                .toList();

        List<AttendanceRecordVO> synthesized = new ArrayList<>();
        LocalTime now = LocalTime.now();
        for (LocalDate d = end; !d.isBefore(start); d = d.minusDays(1)) {
            for (UserScopeRef u : users) {
                if (punchedKeys.contains(u.getUserId() + "#" + d)) {
                    continue; // 当天已有打卡记录，不合成
                }
                // 子部门沿 parent_id 上溯取一级部门排班；整条链无排班时无法公平判定，
                // 不合成该员工该天的行（避免退化成硬编码默认时间造成误判）
                WorkWindow window;
                try {
                    window = deptWindowCache.computeIfAbsent(
                            u.getDepartmentId() == null ? -1 : u.getDepartmentId(),
                            k -> resolveWorkWindow(k == -1 ? null : k));
                } catch (BusinessException e) {
                    continue;
                }
                // 规则 4：过了部门下班时间仍未打上班卡 → 缺勤；当天未到下班时间 → 未打卡
                // 缺勤阈值提前到「下班前 PUNCH_CUTOFF_MINUTES 分钟」的截止点，与打卡锁定口径一致
                LocalTime punchCutoff = window.end().minusMinutes(PUNCH_CUTOFF_MINUTES);
                boolean pastWorkEnd = d.isBefore(today) || now.isAfter(punchCutoff);
                String status = pastWorkEnd ? "absent" : "pending";
                if (query.getStatus() != null && !query.getStatus().isEmpty()
                        && !status.equals(query.getStatus())) {
                    continue;
                }
                AttendanceRecordVO vo = new AttendanceRecordVO();
                vo.setUserId(u.getUserId());
                vo.setRealName(u.getRealName());
                vo.setDepartmentName(u.getDepartmentName());
                vo.setPositionName(u.getPositionName());
                vo.setWorkDate(d.toString());
                vo.setStatus(status);
                vo.setStatusText(STATUS_TEXT.get(status));
                synthesized.add(vo);
            }
        }

        // ---------- 5. 合并排序 + 内存分页 ----------
        List<AttendanceRecordVO> merged = new ArrayList<>(persisted);
        merged.addAll(synthesized);
        merged.sort(Comparator
                .comparing(AttendanceRecordVO::getWorkDate, Comparator.reverseOrder())
                .thenComparing(v -> v.getDepartmentName() == null ? "" : v.getDepartmentName())
                .thenComparing(v -> v.getRealName() == null ? "" : v.getRealName()));

        int total = merged.size();
        int page = Math.max(query.getPage() == null ? 1 : query.getPage(), 1);
        int size = Math.max(query.getSize() == null ? 10 : query.getSize(), 1);
        int from = Math.min((page - 1) * size, total);
        int to = Math.min(from + size, total);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", total);
        data.put("page", page);
        data.put("size", size);
        data.put("records", merged.subList(from, to));
        return data;
    }

    // ==================== 排班管理（超级管理员） ====================

    /** 排班设置页：全部部门及当前排班时间 */
    public List<ScheduleVO> listSchedules() {
        return scheduleMapper.selectScheduleViews();
    }

    /** 保存/更新某部门的弹性上下班时间（仅限一级部门；子部门自动沿用其一级部门排班） */
    public void saveSchedule(ScheduleSaveDTO dto) {
        Department dept = departmentMapper.selectById(dto.getDepartmentId());
        if (dept == null || dept.getStatus() == null || dept.getStatus() != 1) {
            throw new BusinessException(404, "部门不存在或已停用");
        }
        // 子部门不是独立核算主体，不允许单独排班（当前组织架构已不设二级组）
        if (dept.getParentId() != null) {
            Department parent = departmentMapper.selectById(dept.getParentId());
            throw new BusinessException(400, "「" + dept.getName() + "」是子部门，不单独排班；"
                    + "请为其一级部门「" + (parent == null ? "上级部门" : parent.getName()) + "」配置，子部门自动沿用");
        }
        LocalTime start = parseTime(dto.getWorkStartTime(), "上班时间");
        LocalTime end = parseTime(dto.getWorkEndTime(), "下班时间");
        if (!start.isBefore(end)) {
            throw new BusinessException(400, "上班时间必须早于下班时间");
        }

        AttendanceSchedule exist = scheduleMapper.selectOne(new QueryWrapper<AttendanceSchedule>()
                .eq("department_id", dto.getDepartmentId()));
        AttendanceSchedule schedule = exist != null ? exist : new AttendanceSchedule();
        schedule.setDepartmentId(dto.getDepartmentId());
        schedule.setWorkStartTime(start);
        schedule.setWorkEndTime(end);
        schedule.setUpdatedBy(currentUser().getUserId());
        if (schedule.getId() == null) {
            scheduleMapper.insert(schedule);
        } else {
            scheduleMapper.updateById(schedule);
        }
    }

    /** 考勤筛选下拉用的部门列表：只返回一级部门（二级项目组不是独立考勤主体，不可单独筛选） */
    public List<Map<String, Object>> departments() {
        return departmentMapper.selectEnabledDepartments().stream()
                .filter(d -> d.getParentId() == null)
                .map(d -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", d.getId());
                    m.put("name", d.getName());
                    return m;
                }).toList();
    }

    // ==================== 私有工具方法 ====================

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

    /**
     * 当前用户的部门ID。
     * LoginUser（JWT 载荷）里不存部门信息，避免部门调整后 token 失效，
     * 每次需要时从 sys_users 实时查库获取。
     */
    private Integer currentDepartmentId(LoginUser user) {
        var dbUser = userMapper.selectById(user.getUserId());
        return dbUser == null ? null : dbUser.getDepartmentId();
    }

    /** 查某人某天的考勤记录（没有返回 null） */
    private Attendance todayRecord(Integer userId, LocalDate date) {
        return attendanceMapper.selectOne(new QueryWrapper<Attendance>()
                .eq("user_id", userId)
                .eq("work_date", date));
    }

    /**
     * 解析某部门的打卡时间基准：从本部门沿 parent_id 向上追溯，
     * 取第一个已配置排班的部门（子部门 → 其一级部门，如研发部）。
     * 不静默退化为硬编码默认时间；整条链都没有排班时抛可读的业务提示。
     */
    private WorkWindow resolveWorkWindow(Integer departmentId) {
        // 委托带缓存的排班解析服务（外部 bean 调用，@Cacheable 生效）
        return scheduleCacheService.resolve(departmentId);
    }
    
    // WorkWindow 已提取到 com.employee.dto.WorkWindow，供缓存服务共用

    /** 归口部门名：沿 parent_id 上溯到一级部门，考勤展示统一用一级部门名称 */
    private String primaryDeptName(Department dept) {
        if (dept == null) {
            return "未分配部门";
        }
        Department current = dept;
        int guard = 0;
        while (current.getParentId() != null && guard++ < MAX_DEPT_DEPTH) {
            Department parent = departmentMapper.selectById(current.getParentId());
            if (parent == null) {
                break;
            }
            current = parent;
        }
        return current.getName();
    }

    private LocalDate parseDate(String text, LocalDate defaultValue) {
        if (text == null || text.isEmpty()) {
            return defaultValue;
        }
        try {
            return LocalDate.parse(text);
        } catch (Exception e) {
            throw new BusinessException(400, "日期格式错误，应为 yyyy-MM-dd");
        }
    }

    private LocalTime parseTime(String text, String label) {
        try {
            return LocalTime.parse(text, TIME_FMT);
        } catch (Exception e) {
            throw new BusinessException(400, label + "格式错误，应为 HH:mm");
        }
    }

    /** 状态码 → 中文名（Controller/前端展示共用） */
    public static String statusText(String status) {
        return STATUS_TEXT.getOrDefault(status, Objects.toString(status, ""));
    }
}
