package com.example.lightscript.server.service;

import com.example.lightscript.server.entity.Task;
import com.example.lightscript.server.entity.TaskExecution;
import com.example.lightscript.server.entity.TaskLog;
import com.example.lightscript.server.model.AgentModels.*;
import com.example.lightscript.server.model.TaskModels;
import com.example.lightscript.server.repository.TaskRepository;
import com.example.lightscript.server.repository.TaskExecutionRepository;
import com.example.lightscript.server.repository.TaskLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaskService {
    
    private final TaskRepository taskRepository;
    private final TaskLogRepository taskLogRepository;
    private final TaskExecutionRepository taskExecutionRepository;
    
    @Autowired
    private TaskExecutionService taskExecutionService;
    
    @Value("${lightscript.log.storage.path:logs/tasks}")
    private String logStoragePath;
    
    /**
     * 创建多代理任务（新版本）
     * 支持一个任务在多个代理上执行
     */
    @Transactional
    public TaskModels.CreateTaskResponse createMultiAgentTask(
            List<String> agentIds, 
            TaskSpec taskSpec, 
            String createdBy) {
        
        // 验证参数
        if (agentIds == null || agentIds.isEmpty()) {
            throw new IllegalArgumentException("至少需要选择一个代理");
        }
        
        // 验证任务名称唯一性
        if (taskSpec.getTaskName() != null && !taskSpec.getTaskName().trim().isEmpty()) {
            if (taskRepository.existsByTaskName(taskSpec.getTaskName())) {
                throw new IllegalArgumentException("任务名称已存在: " + taskSpec.getTaskName());
            }
        }
        
        // 1. 创建任务记录
        Task task = new Task();
        task.setTaskId(taskSpec.getTaskId() != null ? taskSpec.getTaskId() : UUID.randomUUID().toString());
        task.setTaskName(taskSpec.getTaskName());
        task.setScriptLang(taskSpec.getScriptLang());
        task.setScriptContent(taskSpec.getScriptContent());
        task.setTimeoutSec(taskSpec.getTimeoutSec());
        task.setEnv(taskSpec.getEnv());
        task.setCreatedBy(createdBy);
        task.setCreatedAt(LocalDateTime.now());
        
        task = taskRepository.save(task);
        log.info("Multi-agent task created: {}, targets: {}", task.getTaskId(), agentIds.size());
        
        // 2. 为每个代理创建执行实例
        List<TaskExecution> executions = taskExecutionService.createExecutions(task.getTaskId(), agentIds);
        log.info("Created {} execution instances for task {}", executions.size(), task.getTaskId());
        
        // 3. 返回响应
        TaskModels.CreateTaskResponse response = new TaskModels.CreateTaskResponse();
        response.setTaskId(task.getTaskId());
        response.setTargetAgentCount(agentIds.size());
        response.setMessage("任务创建成功，已分配给 " + agentIds.size() + " 个代理");
        
        return response;
    }
    
    /**
     * 获取任务（包含聚合状态）
     */
    public TaskModels.TaskDTO getTaskWithAggregatedStatus(String taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + taskId));
        
        List<TaskExecution> executions = taskExecutionService.getExecutionsByTaskId(taskId);
        
        return toTaskDTO(task, executions);
    }
    
    /**
     * 获取任务摘要
     */
    public TaskModels.TaskSummaryDTO getTaskSummary(String taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + taskId));
        
        List<TaskExecution> executions = taskExecutionService.getExecutionsByTaskId(taskId);
        
        TaskModels.TaskSummaryDTO summary = new TaskModels.TaskSummaryDTO();
        summary.setTaskId(task.getTaskId());
        summary.setTaskName(task.getTaskName());
        summary.setTargetAgentCount(executions.size());
        
        // 计算聚合状态和统计
        Map<String, Integer> stats = computeExecutionStats(executions);
        summary.setAggregatedStatus(computeAggregatedStatus(executions));
        summary.setCompletedExecutions(stats.get("completed"));
        summary.setExecutionProgress(stats.get("completed") + "/" + executions.size());
        summary.setSuccessCount(stats.get("success"));
        summary.setFailedCount(stats.get("failed"));
        summary.setRunningCount(stats.get("running"));
        summary.setPendingCount(stats.get("pending"));
        
        return summary;
    }
    
    /**
     * 获取所有任务（包含聚合状态）
     */
    public Page<TaskModels.TaskDTO> getAllTasksWithStatus(Pageable pageable) {
        Page<Task> taskPage = taskRepository.findAll(pageable);
        
        List<TaskModels.TaskDTO> taskDTOs = taskPage.getContent().stream()
                .map(task -> {
                    List<TaskExecution> executions = taskExecutionService.getExecutionsByTaskId(task.getTaskId());
                    return toTaskDTO(task, executions);
                })
                .collect(Collectors.toList());
        
        return new PageImpl<>(taskDTOs, pageable, taskPage.getTotalElements());
    }
    
    /**
     * 重启任务
     */
    @Transactional
    public TaskModels.RestartTaskResponse restartTask(String taskId, TaskModels.RestartMode mode) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + taskId));
        
        List<TaskExecution> executions = taskExecutionService.getExecutionsByTaskId(taskId);
        
        List<TaskExecution> toRestart;
        if (mode == TaskModels.RestartMode.FAILED_ONLY) {
            // 只重启失败和超时的执行
            toRestart = executions.stream()
                    .filter(e -> "FAILED".equals(e.getStatus()) || "TIMEOUT".equals(e.getStatus()))
                    .collect(Collectors.toList());
        } else {
            // 重启所有执行
            toRestart = executions;
        }
        
        if (toRestart.isEmpty()) {
            throw new IllegalStateException("没有需要重启的执行实例");
        }
        
        // 为每个需要重启的执行创建新的执行实例
        int newExecutionCount = 0;
        for (TaskExecution execution : toRestart) {
            Integer nextNumber = taskExecutionService.getNextExecutionNumber(taskId, execution.getAgentId());
            taskExecutionService.createExecution(taskId, execution.getAgentId(), nextNumber);
            newExecutionCount++;
        }
        
        log.info("Task {} restarted, mode: {}, new executions: {}", taskId, mode, newExecutionCount);
        
        TaskModels.RestartTaskResponse response = new TaskModels.RestartTaskResponse();
        response.setTaskId(taskId);
        response.setNewExecutionCount(newExecutionCount);
        response.setMessage("任务已重启，创建了 " + newExecutionCount + " 个新的执行实例");
        
        return response;
    }
    
    /**
     * 取消任务（取消所有执行实例）
     */
    @Transactional
    public void cancelTask(String taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + taskId));
        
        taskExecutionService.cancelTaskExecutions(taskId);
        log.info("Task {} cancelled (all executions)", taskId);
    }
    
    /**
     * 计算聚合状态
     */
    private String computeAggregatedStatus(List<TaskExecution> executions) {
        if (executions.isEmpty()) {
            return "PENDING";
        }
        
        long pendingCount = executions.stream()
                .filter(e -> "PENDING".equals(e.getStatus()))
                .count();
        
        long runningCount = executions.stream()
                .filter(e -> "RUNNING".equals(e.getStatus()) || "PULLED".equals(e.getStatus()))
                .count();
        
        long successCount = executions.stream()
                .filter(e -> "SUCCESS".equals(e.getStatus()))
                .count();
        
        long failedCount = executions.stream()
                .filter(e -> "FAILED".equals(e.getStatus()) || "TIMEOUT".equals(e.getStatus()))
                .count();
        
        // 如果有任何执行还在运行或等待，任务状态为进行中
        if (runningCount > 0 || pendingCount > 0) {
            return "IN_PROGRESS";
        }
        
        // 所有执行都已完成
        if (successCount == executions.size()) {
            return "ALL_SUCCESS";
        } else if (failedCount == executions.size()) {
            return "ALL_FAILED";
        } else {
            return "PARTIAL_SUCCESS";
        }
    }
    
    /**
     * 计算执行统计
     */
    private Map<String, Integer> computeExecutionStats(List<TaskExecution> executions) {
        Map<String, Integer> stats = new HashMap<>();
        
        int pending = 0, running = 0, success = 0, failed = 0, timeout = 0, cancelled = 0;
        int completed = 0;
        
        for (TaskExecution execution : executions) {
            String status = execution.getStatus();
            switch (status) {
                case "PENDING":
                    pending++;
                    break;
                case "PULLED":
                case "RUNNING":
                    running++;
                    break;
                case "SUCCESS":
                    success++;
                    completed++;
                    break;
                case "FAILED":
                    failed++;
                    completed++;
                    break;
                case "TIMEOUT":
                    timeout++;
                    completed++;
                    break;
                case "CANCELLED":
                    cancelled++;
                    completed++;
                    break;
            }
        }
        
        stats.put("pending", pending);
        stats.put("running", running);
        stats.put("success", success);
        stats.put("failed", failed);
        stats.put("timeout", timeout);
        stats.put("cancelled", cancelled);
        stats.put("completed", completed);
        
        return stats;
    }
    
    /**
     * 转换为 TaskDTO
     */
    private TaskModels.TaskDTO toTaskDTO(Task task, List<TaskExecution> executions) {
        TaskModels.TaskDTO dto = new TaskModels.TaskDTO();
        dto.setTaskId(task.getTaskId());
        dto.setTaskName(task.getTaskName());
        dto.setScriptLang(task.getScriptLang());
        dto.setScriptContent(task.getScriptContent());
        dto.setTimeoutSec(task.getTimeoutSec());
        dto.setEnv(task.getEnv());
        dto.setCreatedBy(task.getCreatedBy());
        dto.setCreatedAt(task.getCreatedAt());
        
        // 计算聚合状态和统计
        Map<String, Integer> stats = computeExecutionStats(executions);
        dto.setAggregatedStatus(computeAggregatedStatus(executions));
        dto.setTargetAgentCount(executions.size());
        dto.setCompletedExecutions(stats.get("completed"));
        dto.setExecutionProgress(stats.get("completed") + "/" + executions.size());
        dto.setPendingCount(stats.get("pending"));
        dto.setRunningCount(stats.get("running"));
        dto.setSuccessCount(stats.get("success"));
        dto.setFailedCount(stats.get("failed"));
        dto.setTimeoutCount(stats.get("timeout"));
        dto.setCancelledCount(stats.get("cancelled"));
        
        return dto;
    }
    
    // ==================== 旧版本方法（保留向后兼容性）====================
    
    // ==================== 旧版本方法（保留向后兼容性）====================
    
    /**
     * 创建单代理任务（旧版本，保留向后兼容性）
     * @deprecated 使用 createMultiAgentTask 代替
     */
    @Deprecated
    @Transactional
    public String createTask(String agentId, TaskSpec taskSpec, String createdBy) {
        // 使用新的多代理方法，传入单个代理
        TaskModels.CreateTaskResponse response = createMultiAgentTask(
                Collections.singletonList(agentId), 
                taskSpec, 
                createdBy
        );
        return response.getTaskId();
    }
    
    @Transactional
    public List<String> createBatchTasks(List<String> agentIds, TaskSpec taskSpec, String createdBy) {
        return agentIds.stream()
                .map(agentId -> {
                    TaskSpec agentTaskSpec = new TaskSpec();
                    agentTaskSpec.setTaskId(UUID.randomUUID().toString());
                    agentTaskSpec.setScriptLang(taskSpec.getScriptLang());
                    agentTaskSpec.setScriptContent(taskSpec.getScriptContent());
                    agentTaskSpec.setTimeoutSec(taskSpec.getTimeoutSec());
                    agentTaskSpec.setEnv(taskSpec.getEnv());
                    return createTask(agentId, agentTaskSpec, createdBy);
                })
                .collect(Collectors.toList());
    }
    
    /**
     * 代理拉取任务（适配新模型）
     */
    @Transactional
    public List<TaskSpec> pullTasks(String agentId, int maxTasks) {
        // 查询该代理的待处理执行实例
        List<TaskExecution> pendingExecutions = taskExecutionService.getPendingExecutionsByAgentId(agentId);
        
        return pendingExecutions.stream()
                .limit(maxTasks)
                .peek(execution -> {
                    // 更新执行状态为 PULLED
                    taskExecutionService.updateStatus(execution.getId(), "PULLED");
                    log.info("Task execution {} pulled by agent {}", execution.getId(), agentId);
                })
                .map(execution -> {
                    // 获取任务信息并转换为 TaskSpec
                    Task task = taskRepository.findById(execution.getTaskId()).orElse(null);
                    if (task != null) {
                        TaskSpec spec = convertToTaskSpec(task);
                        spec.setTaskId(execution.getTaskId());
                        spec.setExecutionId(execution.getId()); // 设置执行实例ID
                        return spec;
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
    
    /**
     * 确认任务开始执行（使用executionId）
     */
    @Transactional
    public void ackTaskExecution(Long executionId) {
        Optional<TaskExecution> optExecution = taskExecutionService.getExecution(executionId);
        
        if (!optExecution.isPresent()) {
            log.warn("Execution {} not found for ACK", executionId);
            throw new IllegalArgumentException("执行实例不存在: " + executionId);
        }
        
        TaskExecution execution = optExecution.get();
        
        if (!"PULLED".equals(execution.getStatus())) {
            log.warn("Execution {} ACK ignored, status is {}", executionId, execution.getStatus());
            return;
        }
        
        execution.setStatus("RUNNING");
        execution.setStartedAt(LocalDateTime.now());
        
        // 生成日志文件路径
        String logFilePath = generateLogFilePath(execution);
        execution.setLogFilePath(logFilePath);
        
        taskExecutionRepository.save(execution);
        log.info("Task execution {} acknowledged and started, log file: {}", 
            execution.getId(), logFilePath);
    }
    
    /**
     * 追加日志（使用executionId）
     */
    @Transactional
    public void appendLog(LogChunkRequest request) {
        Optional<TaskExecution> optExecution = taskExecutionService.getExecution(request.getExecutionId());
        
        if (!optExecution.isPresent()) {
            log.warn("Execution {} not found for log append", request.getExecutionId());
            return;
        }
        
        TaskExecution execution = optExecution.get();
        String logFilePath = execution.getLogFilePath();
        
        if (logFilePath == null || logFilePath.isEmpty()) {
            log.warn("Task execution {} has no log file path, skipping log append", execution.getId());
            return;
        }
        
        // 写入日志到文件
        writeLogToFile(logFilePath, request);
    }
    
    /**
     * 完成任务（使用executionId）
     */
    @Transactional
    public void finishTask(FinishRequest request) {
        Optional<TaskExecution> optExecution = taskExecutionService.getExecution(request.getExecutionId());
        
        if (!optExecution.isPresent()) {
            log.warn("Execution {} not found for finish", request.getExecutionId());
            return;
        }
        
        TaskExecution execution = optExecution.get();
        
        // 完成执行实例
        taskExecutionService.updateStatus(
            execution.getId(), 
            request.getStatus(), 
            request.getExitCode(), 
            request.getSummary()
        );
        log.info("Task execution {} finished with status {}", execution.getId(), request.getStatus());
    }
    
    public Optional<Task> getTask(String taskId) {
        return taskRepository.findById(taskId);
    }
    
    public Page<Task> getTasksByUser(String username, Pageable pageable) {
        return taskRepository.findByCreatedByOrderByCreatedAtDesc(username, pageable);
    }
    
    public Page<Task> getAllTasks(Pageable pageable) {
        return taskRepository.findAll(pageable);
    }
    
    public List<TaskLog> getTaskLogs(String taskId) {
        return taskLogRepository.findByTaskIdOrderBySeqNumAsc(taskId);
    }
    
    /**
     * 获取待处理任务数（通过TaskExecution统计）
     */
    public long getPendingTaskCount() {
        return taskExecutionService.countExecutionsByStatus("PENDING");
    }
    
    /**
     * 获取运行中任务数（通过TaskExecution统计）
     */
    public long getRunningTaskCount() {
        return taskExecutionService.countExecutionsByStatus("RUNNING");
    }
    
    /**
     * 获取已完成任务数（通过TaskExecution统计）
     */
    public long getCompletedTaskCount() {
        return taskExecutionService.countExecutionsByStatus("SUCCESS");
    }
    
    /**
     * 获取失败任务数（通过TaskExecution统计）
     */
    public long getFailedTaskCount() {
        return taskExecutionService.countExecutionsByStatus("FAILED");
    }
    /**
     * 检查已拉取但未确认的任务（适配新模型）
     */
    @Scheduled(fixedRate = 60000) // 每1分钟检查一次
    @Transactional
    public void checkPulledTasks() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(2); // 2分钟没ACK
        List<TaskExecution> stuckExecutions = taskExecutionRepository.findPulledButNotAcked(threshold);
        
        for (TaskExecution execution : stuckExecutions) {
            execution.setStatus("PENDING");
            execution.setPulledAt(null);
            taskExecutionRepository.save(execution);
            log.warn("Task execution {} reset to PENDING (ACK timeout, agent may not have received it)", 
                execution.getId());
        }
    }
    
    /**
     * 检查超时任务（适配新模型）
     */
    @Scheduled(fixedRate = 300000) // 每5分钟检查一次
    @Transactional
    public void checkTimeoutTasks() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(30); // 30分钟超时
        List<TaskExecution> timeoutExecutions = taskExecutionRepository.findTimeoutExecutions(threshold);
        
        for (TaskExecution execution : timeoutExecutions) {
            taskExecutionService.updateStatus(
                execution.getId(), 
                "TIMEOUT", 
                null, 
                "Task timeout after 30 minutes"
            );
            log.warn("Task execution {} marked as TIMEOUT", execution.getId());
        }
    }
    
    private TaskSpec convertToTaskSpec(Task task) {
        TaskSpec spec = new TaskSpec();
        spec.setTaskId(task.getTaskId());
        spec.setTaskName(task.getTaskName());
        spec.setScriptLang(task.getScriptLang());
        spec.setScriptContent(task.getScriptContent());
        spec.setTimeoutSec(task.getTimeoutSec());
        spec.setEnv(task.getEnv());
        return spec;
    }
    
    /**
     * 生成日志文件路径（适配新模型）
     * 格式：logs/tasks/2024/01/taskId_agentId_executionNumber_startTime.log
     * 例如：logs/tasks/2024/01/abc123_agent1_1_20240115143020.log
     */
    private String generateLogFilePath(TaskExecution execution) {
        LocalDateTime startTime = execution.getStartedAt();
        if (startTime == null) {
            startTime = LocalDateTime.now();
        }
        
        String yearMonth = startTime.format(DateTimeFormatter.ofPattern("yyyy/MM"));
        String dateTime = startTime.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        
        // 提取agentId的前8位作为简短标识
        String agentShort = execution.getAgentId();
        if (agentShort.length() > 8) {
            agentShort = agentShort.substring(0, 8);
        }
        
        String fileName = String.format("%s_%s_%d_%s.log", 
            execution.getTaskId(),
            agentShort,
            execution.getExecutionNumber(),
            dateTime
        );
        
        return String.format("%s/%s/%s", logStoragePath, yearMonth, fileName);
    }
    
    /**
     * 写入日志到文件
     */
    private void writeLogToFile(String logFilePath, LogChunkRequest request) {
        File logFile = new File(logFilePath);
        
        try {
            // 确保目录存在
            logFile.getParentFile().mkdirs();
            
            // 格式化日志行
            String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
            String logLine = String.format("[%s] [%s] %s\n", 
                timestamp, request.getStream(), request.getData());
            
            // 追加写入 (Java 8 兼容)
            Files.write(
                logFile.toPath(), 
                logLine.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            );
            
        } catch (IOException e) {
            log.error("Failed to write log file: {}", logFilePath, e);
            throw new RuntimeException("写入日志文件失败: " + logFilePath, e);
        }
    }


    /**
     * 获取任务执行历史
     */
    public List<TaskExecution> getTaskExecutionHistory(String taskId) {
        return taskExecutionService.getExecutionsByTaskId(taskId);
    }

    /**
     * 获取特定执行实例
     */
    public Optional<TaskExecution> getTaskExecution(Long executionId) {
        return taskExecutionService.getExecution(executionId);
    }

    /**
     * 取消特定执行实例
     */
    @Transactional
    public void cancelExecution(Long executionId) {
        taskExecutionService.cancelExecution(executionId);
    }

}
