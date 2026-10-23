-- ============================================================
-- 公告模块 v2 + 行政角色 + 测试账号 种子脚本（幂等归档）
-- 执行方式：mysql --default-character-set=utf8mb4 -u root -p
--           然后 source 本文件（纯英文路径）
-- 内容对应 2026-10-19~20 公告分级发布/审批/置顶边界/行政角色改造的全部 DB 变更
-- ============================================================
USE employee_db;
-- ---------- 1. sys_notices 结构升级 ----------
-- 状态枚举扩展（分级发布：pending 审批中 / rejected 未通过；archived 为历史遗留不再产生）
ALTER TABLE sys_notices
  MODIFY COLUMN status ENUM('draft','pending','published','rejected','archived') NOT NULL DEFAULT 'draft';
-- 审批与编辑窗口字段
ALTER TABLE sys_notices
  ADD COLUMN submitted_at DATETIME NULL COMMENT 'submit for approval time' AFTER publish_at,
  ADD COLUMN approved_at DATETIME NULL COMMENT 'approval pass time (4h edit window start)' AFTER submitted_at,
  ADD COLUMN reject_reason VARCHAR(200) NULL COMMENT 'reject reason' AFTER approved_at,
  ADD COLUMN approver_id INT UNSIGNED NULL COMMENT 'approver user_id' AFTER reject_reason;
-- 逻辑删除（对齐 MP 全局 logic-delete-field=deleted）
ALTER TABLE sys_notices ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否1是';
-- ---------- 2. sys_departments 加固 ----------
-- 部门字母 code 唯一索引（工号发号依赖，防并发重复）
ALTER TABLE sys_departments ADD UNIQUE INDEX uk_code (code);
-- 停用时间（删除冷却期 30 天判定基准）
ALTER TABLE sys_departments ADD COLUMN disabled_at DATETIME NULL COMMENT '停用时间（删除冷却期判断）' AFTER status;
-- ---------- 3. 公告分级权限码（4 条） ----------
INSERT IGNORE INTO sys_permissions (perm_code, perm_name, resource, action, description) VALUES
('notice:publish:company','direct publish company notice','notice','publish','admin/hr/boss can publish company-wide directly'),
('notice:publish:dept','direct publish dept notice','notice','publish','dept manager can publish own dept directly'),
('notice:submit:dept','submit dept notice application','notice','submit','employee can submit dept notice for approval'),
('notice:manage:all','notice manage override','notice','manage','delete/revoke/pin any notice, hr/admin W3+ only');
-- 角色映射：super_admin=company+create；manager=dept+create；employee=submit:dept
-- ⚠ 分权修正（2026-10-21 定案）：人事保留正常发布链（create/delete/publish:company），
--   但 notice:manage:all（越权删改他人/置顶全员/撤回他人）仅行政(admin)持有；直改库后需走角色矩阵API或重启使缓存失效
INSERT IGNORE INTO sys_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM sys_roles r JOIN sys_permissions p WHERE r.role_code='super_admin' AND p.perm_code IN ('notice:publish:company');
DELETE rp FROM sys_role_permissions rp JOIN sys_roles r ON r.id=rp.role_id JOIN sys_permissions p ON p.id=rp.permission_id
WHERE r.role_code='hr' AND p.perm_code = 'notice:manage:all';
INSERT IGNORE INTO sys_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM sys_roles r JOIN sys_permissions p ON p.perm_code IN ('notice:create','notice:delete','notice:publish:dept') WHERE r.role_code='hr';
-- 人事总监收权（2026-10-21 定案）：全员公告直发/审批权仅行政+老板，人事总监同部门领导口径（本部门直发+审批，全员只能申请）
DELETE rp FROM sys_role_permissions rp JOIN sys_roles r ON r.id=rp.role_id JOIN sys_permissions p ON p.id=rp.permission_id
WHERE r.role_code='hr' AND p.perm_code = 'notice:publish:company';
INSERT IGNORE INTO sys_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM sys_roles r JOIN sys_permissions p WHERE r.role_code='manager' AND p.perm_code IN ('notice:publish:dept');
INSERT IGNORE INTO sys_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM sys_roles r JOIN sys_permissions p WHERE r.role_code='employee' AND p.perm_code IN ('notice:submit:dept');
-- 铁律：老板(super_admin)不得持有 notice:manage:all / notice:publish:dept（只留查看+审批+全员直发）
DELETE rp FROM sys_role_permissions rp JOIN sys_roles r ON r.id=rp.role_id JOIN sys_permissions p ON p.id=rp.permission_id
WHERE r.role_code='super_admin' AND p.perm_code IN ('notice:publish:dept','notice:manage:all');
-- ---------- 4. 行政角色（独立于人事） ----------
INSERT IGNORE INTO sys_roles (role_code, role_name, description, is_system) VALUES
('admin', '行政部', '公告/会议/资产/后勤管理，含公告删除撤回置顶', 1);
INSERT IGNORE INTO sys_role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM sys_roles r JOIN sys_permissions p
WHERE r.role_code='admin' AND p.perm_code IN (
 'notice:create','notice:read:own','notice:read:dept','notice:delete','notice:manage:all','notice:publish:company','notice:publish:dept',
 'news:read','news:manage','meeting:read','meeting:manage','supply:read','supply:manage','vehicle:read','vehicle:manage','asset:read','asset:manage',
 'attendance:check:own','attendance:read:own','attendance:read:dept',
 'employee:read:own','employee:read:dept','approval:submit','approval:audit:dept','notification:read:own',
 'file:read:own','file:upload:own','file:delete:own');
