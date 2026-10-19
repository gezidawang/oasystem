-- ============================================================
-- 工号编码方案落地：部门字母 + 权重字母 + 5 位数字
-- 路径：server-java/db/employee_no_scheme.sql
-- 规则：
--   工号 = [部门首字母] + [权重字母] + [5位数字]（如 RE10002 = 人事部·员工）
--   老板唯一保留单字母工号 A001，不参与双字母规则
--   部门首字母（sys_departments.code，全局唯一）：
--     Y=研发部(原技术部)  R=人事部  X=行政部  S=市场部  O=运营部
--   权重字母：A=老板  G=管理(部门领导/组长)  E=员工
--   调动只换首字母、数字不变（历史数据按 user_id 关联，不受工号变化影响）
-- 内容：
--   1. sys_departments 增加 code 字段（幂等：判存在后再 ADD）
--   2. 技术部更名研发部；为一级部门写入唯一部门字母
--   3. 删除前端组/后端组（研发部不再设独立考勤子主体），其人员归口研发部
--   4. 8 个演示账号按新规则重编 username + employee_no（A001 保留）
-- 本脚本幂等，可重复执行（UPDATE 按旧值匹配、DELETE 天然幂等）
-- ============================================================

USE employee_db;
SET NAMES utf8mb4;

-- ---------- 1. 部门表增加 code 字段 ----------
SET @has_code := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'employee_db' AND TABLE_NAME = 'sys_departments' AND COLUMN_NAME = 'code'
);
SET @ddl := IF(@has_code = 0,
  'ALTER TABLE sys_departments ADD COLUMN code CHAR(1) DEFAULT NULL COMMENT ''部门字母(工号首字母,全局唯一)'' AFTER name',
  'SELECT 1');
PREPARE code_stmt FROM @ddl;
EXECUTE code_stmt;
DEALLOCATE PREPARE code_stmt;

-- ---------- 2. 部门更名 + 写入唯一部门字母 ----------
UPDATE sys_departments SET name = '研发部', code = 'Y' WHERE id = 1;  -- 原技术部（研发口径）
UPDATE sys_departments SET code = 'R' WHERE id = 4;                   -- 人事部
UPDATE sys_departments SET code = 'X' WHERE id = 5;                   -- 行政部
UPDATE sys_departments SET code = 'S' WHERE id = 6;                   -- 市场部
UPDATE sys_departments SET code = 'O' WHERE id = 7;                   -- 运营部（Y 已被研发占用，取 Operations）

-- ---------- 3. 前端组/后端组并入研发部后删除 ----------
-- 先把挂在子部门的人员/排班迁到研发部，再删部门（FK 为 ON DELETE SET NULL，先迁避免置空）
UPDATE sys_users SET department_id = 1 WHERE department_id IN (2, 3);
DELETE FROM biz_attendance_schedules WHERE department_id IN (2, 3);
UPDATE sys_departments child
JOIN sys_departments dup ON child.parent_id = dup.id AND dup.id IN (2, 3)
SET child.parent_id = 1;
DELETE FROM sys_departments WHERE id IN (2, 3);

-- ---------- 4. 演示账号重编（username 与 employee_no 同步）----------
-- 权重：G=管理（部门领导/组长），E=员工；数字按 user id 顺序 10001 起
UPDATE sys_users SET username = 'RG10001', employee_no = 'RG10001' WHERE username = 'H001'; -- 李芳 人事·组长
UPDATE sys_users SET username = 'RE10002', employee_no = 'RE10002' WHERE username = 'H002'; -- 王敏 人事·员工
UPDATE sys_users SET username = 'YG10003', employee_no = 'YG10003' WHERE username = 'M001'; -- 刘强 研发·管理
UPDATE sys_users SET username = 'YG10004', employee_no = 'YG10004' WHERE username = 'M002'; -- 陈静 研发·管理
UPDATE sys_users SET username = 'YE10005', employee_no = 'YE10005' WHERE username = 'E001'; -- 张三 研发·员工
UPDATE sys_users SET username = 'YE10006', employee_no = 'YE10006' WHERE username = 'E002'; -- 李四 研发·员工
UPDATE sys_users SET username = 'SE10007', employee_no = 'SE10007' WHERE username = 'E003'; -- 王五 市场·员工
UPDATE sys_users SET username = 'OE10008', employee_no = 'OE10008' WHERE username = 'E004'; -- 赵六 运营·员工
-- A001（张伟，老板）保持不变

-- ---------- 5. 校验（可选执行）----------
-- SELECT id, name, code, parent_id FROM sys_departments ORDER BY id;
-- SELECT username, employee_no, real_name, department_id, position_id FROM sys_users ORDER BY id;
