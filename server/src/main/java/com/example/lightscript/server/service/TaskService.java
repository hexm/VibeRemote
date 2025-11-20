package com.example.lightscript.server.service;

import com.example.lightscript.server.entity.Task;
import com.example.lightscript.server.entity.TaskExecution;
import com.example.lightscript.server.entity.TaskLog;
import com.example.lightscript.server.model.AgentModels.*;
import com.example.lightscript.server.repository.TaskRepository;
import com.example.lightscript.server.repository.TaskExecutionRepository;
import com.example.lightscript.server.repository.TaskLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaskService {
    
    private final TaskRepository taskRepository;
    private final TaskLogRepository taskLogRepository;
    private final TaskExecutionRepository taskExecutionRepository;
    
    @Value("${lightscript.log.storage.path:logs/tasks}")
    private String logStoragePath;
    
    @Transactional
    public String createTask(String agentId, TaskSpec taskSpec, String createdBy) {
        // 验证任务名称唯一性
        if (taskSpec.getTaskName() != null && !taskSpec.getTaskName().trim().isEmpty()) {
            if (taskRepository.existsByTaskName(taskSpec.getTaskName())) {
                throw new IllegalArgumentException("任务名称已存在: " + taskSpec.getTaskName());
            }
        }
        
        Task task = new Task();
        task.setTaskId(taskSpec.getTaskId() != null ? taskSpec.getTaskId() : UUID.randomUUID().toString());
        task.setAgentId(agentId);
        task.setTaskName(taskSpec.getTaskName()); // 设置任务名称
        task.setScriptLang(taskSpec.getScriptLang());
        task.setScriptContent(taskSpec.getScriptContent());
        task.setTimeoutSec(taskSpec.getTimeoutSec());
        task.setEnv(taskSpec.getEnv());
        task.setStatus("PENDING");
        task.setCreatedBy(createdBy);
        
        task = taskRepository.save(task);
        log.info("Task created: {} for agent {}", task.getTaskId(), agentId);
        return task.getTaskId();
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
    
    @Transactional
    public List<TaskSpec> pullTasks(String agentId, int maxTasks) {
        List<Task> tasks = taskRepository.findByAgentIdAndStatusOrderByCreatedAtAsc(agentId, "PENDING");
        
        return tasks.stream()
                .limit(maxTasks)
                .peek(task -> {
                    task.setStatus("PULLED");
                    task.setPulledAt(LocalDateTime.now());
                    taskRepository.save(task);
                    log.info("Task {} pulled by agent {}", task.getTaskId(), agentId);
                })
                .map(this::convertToTaskSpec)
                .collect(Collectors.toList());
    }
    
    @Transactional
    public void ackTask(String taskId) {
        Optional<Task> taskOpt = taskRepository.findById(taskId);
        if (taskOpt.isPresent()) {
            Task task = taskOpt.get();
            if ("PULLED".equals(task.getStatus())) {
                task.setStatus("RUNNING");
                task.setStartedAt(LocalDateTime.now());
                
                // 执行次数累加
                task.setExecutionCount(task.getExecutionCount() + 1);
                
                // 生成日志文件路径
                String logFilePath = generateLogFilePath(task);
                task.setLogFilePath(logFilePath);
                
                taskRepository.save(task);
                log.info("Task {} acknowledged and started, execution count: {}, log file: {}", 
                    taskId, task.getExecutionCount(), logFilePath);
            } else {
                log.warn("Task {} ACK ignored, current status: {}", taskId, task.getStatus());
            }
        } else {
            log.warn("Task {} not found for ACK", taskId);
        }
    }
    
    @Transactional
    public void appendLog(String taskId, LogChunkRequest request) {
        // 获取任务信息
        Task task = taskRepository.findById(taskId).orElse(null);
        if (task == null) {
            log.warn("Task {} not found for log append", taskId);
            return;
        }
        
        // 获取日志文件路径
        String logFilePath = task.getLogFilePath();
        if (logFilePath == null || logFilePath.isEmpty()) {
            log.warn("Task {} has no log file path, skipping log append", taskId);
            return;
        }
        
        // 写入日志到文件
        writeLogToFile(logFilePath, request);
    }
    
    @Transactional
    public void finishTask(String taskId, FinishRequest request) {
        Optional<Task> taskOpt = taskRepository.findById(taskId);
        if (taskOpt.isPresent()) {
            Task task = taskOpt.get();
            task.setStatus(request.getStatus());
            task.setExitCode(request.getExitCode());
            task.setSummary(request.getSummary());
            task.setFinishedAt(LocalDateTime.now());
            
            taskRepository.save(task);
            log.info("Task finished: {} with status {}", taskId, request.getStatus());
        }
    }
    
    public Optional<Task> getTask(String taskId) {
        return taskRepository.findById(taskId);
    }
    
    public Page<Task> getTasksByAgent(String agentId, Pageable pageable) {
        return taskRepository.findByAgentIdOrderByCreatedAtDesc(agentId, pageable);
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
    
    public long getPendingTaskCount() {
        return taskRepository.countByStatus("PENDING");
    }
    
    public long getRunningTaskCount() {
        return taskRepository.countByStatus("RUNNING");
    }
    
    public long getCompletedTaskCount() {
        return taskRepository.countByStatus("SUCCESS");
    }
    
    public long getFailedTaskCount() {
        return taskRepository.countByStatus("FAILED");
    }
    
    @Transactional
    public void cancelTask(String taskId) {
        Optional<Task> taskOpt = taskRepository.findById(taskId);
        if (taskOpt.isPresent()) {
            Task task = taskOpt.get();
            if ("PENDING".equals(task.getStatus()) || "RUNNING".equals(task.getStatus()) || "PULLED".equals(task.getStatus())) {
                task.setStatus("CANCELLED");
                task.setFinishedAt(LocalDateTime.now());
                task.setSummary("Task cancelled by user");
                taskRepository.save(task);
                log.info("Task {} cancelled", taskId);
            } else {
                log.warn("Task {} cannot be cancelled, current status: {}", taskId, task.getStatus());
            }
        } else {
            log.warn("Task {} not found for cancellation", taskId);
        }
    }
    
    @Scheduled(fixedRate = 60000) // 每1分钟检查一次
    @Transactional
    public void checkPulledTasks() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(2); // 2分钟没ACK
        List<Task> stuckTasks = taskRepository.findPulledButNotAcked(threshold);
        
        for (Task task : stuckTasks) {
            task.setStatus("PENDING");
            task.setPulledAt(null);
            taskRepository.save(task);
            log.warn("Task {} reset to PENDING (ACK timeout, agent may not have received it)", task.getTaskId());
        }
    }
    
    @Scheduled(fixedRate = 300000) // 每5分钟检查一次
    @Transactional
    public void checkTimeoutTasks() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(30); // 30分钟超时
        List<Task> timeoutTasks = taskRepository.findTimeoutTasks(threshold);
        
        for (Task task : timeoutTasks) {
            task.setStatus("TIMEOUT");
            task.setFinishedAt(LocalDateTime.now());
            task.setSummary("Task timeout after 30 minutes");
            taskRepository.save(task);
            log.warn("Task {} marked as TIMEOUT", task.getTaskId());
        }
    }
    
    // 已删除checkOfflineAgentTasks()和handleAgentTaskRecovery()方法
    // 原因：
    // 1. 任务如果真的失败，会通过超时机制标记为TIMEOUT
    // 2. 是否重新执行失败/超时的任务应该由人工决定，而不是系统自动重置
    // 3. 自动重置任务可能导致重复执行，不符合业务需求
    // 解决方案：
    // - 依赖超时机制（checkTimeoutTasks）标记超时任务
    // - 用户在Web界面查看失败/超时任务，手动决定是否重新执行
    
    private TaskSpec convertToTaskSpec(Task task) {
        TaskSpec spec = new TaskSpec();
        spec.setTaskId(task.getTaskId());
        spec.setScriptLang(task.getScriptLang());
        spec.setScriptContent(task.getScriptContent());
        spec.setTimeoutSec(task.getTimeoutSec());
        spec.setEnv(task.getEnv());
        return spec;
    }
    
    /**
     * 生成日志文件路径
     * 格式：logs/tasks/2024/01/taskId_executionCount_startTime.log
     * 例如：logs/tasks/2024/01/abc123_1_20240115143020.log
     */
    private String generateLogFilePath(Task task) {
        LocalDateTime startTime = task.getStartedAt();
        String yearMonth = startTime.format(DateTimeFormatter.ofPattern("yyyy/MM"));
        String dateTime = startTime.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        
        String fileName = String.format("%s_%d_%s.log", 
            task.getTaskId(),
            task.getExecutionCount(),
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
            
            // 追加写入
            Files.writeString(
                logFile.toPath(), 
                logLine, 
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            );
            
        } catch (IOException e) {
            log.error("Failed to write log file: {}", logFilePath, e);
            throw new RuntimeException("写入日志文件失败: " + logFilePath, e);
        }
    }
    
    /**
     * 重启任务
     */
    @Transactional
    public void restartTask(String taskId) {
        Task task = taskRepository.findById(taskId)
            .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + taskId));
        
        // 只有失败或超时的任务可以重启
        if (!"FAILED".equals(task.getStatus()) && !"TIMEOUT".equals(task.getStatus())) {
            throw new IllegalStateException(
                "只有失败或超时的任务可以重启，当前状态: " + task.getStatus());
        }
        
        // 1. 保存当前执行记录到历史表
        saveExecutionHistory(task);
        
        // 2. 重置任务状态（准备下次执行）
        task.setStatus("PENDING");
        task.setExecutionCount(task.getExecutionCount() + 1);
        task.setExitCode(null);
        task.setSummary(null);
        task.setPulledAt(null);
        task.setStartedAt(null);
        task.setFinishedAt(null);
        task.setLogFilePath(null); // 下次启动时重新生成
        
        taskRepository.save(task);
        
        log.info("Task {} restarted, next execution will be: {}", 
            taskId, task.getExecutionCount());
    }
    
    /**
     * 保存执行历史记录
     */
    private void saveExecutionHistory(Task task) {
        TaskExecution execution = new TaskExecution();
        execution.setTaskId(task.getTaskId());
        execution.setExecutionSeq(task.getExecutionCount());
        execution.setStatus(task.getStatus());
        execution.setExitCode(task.getExitCode());
        execution.setStartedAt(task.getStartedAt());
        execution.setFinishedAt(task.getFinishedAt());
        
        if (task.getStartedAt() != null && task.getFinishedAt() != null) {
            execution.setDurationMs(
                Duration.between(task.getStartedAt(), task.getFinishedAt()).toMillis()
            );
        }
        
        execution.setSummary(task.getSummary());
        execution.setLogFilePath(task.getLogFilePath());
        
        if (task.getLogFilePath() != null) {
            File logFile = new File(task.getLogFilePath());
            execution.setLogSizeBytes(logFile.exists() ? logFile.length() : 0L);
        }
        
        taskExecutionRepository.save(execution);
        
        log.info("Saved execution history for task {}, seq {}, log: {}", 
            task.getTaskId(), execution.getExecutionSeq(), task.getLogFilePath());
    }
    
    /**
     * 获取任务执行历史
     */
    public List<TaskExecution> getTaskExecutionHistory(String taskId) {
        return taskExecutionRepository.findByTaskIdOrderByExecutionSeqAsc(taskId);
    }
    
    /**
     * 获取单个执行记录
     */
    public Optional<TaskExecution> getTaskExecution(Long executionId) {
        return taskExecutionRepository.findById(executionId);
    }
    
    /**
     * 判断任务是否有执行历史
     */
    public boolean hasExecutionHistory(String taskId) {
        return taskExecutionRepository.existsByTaskId(taskId);
    }
}
