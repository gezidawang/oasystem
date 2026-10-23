-- ============================================================
-- 8-3 员工建档定案归档：工号发号器序列表（唯一发号入口的并发安全底座）
-- 路径：server-java/db/account_scheme.sql
-- 口径：
--   1) 唯一建档入口在 8-3：同事务写 sys_users + biz_employees + 生成工号 + BCrypt 一次性初始密码；
--   2) 工号 = 部门字母(sys_departments.code) + 权重字母(sys_positions.weight_code A/G/E) + 5位全局流水；
--      A001 总经理单字母特例不通过建档产生（A 字头服务端禁发）；
--   3) 流水发号用单行序列表：UPDATE val=val+1 + LAST_INSERT_ID() 原子取号，并发在行锁上排队，
--      业务事务回滚时取号一并回滚——不重号、不跳号（验收标准6"并发建档不产生重号工号"的实现）；
--   4) 停用/离职：sys_users.status=0 且旧 token 进 Redis 黑名单 user:off:{id}（TTL=token 有效期），
--      JwtAuthenticationFilter 每请求实查（联动 9-5）；
--   5) 双闸门：user:manage 码（已授 hr+super_admin）+ @RequireWeight(3) 职级门槛——人事专员 W1 持码不可操作。
-- 本脚本幂等，可重复执行。
-- ============================================================

USE employee_db;
SET NAMES utf8mb4;

-- 序列表：固定单行 id=1，val=当前最大流水
CREATE TABLE IF NOT EXISTS sys_employee_no_seq (
  id  INT NOT NULL COMMENT '固定单行 id=1',
  val INT NOT NULL COMMENT '工号 5 位流水当前值（LAST_INSERT_ID 原子发号）',
  PRIMARY KEY (id)
) ENGINE = InnoDB COMMENT '8-3 建档工号发号器';

-- 初始值对齐现存最大流水（仅首次插入，幂等）
INSERT INTO sys_employee_no_seq (id, val)
SELECT 1, COALESCE((SELECT MAX(CAST(RIGHT(employee_no, 5) AS UNSIGNED))
                    FROM sys_users
                    WHERE employee_no REGEXP '^[A-Z][AGE][0-9]{5}$'), 10000)
WHERE NOT EXISTS (SELECT 1 FROM sys_employee_no_seq WHERE id = 1);
