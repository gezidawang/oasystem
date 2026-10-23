package com.employee.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.employee.dto.PositionDTO;
import com.employee.entity.Position;
import com.employee.entity.User;
import com.employee.exception.BusinessException;
import com.employee.mapper.DepartmentMapper;
import com.employee.mapper.PositionMapper;
import com.employee.mapper.UserMapper;
import com.employee.security.LoginUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 岗位管理服务（Phase 1, 8-2）。
 * 8-2-1 岗位增改/等级：org:manage + W3；
 * 8-2-2 weight_code 维护：权重敏感，仅 W4（总经理）可改/可设不匹配组合。
 */
@Service
public class PositionService {

    private final PositionMapper positionMapper;
    private final UserMapper userMapper;
    private final DepartmentMapper departmentMapper;

    public PositionService(PositionMapper positionMapper, UserMapper userMapper,
                           DepartmentMapper departmentMapper) {
        this.positionMapper = positionMapper;
        this.userMapper = userMapper;
        this.departmentMapper = departmentMapper;
    }

    // ==================== 列表 ====================

    public List<Map<String, Object>> list() {
        List<Position> positions = positionMapper.selectList(
                new QueryWrapper<Position>().orderByDesc("level"));
        return positions.stream().map(p -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", p.getId());
            row.put("name", p.getName());
            row.put("level", p.getLevel());
            row.put("weightCode", p.getWeightCode());
            row.put("departmentId", p.getDepartmentId());
            row.put("departmentName", p.getDepartmentId() == null ? "全公司"
                    : Objects.requireNonNull(departmentMapper.selectById(p.getDepartmentId()), "部门存在").getName());
            row.put("userCount", userMapper.selectCount(
                    new QueryWrapper<User>().eq("position_id", p.getId())));
            return row;
        }).toList();
    }

    // ==================== 新增 ====================

    public void create(PositionDTO dto) {
        validateNameUnique(dto.getName(), null);
        // 权重字母必须与等级匹配，不匹配的组合仅 W4 可设（防 W3 自建高权重岗位）
        if (!expectedWeight(dto.getLevel()).equals(dto.getWeightCode()) && !isW4()) {
            throw new BusinessException(403, "权重字母与等级不匹配的组合仅总经理可设置");
        }
        Position position = new Position();
        position.setName(dto.getName());
        position.setDepartmentId(dto.getDepartmentId());
        position.setLevel(dto.getLevel());
        position.setWeightCode(dto.getWeightCode());
        positionMapper.insert(position);
    }

    // ==================== 修改 ====================

    public void update(Integer id, PositionDTO dto) {
        Position existing = positionMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "岗位不存在");
        }
        validateNameUnique(dto.getName(), id);

        boolean weightChanged = dto.getWeightCode() != null
                && !dto.getWeightCode().equals(existing.getWeightCode());
        boolean levelChanged = !dto.getLevel().equals(existing.getLevel());
        // weight_code 变更：仅 W4（8-2-2 铁律）
        if (weightChanged && !isW4()) {
            throw new BusinessException(403, "权重字母（weight_code）仅总经理可修改");
        }
        // 等级变更会连带影响工号权重字母一致性：改等级时若不同步提交合法 weightCode，需 W4 或保持匹配
        if (levelChanged && !weightChanged
                && !dto.getWeightCode().equals(expectedWeight(dto.getLevel())) && !isW4()) {
            throw new BusinessException(403, "等级变更需同步调整权重字母（仅总经理可设不匹配组合）");
        }

        existing.setName(dto.getName());
        existing.setDepartmentId(dto.getDepartmentId());
        existing.setLevel(dto.getLevel());
        if (dto.getWeightCode() != null) {
            existing.setWeightCode(dto.getWeightCode());
        }
        positionMapper.updateById(existing);
    }

    // ==================== 删除（仅 W4，且无人在岗） ====================

    public void delete(Integer id) {
        Position existing = positionMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "岗位不存在");
        }
        Long count = userMapper.selectCount(new QueryWrapper<User>().eq("position_id", id));
        if (count > 0) {
            throw new BusinessException(400, "该岗位下还有 " + count + " 名员工，无法删除");
        }
        positionMapper.deleteById(id);
    }

    // ==================== 私有 ====================

    /** 等级 → 权重字母映射（E=员工1 / G=主管2-负责人3 / A=总经理99） */
    private String expectedWeight(Integer level) {
        if (level == null) return "E";
        if (level >= 99) return "A";
        if (level >= 2) return "G";
        return "E";
    }

    /** 当前登录人是否 W4（总经理级，level>=99） */
    private boolean isW4() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof LoginUser loginUser)) {
            return false;
        }
        User user = userMapper.selectById(loginUser.getUserId());
        if (user == null || user.getPositionId() == null) {
            return false;
        }
        Position position = positionMapper.selectById(user.getPositionId());
        return position != null && position.getLevel() != null && position.getLevel() >= 99;
    }

    private void validateNameUnique(String name, Integer excludeId) {
        QueryWrapper<Position> qw = new QueryWrapper<Position>().eq("name", name);
        if (excludeId != null) {
            qw.ne("id", excludeId);
        }
        if (positionMapper.selectCount(qw) > 0) {
            throw new BusinessException(400, "岗位名称「" + name + "」已存在");
        }
    }
}
