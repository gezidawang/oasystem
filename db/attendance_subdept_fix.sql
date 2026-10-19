-- ============================================================
-- 考勤排班归属修正：子部门（前端组/后端组）不单独排班
-- 路径：server-java/db/attendance_subdept_fix.sql
-- 业务口径：技术部已涵盖前端组、后端组，子部门不是独立核算主体；
--   排班只面向一级部门（parent_id IS NULL），子部门员工打卡时
--   由后端沿 parent_id 上溯沿用技术部排班。
-- 内容：
--   1. 删除子部门的多余排班行（历史上 attendance.sql 曾为所有部门种入排班，
--      本次清理 biz_attendance_schedules 中 department_id 指向子部门的行，
--      即：前端组(id=2)、后端组(id=3) 两行）
--   2. 校验查询（注释提供，可手动执行）
-- 本脚本幂等，可重复执行（DELETE 天然幂等）
-- ============================================================

USE employee_db;
SET NAMES utf8mb4;

-- ---------- 1. 清理子部门的排班行 ----------
DELETE s FROM biz_attendance_schedules s
JOIN sys_departments d ON d.id = s.department_id
WHERE d.parent_id IS NOT NULL;

-- ---------- 2. 校验（可选执行）----------
-- 排班表中应只剩一级部门：技术部/人事部/行政部/市场部/运营部
-- SELECT s.department_id, d.name, s.work_start_time, s.work_end_time
-- FROM biz_attendance_schedules s
-- JOIN sys_departments d ON d.id = s.department_id
-- ORDER BY s.department_id;
