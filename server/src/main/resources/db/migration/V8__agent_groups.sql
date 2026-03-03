-- V8: Agent分组功能
-- 创建Agent分组表和分组成员关联表

-- Agent分组表
CREATE TABLE IF NOT EXISTS agent_group (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE COMMENT '分组名称',
    description VARCHAR(500) COMMENT '描述',
    type VARCHAR(20) NOT NULL DEFAULT 'CUSTOM' COMMENT '类型:BUSINESS,ENVIRONMENT,REGION,CUSTOM',
    created_by VARCHAR(50) COMMENT '创建者',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_name (name),
    INDEX idx_type (type),
    INDEX idx_created_by (created_by)
) COMMENT='Agent分组表';

-- 分组成员关联表
CREATE TABLE IF NOT EXISTS agent_group_member (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id BIGINT NOT NULL COMMENT '分组ID',
    agent_id VARCHAR(100) NOT NULL COMMENT 'Agent ID',
    added_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    UNIQUE KEY uk_group_agent (group_id, agent_id),
    FOREIGN KEY (group_id) REFERENCES agent_group(id) ON DELETE CASCADE,
    INDEX idx_group_id (group_id),
    INDEX idx_agent_id (agent_id)
) COMMENT='分组成员关联表';
