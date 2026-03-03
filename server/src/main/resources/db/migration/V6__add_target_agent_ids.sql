-- V6: 添加目标代理ID列表字段
-- 用于保存任务创建时的目标代理列表，以便草稿任务启动时使用

-- 添加目标代理ID列表字段
ALTER TABLE task ADD COLUMN target_agent_ids VARCHAR(2000);

-- 从执行实例中回填现有任务的目标代理列表
UPDATE task t
SET t.target_agent_ids = (
    SELECT GROUP_CONCAT(DISTINCT te.agent_id SEPARATOR ',')
    FROM task_execution te
    WHERE te.task_id = t.task_id
)
WHERE EXISTS (
    SELECT 1 FROM task_execution te WHERE te.task_id = t.task_id
);
