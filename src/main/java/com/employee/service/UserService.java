package com.employee.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.employee.dto.AccountDTO;
import com.employee.entity.Department;
import com.employee.entity.Employee;
import com.employee.entity.Position;
import com.employee.entity.Role;
import com.employee.entity.User;
import com.employee.exception.BusinessException;
import com.employee.mapper.DepartmentMapper;
import com.employee.mapper.EmployeeMapper;
import com.employee.mapper.PositionMapper;
import com.employee.mapper.RoleMapper;
import com.employee.mapper.UserMapper;
import com.employee.utils.JwtUtil;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 员工账号管理（区域八 8-3：唯一建档入口）。
 *
 * 定案口径（08 文档）：
 * - 建档同事务写 sys_users + biz_employees 并生成工号（部门字母+权重字母+5位全局流水，A001 特例不再发）；
 * - 初始密码服务端随机生成 → BCrypt 入库 → 仅一次性返回给录入人，不落日志明文；
 * - phone/email 建档即 AES 字段级加密（TypeHandler），列表展示掩码；
 * - 停用（离职禁用）：status=0 且旧 token 立即进 Redis 黑名单（user:off:{id}，TTL=token 有效期），
 *   认证过滤器实查——联动 9-5 账号安全；
 * - 解锁：复用 AuthService.unlockAccount（清账号锁与失败计数，企业"帮助台解锁"）；
 * - 工号编辑规则：本模块不改工号；调岗/晋升换字母归区域四异动流程（数字终身不变）。
 */
@Service
public class UserService {

    /** 停用账号 token 黑名单键前缀（过滤器每次请求实查） */
    public static final String OFF_KEY_PREFIX = "user:off:";

    private static final SecureRandom RANDOM = new SecureRandom();
    /** 初始密码字符集：去掉了易混淆的 0/O/1/l/I */
    private static final char[] PWD_CHARS = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789@#$%&*".toCharArray();

    private final UserMapper userMapper;
    private final EmployeeMapper employeeMapper;
    private final DepartmentMapper departmentMapper;
    private final PositionMapper positionMapper;
    private final RoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;
    private final JwtUtil jwtUtil;
    private final AuthService authService;

    public UserService(UserMapper userMapper, EmployeeMapper employeeMapper,
                       DepartmentMapper departmentMapper, PositionMapper positionMapper,
                       RoleMapper roleMapper, PasswordEncoder passwordEncoder,
                       StringRedisTemplate redisTemplate, JwtUtil jwtUtil, AuthService authService) {
        this.userMapper = userMapper;
        this.employeeMapper = employeeMapper;
        this.departmentMapper = departmentMapper;
        this.positionMapper = positionMapper;
        this.roleMapper = roleMapper;
        this.passwordEncoder = passwordEncoder;
        this.redisTemplate = redisTemplate;
        this.jwtUtil = jwtUtil;
        this.authService = authService;
    }

    // ==================== 列表（手机号掩码，密码永不回传） ====================

    public Map<String, Object> page(String keyword, Integer departmentId, Integer status, int page, int size) {
        QueryWrapper<User> qw = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            qw.and(w -> w.like("real_name", kw).or().like("employee_no", kw));
        }
        if (departmentId != null) {
            qw.eq("department_id", departmentId);
        }
        if (status != null) {
            qw.eq("status", status);
        }
        qw.orderByDesc("id");
        Page<User> p = userMapper.selectPage(new Page<>(page, size), qw);

        // 批量补部门/岗位/角色名与档案在职状态（按 id 集批查防 N+1；档案表按 user_id 映射）
        Map<Integer, String> deptNames = new LinkedHashMap<>();
        java.util.Set<Integer> deptIds = p.getRecords().stream().map(User::getDepartmentId)
                .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        if (!deptIds.isEmpty()) {
            departmentMapper.selectBatchIds(deptIds).forEach(d -> deptNames.put(d.getId(), d.getName()));
        }
        Map<Integer, Position> positions = new LinkedHashMap<>();
        java.util.Set<Integer> posIds = p.getRecords().stream().map(User::getPositionId)
                .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        if (!posIds.isEmpty()) {
            positionMapper.selectBatchIds(posIds).forEach(x -> positions.put(x.getId(), x));
        }
        Map<Integer, String> roleNames = new LinkedHashMap<>();
        java.util.Set<Integer> roleIds = p.getRecords().stream().map(User::getRoleId)
                .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        if (!roleIds.isEmpty()) {
            roleMapper.selectBatchIds(roleIds).forEach(r -> roleNames.put(r.getId(), r.getRoleName()));
        }
        var userIds = p.getRecords().stream().map(User::getId).toList();
        Map<Integer, String> empStatus = new LinkedHashMap<>();
        if (!userIds.isEmpty()) {
            employeeMapper.selectList(new QueryWrapper<Employee>().in("user_id", userIds))
                    .forEach(e -> empStatus.put(e.getUserId(), e.getStatus()));
        }

