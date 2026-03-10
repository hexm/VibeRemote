-- 添加任务执行跟踪字段
-- 2026-03-10: 为Task表添加执行次数、开始时间、结束时间字段

-- 检查并添加execution_count列（如果不存在）
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
     WHERE table_name = 'tasks' 
     AND column_name = 'execution_count' 
     AND table_schema = DATABASE()) = 0,
    'ALTER TABLE tasks ADD COLUMN execution_count INTEGER DEFAULT 1',
    'SELECT "execution_count column already exists"'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 添加started_at列（如果不存在）
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
     WHERE table_name = 'tasks' 
     AND column_name = 'started_at' 
     AND table_schema = DATABASE()) = 0,
    'ALTER TABLE tasks ADD COLUMN started_at TIMESTAMP',
    'SELECT "started_at column already exists"'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 添加finished_at列（如果不存在）
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
     WHERE table_name = 'tasks' 
     AND column_name = 'finished_at' 
     AND table_schema = DATABASE()) = 0,
    'ALTER TABLE tasks ADD COLUMN finished_at TIMESTAMP',
    'SELECT "finished_at column already exists"'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 为现有任务设置默认值
UPDATE tasks SET execution_count = 1 WHERE execution_count IS NULL OR execution_count = 0;