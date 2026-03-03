-- V7: 用户管理功能
-- 创建用户表和用户权限关联表

-- 用户表
CREATE TABLE IF NOT EXISTS user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    password VARCHAR(255) NOT NULL COMMENT '密码(BCrypt加密)',
    email VARCHAR(100) COMMENT '邮箱',
    real_name VARCHAR(50) COMMENT '真实姓名',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态:ACTIVE,DISABLED',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    last_login_at TIMESTAMP NULL COMMENT '最后登录时间',
    INDEX idx_username (username),
    INDEX idx_status (status)
) COMMENT='用户表';

-- 用户权限关联表
CREATE TABLE IF NOT EXISTS user_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    permission_code VARCHAR(50) NOT NULL COMMENT '权限代码',
    UNIQUE KEY uk_user_permission (user_id, permission_code),
    FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_permission_code (permission_code)
) COMMENT='用户权限关联表';

-- 插入默认管理员用户（密码：admin123，BCrypt加密）
INSERT INTO user (username, password, email, real_name, status) 
VALUES ('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'admin@example.com', '系统管理员', 'ACTIVE');

-- 为管理员分配所有权限
INSERT INTO user_permission (user_id, permission_code) VALUES
(1, 'user:create'),
(1, 'user:edit'),
(1, 'user:delete'),
(1, 'user:view'),
(1, 'task:create'),
(1, 'task:execute'),
(1, 'task:delete'),
(1, 'task:view'),
(1, 'script:create'),
(1, 'script:edit'),
(1, 'script:delete'),
(1, 'script:view'),
(1, 'agent:view'),
(1, 'agent:group'),
(1, 'log:view'),
(1, 'system:settings');
