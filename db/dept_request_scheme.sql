-- ============================================================
-- 【二期-1 编制申请流】sys_dept_change_requests + 编制申请权限码（2026-10-22，幂等，可重复执行）
-- 业务原因：
--   编制变更是公司治理事项（动预算/汇报线），采用"部门负责人/主管申请 → 老板审批 → 人事执行建制"三段式，
--   复用公告的分级审批模式（含驳回原因必填/仅双方可见）；建制执行仍走既有部门管理（org:manage），零改动。
-- 状态机：pending → approved / rejected；approved → done（人事执行建制后回填 result_dept_id）。
-- 权限码：新增 dept:manage:request（编制申请提交），只新增不修改既有码；审批=岗位门槛 W4，执行=org:manage+W3。
-- ============================================================

USE employee_db;
SET NAMES utf8mb4;

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

-- ============================================================
-- 【2026-10-24 DOA 三权分立收敛】权限码归属重排 + 执行失败回退列（幂等，可重复执行）
-- 背景：老板账号同时命中"申请+审批+执行"三段（自审自批闭环），属必须收的人治口子；
--       撤编执行才撞"在职员工"硬约束产生永远关不掉的死单。
-- 收敛后职责矩阵（谁能做什么/谁不能做什么）：
--   申请（dept:manage:request + W2）：manager/hr/admin 保留；**super_admin 剥除**（老板的建制需求由人事/部门代提）；
--   审批（dept:manage:approve + W99）：新增专用码，**仅授 super_admin**；服务层硬判定"审批人≠申请人"；
--   执行建制（org:manage + W3）：hr 保留；**super_admin 剥除**（老板不再直接建制，部门管理页写操作同步失权）；
--       服务层硬判定"执行人≠申请人且≠本单审批人"；
--   查看：老板/人事看全部申请单（canSeeAll 不变）。
--   缺位一律靠上提兜底（审批只老板能拍），不向低职级摊薄，也不给任何人开特例通道。
-- 执行失败回退：新增 fail_reason 列，人事执行撞硬约束时单据回退 failed 态并留痕，
--   老板可重新审批（整改后重试）或驳回终止，杜绝死单；
--   撤编/调整提交阶段同口径预检（在职员工/子部门/字母占用），早失败不再产生注定失败的审批单。
-- ============================================================

-- 5) 执行失败原因列（幂等）
SET @has_fail := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_dept_change_requests' AND COLUMN_NAME = 'fail_reason'
);
SET @ddl := IF(@has_fail = 0,
  'ALTER TABLE sys_dept_change_requests ADD COLUMN fail_reason VARCHAR(500) NULL COMMENT ''执行失败原因（failed 态留痕，系统校验信息不脱敏）''',
  'SELECT 1');
PREPARE st FROM @ddl; EXECUTE st; DEALLOCATE PREPARE st;

-- 6) 审批专用码（仅老板）
INSERT IGNORE INTO sys_permissions (perm_code, perm_name, resource, action, description)
VALUES ('dept:manage:approve', '编制申请审批（总经理）', 'dept', 'approve',
        '编制变更唯一拍板权（W99），不得审批自己提交的申请（职责分离）');
INSERT IGNORE INTO sys_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM sys_roles r JOIN sys_permissions p ON p.perm_code = 'dept:manage:approve'
WHERE r.role_code = 'super_admin';

-- 7) 收敛：剥除老板的提案权与建制执行权（只保留审批兜底与查看）
DELETE rp FROM sys_role_permissions rp
JOIN sys_roles r ON r.id = rp.role_id
JOIN sys_permissions p ON p.id = rp.permission_id
WHERE r.role_code = 'super_admin' AND p.perm_code IN ('dept:manage:request', 'org:manage');

-- 8) 收敛后验证：各码的持有角色
SELECT '=== DOA perm matrix after ===' AS info;
SELECT r.role_code, p.perm_code FROM sys_role_permissions rp
JOIN sys_roles r ON r.id = rp.role_id JOIN sys_permissions p ON p.id = rp.permission_id
WHERE p.perm_code IN ('dept:manage:request', 'dept:manage:approve', 'org:manage')
ORDER BY p.perm_code, r.role_code;