-- ---------- 5. 测试账号（密码 123456，哈希复用 A001） ----------
SET @pwd = (SELECT password FROM sys_users WHERE username='A001');
INSERT IGNORE INTO sys_users (username, password, real_name, role_id, department_id, position_id, employee_no, status)
SELECT 'XG10009', @pwd, '周成雄', r.id, 5, 3, 'XG10009', 1 FROM sys_roles r WHERE r.role_code='admin';
INSERT IGNORE INTO sys_users (username, password, real_name, role_id, department_id, position_id, employee_no, status)
SELECT 'XE10010', @pwd, '吴敏', r.id, 5, 2, 'XE10010', 1 FROM sys_roles r WHERE r.role_code='admin';
-- ---------- 6. 演示公告（4 条真实场景） ----------
INSERT IGNORE INTO sys_notices (id,title,content,type,department_id,publisher_id,status,top,publish_at) VALUES
(1,'关于2026年国庆节放假安排的通知','<p>全体员工：</p><p>根据国家法定假日安排，结合公司实际情况，2026年国庆节放假安排如下：</p><p>10月1日至10月7日放假，共7天。10月8日（周四）正常上班。各部门请提前做好工作交接和值班安排。</p><p>祝全体员工节日快乐！</p><p style="text-align:right">行政部</p>','all',NULL,2,'published',1,'2026-09-25 09:00:00'),
(2,'研发部关于代码评审流程调整的通知','<p>研发部全体成员：</p><p>为提升代码质量，从本周起代码评审流程调整如下：</p><p>1. 所有 MR 必须至少 2 人 Approve 才能合入；</p><p>2. 核心模块变更需部门负责人参与评审；</p><p>3. 评审响应时间不超过 4 小时。</p><p>请严格执行。</p><p style="text-align:right">研发部 - 刘强</p>','department',1,4,'published',0,'2026-10-15 14:30:00'),
(3,'关于加强考勤管理的通知','<p>全体员工：</p><p>近期发现部分员工存在代打卡、迟到早退等现象。为维护正常办公秩序，特此强调：</p><p>1. 打卡必须本人操作，严禁代打卡；</p><p>2. 最迟上班打卡时间为下班前1小时（如18:00下班，最迟17:00前须打上班卡）；</p><p>3. 超过最迟打卡时间未打卡按缺勤处理。</p><p>请各位知悉并遵守。</p><p style="text-align:right">人事部</p>','all',NULL,2,'published',0,'2026-10-18 10:00:00'),
(4,'公司OA系统升级维护公告','<p>各位同事：</p><p>为提升系统性能和用户体验，OA系统将于本周六（10月21日）22:00至次日06:00进行版本升级维护。届时系统将暂停服务，请提前保存工作内容。</p><p>如有疑问请联系行政部。</p><p style="text-align:right">行政部</p>','all',NULL,5,'published',0,'2026-10-19 16:00:00');
-- ---------- 7. 验证 ----------
SELECT '=== notice perms by role ===' AS info;
SELECT r.role_code, p.perm_code FROM sys_role_permissions rp
JOIN sys_roles r ON r.id=rp.role_id JOIN sys_permissions p ON p.id=rp.permission_id
WHERE p.perm_code LIKE 'notice:%' ORDER BY p.perm_code, r.role_code;
SELECT '=== admin users ===' AS info;
SELECT id,username,real_name,role_id,department_id,position_id FROM sys_users WHERE username LIKE 'X%';

