-- ========================================
-- 日志文件存储升级
-- 版本: V2
-- 日期: 2024-01-15
-- 说明: 将日志存储从数据库改为文件系统
-- ========================================

-- 1. 为Task表增加新字段（如果不存在）
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
     WHERE table_name = 'tasks' 
     AND column_name = 'execution_count' 
     AND table_schema = DATABASE()) = 0,
    'ALTER TABLE tasks ADD COLUMN execution_count INT DEFAULT 0 COMMENT ''执行次数，每次启动时累加''',
    'SELECT "execution_count column already exists"'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
     WHERE table_name = 'tasks' 
     AND column_name = 'log_file_path' 
     AND table_schema = DATABASE()) = 0,
    'ALTER TABLE tasks ADD COLUMN log_file_path VARCHAR(500) COMMENT ''日志文件路径''',
    'SELECT "log_file_path column already exists"'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. 为已运行的任务设置execution_count
UPDATE tasks 
SET execution_count = 1 
WHERE status IN ('RUNNING', 'SUCCESS', 'FAILED', 'TIMEOUT')
AND started_at IS NOT NULL;

-- 3. 注释：TaskLog表保留但不再使用
-- 原因：日志改为存储到文件系统
-- 选项1：保留表结构作为备用
-- 选项2：清空数据但保留表
-- TRUNCATE TABLE task_logs;
-- 选项3：完全删除表
-- DROP TABLE task_logs;

-- 推荐：保留表结构，暂时不清理数据，待系统稳定后再清理
