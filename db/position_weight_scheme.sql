-- ============================================================
-- 职位档位与权重体系落地（Q8 方案A + Q9 建议人选）
-- 路径：server-java/db/position_weight_scheme.sql
-- 决策依据：详细设计/00-权重体系与权限矩阵.md §二 §五
-- 内容：
--   1. sys_positions 新增 weight_code 字段（与工号第二字母一一对应）
--   2. 职位扩为 4 档：总经理(99/A) 部门负责人(3/G) 主管(2/G) 员工(1/E)
--      —— 原「组长(2)」重命名为「主管(2)」，消除已删除的"二级组"语义
--   3. 演示账号职位按建议人选重新指派
-- 工号影响评估：部门负责人与主管的权重字母同为 G，总经理走 A001 单字母特例，
--   因此本次调整【不产生任何工号变更】，登录名与历史数据均不受影响。
-- 本脚本幂等，可重复执行。
-- ============================================================

USE employee_db;
SET NAMES utf8mb4;

-- ---------- 1. 新增 weight_code 字段 ----------
SET @has_wc := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'employee_db' AND TABLE_NAME = 'sys_positions' AND COLUMN_NAME = 'weight_code'
);
SET @ddl := IF(@has_wc = 0,
  'ALTER TABLE sys_positions ADD COLUMN weight_code CHAR(1) DEFAULT NULL COMMENT ''权重字母(工号第2位): A=总经理 G=管理 E=员工'' AFTER level',
  'SELECT 1');
PREPARE wc_stmt FROM @ddl;
EXECUTE wc_stmt;
DEALLOCATE PREPARE wc_stmt;

-- ---------- 2. 职位档位（4 档）----------
-- id=1 原「组长」→ 改名「主管」，level 不变，引用它的用户无需迁移
UPDATE sys_positions SET name = '主管',       level = 2,  weight_code = 'G' WHERE id = 1;
UPDATE sys_positions SET name = '员工',       level = 1,  weight_code = 'E' WHERE id = 2;
INSERT INTO sys_positions (id, name, level, weight_code) VALUES
  (3, '部门负责人', 3,  'G'),
  (4, '总经理',    99, 'A')
ON DUPLICATE KEY UPDATE name = VALUES(name), level = VALUES(level), weight_code = VALUES(weight_code);

-- ---------- 3. 演示账号职位指派（Q9 建议人选）----------
UPDATE sys_users SET position_id = 4 WHERE username = 'A001';     -- 张伟   总经理   W4
UPDATE sys_users SET position_id = 3 WHERE username = 'RG10001';  -- 李芳   人事·部门负责人 W3
UPDATE sys_users SET position_id = 3 WHERE username = 'YG10003';  -- 刘强   研发部·部门负责人 W3
UPDATE sys_users SET position_id = 1 WHERE username = 'YG10004';  -- 陈静   研发部·主管       W2
UPDATE sys_users SET position_id = 2 WHERE username IN
  ('RE10002', 'YE10005', 'YE10006', 'SE10007', 'OE10008');        -- 其余   员工             W1

-- ---------- 4. 校验（可选执行）----------
-- SELECT id, name, level, weight_code FROM sys_positions ORDER BY level DESC;
-- SELECT u.username, u.real_name, d.name AS dept, p.name AS position, p.level, p.weight_code
-- FROM sys_users u LEFT JOIN sys_departments d ON d.id = u.department_id
-- LEFT JOIN sys_positions p ON p.id = u.position_id ORDER BY p.level DESC, u.id;
