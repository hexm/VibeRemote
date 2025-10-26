package com.example.lightscript.server.service;

import com.example.lightscript.server.entity.Task;
import com.example.lightscript.server.entity.TaskLog;
import com.example.lightscript.server.model.AgentModels.*;
import com.example.lightscript.server.repository.TaskRepository;
import com.example.lightscript.server.repository.TaskLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
    
    @Transactional
    public String createTask(String agentId, TaskSpec taskSpec, String createdBy) {
        Task task = new Task();
        task.setTaskId(taskSpec.getTaskId() != null ? taskSpec.getTaskId() : UUID.randomUUID().toString());
        task.setAgentId(agentId);
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
                taskRepository.save(task);
                log.info("Task {} acknowledged and started", taskId);
            } else {
                log.warn("Task {} ACK ignored, current status: {}", taskId, task.getStatus());
            }
        } else {
            log.warn("Task {} not found for ACK", taskId);
        }
    }
    
    @Transactional
    public void appendLog(String taskId, LogChunkRequest request) {
        TaskLog log = new TaskLog();
        log.setTaskId(taskId);
        log.setSeqNum(request.getSeq());
        log.setStream(request.getStream());
        log.setContent(request.getData());
        
        taskLogRepository.save(log);
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
}
