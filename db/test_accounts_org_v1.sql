-- ============================================================
-- 【组织架构测试数据 v1】子部门 + 各档岗位测试账号 + 管辖关系（2026-10-22，幂等，可重复执行）
-- 业务原因：
--   1) 补全"每个部门×每档岗位"的测试矩阵，覆盖数据范围（own/dept/dept_subtree/all）与权重（W1-W4）全组合；
--   2) 财务部按内控"不相容职务分离"拆分会计组/出纳组（会计管账、出纳管钱，互不兼办），税务审计组独立；
--   3) 财务子组全部 confidential=1，延续财务保密隔离（外人连存在都看不到）；
--   4) 组=树状编制的叶子节点：组负责人持"主管"岗（W2，数据范围=dept=本组），部门负责人持"部门负责人"岗（W3，范围=dept_subtree=含各组）。
-- 工号规则：部门字母+权重字母+5位数字（G=主管/负责人，E=员工）；密码统一 123456（哈希复用 A001）。
-- ============================================================

-- ---------- 1) 子部门（code 全局唯一，作为工号首字母） ----------
INSERT IGNORE INTO sys_departments (name, code, parent_id, status, confidential) VALUES
('研发一组',   'B', 1,  1, 0),
('研发二组',   'D', 1,  1, 0),
('会计组',     'J', 31, 1, 1),
('出纳组',     'N', 31, 1, 1),
('税务审计组', 'K', 31, 1, 1);

-- ---------- 2) 测试账号（哈希复用 A001；INSERT IGNORE 幂等） ----------
SET @pwd = (SELECT password FROM sys_users WHERE username='A001');

-- 人事部主管（hr 职能域 + 主管岗 W2）
INSERT IGNORE INTO sys_users (username, password, real_name, role_id, department_id, position_id, employee_no, status)
SELECT 'RG10013', @pwd, '周晴', r.id, 4, 1, 'RG10013', 1 FROM sys_roles r WHERE r.role_code='hr';
-- 行政部主管（admin 职能域 + 主管岗 W2；admin 的治理码均被 W3 闸门拦住，不会越权）
INSERT IGNORE INTO sys_users (username, password, real_name, role_id, department_id, position_id, employee_no, status)
SELECT 'XG10014', @pwd, '吴亮', r.id, 5, 1, 'XG10014', 1 FROM sys_roles r WHERE r.role_code='admin';

-- 市场部：负责人 W3 + 主管 W2（manager 职能域=部门领导类）
INSERT IGNORE INTO sys_users (username, password, real_name, role_id, department_id, position_id, employee_no, status)
SELECT 'SG10015', @pwd, '郑帆', r.id, 6, 3, 'SG10015', 1 FROM sys_roles r WHERE r.role_code='manager';
INSERT IGNORE INTO sys_users (username, password, real_name, role_id, department_id, position_id, employee_no, status)
SELECT 'SG10016', @pwd, '冯磊', r.id, 6, 1, 'SG10016', 1 FROM sys_roles r WHERE r.role_code='manager';

-- 运营部：负责人 W3 + 主管 W2
INSERT IGNORE INTO sys_users (username, password, real_name, role_id, department_id, position_id, employee_no, status)
SELECT 'OG10017', @pwd, '韩涛', r.id, 7, 3, 'OG10017', 1 FROM sys_roles r WHERE r.role_code='manager';
INSERT IGNORE INTO sys_users (username, password, real_name, role_id, department_id, position_id, employee_no, status)
SELECT 'OG10018', @pwd, '杨光', r.id, 7, 1, 'OG10018', 1 FROM sys_roles r WHERE r.role_code='manager';

-- 市场营销部：负责人 W3 + 主管 W2 + 员工 W1（三层齐全）
INSERT IGNORE INTO sys_users (username, password, real_name, role_id, department_id, position_id, employee_no, status)
SELECT 'CG10029', @pwd, '曹营', r.id, 8, 3, 'CG10029', 1 FROM sys_roles r WHERE r.role_code='manager';
INSERT IGNORE INTO sys_users (username, password, real_name, role_id, department_id, position_id, employee_no, status)
SELECT 'CG10030', @pwd, '邓销', r.id, 8, 1, 'CG10030', 1 FROM sys_roles r WHERE r.role_code='manager';
INSERT IGNORE INTO sys_users (username, password, real_name, role_id, department_id, position_id, employee_no, status)
SELECT 'CE10031', @pwd, '谭广', r.id, 8, 2, 'CE10031', 1 FROM sys_roles r WHERE r.role_code='employee';

-- 研发一组/二组：组负责人（=主管岗 W2）+ 组员
INSERT IGNORE INTO sys_users (username, password, real_name, role_id, department_id, position_id, employee_no, status)
SELECT 'BG10019', @pwd, '赵一', r.id, (SELECT id FROM sys_departments WHERE code='B'), 1, 'BG10019', 1 FROM sys_roles r WHERE r.role_code='manager';
INSERT IGNORE INTO sys_users (username, password, real_name, role_id, department_id, position_id, employee_no, status)
SELECT 'BE10020', @pwd, '钱二', r.id, (SELECT id FROM sys_departments WHERE code='B'), 2, 'BE10020', 1 FROM sys_roles r WHERE r.role_code='employee';
INSERT IGNORE INTO sys_users (username, password, real_name, role_id, department_id, position_id, employee_no, status)
SELECT 'DG10021', @pwd, '孙三', r.id, (SELECT id FROM sys_departments WHERE code='D'), 1, 'DG10021', 1 FROM sys_roles r WHERE r.role_code='manager';
INSERT IGNORE INTO sys_users (username, password, real_name, role_id, department_id, position_id, employee_no, status)
SELECT 'DE10022', @pwd, '李五', r.id, (SELECT id FROM sys_departments WHERE code='D'), 2, 'DE10022', 1 FROM sys_roles r WHERE r.role_code='employee';

