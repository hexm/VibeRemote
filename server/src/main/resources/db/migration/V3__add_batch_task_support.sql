-- 批量任务功能数据库迁移脚本
-- 版本: V3
-- 描述: 添加批量任务支持，包括batch_tasks表和tasks表字段扩展

-- 1. 创建批量任务表
CREATE TABLE IF NOT EXISTS batch_tasks (
    batch_id VARCHAR(64) PRIMARY KEY COMMENT '批量任务ID',
    batch_name VARCHAR(200) COMMENT '批量任务名称',
    script_lang VARCHAR(20) COMMENT '脚本类型: bash/powershell/cmd',
    script_content TEXT COMMENT '脚本内容',
    timeout_sec INT COMMENT '超时时间（秒）',
    target_agent_count INT COMMENT '目标客户端数量',
    created_by VARCHAR(64) COMMENT '创建者',
    created_at TIMESTAMP COMMENT '创建时间',
    finished_at TIMESTAMP COMMENT '完成时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='批量任务表';

-- 2. 修改tasks表，添加批量任务关联字段
ALTER TABLE tasks 
ADD COLUMN batch_id VARCHAR(64) COMMENT '所属批量任务ID，null表示普通任务' AFTER agent_id,
ADD COLUMN task_name VARCHAR(200) COMMENT '任务名称' AFTER batch_id;

-- 3. 添加索引以提升查询性能
CREATE INDEX idx_tasks_batch_id ON tasks(batch_id);
CREATE INDEX idx_batch_tasks_created_at ON batch_tasks(created_at);
CREATE INDEX idx_tasks_batch_id_status ON tasks(batch_id, status);

-- 4. 添加外键约束（可选，根据实际需求）
-- ALTER TABLE tasks 
-- ADD CONSTRAINT fk_tasks_batch_id 
-- FOREIGN KEY (batch_id) REFERENCES batch_tasks(batch_id) ON DELETE SET NULL;

-- 注意事项：
-- 1. 如果使用H2数据库（开发环境），部分语法可能需要调整
-- 2. 外键约束已注释，如需要可取消注释
-- 3. 执行前请备份数据库
