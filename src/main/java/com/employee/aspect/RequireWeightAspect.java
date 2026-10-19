package com.employee.aspect;

import com.employee.annotation.RequireWeight;
import com.employee.entity.Position;
import com.employee.entity.User;
import com.employee.exception.BusinessException;
import com.employee.mapper.PositionMapper;
import com.employee.mapper.UserMapper;
import com.employee.security.LoginUser;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 权重门槛切面（Phase 0-7）。
 * 在 @PreAuthorize（权限码）之后、业务方法之前执行。
 * 实时查库取当前用户 position.level，不足则抛 403 统一 Result。
 * <p>
 * 设计铁律：
 * - 无职位（positionId NULL）→ level=1（最低），不得放行高门槛接口。
 * - 不信任 JWT 中的职级（避免职位调整后 token 过期问题）。
 * - 不改变 HTTP 500；抛 BusinessException(403) → GlobalExceptionHandler → Result{code:403}。
 */
@Aspect
@Component
public class RequireWeightAspect {

    /** 无岗位时默认最低 level */
    private static final int DEFAULT_LEVEL = 1;

    private final UserMapper userMapper;
    private final PositionMapper positionMapper;

    public RequireWeightAspect(UserMapper userMapper, PositionMapper positionMapper) {
        this.userMapper = userMapper;
        this.positionMapper = positionMapper;
    }

    @Before("@annotation(requireWeight)")
    public void checkWeight(JoinPoint joinPoint, RequireWeight requireWeight) {
        int required = requireWeight.value();
        int userLevel = resolveCurrentLevel();
        if (userLevel < required) {
            throw new BusinessException(403,
                    "职级未达门槛：需 level ≥ " + required + "，当前 level = " + userLevel);
        }
    }

    /**
     * 实时查库解析当前登录用户的岗位 level。
     * 链路：SecurityContext → userId → User.positionId → Position.level。
     * 任一环节缺失则返回 DEFAULT_LEVEL(1)。
     */
    private int resolveCurrentLevel() {
        LoginUser loginUser = getLoginUser();
        if (loginUser == null) {
            return DEFAULT_LEVEL;
        }
        User user = userMapper.selectById(loginUser.getUserId());
        if (user == null || user.getPositionId() == null) {
            return DEFAULT_LEVEL;
        }
        Position position = positionMapper.selectById(user.getPositionId());
        if (position == null || position.getLevel() == null) {
            return DEFAULT_LEVEL;
        }
        return position.getLevel();
    }

    private LoginUser getLoginUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser loginUser) {
            return loginUser;
        }
        return null;
    }
}
