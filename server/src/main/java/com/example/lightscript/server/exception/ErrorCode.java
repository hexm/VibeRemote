package com.example.lightscript.server.exception;

/**
 * 错误码枚举
 * 定义系统中所有的错误类型和错误信息
 */
public enum ErrorCode {
    
    // 通用错误 (1000-1999)
    SUCCESS(0, "操作成功"),
    SYSTEM_ERROR(1000, "系统内部错误"),
    INVALID_PARAMETER(1001, "参数错误: %s"),
    RESOURCE_NOT_FOUND(1002, "资源不存在: %s"),
    OPERATION_FAILED(1003, "操作失败: %s"),
    
    // 认证授权错误 (2000-2999)
    UNAUTHORIZED(2000, "未授权访问"),
    INVALID_TOKEN(2001, "无效的令牌"),
    TOKEN_EXPIRED(2002, "令牌已过期"),
    INVALID_CREDENTIALS(2003, "用户名或密码错误"),
    ACCESS_DENIED(2004, "访问被拒绝"),
    
    // Agent相关错误 (3000-3999)
    INVALID_REGISTER_TOKEN(3000, "无效的注册令牌"),
    AGENT_NOT_FOUND(3001, "Agent不存在: %s"),
    AGENT_OFFLINE(3002, "Agent离线: %s"),
    AGENT_TOKEN_INVALID(3003, "Agent令牌无效"),
    AGENT_ALREADY_EXISTS(3004, "Agent已存在: %s"),
    
    // 任务相关错误 (4000-4999)
    TASK_NOT_FOUND(4000, "任务不存在: %s"),
    TASK_ALREADY_RUNNING(4001, "任务正在运行: %s"),
    TASK_EXECUTION_FAILED(4002, "任务执行失败: %s"),
    INVALID_SCRIPT_TYPE(4003, "不支持的脚本类型: %s"),
    TASK_TIMEOUT(4004, "任务执行超时: %s"),
    TASK_CANCELLED(4005, "任务已取消: %s"),
    
    // 脚本相关错误 (5000-5999)
    SCRIPT_VALIDATION_FAILED(5000, "脚本验证失败: %s"),
    DANGEROUS_COMMAND_DETECTED(5001, "检测到危险命令: %s"),
    SCRIPT_EXECUTION_ERROR(5002, "脚本执行错误: %s"),
    SCRIPT_TIMEOUT(5003, "脚本执行超时"),
    
    // 文件相关错误 (6000-6999)
    FILE_NOT_FOUND(6000, "文件不存在: %s"),
    FILE_UPLOAD_FAILED(6001, "文件上传失败: %s"),
    FILE_DOWNLOAD_FAILED(6002, "文件下载失败: %s"),
    INVALID_FILE_TYPE(6003, "不支持的文件类型: %s"),
    FILE_SIZE_EXCEEDED(6004, "文件大小超过限制: %s");
    
    private final int code;
    private final String message;
    
    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
    
    public int getCode() {
        return code;
    }
    
    public String getMessage() {
        return message;
    }
    
    @Override
    public String toString() {
        return String.format("[%d] %s", code, message);
    }
}
