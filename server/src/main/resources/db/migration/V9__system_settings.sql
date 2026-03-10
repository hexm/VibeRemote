-- 系统参数表
CREATE TABLE system_setting (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    setting_key VARCHAR(100) NOT NULL UNIQUE COMMENT '参数键',
    setting_value TEXT COMMENT '参数值',
    setting_type VARCHAR(20) DEFAULT 'STRING' COMMENT '参数类型: STRING, NUMBER, BOOLEAN, JSON',
    description VARCHAR(500) COMMENT '参数描述',
    category VARCHAR(50) COMMENT '参数类别',
    is_encrypted BOOLEAN DEFAULT FALSE COMMENT '是否加密',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_category (category),
    INDEX idx_key (setting_key)
) COMMENT='系统参数配置表';

-- 插入默认系统参数
INSERT INTO system_setting (setting_key, setting_value, setting_type, description, category, is_encrypted) VALUES
('system.name', 'LightScript', 'STRING', '系统名称', '系统配置', FALSE),
('system.timezone', 'Asia/Shanghai', 'STRING', '系统时区', '系统配置', FALSE),
('task.default_timeout', '300', 'NUMBER', '任务默认超时时间（秒）', '任务配置', FALSE),
('task.max_concurrent', '10', 'NUMBER', '最大并发任务数', '任务配置', FALSE),
('agent.heartbeat_interval', '30', 'NUMBER', 'Agent心跳间隔（秒）', 'Agent配置', FALSE),
('agent.offline_threshold', '90', 'NUMBER', 'Agent离线阈值（秒）', 'Agent配置', FALSE),
('security.session_timeout', '3600', 'NUMBER', '会话超时时间（秒）', '安全配置', FALSE),
('security.password_min_length', '6', 'NUMBER', '密码最小长度', '安全配置', FALSE);
