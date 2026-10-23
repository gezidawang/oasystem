-- ============================================================
-- 【二期-1 编制申请流】sys_dept_change_requests + 编制申请权限码（2026-10-22，幂等，可重复执行）
-- 业务原因：
--   编制变更是公司治理事项（动预算/汇报线），采用"部门负责人/主管申请 → 老板审批 → 人事执行建制"三段式，
--   复用公告的分级审批模式（含驳回原因必填/仅双方可见）；建制执行仍走既有部门管理（org:manage），零改动。
-- 状态机：pending → approved / rejected；approved → done（人事执行建制后回填 result_dept_id）。
-- 权限码：新增 dept:manage:request（编制申请提交），只新增不修改既有码；审批=岗位门槛 W4，执行=org:manage+W3。
-- ============================================================

-- 1) 申请表（不存在则建）
SET @has_tbl := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_dept_change_requests');
SET @ddl := IF(@has_tbl = 0,
  'CREATE TABLE sys_dept_change_requests (
     id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
     type VARCHAR(20) NOT NULL COMMENT ''申请类型:create/disable/update'',
     target_dept_id INT UNSIGNED NULL COMMENT ''调整/撤编的目标部门id'',
     name VARCHAR(50) NULL COMMENT ''拟设/拟改部门名'',
     code CHAR(1) NULL COMMENT ''拟设/拟改部门字母（工号首字母）'',
     parent_id INT UNSIGNED NULL COMMENT ''拟上级部门id（NULL=一级）'',
     reason VARCHAR(500) NOT NULL COMMENT ''申请理由（必填）'',
     applicant_id INT UNSIGNED NOT NULL COMMENT ''申请人 user_id'',
     status VARCHAR(20) NOT NULL DEFAULT ''pending'' COMMENT ''pending/approved/rejected/done'',
     approver_id INT UNSIGNED NULL COMMENT ''审批人 user_id（老板）'',
     reject_reason VARCHAR(500) NULL COMMENT ''驳回原因（仅申请人/审批人可见）'',
     result_dept_id INT UNSIGNED NULL COMMENT ''执行建制后产生的部门id'',
     created_at DATETIME NULL, decided_at DATETIME NULL, done_at DATETIME NULL,
     KEY idx_status (status), KEY idx_applicant (applicant_id)
   ) COMMENT=''编制变更申请（负责人/主管申请、老板审批、人事执行建制）''',
  'SELECT 1');
PREPARE st FROM @ddl; EXECUTE st; DEALLOCATE PREPARE st;

-- 2) 编制申请提交权限码（新码，幂等）
INSERT IGNORE INTO sys_permissions (perm_code, perm_name, resource, action, description)
VALUES ('dept:manage:request', 'submit department change request', 'dept', 'request',
        '编制变更申请提交（部门负责人/主管），老板审批、人事执行建制');

-- 3) 角色映射：申请提交归领导层角色（super_admin/hr/admin/manager）；执行(org:manage)与审批(W4)不在此列
INSERT IGNORE INTO sys_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM sys_roles r JOIN sys_permissions p ON p.perm_code = 'dept:manage:request'
WHERE r.role_code IN ('super_admin','hr','admin','manager');

-- 4) 验证
SELECT '=== dept_request table ===' AS info;
SHOW COLUMNS FROM sys_dept_change_requests;
SELECT '=== request perm by role ===' AS info;
SELECT r.role_code FROM sys_role_permissions rp
JOIN sys_roles r ON r.id = rp.role_id JOIN sys_permissions p ON p.id = rp.permission_id
WHERE p.perm_code = 'dept:manage:request';
