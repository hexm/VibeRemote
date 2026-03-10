-- 脚本管理表
CREATE TABLE scripts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    script_id VARCHAR(50) NOT NULL UNIQUE COMMENT '脚本ID，如S001',
    name VARCHAR(255) NOT NULL COMMENT '脚本名称',
    filename VARCHAR(255) NOT NULL COMMENT '文件名',
    type VARCHAR(50) NOT NULL COMMENT '脚本类型：bash, powershell, cmd, python, javascript, typescript',
    description TEXT COMMENT '脚本描述',
    content LONGTEXT COMMENT '脚本内容（手动录入时使用）',
    file_path VARCHAR(500) COMMENT '文件路径（上传文件时使用）',
    file_size BIGINT COMMENT '文件大小（字节）',
    encoding VARCHAR(50) DEFAULT 'UTF-8' COMMENT '编码格式',
    is_uploaded BOOLEAN DEFAULT FALSE COMMENT '是否为上传的文件',
    usage_count INT DEFAULT 0 COMMENT '使用次数',
    created_by VARCHAR(100) NOT NULL COMMENT '创建者',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    INDEX idx_script_id (script_id),
    INDEX idx_name (name),
    INDEX idx_type (type),
    INDEX idx_created_by (created_by),
    INDEX idx_created_at (created_at)
) COMMENT='脚本管理表';

-- 插入默认脚本数据
INSERT INTO scripts (script_id, name, filename, type, description, content, is_uploaded, created_by) VALUES
('S001', '系统更新脚本', 'update-system.sh', 'bash', '更新系统软件包和安全补丁', 
'#!/bin/bash
echo "开始系统更新..."
apt update
apt upgrade -y
echo "系统更新完成"', FALSE, 'system'),

('S002', '日志清理脚本', 'cleanup-logs.sh', 'bash', '清理系统日志文件，释放磁盘空间',
'#!/bin/bash
echo "开始清理日志..."
find /var/log -name "*.log" -mtime +30 -delete
echo "日志清理完成"', FALSE, 'system'),

('S003', '数据备份脚本', 'backup-data.ps1', 'powershell', '备份重要数据到指定目录',
'# PowerShell 数据备份脚本
Write-Host "开始数据备份..."
$source = "C:\\Data"
$destination = "C:\\Backup"
Copy-Item -Path $source -Destination $destination -Recurse
Write-Host "数据备份完成"', FALSE, 'system');