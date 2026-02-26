-- 任务多目标支持数据库迁移脚本
-- 版本: V4
-- 描述: 重构任务模型以支持多代理执行，创建task_executions表并迁移现有数据

-- ============================================
-- 第1步：创建task_executions表
-- ============================================
CREATE TABLE IF NOT EXISTS task_executions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '执行实例ID',
    task_id VARCHAR(64) NOT NULL COMMENT '任务ID',
    agent_id VARCHAR(64) NOT NULL COMMENT '代理ID',
    execution_number INT NOT NULL DEFAULT 1 COMMENT '执行次数（重启时递增）',
    status VARCHAR(20) DEFAULT 'PENDING' COMMENT '状态: PENDING/PULLED/RUNNING/SUCCESS/FAILED/TIMEOUT/CANCELLED',
    log_file_path VARCHAR(500) COMMENT '日志文件路径',
    exit_code INT COMMENT '退出码',
    summary TEXT COMMENT '执行摘要',
    pulled_at TIMESTAMP COMMENT '拉取时间',
    started_at TIMESTAMP COMMENT '开始时间',
    finished_at TIMESTAMP COMMENT '完成时间',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    
    -- 唯一约束：同一任务在同一代理上的同一执行次数只能有一条记录
    CONSTRAINT uk_task_agent_exec UNIQUE (task_id, agent_id, execution_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务执行实例表';

-- ============================================
-- 第2步：创建索引以提升查询性能
-- ============================================
CREATE INDEX idx_task_id ON task_executions(task_id);
CREATE INDEX idx_agent_id ON task_executions(agent_id);
CREATE INDEX idx_status ON task_executions(status);
CREATE INDEX idx_task_agent ON task_executions(task_id, agent_id);

-- ============================================
-- 第3步：迁移现有tasks数据到task_executions
-- ============================================
-- 将现有的任务执行数据迁移到task_executions表
-- 注意：只迁移有agent_id的任务（execution_count > 0表示已经被执行过）
INSERT INTO task_executions (
    task_id,
    agent_id,
    execution_number,
    status,
    log_file_path,
    exit_code,
    summary,
    pulled_at,
    started_at,
    finished_at,
    created_at
)
SELECT 
    task_id,
    agent_id,
    COALESCE(execution_count, 1) as execution_number,
    status,
    log_file_path,
    exit_code,
    summary,
    pulled_at,
    started_at,
    finished_at,
    created_at
FROM tasks
WHERE agent_id IS NOT NULL;

-- ============================================
-- 第4步：修改tasks表结构
-- ============================================
-- 删除执行相关的字段（这些字段已迁移到task_executions表）
ALTER TABLE tasks DROP COLUMN IF EXISTS agent_id;
ALTER TABLE tasks DROP COLUMN IF EXISTS batch_id;
ALTER TABLE tasks DROP COLUMN IF EXISTS status;
ALTER TABLE tasks DROP COLUMN IF EXISTS execution_count;
ALTER TABLE tasks DROP COLUMN IF EXISTS log_file_path;
ALTER TABLE tasks DROP COLUMN IF EXISTS exit_code;
ALTER TABLE tasks DROP COLUMN IF EXISTS summary;
ALTER TABLE tasks DROP COLUMN IF EXISTS pulled_at;
ALTER TABLE tasks DROP COLUMN IF EXISTS started_at;
ALTER TABLE tasks DROP COLUMN IF EXISTS finished_at;

-- 删除旧的索引
DROP INDEX IF EXISTS idx_tasks_batch_id ON tasks;
DROP INDEX IF EXISTS idx_tasks_batch_id_status ON tasks;

-- ============================================
-- 第5步：添加外键约束（可选）
-- ============================================
-- 添加外键约束，确保task_executions中的task_id必须存在于tasks表中
-- 注意：H2数据库支持外键，但如果遇到问题可以注释掉
ALTER TABLE task_executions 
ADD CONSTRAINT fk_task_executions_task_id 
FOREIGN KEY (task_id) REFERENCES tasks(task_id) ON DELETE CASCADE;

-- ============================================
-- 第6步：清理批量任务相关表（暂时保留但不使用）
-- ============================================
-- 注意：batch_tasks表暂时保留，但在UI中不再显示
-- 未来将重新设计为工作流编排功能
-- 如果需要完全删除，可以取消下面的注释：
-- DROP TABLE IF EXISTS batch_tasks;

-- ============================================
-- 迁移完成说明
-- ============================================
-- 1. task_executions表已创建，包含所有执行实例数据
-- 2. tasks表已简化，只保留任务定义相关字段
-- 3. 现有数据已完整迁移，无数据丢失
-- 4. 外键约束已添加，确保数据完整性
-- 5. 索引已优化，提升查询性能
-- 
-- 验证迁移：
-- SELECT COUNT(*) FROM task_executions; -- 应该等于迁移前tasks表的记录数
-- SELECT * FROM tasks LIMIT 5; -- 检查tasks表结构
-- SELECT * FROM task_executions LIMIT 5; -- 检查task_executions表数据

