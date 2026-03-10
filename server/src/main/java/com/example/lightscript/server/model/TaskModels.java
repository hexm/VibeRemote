package com.example.lightscript.server.model;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 任务相关的数据传输对象（DTO）
 */
public class TaskModels {

    /**
     * 任务DTO - 包含聚合状态和进度信息
     */
    @Data
    public static class TaskDTO {
        private String taskId;
        private String taskName;
        private String scriptLang;
        private String scriptContent;
        private Integer timeoutSec;
        private Map<String, String> env;
        private String createdBy;
        private LocalDateTime createdAt;
        
        // 任务执行跟踪字段
        private Integer executionCount; // 执行次数
        private LocalDateTime startedAt; // 任务开始时间
        private LocalDateTime finishedAt; // 任务结束时间
        
        // 任务状态
        private String taskStatus; // DRAFT | PENDING | RUNNING | SUCCESS | FAILED | PARTIAL_SUCCESS | STOPPED | CANCELLED
        
        // 任务类型
        private String taskType; // SCRIPT | FILE_TRANSFER
        
        // 统计字段（从TaskExecution计算得出）
        private Integer targetAgentCount; // 目标代理数量
        private Integer completedExecutions; // 已完成的执行数
        private String executionProgress; // 执行进度，如 "3/5"
        
        // 执行统计
        private Integer pendingCount;
        private Integer runningCount;
        private Integer successCount;
        private Integer failedCount;
        private Integer timeoutCount;
        private Integer cancelledCount;
    }

    /**
     * 任务执行实例DTO
     */
    @Data
    public static class TaskExecutionDTO {
        private Long id;
        private String taskId;
        private String agentId;
        private String agentHostname; // 代理主机名（从Agent表关联）
        private Integer executionNumber;
        private String status;
        private String logFilePath;
        private Integer exitCode;
        private String summary;
        private LocalDateTime pulledAt;
        private LocalDateTime startedAt;
        private LocalDateTime finishedAt;
        private LocalDateTime createdAt;
        
        // 计算字段
        private Long durationMs; // 执行耗时（毫秒）
        
        // 文件传输相关字段
        private String fileId; // 传输的文件ID
        private String fileName; // 文件名（从File表关联）
        private String fileSizeDisplay; // 格式化的文件大小
        private String targetPath; // 目标路径
        private Long transferSize; // 实际传输大小
        private Boolean checksumVerified; // 校验是否通过
        private Long transferSpeed; // 传输速度（字节/秒）
        private String errorDetails; // 详细错误信息
    }

    /**
     * 任务摘要DTO - 用于快速查询任务状态
     */
    @Data
    public static class TaskSummaryDTO {
        private String taskId;
        private String taskName;
        private Integer targetAgentCount;
        private Integer completedExecutions;
        private String executionProgress;
        private Integer successCount;
        private Integer failedCount;
        private Integer runningCount;
        private Integer pendingCount;
    }

    /**
     * 创建任务请求
     */
    @Data
    public static class CreateTaskRequest {
        @NotEmpty(message = "至少需要选择一个代理")
        private List<String> agentIds;
        
        @NotBlank(message = "任务名称不能为空")
        private String taskName;
        
        @NotBlank(message = "脚本语言不能为空")
        private String scriptLang;
        
        @NotBlank(message = "脚本内容不能为空")
        private String scriptContent;
        
        @NotNull(message = "超时时间不能为空")
        private Integer timeoutSec;
        
        private Map<String, String> env;
    }

    /**
     * 创建任务响应
     */
    @Data
    public static class CreateTaskResponse {
        private String taskId;
        private String taskStatus; // 任务状态
        private Integer targetAgentCount;
        private String message;
    }
    
    /**
     * 启动任务响应
     */
    @Data
    public static class StartTaskResponse {
        private String taskId;
        private String taskStatus;
        private Integer executionCount;
        private String message;
    }
    
    /**
     * 停止任务响应
     */
    @Data
    public static class StopTaskResponse {
        private String taskId;
        private String taskStatus;
        private Integer cancelledCount;
        private String message;
    }

    /**
     * 重启任务请求
     */
    @Data
    public static class RestartTaskRequest {
        @NotBlank(message = "任务ID不能为空")
        private String taskId;
        
        @NotNull(message = "重启模式不能为空")
        private RestartMode mode; // ALL 或 FAILED_ONLY
    }

    /**
     * 重启模式枚举
     */
    public enum RestartMode {
        ALL,          // 重启所有执行
        FAILED_ONLY   // 仅重启失败的执行
    }

    /**
     * 重启任务响应
     */
    @Data
    public static class RestartTaskResponse {
        private String taskId;
        private Integer newExecutionCount;
        private String message;
    }

    /**
     * 取消执行请求
     */
    @Data
    public static class CancelExecutionRequest {
        @NotNull(message = "执行ID不能为空")
        private Long executionId;
    }

    /**
     * 取消任务请求
     */
    @Data
    public static class CancelTaskRequest {
        @NotBlank(message = "任务ID不能为空")
        private String taskId;
    }

    /**
     * 通用响应
     */
    @Data
    public static class GenericResponse {
        private boolean success;
        private String message;
        private Object data;
        
        public static GenericResponse success(String message) {
            GenericResponse response = new GenericResponse();
            response.setSuccess(true);
            response.setMessage(message);
            return response;
        }
        
        public static GenericResponse success(String message, Object data) {
            GenericResponse response = new GenericResponse();
            response.setSuccess(true);
            response.setMessage(message);
            response.setData(data);
            return response;
        }
        
        public static GenericResponse error(String message) {
            GenericResponse response = new GenericResponse();
            response.setSuccess(false);
            response.setMessage(message);
            return response;
        }
    }

    /**
     * 日志查询请求
     */
    @Data
    public static class LogQueryRequest {
        private Integer offset = 0;
        private Integer limit = 1000;
    }

    /**
     * 日志查询响应
     */
    @Data
    public static class LogQueryResponse {
        private String content;
        private Integer totalLines;
        private Integer offset;
        private Integer limit;
        private Boolean hasMore;
    }

    /**
     * 任务列表查询请求
     */
    @Data
    public static class TaskListRequest {
        private String status; // 状态过滤
        private String createdBy; // 创建者过滤
        private Integer page = 0;
        private Integer size = 10;
    }

    /**
     * 执行实例列表查询请求
     */
    @Data
    public static class ExecutionListRequest {
        @NotBlank(message = "任务ID不能为空")
        private String taskId;
        
        private String status; // 状态过滤（可选）
    }
}
