-- ============================================================
-- 为现有 9 个 sys_users 账号回填 biz_employees 档案（测试用，幂等）
-- 规则：仅对尚无档案行的 user_id 插入；department_id 为 NOT NULL，跳过空值并提示
-- 执行方式：复制到纯英文路径后用 --default-character-set=utf8mb4 source（保中文不乱码）
-- ============================================================

USE employee_db;
SET NAMES utf8mb4;

INSERT INTO biz_employees (user_id, real_name, gender, department_id, position, hire_date, status, created_at)
SELECT u.id,
       u.real_name,
       '未设置',
       u.department_id,
       p.name,
       COALESCE(DATE(u.created_at), CURDATE()),
       '在职',
       NOW()
FROM sys_users u
LEFT JOIN sys_positions p ON p.id = u.position_id
WHERE u.department_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM biz_employees e WHERE e.user_id = u.id);