-- ============================================================
-- 【定案口径 2026-10-21】删除/撤回三层 + 审批分流（代码实现在 NoticeService.revoke/delete/checkApprovePermission/list，本处仅归档规则）
-- 删除/撤回：
--   一、发布人本人：可撤回自己的已发布（退回自己草稿箱，publisher 不变）；删除仅限自己的草稿/未通过（已发布需先撤，业务原因：保留读者已读凭证不被静默销毁）
--   二、行政（notice:manage:all + 岗位W3，hasManageOverride）：全范围删/撤不受部门归属限制；但保密隔离部门无效（业务原因：治理权不等于侵犯部门保密边界）
--   三、部门负责人（notice:publish:dept）：可删/撤 type=department 且公告部门∈本部门或子部门（复用 dept:subtree 缓存）；
--       不依赖 manage:all、严禁跨部门；保密部门仅限严格同部门负责人（不外溢子树）
--   四、老板：不参与删除/撤回（无 manage:all），保留审批兜底
-- 审批分流：
--   全员申请(type=all) → 仅行政/老板（notice:publish:company），含各部门负责人/人事总监提交的全员申请（部门领导无全员直发权）
--   部门申请(type=department) → 仅公告所属部门负责人（publish:dept+同部门），行政/人事对他部门内审一律 403 且不进其队列
--   部门暂无负责人 → 老板兜底审批（含保密部门无负责人场景）
-- 双身份合并：行政兼某部门负责人时（如行政总监），本部门内审申请额外进其待审批队列（不互相剥夺）。
-- 【审批权分级定案 2026-10-22】（正规企业 DOA 口径；实现见 NoticeService.isDeptApprover）
--   公告内审审批权收归 W3（部门负责人/总监）：主管(W2)是执行层（初审转呈），无拍板权；
--   业务原因：审批=拍板=承担责任，权力与责任对齐；缺位场景由老板兜底（上提一级）覆盖，
--   不靠向主管摊薄审批权（正规做法是代理审批/上提，不是下放）。W2 仍保留直发本部门/删撤/置顶等执行层能力。
-- 【全员公告治理权定案 2026-10-21】（纯注释，零数据变更；实现见 NoticeService.isCompanyApprover）
--   全员公告的直发/审批 = 行政管理层（notice:publish:company + 实时职级≥3）或老板（super_admin）；
--   W1 专员（含持有 admin 角色码者，如吴敏 XE10010）对全员公告只能提交申请，不能直发也不能审批。
--   业务原因：全员广播与审批属行政管理层治理权，权限码 alone 不足以放行，必须叠加实时职级闸门，
-- 防止角色授权把 W1 专员抬到管理层级（与 notice:manage:all 的 W3 闸门同一套口径）。
-- 【未通过列表口径 + 驳回原因可见性 定案 2026-10-21】（纯注释，零数据变更；实现见 NoticeService.list/maskRejectReason）
--   未通过(status=rejected)列表：只显示 ①自己提交/发布且被驳回的 ②自己作为审批人驳回的（approver_id=当前用户）；
--   其他人的驳回记录一律不显示（含行政/老板）。业务原因：驳回原因属敏感信息仅相关双方可见，列表范围同步收窄，
--   避免无关人员借列表窥探他人被驳回记录的存在与内容。
--   驳回原因(reject_reason)可见性：仅驳回人本人（approver_id）与申请/发布人本人（publisher_id）可见；
--   其他任何人（含其他领导/行政/老板）后端返回前置空。业务原因：驳回意见可能包含对申请人的评价，属敏感人事信息。
--   驳回原因必填：前端弹窗校验 + 后端 reject() 同步拒收空值（空原因无法定位问题，驳回即失去意义）。
-- 【链内一体原则 2026-10-22】（实现见 PositionScopeService.currentVisibleDepts / NoticeService.detail/checkApprovePermission）
--   组是部门的一部分：可见节点 = 祖先链（部门公告向下穿透到组）∪ 本节点 ∪（负责人另加本链子树）；
--   组公告仍组内私有（兄弟组/部门直属人员不可见，精准投放）；保密链外依旧死隔离（链内上级/本组可见）。
--   业务原因：组员也是部门成员，部门级通知必须全链可达；组内事务不打扰无关同事。
--   同步口径：部门申请由本链负责人（W3）审批（含各组），不堵老板兜底；已发布详情可见性与列表同一套链规则。
-- 【驳回时间列 2026-10-21】（幂等 DDL，重复执行安全）
--   业务原因："未通过"列表时间列需展示驳回时刻，此前无该字段只能误显申请时间（created_at）。
--   新行由 reject() 实时落库；重新提交/撤回时清空重算；历史驳回行以创建时间近似补齐。
SET @has_col := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_notices' AND COLUMN_NAME = 'rejected_at');
SET @ddl := IF(@has_col = 0,
  'ALTER TABLE sys_notices ADD COLUMN rejected_at DATETIME NULL COMMENT ''驳回时间'' AFTER reject_reason',
  'SELECT 1');
