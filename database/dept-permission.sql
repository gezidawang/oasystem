-- =========================================================
-- 部门权限边界 / 部门文件 / 员工档案可见性 —— 数据库表设计
-- 路径：database/dept-permission.sql
--
-- 设计目标：
--   1. 部门文件与部门强绑定，按部门边界隔离；
--   2. 部门领导仅可见本部门员工档案与本部门文件，无跨部门权；
--   3. 人事部（hr）可全公司可见，但所有跨部门操作留痕；
--   4. 管理员（admin）可见所有部门，但无直接修改权（仅可改人事/部门领导提交的审批）；
--   5. 普通员工仅可见本部门同事信息 + 个人档案；
--   6. 跨部门访问走「申请-审批」流程，存于本套表中。
-- =========================================================

-- ---------------------------------------------------------
-- 1. 部门表（基础表，假定已存在；此处给出最小字段约定供外键引用）
-- ---------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_departments (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  company_id    BIGINT NOT NULL,
  parent_id     BIGINT DEFAULT 0 COMMENT '0=顶级部门',
  name          VARCHAR(64) NOT NULL,
  code          VARCHAR(32),
  leader_id     INT,
  status        TINYINT(1) DEFAULT 1,
  created_at    DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- ---------------------------------------------------------
-- 2. 部门文件表（与部门强绑定）
--    - visibility: dept=本部门可见；visible=全公司公开；cross=需申请
--    - dept_id=0 表示「公司级公开文件」
-- ---------------------------------------------------------
DROP TABLE IF EXISTS sys_dept_files;
CREATE TABLE sys_dept_files (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  company_id    BIGINT NOT NULL,
  dept_id       BIGINT NOT NULL COMMENT '所属部门ID；0=公司级公开文件',
  file_name     VARCHAR(255) NOT NULL,
  file_path     VARCHAR(500) NOT NULL,
  file_size     BIGINT DEFAULT 0,
  category      VARCHAR(32) DEFAULT '部门文件' COMMENT '制度/流程/表单/模板/部门文件',
  visibility    VARCHAR(16) DEFAULT 'dept' COMMENT 'dept/visible/cross',
  uploaded_by   BIGINT NOT NULL COMMENT '上传人 user_id',
  uploaded_by_dept BIGINT NOT NULL COMMENT '上传时所在部门（冗余，便于审计）',
  status        TINYINT(1) DEFAULT 1 COMMENT '1=正常 0=已删除',
  created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_dept (dept_id),
  INDEX idx_company_dept (company_id, dept_id),
  INDEX idx_visibility (visibility)
);

-- ---------------------------------------------------------
-- 3. 部门文件访问日志（所有读/写操作留痕）
-- ---------------------------------------------------------
DROP TABLE IF EXISTS sys_dept_file_logs;
CREATE TABLE sys_dept_file_logs (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  file_id     BIGINT NOT NULL,
  user_id     BIGINT NOT NULL,
  user_dept_id BIGINT NOT NULL COMMENT '操作人部门（冗余审计）',
  action      VARCHAR(16) NOT NULL COMMENT 'view/download/upload/delete/cross_request',
  ip          VARCHAR(64),
  user_agent  VARCHAR(255),
  created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_file (file_id),
  INDEX idx_user (user_id),
  INDEX idx_action (action)
);

-- ---------------------------------------------------------
-- 4. 部门文件跨部门访问申请表
--    - 当 visibility='cross' 或员工申请临时访问其他部门文件时使用
--    - status: pending/approved/rejected/expired
-- ---------------------------------------------------------
DROP TABLE IF EXISTS sys_dept_file_access_requests;
CREATE TABLE sys_dept_file_access_requests (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  file_id       BIGINT NOT NULL,
  applicant_id  BIGINT NOT NULL COMMENT '申请人 user_id',
  applicant_dept_id BIGINT NOT NULL,
  target_dept_id BIGINT NOT NULL COMMENT '目标文件所属部门',
  reason        VARCHAR(500) NOT NULL,
  status        VARCHAR(16) DEFAULT 'pending' COMMENT 'pending/approved/rejected/expired',
  approver_id   BIGINT COMMENT '审批人（一般是目标部门领导或人事）',
  approved_at   DATETIME,
  expire_at     DATETIME COMMENT '访问有效期',
  created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_file (file_id),
  INDEX idx_applicant (applicant_id),
  INDEX idx_status (status)
);

-- ---------------------------------------------------------
-- 5. 部门员工档案可见性配置
--    - 控制「角色 × 源部门 × 目标部门」下，是否可查看 / 编辑员工档案
--    - 默认 dept_leader 仅本部门；hr/admin 全公司；employee 仅本部门
--    - 特殊情况（如联合项目组临时授权）写入此表
-- ---------------------------------------------------------
DROP TABLE IF EXISTS sys_dept_employee_visibility;
CREATE TABLE sys_dept_employee_visibility (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  role_id       BIGINT NOT NULL COMMENT '角色ID（对应 sys_roles.id）',
  from_dept_id  BIGINT NOT NULL COMMENT '查看方所在部门',
  to_dept_id    BIGINT NOT NULL COMMENT '被查看方所在部门；0=全部',
  can_view      TINYINT(1) DEFAULT 1,
  can_edit      TINYINT(1) DEFAULT 0,
  remark        VARCHAR(255),
  enabled       TINYINT(1) DEFAULT 1,
  created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_role_from_to (role_id, from_dept_id, to_dept_id),
  INDEX idx_enabled (enabled)
);

-- ---------------------------------------------------------
-- 6. 员工档案跨部门查看申请表
-- ---------------------------------------------------------
DROP TABLE IF EXISTS sys_employee_view_requests;
CREATE TABLE sys_employee_view_requests (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  applicant_id  BIGINT NOT NULL,
  applicant_dept_id BIGINT NOT NULL,
  target_employee_id BIGINT NOT NULL,
  target_dept_id BIGINT NOT NULL,
  reason        VARCHAR(500) NOT NULL,
  status        VARCHAR(16) DEFAULT 'pending',
  approver_id   BIGINT,
  approved_at   DATETIME,
  expire_at     DATETIME,
  created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_applicant (applicant_id),
  INDEX idx_target (target_employee_id),
  INDEX idx_status (status)
);

-- ---------------------------------------------------------
-- 7. 初始化默认可见性矩阵（与前端 permission.js 对齐）
--    - role_id 1=admin 2=hr 3=dept_leader 4=employee
-- ---------------------------------------------------------
INSERT IGNORE INTO sys_dept_employee_visibility (role_id, from_dept_id, to_dept_id, can_view, can_edit, remark) VALUES
  (1, 0, 0, 1, 0, '管理员：全公司可见，无直接修改权'),
  (2, 0, 0, 1, 1, '人事部：全公司可见 + 可编辑'),
  (3, 0, 0, 1, 0, '部门领导：默认仅本部门'),
  (4, 0, 0, 0, 0, '普通员工：默认无跨部门查看权');

-- ---------------------------------------------------------
-- 8. 视图（便捷查询「我能看的员工」）
--    - 通过 session 变量 @current_user_id / @current_dept_id / @current_role_id 注入
-- ---------------------------------------------------------
DROP VIEW IF EXISTS v_visible_employees;
CREATE VIEW v_visible_employees AS
SELECT u.*
FROM sys_users u
WHERE
  -- hr 全公司可见
  EXISTS (SELECT 1 FROM sys_users me WHERE me.id = @current_user_id AND me.role_id = 2)
  OR
  -- admin 全公司可见（除人事决策类页面）
  EXISTS (SELECT 1 FROM sys_users me WHERE me.id = @current_user_id AND me.role_id = 1)
  OR
  -- 部门领导：本部门可见
  (EXISTS (SELECT 1 FROM sys_users me WHERE me.id = @current_user_id AND me.role_id = 3)
    AND u.department_id = @current_dept_id)
  OR
  -- 普通员工：本部门可见 + 自己
  (EXISTS (SELECT 1 FROM sys_users me WHERE me.id = @current_user_id AND me.role_id = 4)
    AND (u.department_id = @current_dept_id OR u.id = @current_user_id));