-- V5: 任务手动启动功能支持
-- 添加任务状态字段，支持草稿、启动、停止等生命周期管理

-- 1. 添加任务状态字段
ALTER TABLE task ADD COLUMN task_status VARCHAR(20) DEFAULT 'PENDING';

-- 2. 添加索引以提高查询性能
ALTER TABLE task ADD INDEX idx_task_status (task_status);

-- 3. 更新现有数据的task_status
-- 根据执行实例状态计算任务状态
UPDATE task t 
SET t.task_status = (
    CASE 
        -- 没有执行实例的任务设为DRAFT
        WHEN NOT EXISTS (SELECT 1 FROM task_execution te WHERE te.task_id = t.task_id) 
            THEN 'DRAFT'
        -- 有RUNNING或PULLED状态的执行实例
        WHEN EXISTS (SELECT 1 FROM task_execution te WHERE te.task_id = t.task_id AND te.status IN ('RUNNING', 'PULLED'))
            THEN 'RUNNING'
        -- 所有执行实例都是SUCCESS
        WHEN (SELECT COUNT(*) FROM task_execution te WHERE te.task_id = t.task_id AND te.status = 'SUCCESS') = 
             (SELECT COUNT(*) FROM task_execution te WHERE te.task_id = t.task_id)
            THEN 'SUCCESS'
        -- 所有执行实例都是FAILED或TIMEOUT
        WHEN (SELECT COUNT(*) FROM task_execution te WHERE te.task_id = t.task_id AND te.status IN ('FAILED', 'TIMEOUT')) = 
             (SELECT COUNT(*) FROM task_execution te WHERE te.task_id = t.task_id)
            THEN 'FAILED'
        -- 所有执行实例都是CANCELLED
        WHEN (SELECT COUNT(*) FROM task_execution te WHERE te.task_id = t.task_id AND te.status = 'CANCELLED') = 
             (SELECT COUNT(*) FROM task_execution te WHERE te.task_id = t.task_id)
            THEN 'CANCELLED'
        -- 部分成功部分失败
        WHEN EXISTS (SELECT 1 FROM task_execution te WHERE te.task_id = t.task_id AND te.status = 'SUCCESS')
         AND EXISTS (SELECT 1 FROM task_execution te WHERE te.task_id = t.task_id AND te.status IN ('FAILED', 'TIMEOUT'))
            THEN 'PARTIAL_SUCCESS'
        -- 默认为PENDING
        ELSE 'PENDING'
    END
);
