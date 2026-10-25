-- ============================================================
-- 权限码中文名统一（2026-10-23）：Phase 0-5 补码期落的英文 perm_name 统一为中文，
-- 与角色权限矩阵页展示口径对齐（术语跟公告/审批/编制定案文案一致）。
-- 按 perm_code 匹配更新，幂等可重复执行；仅改展示名，不改任何权限码本身与授权关系。
-- ============================================================
USE employee_db;
SET NAMES utf8mb4;

UPDATE sys_permissions SET perm_name = '部门审批（审核）'          WHERE perm_code = 'approval:audit:dept';
UPDATE sys_permissions SET perm_name = '审批流程配置'              WHERE perm_code = 'workflow:manage';
UPDATE sys_permissions SET perm_name = '阅读部门公告'              WHERE perm_code = 'notice:read:dept';
UPDATE sys_permissions SET perm_name = '会议查看'                  WHERE perm_code = 'meeting:read';
UPDATE sys_permissions SET perm_name = '会议管理'                  WHERE perm_code = 'meeting:manage';
UPDATE sys_permissions SET perm_name = '转正审批'                  WHERE perm_code = 'employee:regularize';
UPDATE sys_permissions SET perm_name = '异动调岗管理'              WHERE perm_code = 'employee:transfer';
UPDATE sys_permissions SET perm_name = '绩效查看'                  WHERE perm_code = 'performance:read';
UPDATE sys_permissions SET perm_name = '绩效管理'                  WHERE perm_code = 'performance:manage';
UPDATE sys_permissions SET perm_name = '培训查看'                  WHERE perm_code = 'training:read';
UPDATE sys_permissions SET perm_name = '培训管理'                  WHERE perm_code = 'training:manage';
UPDATE sys_permissions SET perm_name = '借款申请'                  WHERE perm_code = 'loan:submit';
UPDATE sys_permissions SET perm_name = '借款审批'                  WHERE perm_code = 'loan:audit';
UPDATE sys_permissions SET perm_name = '付款申请'                  WHERE perm_code = 'payment:submit';
UPDATE sys_permissions SET perm_name = '付款查看'                  WHERE perm_code = 'payment:read';
UPDATE sys_permissions SET perm_name = '我的通知'                  WHERE perm_code = 'notification:read:own';
UPDATE sys_permissions SET perm_name = '系统日志查看'              WHERE perm_code = 'log:read';
UPDATE sys_permissions SET perm_name = '安全设置'                  WHERE perm_code = 'security:manage';
UPDATE sys_permissions SET perm_name = '员工账号管理（建档/启停/重置）' WHERE perm_code = 'user:manage';
UPDATE sys_permissions SET perm_name = '全员公告直发/审批（管理层）'    WHERE perm_code = 'notice:publish:company';
UPDATE sys_permissions SET perm_name = '部门公告直发/内审审批'          WHERE perm_code = 'notice:publish:dept';
UPDATE sys_permissions SET perm_name = '部门公告申请'                   WHERE perm_code = 'notice:submit:dept';
UPDATE sys_permissions SET perm_name = '公告治理越权（跨部门删撤顶）'   WHERE perm_code = 'notice:manage:all';
UPDATE sys_permissions SET perm_name = '编制变更申请'                   WHERE perm_code = 'dept:manage:request';
