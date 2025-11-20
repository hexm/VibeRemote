-- ========================================
-- 任务执行历史功能
-- 版本: V3
-- 日期: 2024-11-08
-- 说明: 增加任务执行历史表，支持任务重启和历史查询
-- ========================================

-- 创建任务执行历史表
CREATE TABLE task_executions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id VARCHAR(64) NOT NULL COMMENT '任务ID',
    execution_seq INT NOT NULL COMMENT '执行序号：1, 2, 3...',
    status VARCHAR(20) COMMENT '执行状态：SUCCESS/FAILED/TIMEOUT',
    exit_code INT COMMENT '退出码',
    started_at TIMESTAMP COMMENT '启动时间',
    finished_at TIMESTAMP COMMENT '完成时间',
    duration_ms BIGINT COMMENT '执行时长（毫秒）',
    summary TEXT COMMENT '执行摘要',
    log_file_path VARCHAR(500) COMMENT '日志文件路径',
    log_size_bytes BIGINT COMMENT '日志文件大小',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    
    UNIQUE KEY uk_task_execution (task_id, execution_seq),
    INDEX idx_task_id (task_id),
    INDEX idx_created_at (created_at)
) COMMENT='任务执行历史记录表';

-- 说明：
-- 1. task_id + execution_seq 联合唯一索引，确保每次执行记录唯一
-- 2. execution_seq 对应 Task.executionCount，记录是第几次执行
-- 3. 只在任务重启时写入历史记录，首次成功的任务无历史
-- 4. 保存日志文件路径而非日志内容，节省数据库空间
