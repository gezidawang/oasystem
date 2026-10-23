USE employee_db;
-- 1) 部门保密隔离标志
ALTER TABLE sys_departments ADD COLUMN confidential TINYINT NOT NULL DEFAULT 0 COMMENT 'confidential isolation: internal notices visible/operable only by own dept + boss';
-- 2) 财务部（code=F），开启隔离
INSERT INTO sys_departments (name, code, parent_id, status, confidential) VALUES ('财务部', 'F', NULL, 1, 1);
SET @fin_dept = LAST_INSERT_ID();
-- 3) 财务测试账号：FG10012 财务负责人(W3) / FE10011 财务员工(W1)
SET @pwd = (SELECT password FROM sys_users WHERE username='A001');
INSERT INTO sys_users (username,password,real_name,role_id,department_id,position_id,employee_no,status)
VALUES ('FG10012',@pwd,'钱多多',4,@fin_dept,3,'FG10012',1), ('FE10011',@pwd,'孙账账',5,@fin_dept,2,'FE10011',1);
UPDATE sys_departments SET manager_id=(SELECT id FROM sys_users WHERE username='FG10012') WHERE id=@fin_dept;
SELECT id,name,code,confidential,manager_id FROM sys_departments WHERE confidential=1;
SELECT id,username,real_name,department_id FROM sys_users WHERE username LIKE 'F%';