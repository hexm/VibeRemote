package com.example.lightscript.server.service;

import com.example.lightscript.server.entity.Task;
import com.example.lightscript.server.entity.TaskExecution;
import com.example.lightscript.server.entity.TaskLog;
import com.example.lightscript.server.entity.Agent;
import com.example.lightscript.server.model.AgentModels.*;
import com.example.lightscript.server.model.TaskModels;
import com.example.lightscript.server.model.FileModels;
import com.example.lightscript.server.repository.TaskRepository;
import com.example.lightscript.server.repository.TaskExecutionRepository;
import com.example.lightscript.server.repository.TaskLogRepository;
import com.example.lightscript.server.repository.AgentRepository;
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
import java.time.LocalDate;
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
    private AgentRepository agentRepository;
    
    @Autowired
    private TaskExecutionService taskExecutionService;
    
    @Autowired
    private AgentService agentService;
    
    @Autowired
    private FileService fileService;
    
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
            String createdBy,
            boolean autoStart) {
        
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
        
        // 保存目标代理列表
        task.setTargetAgentIds(String.join(",", agentIds));
        
        // 根据autoStart设置初始状态
        if (autoStart) {
            task.setTaskStatus("PENDING");
        } else {
            task.setTaskStatus("DRAFT");
        }
        
        task = taskRepository.save(task);
        log.info("Multi-agent task created: {}, targets: {}, autoStart: {}", 
            task.getTaskId(), agentIds.size(), autoStart);
        
        // 2. 如果autoStart=true，为每个代理创建执行实例
        int executionCount = 0;
        if (autoStart) {
            List<TaskExecution> executions = taskExecutionService.createExecutions(task.getTaskId(), agentIds);
            executionCount = executions.size();
            log.info("Created {} execution instances for task {}", executionCount, task.getTaskId());
            
            // 更新任务状态
            updateTaskStatus(task.getTaskId());
        }
        
        // 3. 返回响应
        TaskModels.CreateTaskResponse response = new TaskModels.CreateTaskResponse();
        response.setTaskId(task.getTaskId());
        response.setTaskStatus(task.getTaskStatus());
        response.setTargetAgentCount(agentIds.size());
        if (autoStart) {
            response.setMessage("任务创建成功，已分配给 " + agentIds.size() + " 个代理");
        } else {
            response.setMessage("任务创建成功（草稿状态），需要手动启动");
        }
        
        return response;
    }
    /**
     * 创建文件传输任务
     */
    @Transactional
    public TaskModels.CreateTaskResponse createFileTransferTask(
            TaskModels.CreateFileTransferTaskRequest request,
            String createdBy) {

        // 验证参数
        if (request.getAgentIds() == null || request.getAgentIds().isEmpty()) {
            throw new IllegalArgumentException("至少需要选择一个代理");
        }

        // 验证文件是否存在
        if (!fileService.existsById(request.getFileId())) {
            throw new IllegalArgumentException("文件不存在: " + request.getFileId());
        }

        // 验证任务名称唯一性
        if (request.getTaskName() != null && !request.getTaskName().trim().isEmpty()) {
            if (taskRepository.existsByTaskName(request.getTaskName())) {
                throw new IllegalArgumentException("任务名称已存在: " + request.getTaskName());
            }
        }

        // 1. 创建任务记录
        Task task = new Task();
        task.setTaskId(UUID.randomUUID().toString());
        task.setTaskName(request.getTaskName());
        task.setTaskType("FILE_TRANSFER");
        task.setTimeoutSec(request.getTimeoutSec());
        task.setCreatedBy(createdBy);
        task.setCreatedAt(LocalDateTime.now());
        task.setTargetAgentIds(String.join(",", request.getAgentIds()));
        task.setTaskStatus("PENDING"); // 文件传输任务自动启动
        
        // 设置文件传输配置
        task.setOverwriteExisting(request.getOverwriteExisting());
        task.setVerifyChecksum(request.getVerifyChecksum());

        task = taskRepository.save(task);
        log.info("File transfer task created: {}, file: {}, targets: {}",
            task.getTaskId(), request.getFileId(), request.getAgentIds().size());

        // 2. 为每个代理创建执行实例
        List<TaskExecution> executions = new ArrayList<>();
        for (String agentId : request.getAgentIds()) {
            TaskExecution execution = new TaskExecution();
            execution.setTaskId(task.getTaskId());
            execution.setAgentId(agentId);
            execution.setExecutionNumber(1);
            execution.setStatus("PENDING");
            execution.setFileId(request.getFileId());
            execution.setTargetPath(request.getTargetPath());
            execution.setCreatedAt(LocalDateTime.now());

            executions.add(execution);
        }

        taskExecutionRepository.saveAll(executions);
        log.info("Created {} file transfer execution instances for task {}",
            executions.size(), task.getTaskId());

        // 3. 更新任务状态
        updateTaskStatus(task.getTaskId());

        // 4. 返回响应
        TaskModels.CreateTaskResponse response = new TaskModels.CreateTaskResponse();
        response.setTaskId(task.getTaskId());
        response.setTaskStatus(task.getTaskStatus());
        response.setTargetAgentCount(request.getAgentIds().size());
        response.setMessage("文件传输任务创建成功，已分配给 " + request.getAgentIds().size() + " 个代理");

        return response;
    }
    
    /**
     * 计算任务状态（根据执行实例状态）
     */
    public String calculateTaskStatus(String taskId) {
        Task task = taskRepository.findById(taskId)
            .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + taskId));
        
        // 获取所有执行实例
        List<TaskExecution> executions = taskExecutionService.getExecutionsByTaskId(taskId);
        
        // 如果没有执行实例，保持DRAFT状态
        if (executions.isEmpty()) {
            return "DRAFT";
        }
        
        // 有执行实例时，根据执行实例状态计算任务状态
        // 统计各状态数量
        long totalCount = executions.size();
        long pendingCount = executions.stream().filter(e -> "PENDING".equals(e.getStatus())).count();
        long pulledCount = executions.stream().filter(e -> "PULLED".equals(e.getStatus())).count();
        long runningCount = executions.stream().filter(e -> "RUNNING".equals(e.getStatus())).count();
        long successCount = executions.stream().filter(e -> "SUCCESS".equals(e.getStatus())).count();
        long failedCount = executions.stream().filter(e -> "FAILED".equals(e.getStatus()) || "TIMEOUT".equals(e.getStatus())).count();
        long cancelledCount = executions.stream().filter(e -> "CANCELLED".equals(e.getStatus())).count();
        
        // 状态判断逻辑
        if (runningCount > 0 || pulledCount > 0) {
            return "RUNNING";
        }
        
        if (successCount == totalCount) {
            return "SUCCESS";
        }
        
        if (failedCount == totalCount) {
            return "FAILED";
        }
        
        if (cancelledCount == totalCount) {
            return "CANCELLED";
        }
        
        if (successCount > 0 && failedCount > 0) {
            return "PARTIAL_SUCCESS";
        }
        
        if (pendingCount == totalCount) {
            return "PENDING";
        }
        
        // 默认返回PENDING
        return "PENDING";
    }
    
    /**
     * 更新任务状态
     */
    @Transactional
    public void updateTaskStatus(String taskId) {
        Task task = taskRepository.findById(taskId)
            .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + taskId));
        
        String oldStatus = task.getTaskStatus();
        String newStatus = calculateTaskStatus(taskId);
        
        if (!newStatus.equals(oldStatus)) {
            task.setTaskStatus(newStatus);
            
            // 更新任务时间字段
            if ("RUNNING".equals(newStatus) && task.getStartedAt() == null) {
                // 任务开始运行时设置开始时间（如果还没有设置）
                task.setStartedAt(LocalDateTime.now());
            } else if (("SUCCESS".equals(newStatus) || "FAILED".equals(newStatus) || 
                       "PARTIAL_SUCCESS".equals(newStatus) || "STOPPED".equals(newStatus) || 
                       "CANCELLED".equals(newStatus)) && task.getFinishedAt() == null) {
                // 任务完成时设置结束时间
                task.setFinishedAt(LocalDateTime.now());
            }
            
            taskRepository.save(task);
            log.info("Task {} status updated: {} -> {}", taskId, oldStatus, newStatus);
        }
    }
    
    /**
     * 启动任务
     */
    @Transactional
    public TaskModels.StartTaskResponse startTask(String taskId) {
        Task task = taskRepository.findById(taskId)
            .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + taskId));
        
        // 验证任务状态为DRAFT
        if (!"DRAFT".equals(task.getTaskStatus())) {
            throw new IllegalStateException("只能启动草稿状态的任务，当前状态: " + task.getTaskStatus());
        }
        
        // 获取目标代理列表（从第一次创建时应该保存，这里需要从某处获取）
        // 由于当前设计中没有保存目标代理列表，我们需要从用户输入获取
        // 这里暂时抛出异常，需要调用者提供代理列表
        throw new UnsupportedOperationException("启动任务需要提供目标代理列表，请使用startTask(taskId, agentIds)方法");
    }
    
    /**
     * 启动任务（带代理列表）
     */
    @Transactional
    public TaskModels.StartTaskResponse startTask(String taskId, List<String> agentIds) {
        Task task = taskRepository.findById(taskId)
            .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + taskId));
        
        // 验证任务状态为DRAFT
        if (!"DRAFT".equals(task.getTaskStatus())) {
            throw new IllegalStateException("只能启动草稿状态的任务，当前状态: " + task.getTaskStatus());
        }
        
        // 如果没有提供代理列表，使用任务创建时保存的代理列表
        List<String> targetAgentIds = agentIds;
        if (targetAgentIds == null || targetAgentIds.isEmpty()) {
            if (task.getTargetAgentIds() != null && !task.getTargetAgentIds().isEmpty()) {
                targetAgentIds = Arrays.asList(task.getTargetAgentIds().split(","));
                log.info("Task {} using saved agent list: {}", taskId, targetAgentIds);
            }
        }
        
        // 验证代理列表
        if (targetAgentIds == null || targetAgentIds.isEmpty()) {
            throw new IllegalArgumentException("无法确定目标代理列表，请提供代理ID");
        }
        
        // 为所有目标代理创建执行实例
        List<TaskExecution> executions = taskExecutionService.createExecutions(taskId, targetAgentIds);
        log.info("Task {} started, created {} execution instances", taskId, executions.size());
        
        // 更新任务状态
        updateTaskStatus(taskId);
        
        // 返回响应
        TaskModels.StartTaskResponse response = new TaskModels.StartTaskResponse();
        response.setTaskId(taskId);
        response.setTaskStatus(task.getTaskStatus());
        response.setExecutionCount(executions.size());
        response.setMessage("任务已启动，创建了 " + executions.size() + " 个执行实例");
        
        return response;
    }
    
    /**
     * 停止任务
     */
    @Transactional
    public TaskModels.StopTaskResponse stopTask(String taskId) {
        Task task = taskRepository.findById(taskId)
            .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + taskId));
        
        // 计算当前状态
        String currentStatus = calculateTaskStatus(taskId);
        
        // 验证任务状态可停止（PENDING或RUNNING）
        if (!"PENDING".equals(currentStatus) && !"RUNNING".equals(currentStatus)) {
            throw new IllegalStateException("只能停止待执行或执行中的任务，当前状态: " + currentStatus);
        }
        
        // 查询所有未完成的执行实例
        List<TaskExecution> executions = taskExecutionService.getExecutionsByTaskId(taskId);
        List<TaskExecution> toCancel = executions.stream()
            .filter(e -> "PENDING".equals(e.getStatus()) || "PULLED".equals(e.getStatus()) || "RUNNING".equals(e.getStatus()))
            .collect(Collectors.toList());
        
        // 取消这些执行实例
        int cancelledCount = 0;
        for (TaskExecution execution : toCancel) {
            taskExecutionService.cancelExecution(execution.getId());
            cancelledCount++;
        }
        
        log.info("Task {} stopped, cancelled {} execution instances", taskId, cancelledCount);
        
        // 更新任务状态
        updateTaskStatus(taskId);
        
        // 返回响应
        TaskModels.StopTaskResponse response = new TaskModels.StopTaskResponse();
        response.setTaskId(taskId);
        response.setTaskStatus(task.getTaskStatus());
        response.setCancelledCount(cancelledCount);
        response.setMessage("任务已停止，取消了 " + cancelledCount + " 个执行实例");
        
        return response;
    }
    
    /**
     * 获取任务详情
     */
    public TaskModels.TaskDTO getTaskWithDetails(String taskId) {
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
        
        // 计算统计信息
        Map<String, Integer> stats = computeExecutionStats(executions);
        summary.setCompletedExecutions(stats.get("completed"));
        summary.setExecutionProgress(stats.get("completed") + "/" + executions.size());
        summary.setSuccessCount(stats.get("success"));
        summary.setFailedCount(stats.get("failed"));
        summary.setRunningCount(stats.get("running"));
        summary.setPendingCount(stats.get("pending"));
        
        return summary;
    }
    
    /**
     * 获取所有任务
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
     * 按状态获取任务
     */
    public Page<TaskModels.TaskDTO> getTasksByStatus(String status, Pageable pageable) {
        Page<Task> taskPage = taskRepository.findByTaskStatus(status, pageable);
        
        List<TaskModels.TaskDTO> taskDTOs = taskPage.getContent().stream()
                .map(task -> {
                    List<TaskExecution> executions = taskExecutionService.getExecutionsByTaskId(task.getTaskId());
                    return toTaskDTO(task, executions);
                })
                .collect(Collectors.toList());
        
        return new PageImpl<>(taskDTOs, pageable, taskPage.getTotalElements());
    }
    
    /**
     * 按筛选条件获取任务
     */
    public Page<TaskModels.TaskDTO> getTasksByFilters(String status, String taskType, Pageable pageable) {
        Page<Task> taskPage;
        
        if (status != null && !status.isEmpty() && taskType != null && !taskType.isEmpty()) {
            // 同时按状态和类型筛选
            taskPage = taskRepository.findByTaskStatusAndTaskType(status, taskType, pageable);
        } else if (status != null && !status.isEmpty()) {
            // 只按状态筛选
            taskPage = taskRepository.findByTaskStatus(status, pageable);
        } else if (taskType != null && !taskType.isEmpty()) {
            // 只按类型筛选
            taskPage = taskRepository.findByTaskType(taskType, pageable);
        } else {
            // 无筛选条件
            taskPage = taskRepository.findAll(pageable);
        }
        
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
        
        // 验证任务状态可重启
        String currentStatus = calculateTaskStatus(taskId);
        if (!"SUCCESS".equals(currentStatus) && !"FAILED".equals(currentStatus) && 
            !"PARTIAL_SUCCESS".equals(currentStatus) && !"STOPPED".equals(currentStatus) &&
            !"CANCELLED".equals(currentStatus)) {
            throw new IllegalStateException("只能重启已完成、失败、部分成功、已停止或已取消的任务，当前状态: " + currentStatus);
        }
        
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
        
        // 重置需要重启的执行实例状态，并递增执行次数
        int restartedCount = 0;
        for (TaskExecution execution : toRestart) {
            // 递增执行次数
            execution.setExecutionNumber(execution.getExecutionNumber() + 1);
            
            // 重置执行状态
            execution.setStatus("PENDING");
            execution.setStartedAt(null);
            execution.setFinishedAt(null);
            execution.setSummary(null);
            execution.setExitCode(null);
            execution.setPulledAt(null);
            execution.setLogFilePath(null);
            
            taskExecutionRepository.save(execution);
            restartedCount++;
        }
        
        // 更新任务级别的执行次数和开始时间
        task.setExecutionCount(task.getExecutionCount() == null ? 2 : task.getExecutionCount() + 1);
        task.setStartedAt(LocalDateTime.now());
        task.setFinishedAt(null); // 清空结束时间，因为任务重新开始
        taskRepository.save(task);
        
        log.info("Task {} restarted, mode: {}, restarted executions: {}, task execution count: {}", 
            taskId, mode, restartedCount, task.getExecutionCount());
        
        // 更新任务状态
        updateTaskStatus(taskId);
        
        TaskModels.RestartTaskResponse response = new TaskModels.RestartTaskResponse();
        response.setTaskId(taskId);
        response.setNewExecutionCount(restartedCount);
        response.setMessage("任务已重启，重置了 " + restartedCount + " 个执行实例的状态");
        
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
        
        // 设置任务执行跟踪字段
        dto.setExecutionCount(task.getExecutionCount());
        dto.setStartedAt(task.getStartedAt());
        dto.setFinishedAt(task.getFinishedAt());
        
        // 设置任务状态
        dto.setTaskStatus(task.getTaskStatus());
        
        // 设置任务类型
        dto.setTaskType(task.getTaskType());
        
        // 设置文件传输配置
        dto.setOverwriteExisting(task.getOverwriteExisting());
        dto.setVerifyChecksum(task.getVerifyChecksum());
        
        // 如果是文件传输任务，获取文件信息
        if ("FILE_TRANSFER".equals(task.getTaskType()) && !executions.isEmpty()) {
            TaskExecution firstExecution = executions.get(0);
            if (firstExecution.getFileId() != null) {
                dto.setFileId(firstExecution.getFileId());
                dto.setTargetPath(firstExecution.getTargetPath());
                
                // 获取文件名
                try {
                    if (fileService.existsById(firstExecution.getFileId())) {
                        FileModels.FileDTO fileInfo = fileService.getFileById(firstExecution.getFileId());
                        dto.setFileName(fileInfo.getName());
                    }
                } catch (Exception e) {
                    log.warn("Failed to get file info for fileId: {}", firstExecution.getFileId(), e);
                }
            }
        }
        
        // 计算统计信息
        Map<String, Integer> stats = computeExecutionStats(executions);
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
        // 使用新的多代理方法，传入单个代理，默认autoStart=true
        TaskModels.CreateTaskResponse response = createMultiAgentTask(
                Collections.singletonList(agentId), 
                taskSpec, 
                createdBy,
                true
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
        // 检查Agent是否正在升级
        Optional<Agent> agentOpt = agentRepository.findByAgentId(agentId);
        if (agentOpt.isPresent()) {
            Agent agent = agentOpt.get();
            if ("UPGRADING".equals(agent.getStatus())) {
                log.info("Agent {} is upgrading, no tasks will be assigned", agentId);
                return Collections.emptyList(); // 升级中不分发任务
            }
        }
        
        // 使用新的Repository方法，只查询可执行状态任务的待处理执行实例
        List<TaskExecution> pendingExecutions = taskExecutionRepository.findPendingExecutionsForActiveTasks(agentId);
        
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
                        TaskSpec spec = convertToTaskSpec(task, execution);
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
        
        // 增加Agent任务计数
        agentService.incrementTaskCount(execution.getAgentId());
        
        // 更新任务状态
        updateTaskStatus(execution.getTaskId());
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
        
        // 更新任务状态
        updateTaskStatus(execution.getTaskId());
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
        return convertToTaskSpec(task, null);
    }
    
    private TaskSpec convertToTaskSpec(Task task, TaskExecution execution) {
        TaskSpec spec = new TaskSpec();
        spec.setTaskId(task.getTaskId());
        spec.setTaskName(task.getTaskName());
        spec.setTaskType(task.getTaskType());
        spec.setScriptLang(task.getScriptLang());
        spec.setScriptContent(task.getScriptContent());
        spec.setTimeoutSec(task.getTimeoutSec());
        spec.setEnv(task.getEnv());
        
        // 如果是文件传输任务且有执行实例，设置文件传输相关信息
        if ("FILE_TRANSFER".equals(task.getTaskType()) && execution != null) {
            spec.setFileId(execution.getFileId());
            spec.setTargetPath(execution.getTargetPath());
            // 从任务配置中获取这些信息
            spec.setOverwriteExisting(task.getOverwriteExisting() != null ? task.getOverwriteExisting() : false);
            spec.setVerifyChecksum(task.getVerifyChecksum() != null ? task.getVerifyChecksum() : true);
        }
        
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
        
        // 获取执行实例以更新任务状态
        Optional<TaskExecution> optExecution = taskExecutionService.getExecution(executionId);
        if (optExecution.isPresent()) {
            updateTaskStatus(optExecution.get().getTaskId());
        }
    }
    
    /**
     * 获取任务执行趋势数据（最近10天）
     */
    public List<Map<String, Object>> getTaskTrends() {
        List<Map<String, Object>> trends = new ArrayList<>();
        LocalDate today = LocalDate.now();
        
        for (int i = 9; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            LocalDateTime startOfDay = date.atStartOfDay();
            LocalDateTime endOfDay = date.atTime(23, 59, 59);
            
            // 查询当天的任务统计
            long totalTasks = taskRepository.countByCreatedAtBetween(startOfDay, endOfDay);
            long successTasks = taskRepository.countByCreatedAtBetweenAndTaskStatus(startOfDay, endOfDay, "SUCCESS");
            long failedTasks = taskRepository.countByCreatedAtBetweenAndTaskStatus(startOfDay, endOfDay, "FAILED");
            
            Map<String, Object> dayData = new HashMap<>();
            dayData.put("date", date.toString());
            dayData.put("total", totalTasks);
            dayData.put("success", successTasks);
            dayData.put("failed", failedTasks);
            
            trends.add(dayData);
        }
        
        return trends;
    }

}
