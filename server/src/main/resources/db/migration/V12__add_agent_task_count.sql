-- 添加Agent任务计数字段
-- 2026-03-10: 优化Agent列表页面性能

-- 添加任务计数字段
ALTER TABLE agents ADD COLUMN task_count INT DEFAULT 0;

-- 初始化现有Agent的任务计数
-- 统计每个Agent已执行的任务数量
UPDATE agents 
SET task_count = (
    SELECT COUNT(*) 
    FROM task_executions te 
    WHERE te.agent_id = agents.agent_id
);

-- 确保新Agent的默认值为0
UPDATE agents SET task_count = 0 WHERE task_count IS NULL;