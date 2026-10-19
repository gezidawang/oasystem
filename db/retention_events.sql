-- ============================================================
-- OA employee_db 分层数据留存策略调整（正式口径 B）
-- 规则：
--   普通通知            : 满 12 个月直接分批清理
--   操作日志 audit      : 满 12 个月 先归档再删主表 -> 归档满 3 年彻底删除
--   人员调动 histories  : 满 12 个月 先归档再删主表 -> 归档满 3 年彻底删除
-- 约束：每批 <= 1000 行；批间 SLEEP；归档后按主键 JOIN 精确删除，避免误删未归档行
-- ============================================================

USE employee_db;
SET GLOBAL event_scheduler = ON;

-- ---------- 0. 人员调动历史归档表（结构与主表一致，LIKE 不复制外键）----------
CREATE TABLE IF NOT EXISTS biz_employee_histories_archive LIKE biz_employee_histories;

-- ---------- 1. 操作日志：12个月 归档 + 删主表 ----------
DROP EVENT IF EXISTS archive_audit_logs;
DELIMITER $$
CREATE EVENT archive_audit_logs
ON SCHEDULE EVERY 1 DAY STARTS (CURRENT_DATE + INTERVAL 1 DAY + INTERVAL 3 HOUR)
COMMENT '审计操作日志满12个月归档并从主表分批删除'
DO
BEGIN
  DECLARE v_rows INT DEFAULT 1;
  DECLARE v_cutoff DATETIME;
  SET v_cutoff = DATE_SUB(NOW(), INTERVAL 12 MONTH);
  WHILE v_rows > 0 DO
    INSERT IGNORE INTO sys_audit_logs_archive
      (id, user_id, action, resource, target_id, old_value, new_value,
       ip_address, user_agent, description, created_at)
    SELECT id, user_id, action, resource, target_id, old_value, new_value,
           ip_address, user_agent, description, created_at
    FROM sys_audit_logs
    WHERE created_at < v_cutoff
    LIMIT 1000;
    SET v_rows = ROW_COUNT();
    DELETE sl FROM sys_audit_logs sl
      JOIN sys_audit_logs_archive sa ON sl.id = sa.id
    WHERE sl.created_at < v_cutoff;
    DO SLEEP(0.5);
  END WHILE;
END$$
DELIMITER ;

-- ---------- 2. 操作日志归档：满3年 彻底删除 ----------
DROP EVENT IF EXISTS purge_audit_logs_archive;
DELIMITER $$
CREATE EVENT purge_audit_logs_archive
ON SCHEDULE EVERY 1 DAY STARTS (CURRENT_DATE + INTERVAL 1 DAY + INTERVAL 3 HOUR)
COMMENT '审计日志归档表满3年彻底删除'
DO
BEGIN
  DECLARE v_rows INT DEFAULT 1;
  DECLARE v_cutoff DATETIME;
  SET v_cutoff = DATE_SUB(NOW(), INTERVAL 3 YEAR);
  WHILE v_rows > 0 DO
    DELETE FROM sys_audit_logs_archive
    WHERE created_at < v_cutoff
    LIMIT 1000;
    SET v_rows = ROW_COUNT();
    DO SLEEP(0.5);
  END WHILE;
END$$
DELIMITER ;

-- ---------- 3. 人员调动历史：12个月 归档 + 删主表 ----------
DROP EVENT IF EXISTS archive_employee_histories;
DELIMITER $$
CREATE EVENT archive_employee_histories
ON SCHEDULE EVERY 1 DAY STARTS (CURRENT_DATE + INTERVAL 1 DAY + INTERVAL 3 HOUR)
COMMENT '人员调动/修改历史满12个月归档并从主表分批删除'
DO
BEGIN
  DECLARE v_rows INT DEFAULT 1;
  DECLARE v_cutoff DATETIME;
  SET v_cutoff = DATE_SUB(NOW(), INTERVAL 12 MONTH);
  WHILE v_rows > 0 DO
    INSERT IGNORE INTO biz_employee_histories_archive
      (id, employee_id, change_type, old_value, new_value, applicant_id,
       changed_by, approved_by, method, reason, created_at)
    SELECT id, employee_id, change_type, old_value, new_value, applicant_id,
           changed_by, approved_by, method, reason, created_at
    FROM biz_employee_histories
    WHERE created_at < v_cutoff
    LIMIT 1000;
    SET v_rows = ROW_COUNT();
    DELETE eh FROM biz_employee_histories eh
      JOIN biz_employee_histories_archive ea ON eh.id = ea.id
    WHERE eh.created_at < v_cutoff;
    DO SLEEP(0.5);
  END WHILE;
END$$
DELIMITER ;

-- ---------- 4. 人员调动历史归档：满3年 彻底删除 ----------
DROP EVENT IF EXISTS purge_employee_histories_archive;
DELIMITER $$
CREATE EVENT purge_employee_histories_archive
ON SCHEDULE EVERY 1 DAY STARTS (CURRENT_DATE + INTERVAL 1 DAY + INTERVAL 3 HOUR)
COMMENT '人员调动历史归档表满3年彻底删除'
DO
BEGIN
  DECLARE v_rows INT DEFAULT 1;
  DECLARE v_cutoff DATETIME;
  SET v_cutoff = DATE_SUB(NOW(), INTERVAL 3 YEAR);
  WHILE v_rows > 0 DO
    DELETE FROM biz_employee_histories_archive
    WHERE created_at < v_cutoff
    LIMIT 1000;
    SET v_rows = ROW_COUNT();
    DO SLEEP(0.5);
  END WHILE;
END$$
DELIMITER ;

-- ---------- 5. 普通通知：满12个月 直接分批清理 ----------
DROP EVENT IF EXISTS cleanup_notifications;
DELIMITER $$
CREATE EVENT cleanup_notifications
ON SCHEDULE EVERY 1 DAY STARTS (CURRENT_DATE + INTERVAL 1 DAY + INTERVAL 3 HOUR)
COMMENT '普通通知满12个月直接分批清理'
DO
BEGIN
  DECLARE v_rows INT DEFAULT 1;
  DECLARE v_cutoff DATETIME;
  SET v_cutoff = DATE_SUB(NOW(), INTERVAL 12 MONTH);
  WHILE v_rows > 0 DO
    DELETE FROM sys_notifications
    WHERE created_at < v_cutoff
    LIMIT 1000;
    SET v_rows = ROW_COUNT();
    DO SLEEP(0.5);
  END WHILE;
END$$
DELIMITER ;

-- ---------- 6. 文件操作日志：满12个月 直接分批清理（普通日志）----------
DROP EVENT IF EXISTS cleanup_file_logs;
DELIMITER $$
CREATE EVENT cleanup_file_logs
ON SCHEDULE EVERY 1 DAY STARTS (CURRENT_DATE + INTERVAL 1 DAY + INTERVAL 3 HOUR)
COMMENT '文件操作日志满12个月直接分批清理'
DO
BEGIN
  DECLARE v_rows INT DEFAULT 1;
  DECLARE v_cutoff DATETIME;
  SET v_cutoff = DATE_SUB(NOW(), INTERVAL 12 MONTH);
  WHILE v_rows > 0 DO
    DELETE FROM sys_file_logs
    WHERE created_at < v_cutoff
    LIMIT 1000;
    SET v_rows = ROW_COUNT();
    DO SLEEP(0.5);
  END WHILE;
END$$
DELIMITER ;
