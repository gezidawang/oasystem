-- ============================================================
-- 考勤角色定位修正：超级管理员（老板）不是被考勤对象
-- 路径：server-java/db/attendance_superadmin_fix.sql
-- 内容：
--   1. 吊销 super_admin 角色的打卡权限 attendance:check:own
--      （保留 attendance:read:all 全公司查看 + attendance:schedule:manage 排班管理）
--   2. 清理 super_admin 用户的历史打卡记录（老板不应出现在考勤表里）
-- 本脚本幂等，可重复执行（DELETE 天然幂等）
-- ============================================================

USE employee_db;
SET NAMES utf8mb4;

-- ---------- 1. 吊销超管的打卡权限 ----------
DELETE rp FROM sys_role_permissions rp
JOIN sys_roles r ON rp.role_id = r.id
JOIN sys_permissions p ON rp.permission_id = p.id
WHERE r.role_code = 'super_admin'
  AND p.perm_code = 'attendance:check:own';

-- ---------- 2. 清理老板的历史打卡记录 ----------
DELETE a FROM biz_attendance a
JOIN sys_users u ON u.id = a.user_id
JOIN sys_roles r ON r.id = u.role_id
WHERE r.role_code = 'super_admin';

-- ---------- 3. 校验（可选执行）----------
-- 超管剩余考勤权限应为：attendance:read:own / read:dept / read:all / schedule:manage（无 check:own）
-- SELECT p.perm_code FROM sys_permissions p
-- JOIN sys_role_permissions rp ON rp.permission_id = p.id
-- JOIN sys_roles r ON r.id = rp.role_id
-- WHERE r.role_code = 'super_admin' AND p.perm_code LIKE 'attendance%';
