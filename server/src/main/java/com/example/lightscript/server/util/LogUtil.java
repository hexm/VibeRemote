package com.example.lightscript.server.util;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 日志工具类
 * 提供统一的日志记录方法和格式
 */
@Slf4j
public class LogUtil {
    
    private static final Logger BUSINESS_LOGGER = LoggerFactory.getLogger("BUSINESS");
    private static final Logger SECURITY_LOGGER = LoggerFactory.getLogger("SECURITY");
    private static final Logger PERFORMANCE_LOGGER = LoggerFactory.getLogger("PERFORMANCE");
    
    /**
     * 记录业务操作日志
     */
    public static void logBusiness(String operation, String userId, String details) {
        BUSINESS_LOGGER.info("[BUSINESS] Operation: {}, User: {}, Details: {}", 
                operation, userId, details);
    }
    
    /**
     * 记录业务操作日志（带结果）
     */
    public static void logBusiness(String operation, String userId, String details, boolean success) {
        BUSINESS_LOGGER.info("[BUSINESS] Operation: {}, User: {}, Details: {}, Success: {}", 
                operation, userId, details, success);
    }
    
    /**
     * 记录安全相关日志
     */
    public static void logSecurity(String event, String userId, String clientIp, String details) {
        SECURITY_LOGGER.info("[SECURITY] Event: {}, User: {}, IP: {}, Details: {}", 
                event, userId, clientIp, details);
    }
    
    /**
     * 记录安全警告
     */
    public static void logSecurityWarning(String event, String userId, String clientIp, String details) {
        SECURITY_LOGGER.warn("[SECURITY_WARNING] Event: {}, User: {}, IP: {}, Details: {}", 
                event, userId, clientIp, details);
    }
    
    /**
     * 记录性能日志
     */
    public static void logPerformance(String operation, long duration, String details) {
        PERFORMANCE_LOGGER.info("[PERFORMANCE] Operation: {}, Duration: {}ms, Details: {}", 
                operation, duration, details);
    }
    
    /**
     * 记录Agent操作日志
     */
    public static void logAgent(String operation, String agentId, String hostname, String details) {
        log.info("[AGENT] Operation: {}, AgentId: {}, Hostname: {}, Details: {}", 
                operation, agentId, hostname, details);
    }
    
    /**
     * 记录任务操作日志
     */
    public static void logTask(String operation, String taskId, String agentId, String details) {
        log.info("[TASK] Operation: {}, TaskId: {}, AgentId: {}, Details: {}", 
                operation, taskId, agentId, details);
    }
    
    /**
     * 记录任务状态变更日志
     */
    public static void logTaskStatus(String taskId, String agentId, String oldStatus, String newStatus, String reason) {
        log.info("[TASK_STATUS] TaskId: {}, AgentId: {}, Status: {} -> {}, Reason: {}", 
                taskId, agentId, oldStatus, newStatus, reason);
    }
    
    /**
     * 记录API调用日志
     */
    public static void logApiCall(String method, String uri, String userId, long duration, int statusCode) {
        log.info("[API] Method: {}, URI: {}, User: {}, Duration: {}ms, Status: {}", 
                method, uri, userId, duration, statusCode);
    }
    
    /**
     * 记录错误日志
     */
    public static void logError(String operation, String details, Throwable throwable) {
        log.error("[ERROR] Operation: {}, Details: {}, Exception: {}", 
                operation, details, throwable.getMessage(), throwable);
    }
    
    /**
     * 记录系统启动日志
     */
    public static void logSystemStart(String component, String version, String details) {
        log.info("[SYSTEM_START] Component: {}, Version: {}, Details: {}", 
                component, version, details);
    }
    
    /**
     * 记录系统关闭日志
     */
    public static void logSystemShutdown(String component, String reason) {
        log.info("[SYSTEM_SHUTDOWN] Component: {}, Reason: {}", 
                component, reason);
    }
}
