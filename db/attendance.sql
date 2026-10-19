-- ============================================================
-- 考勤打卡功能数据库迁移脚本
-- 路径：server-java/db/attendance.sql
-- 依赖：employee_db 中已存在 sys_users / sys_roles / sys_permissions /
--       sys_departments / sys_positions / biz_attendance
-- 内容：
--   1. 清理 sys_departments 历史重复数据（同名部门只保留最小 id）
--   2. 新建部门排班表 biz_attendance_schedules（每个部门可配置弹性上下班时间）
--   3. biz_attendance 增加 (user_id, work_date) 唯一键，防止一天重复打卡记录
--   4. 职位/权重档位已迁出至 position_weight_scheme.sql（本脚本不再种职位）
--   5. 为 9 个演示账号分配部门（职位由权重脚本统一指派）
--   6. 新增权限：attendance:read:all（全公司考勤）、attendance:schedule:manage（排班设置）
-- 本脚本幂等，可重复执行
-- ============================================================

USE employee_db;
SET NAMES utf8mb4;

-- ---------- 1. 清理重复部门（历史脏数据：同名部门被重复插入了 4 份）----------
-- 先把「即将被删除的重复部门」的下属部门指到保留行，避免外键悬空
UPDATE sys_departments child
JOIN sys_departments dup ON child.parent_id = dup.id
JOIN (SELECT name, MIN(id) AS keep_id FROM sys_departments GROUP BY name) k ON dup.name = k.name
SET child.parent_id = k.keep_id
WHERE dup.id <> k.keep_id;

-- 每组同名部门只保留最小 id
DELETE d FROM sys_departments d
JOIN (SELECT name, MIN(id) AS keep_id FROM sys_departments GROUP BY name) k ON d.name = k.name
WHERE d.id <> k.keep_id;

-- ---------- 2. 部门排班表 ----------
-- 每个部门一行，记录该部门的弹性上班/下班时间；
-- 部门没有排班记录时，后端按默认 09:00-18:00 兜底
CREATE TABLE IF NOT EXISTS biz_attendance_schedules (
  id              INT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '排班ID',
  department_id   INT UNSIGNED NOT NULL COMMENT '部门ID',
  work_start_time TIME NOT NULL DEFAULT '09:00:00' COMMENT '上班时间',
  work_end_time   TIME NOT NULL DEFAULT '18:00:00' COMMENT '下班时间',
  updated_by      INT UNSIGNED DEFAULT NULL COMMENT '最后修改人 user_id',
  created_at      DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_schedule_dept (department_id),
  CONSTRAINT fk_schedule_dept FOREIGN KEY (department_id)
    REFERENCES sys_departments (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='部门考勤排班表（弹性上下班时间）';

-- 为现存「一级部门」（parent_id 为 NULL）补默认排班 09:00-18:00（已存在的不覆盖，保证幂等）。
-- 不按技术栈划分二级组；若存在子部门也不单独排班，打卡时沿 parent_id 沿用所属一级部门排班
INSERT INTO biz_attendance_schedules (department_id, work_start_time, work_end_time)
SELECT d.id, '09:00:00', '18:00:00'
FROM sys_departments d
WHERE d.parent_id IS NULL
  AND NOT EXISTS (
  SELECT 1 FROM biz_attendance_schedules s WHERE s.department_id = d.id
);

-- ---------- 3. 打卡表加唯一键（一人一天只能有一条考勤记录）----------
-- MySQL 8 不支持 ADD UNIQUE IF NOT EXISTS，用 information_schema 判断后动态执行
SET @uk_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = 'employee_db'
    AND TABLE_NAME = 'biz_attendance'
    AND INDEX_NAME = 'uk_attendance_user_date'
);
SET @ddl := IF(@uk_exists = 0,
  'ALTER TABLE biz_attendance ADD UNIQUE KEY uk_attendance_user_date (user_id, work_date)',
  'SELECT 1');
PREPARE uk_stmt FROM @ddl;
EXECUTE uk_stmt;
DEALLOCATE PREPARE uk_stmt;

-- ---------- 4. 职位种子 ----------
-- 已迁出：职位档位与权重由 position_weight_scheme.sql 统一维护（4 档 + weight_code）。
-- 本脚本不再插入/覆盖 sys_positions，避免重跑时把「主管」改回旧名「组长」。

-- ---------- 5. 演示账号分配部门 ----------
-- 现存一级部门：1=研发部 4=人事部 5=行政部 6=市场部 7=运营部
-- （不设按技术栈划分的二级组；工号规则见 employee_no_scheme.sql，
--   职位/权重指派见 position_weight_scheme.sql，本脚本不再写 position_id）
UPDATE sys_users SET department_id = 1 WHERE username = 'A001';     -- 张伟   研发部
UPDATE sys_users SET department_id = 4 WHERE username = 'RG10001';  -- 李芳   人事部
UPDATE sys_users SET department_id = 4 WHERE username = 'RE10002';  -- 王敏   人事部
UPDATE sys_users SET department_id = 1 WHERE username = 'YG10003';  -- 刘强   研发部
UPDATE sys_users SET department_id = 1 WHERE username = 'YG10004';  -- 陈静   研发部
UPDATE sys_users SET department_id = 1 WHERE username = 'YE10005';  -- 张三   研发部
UPDATE sys_users SET department_id = 1 WHERE username = 'YE10006';  -- 李四   研发部
UPDATE sys_users SET department_id = 6 WHERE username = 'SE10007';  -- 王五   市场部
UPDATE sys_users SET department_id = 7 WHERE username = 'OE10008';  -- 赵六   运营部

-- ---------- 6. 新增权限（显式指定 id，配合 INSERT IGNORE 保证幂等）----------
INSERT IGNORE INTO sys_permissions (id, perm_code, perm_name, resource, action, description) VALUES
  (40, 'attendance:read:all',      '查看全公司考勤', 'attendance', 'read',     '查看全公司所有部门的考勤打卡记录'),
  (41, 'attendance:schedule:manage', '考勤排班设置', 'attendance', 'schedule', '超级管理员按部门设定弹性上下班时间');

-- 权限授予：超管拥有全部；人事额外获得全公司考勤查看权
INSERT IGNORE INTO sys_role_permissions (role_id, permission_id) VALUES
  (1, 40), (1, 41), (3, 40);