PREPARE st FROM @ddl; EXECUTE st; DEALLOCATE PREPARE st;
UPDATE sys_notices SET rejected_at = created_at WHERE status = 'rejected' AND rejected_at IS NULL;
-- 【已发布列表可见范围 2026-10-21】（纯注释，零数据变更；实现见 NoticeService.list 已发布分支）
--   部门领导层（notice:publish:dept + 实时职级≥2）已发布可见范围收窄为"自己有权管辖的部分"：
--     ①自己提交/发布的（无论类型）②管辖范围内的部门公告（本部门或子部门；保密部门仅严格本部门，不外溢子树）③全员公告；
--   其他部门的部门公告一律不显示。业务原因：非管辖部门的公告与己无关、徒增信息干扰。
--   其余角色零变动：普通员工=全员+本部门；行政管理层=非保密全范围；老板=全范围 （含保密，只看不动）；
--   保密隔离/审批分流/置顶/删撤三层/4小时窗口等全部保持原样（判定与 deptHeadScope 同用 dept:subtree 缓存）。

-- 【链内一体终版口径 2026-10-23】（纯注释，零数据变更；实现见 PositionScopeService / NoticeService）
--   原则：可见即治理——部门公告沿组织链穿透，治理权随链展开；链外死隔离不变（fail-closed）：
--   ① 置顶链式：部门公告置顶归本链领导层（publish:dept 且 W2+，公告部门∈本人链子树，含各组/保密子组）；
--      非保密部门另可被行政管理层跨部门顶（治理口径）；全员置顶仅行政（保密成员不得顶全员）。
--   ② 保密链操作：保密公告删/撤/顶归本链领导层（链头可治理下属组）；链外（行政/兄弟组/他链）仍 403/404，"不外溢"不变。
--   ③ 草稿隐私：未提交草稿仅本人可见（审批人/行政/老板提交前一律 404）；提交后链内领导才能打开详情（队列=详情一致）。
--   ④ 行政治理窗口（回退后恢复定案）：行政借治理权删/撤他人仅限发布后 2 小时内且限当日；本人/链内领导层不受限。
--   ⑤ 发布目标限定：保密目标部门仅本链可发（含链头向下属组代发）；dept 范围岗位不得代发下级组公告。
--   本文件同日恢复被编辑器回退丢失的定案能力：服务端 XSS 白名单净化（jsoup 依赖重回 pom）、
--   类型白名单、定时发布≤now+4h、更新状态路由与字段级权限（部分 PUT 不清空字段）。
