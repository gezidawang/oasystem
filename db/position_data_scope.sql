-- ============================================================
-- 【数据范围岗位化】sys_positions.data_scope（2026-10-22 定案，幂等 DDL，重复执行安全）
-- 业务原因：
--   1) 数据范围是岗位属性，谁坐这个位子适用同一套规则，杜绝"按人特批"的人治例外；
--   2) 三正交干净切分：角色=职能域（能做什么操作），岗位=数据范围（能看哪些行），
--      权重=操作门槛（W1-W4），部门属性=保密例外（confidential 严格本部门不外溢）；
--   3) 根治 admin 角色裸权限码导致的跨部门可见性泄漏（复现者：行政总监/行政专员）。
-- 等级语义：
--   own=仅本人相关行；dept=本部门；dept_subtree=本部门+子部门（平铺期子部门不存在=退化为本部门）；
--   all=全公司（保密部门仍仅对本部门成员与老板可见——overlay，见 NoticeService/PositionScopeService）。
-- 回填口径：
--   总经理(99)=all；部门负责人(3)=dept_subtree；主管(2)=dept；员工(1)=dept。
--   将来建"组"级子部门后，主管岗可逐岗位改为 dept_subtree/组范围，只改数据不改代码。
-- ============================================================

-- 1) 加列（已存在则跳过）
SET @has_col := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_positions' AND COLUMN_NAME = 'data_scope');
SET @ddl := IF(@has_col = 0,
  'ALTER TABLE sys_positions ADD COLUMN data_scope VARCHAR(20) NOT NULL DEFAULT ''dept'' COMMENT ''数据范围:own/dept/dept_subtree/all（岗位属性）'' AFTER level',
  'SELECT 1');
PREPARE st FROM @ddl; EXECUTE st; DEALLOCATE PREPARE st;

-- 2) 按岗位等级回填（幂等，可重复执行）
UPDATE sys_positions SET data_scope = 'all'         WHERE level >= 99;
UPDATE sys_positions SET data_scope = 'dept_subtree' WHERE level >= 3 AND level < 99;
UPDATE sys_positions SET data_scope = 'dept'        WHERE level < 3;

-- 3) 验证
SELECT '=== positions data_scope ===' AS info;
SELECT id, name, level, data_scope FROM sys_positions ORDER BY level DESC;