-- 财务三组（保密）：组负责人（主管岗 W2）+ 组员
INSERT IGNORE INTO sys_users (username, password, real_name, role_id, department_id, position_id, employee_no, status)
SELECT 'JG10023', @pwd, '周会', r.id, (SELECT id FROM sys_departments WHERE code='J'), 1, 'JG10023', 1 FROM sys_roles r WHERE r.role_code='manager';
INSERT IGNORE INTO sys_users (username, password, real_name, role_id, department_id, position_id, employee_no, status)
SELECT 'JE10024', @pwd, '吴账', r.id, (SELECT id FROM sys_departments WHERE code='J'), 2, 'JE10024', 1 FROM sys_roles r WHERE r.role_code='employee';
INSERT IGNORE INTO sys_users (username, password, real_name, role_id, department_id, position_id, employee_no, status)
SELECT 'NG10025', @pwd, '郑钱', r.id, (SELECT id FROM sys_departments WHERE code='N'), 1, 'NG10025', 1 FROM sys_roles r WHERE r.role_code='manager';
INSERT IGNORE INTO sys_users (username, password, real_name, role_id, department_id, position_id, employee_no, status)
SELECT 'NE10026', @pwd, '王款', r.id, (SELECT id FROM sys_departments WHERE code='N'), 2, 'NE10026', 1 FROM sys_roles r WHERE r.role_code='employee';
INSERT IGNORE INTO sys_users (username, password, real_name, role_id, department_id, position_id, employee_no, status)
SELECT 'KG10027', @pwd, '冯稽', r.id, (SELECT id FROM sys_departments WHERE code='K'), 1, 'KG10027', 1 FROM sys_roles r WHERE r.role_code='manager';
INSERT IGNORE INTO sys_users (username, password, real_name, role_id, department_id, position_id, employee_no, status)
SELECT 'KE10028', @pwd, '唐审', r.id, (SELECT id FROM sys_departments WHERE code='K'), 2, 'KE10028', 1 FROM sys_roles r WHERE r.role_code='employee';

-- ---------- 3) 管辖关系（谁管什么部门：manager_id=该部门负责人/组负责人） ----------
UPDATE sys_departments d JOIN sys_users u ON u.username='YG10003' SET d.manager_id=u.id WHERE d.code='Y';   -- 研发部←刘强
UPDATE sys_departments d JOIN sys_users u ON u.username='RG10001' SET d.manager_id=u.id WHERE d.code='R';   -- 人事部←李芳
UPDATE sys_departments d JOIN sys_users u ON u.username='XG10009' SET d.manager_id=u.id WHERE d.code='X';   -- 行政部←周成雄
UPDATE sys_departments d JOIN sys_users u ON u.username='SG10015' SET d.manager_id=u.id WHERE d.code='S';   -- 市场部←郑帆
UPDATE sys_departments d JOIN sys_users u ON u.username='OG10017' SET d.manager_id=u.id WHERE d.code='O';   -- 运营部←韩涛
UPDATE sys_departments d JOIN sys_users u ON u.username='CG10029' SET d.manager_id=u.id WHERE d.code='C';   -- 市场营销←曹营
UPDATE sys_departments d JOIN sys_users u ON u.username='FG10012' SET d.manager_id=u.id WHERE d.code='F';   -- 财务部←钱多多
UPDATE sys_departments d JOIN sys_users u ON u.username='BG10019' SET d.manager_id=u.id WHERE d.code='B';   -- 研发一组←赵一
UPDATE sys_departments d JOIN sys_users u ON u.username='DG10021' SET d.manager_id=u.id WHERE d.code='D';   -- 研发二组←孙三
UPDATE sys_departments d JOIN sys_users u ON u.username='JG10023' SET d.manager_id=u.id WHERE d.code='J';   -- 会计组←周会
UPDATE sys_departments d JOIN sys_users u ON u.username='NG10025' SET d.manager_id=u.id WHERE d.code='N';   -- 出纳组←郑钱
UPDATE sys_departments d JOIN sys_users u ON u.username='KG10027' SET d.manager_id=u.id WHERE d.code='K';   -- 税务审计组←冯稽

-- ---------- 4) 验证 ----------
SELECT '=== 部门树与管辖 ===' AS info;
SELECT d.id, d.name, d.code, d.parent_id, d.confidential, u.real_name AS manager
FROM sys_departments d LEFT JOIN sys_users u ON u.id = d.manager_id
ORDER BY d.parent_id IS NULL DESC, d.parent_id, d.id;
SELECT '=== 测试账号矩阵 ===' AS info;
SELECT u.employee_no, u.real_name, r.role_code, p.name AS position, p.level, d.name AS dept
FROM sys_users u JOIN sys_roles r ON r.id=u.role_id
JOIN sys_positions p ON p.id=u.position_id LEFT JOIN sys_departments d ON d.id=u.department_id
ORDER BY d.id, p.level DESC;