        List<Map<String, Object>> records = p.getRecords().stream().map(u -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", u.getId());
            row.put("employeeNo", u.getEmployeeNo());
            row.put("realName", u.getRealName());
            row.put("departmentId", u.getDepartmentId());
            row.put("departmentName", deptNames.get(u.getDepartmentId()));
            Position pos = u.getPositionId() == null ? null : positions.get(u.getPositionId());
            row.put("positionName", pos != null ? pos.getName() : null);
            row.put("positionLevel", pos != null ? pos.getLevel() : null);
            row.put("roleName", roleNames.get(u.getRoleId()));
            row.put("phone", maskPhone(u.getPhone()));
            row.put("status", u.getStatus());
            row.put("employeeStatus", empStatus.get(u.getId()));
            row.put("lastLoginAt", u.getLastLoginAt());
            row.put("createdAt", u.getCreatedAt());
            return row;
        }).toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", records);
        result.put("total", p.getTotal());
        result.put("page", p.getCurrent());
        result.put("size", p.getSize());
        return result;
    }

    // ==================== 建档（唯一入口：工号发号 + 双表同事务 + 一次性初始密码） ====================

    @Transactional
    public Map<String, Object> createAccount(AccountDTO dto) {
        Department dept = departmentMapper.selectById(dto.getDepartmentId());
        if (dept == null) {
            throw new BusinessException(400, "部门不存在");
        }
        if (dept.getCode() == null || dept.getCode().isBlank()) {
            throw new BusinessException(400, "该部门未维护字母 code（工号首字母来源），请先在部门管理中补充");
        }
        Position position = positionMapper.selectById(dto.getPositionId());
        if (position == null) {
            throw new BusinessException(400, "岗位不存在");
        }
        if (!java.util.Objects.equals(position.getDepartmentId(), dto.getDepartmentId())
                && position.getDepartmentId() != null) {
            // 岗位为全局模板（department_id 为空）时不做匹配；若岗位绑定了部门则必须一致
            throw new BusinessException(400, "岗位与部门不匹配（岗位须属于所选部门）");
        }
        Role role = roleMapper.selectById(dto.getRoleId());
        if (role == null) {
            throw new BusinessException(400, "角色不存在");
        }
        if (position.getLevel() != null && position.getLevel() >= 99) {
            // 总经理为系统预置唯一账号（A001 单字母特例），不参与建档发号
            throw new BusinessException(400, "总经理账号唯一（A001），不可通过建档创建");
        }
        // 权重字母直取岗位 weight_code（A=老板/G=管理/E 员工，Q9 已落库）；缺失时按 level 回退（≥2→G）；A 字头禁止再发
        String weightCode = position.getWeightCode();
        char weight = weightCode != null && !weightCode.isBlank()
                ? Character.toUpperCase(weightCode.trim().charAt(0))
                : (position.getLevel() != null && position.getLevel() >= 2 ? 'G' : 'E');
        if (weight == 'A') {
            throw new BusinessException(400, "A 字头为总经理专属工号，不可通过建档创建");
        }
        String initialPassword = randomPassword();

        // 并发安全发号：序列表行锁原子递增（每 attempt 取独占流水），唯一索引另作最后兜底；
        // 同事务回滚时取号一并回滚，不重号不废号（验收标准 6）
        for (int attempt = 1; attempt <= 5; attempt++) {
            userMapper.nextEmployeeSerial();
            int serial = userMapper.lastEmployeeSerial();
            String employeeNo = dept.getCode().trim().toUpperCase() + weight + String.format("%05d", serial);
            User user = new User();
            user.setUsername(employeeNo);
            user.setPassword(passwordEncoder.encode(initialPassword));
            user.setRealName(dto.getRealName().trim());
            user.setRoleId(dto.getRoleId());
            user.setDepartmentId(dto.getDepartmentId());
            user.setPositionId(dto.getPositionId());
            user.setEmployeeNo(employeeNo);
            user.setPhone(dto.getPhone());
            user.setEmail(dto.getEmail());
            user.setStatus(1);
            try {
                userMapper.insert(user);
            } catch (DuplicateKeyException e) {
                if (attempt == 5) {
                    throw new BusinessException(409, "工号并发撞号，请稍后重试");
                }
                continue;
            }

            Employee emp = new Employee();
            emp.setUserId(user.getId());
            emp.setRealName(user.getRealName());
            emp.setGender(dto.getGender() == null || dto.getGender().isBlank() ? "未设置" : dto.getGender());
            emp.setAge(dto.getAge());
            emp.setPhone(dto.getPhone());
            emp.setDepartmentId(dto.getDepartmentId());
            emp.setPosition(position.getName());
            emp.setHireDate(dto.getHireDate() != null ? dto.getHireDate() : LocalDate.now());
            // 新入职默认试用；在职由区域四转正流程变更
            emp.setStatus("在职".equals(dto.getEmployeeStatus()) ? "在职" : "试用");
            employeeMapper.insert(emp);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", user.getId());
            result.put("employeeNo", employeeNo);
            result.put("realName", user.getRealName());
            result.put("departmentName", dept.getName());
            result.put("positionName", position.getName());
            result.put("roleName", role.getRoleName());
            result.put("employeeStatus", emp.getStatus());
            // 一次性初始密码：仅本次响应返回，库内为 BCrypt 密文，审计日志不落此值
            result.put("initialPassword", initialPassword);
            return result;
        }
        throw new BusinessException(409, "工号发号失败，请重试");
    }

    // ==================== 受限编辑（个人信息字段；工号/部门/岗位/角色不可在此改） ====================

    @Transactional
    public void updateProfile(Integer id, AccountDTO dto) {
        User user = requireUser(id);
        if (dto.getRealName() != null && !dto.getRealName().isBlank()) {
            user.setRealName(dto.getRealName().trim());
        }
        if (dto.getPhone() != null && !dto.getPhone().isBlank()) {
            user.setPhone(dto.getPhone());
        }
        user.setEmail(dto.getEmail());
        userMapper.updateById(user);

        Employee emp = employeeMapper.selectOne(new QueryWrapper<Employee>().eq("user_id", id));
        if (emp != null) {
            emp.setRealName(user.getRealName());
            if (dto.getGender() != null && !dto.getGender().isBlank()) {
                emp.setGender(dto.getGender());
            }
            emp.setAge(dto.getAge());
            if (dto.getPhone() != null && !dto.getPhone().isBlank()) {
                emp.setPhone(dto.getPhone());
            }
            if (dto.getHireDate() != null) {
                emp.setHireDate(dto.getHireDate());
            }
            if (dto.getEmployeeStatus() != null
                    && List.of("试用", "在职", "离职").contains(dto.getEmployeeStatus())) {
                emp.setStatus(dto.getEmployeeStatus());
                // 离职联动禁用账号（8-3-2 口径：离职禁用）
                if ("离职".equals(dto.getEmployeeStatus())) {
                    applyEnable(user, false);
                }
            }
            employeeMapper.updateById(emp);
        }
    }

    // ==================== 启用/停用（停用即旧 token 进黑名单） ====================

    public void updateStatus(Integer id, Integer status) {
        User user = requireUser(id);
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(400, "状态只允许 0停用/1启用");
        }
        if (user.getId() == 1) {
            // 老板 A001 是唯一管理锚点，禁用会导致无人可恢复，服务端硬拦截
            throw new BusinessException(400, "总经理账号不可停用");
        }
        applyEnable(user, status == 1);
        user.setStatus(status);
        userMapper.updateById(user);
    }

    /** 黑名单落键/撤键：停用写入（TTL=token 有效期，自然过期后键消失），启用清除 */
    private void applyEnable(User user, boolean enable) {
        try {
            String key = OFF_KEY_PREFIX + user.getId();
            if (enable) {
                redisTemplate.delete(key);
            } else {
                redisTemplate.opsForValue().set(key, "1",
                        jwtUtil.getAccessTokenExpiration(), TimeUnit.MILLISECONDS);
            }
        } catch (Exception ignored) {
            // 与登录锁定同口径：Redis 异常不阻断主流程（token 到期自然失效兜底）
        }
    }

    // ==================== 重置初始密码（BCrypt 重加密 + 清登录锁，一次性返回） ====================

    public String resetPassword(Integer id) {
        User user = requireUser(id);
        String pwd = randomPassword();
        user.setPassword(passwordEncoder.encode(pwd));
        userMapper.updateById(user);
        // 重置后清失败锁定与计数，避免"改完密码仍被锁在门外"
        authService.unlockAccount(user.getUsername());
        return pwd;
    }

    // ==================== 解锁（帮助台口径：清账号锁与失败计数） ====================

    public void unlock(Integer id) {
        User user = requireUser(id);
        authService.unlockAccount(user.getUsername());
    }

    // ==================== 私有工具 ====================

    private User requireUser(Integer id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(404, "账号不存在");
        }
        return user;
    }

    /** 12 位随机初始密码：保证至少含一大写、一小写、一数字、一符号（满足复杂度基线） */
    private String randomPassword() {
        String upper = "ABCDEFGHJKLMNPQRSTUVWXYZ";
        String lower = "abcdefghijkmnopqrstuvwxyz";
        String digit = "23456789";
        String symbol = "@#$%&*";
        StringBuilder sb = new StringBuilder();
        sb.append(upper.charAt(RANDOM.nextInt(upper.length())))
          .append(lower.charAt(RANDOM.nextInt(lower.length())))
          .append(digit.charAt(RANDOM.nextInt(digit.length())))
          .append(symbol.charAt(RANDOM.nextInt(symbol.length())));
        for (int i = 4; i < 12; i++) {
            sb.append(PWD_CHARS[RANDOM.nextInt(PWD_CHARS.length)]);
        }
        // 打乱前 4 位的固定类型位置
        char[] arr = sb.toString().toCharArray();
        for (int i = arr.length - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            char t = arr[i];
            arr[i] = arr[j];
            arr[j] = t;
        }
        return new String(arr);
    }

    /** 手机号掩码：前 3 后 4（TypeHandler 已解密为明文，展示层脱敏） */
    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
