package com.example.lightscript.server.service;

import com.example.lightscript.server.entity.TaskExecution;
import com.example.lightscript.server.model.TaskModels;
import com.example.lightscript.server.repository.TaskExecutionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
public class TaskExecutionServiceImpl implements TaskExecutionService {

    @Autowired
    private TaskExecutionRepository taskExecutionRepository;

    @Override
    @Transactional
    public TaskExecution createExecution(String taskId, String agentId, Integer executionNumber) {
        TaskExecution execution = new TaskExecution();
        execution.setTaskId(taskId);
        execution.setAgentId(agentId);
        execution.setExecutionNumber(executionNumber);
        execution.setStatus("PENDING");
        execution.setCreatedAt(LocalDateTime.now());
        
        log.info("创建任务执行实例: taskId={}, agentId={}, executionNumber={}", 
                taskId, agentId, executionNumber);
        
        return taskExecutionRepository.save(execution);
    }

    @Override
    @Transactional
    public List<TaskExecution> createExecutions(String taskId, List<String> agentIds) {
        List<TaskExecution> executions = new ArrayList<>();
        
        for (String agentId : agentIds) {
            TaskExecution execution = createExecution(taskId, agentId, 1);
            executions.add(execution);
        }
        
        log.info("批量创建任务执行实例: taskId={}, agentCount={}", taskId, agentIds.size());
        
        return executions;
    }

    @Override
    @Transactional
    public void updateStatus(Long executionId, String status) {
        Optional<TaskExecution> optExecution = taskExecutionRepository.findById(executionId);
        if (optExecution.isPresent()) {
            TaskExecution execution = optExecution.get();
            String oldStatus = execution.getStatus();
            execution.setStatus(status);
            
            // 根据状态更新时间戳
            LocalDateTime now = LocalDateTime.now();
            switch (status) {
                case "PULLED":
                    execution.setPulledAt(now);
                    break;
                case "RUNNING":
                    execution.setStartedAt(now);
                    break;
                case "SUCCESS":
                case "FAILED":
                case "TIMEOUT":
                case "CANCELLED":
                    execution.setFinishedAt(now);
                    break;
            }
            
            taskExecutionRepository.save(execution);
            log.info("更新执行状态: executionId={}, {} -> {}", executionId, oldStatus, status);
        } else {
            log.warn("执行实例不存在: executionId={}", executionId);
        }
    }

    @Override
    @Transactional
    public void updateStatus(Long executionId, String status, Integer exitCode, String summary) {
        Optional<TaskExecution> optExecution = taskExecutionRepository.findById(executionId);
        if (optExecution.isPresent()) {
            TaskExecution execution = optExecution.get();
            execution.setStatus(status);
            execution.setExitCode(exitCode);
            execution.setSummary(summary);
            execution.setFinishedAt(LocalDateTime.now());
            
            taskExecutionRepository.save(execution);
            log.info("更新执行状态（完成）: executionId={}, status={}, exitCode={}", 
                    executionId, status, exitCode);
        } else {
            log.warn("执行实例不存在: executionId={}", executionId);
        }
    }

    @Override
    public List<TaskExecution> getExecutionsByTaskId(String taskId) {
        return taskExecutionRepository.findByTaskIdOrderByCreatedAtAsc(taskId);
    }

    @Override
    public List<TaskExecution> getExecutionsByTaskIdAndStatus(String taskId, String status) {
        return taskExecutionRepository.findByTaskIdAndStatus(taskId, status);
    }

    @Override
    public List<TaskExecution> getPendingExecutionsByAgentId(String agentId) {
        return taskExecutionRepository.findByAgentIdAndStatusOrderByCreatedAtAsc(agentId, "PENDING");
    }

    @Override
    public List<TaskExecution> getExecutionsByAgentId(String agentId) {
        return taskExecutionRepository.findByAgentIdOrderByCreatedAtDesc(agentId);
    }

    @Override
    public Optional<TaskExecution> getExecution(Long executionId) {
        return taskExecutionRepository.findById(executionId);
    }

    @Override
    public Optional<TaskExecution> getLatestExecution(String taskId, String agentId) {
        return taskExecutionRepository.findFirstByTaskIdAndAgentIdOrderByExecutionNumberDesc(taskId, agentId);
    }

    @Override
    public Integer getNextExecutionNumber(String taskId, String agentId) {
        Optional<TaskExecution> latestExecution = getLatestExecution(taskId, agentId);
        return latestExecution.map(execution -> execution.getExecutionNumber() + 1).orElse(1);
    }

    @Override
    @Transactional
    public void cancelExecution(Long executionId) {
        Optional<TaskExecution> optExecution = taskExecutionRepository.findById(executionId);
        if (optExecution.isPresent()) {
            TaskExecution execution = optExecution.get();
            String status = execution.getStatus();
            
            // 只能取消未完成的执行
            if ("PENDING".equals(status) || "PULLED".equals(status) || "RUNNING".equals(status)) {
                execution.setStatus("CANCELLED");
                execution.setFinishedAt(LocalDateTime.now());
                taskExecutionRepository.save(execution);
                log.info("取消执行实例: executionId={}", executionId);
            } else {
                log.warn("无法取消已完成的执行实例: executionId={}, status={}", executionId, status);
                throw new IllegalStateException("无法取消状态为 " + status + " 的执行实例");
            }
        } else {
            log.warn("执行实例不存在: executionId={}", executionId);
            throw new IllegalArgumentException("执行实例不存在: " + executionId);
        }
    }

    @Override
    @Transactional
    public void cancelTaskExecutions(String taskId) {
        List<String> cancelableStatuses = Arrays.asList("PENDING", "PULLED", "RUNNING");
        List<TaskExecution> executions = taskExecutionRepository.findByTaskIdAndStatusIn(taskId, cancelableStatuses);
        
        LocalDateTime now = LocalDateTime.now();
        for (TaskExecution execution : executions) {
            execution.setStatus("CANCELLED");
            execution.setFinishedAt(now);
        }
        
        taskExecutionRepository.saveAll(executions);
        log.info("取消任务的所有执行实例: taskId={}, count={}", taskId, executions.size());
    }

    @Override
    public long countExecutionsByTaskId(String taskId) {
        return taskExecutionRepository.countByTaskId(taskId);
    }

    @Override
    public long countExecutionsByTaskIdAndStatus(String taskId, String status) {
        return taskExecutionRepository.countByTaskIdAndStatus(taskId, status);
    }

    @Override
    public long countExecutionsByStatus(String status) {
        return taskExecutionRepository.countByStatus(status);
    }

    @Override
    public TaskModels.TaskExecutionDTO toDTO(TaskExecution execution) {
        if (execution == null) {
            return null;
        }
        
        TaskModels.TaskExecutionDTO dto = new TaskModels.TaskExecutionDTO();
        dto.setId(execution.getId());
        dto.setTaskId(execution.getTaskId());
        dto.setAgentId(execution.getAgentId());
        dto.setExecutionNumber(execution.getExecutionNumber());
        dto.setStatus(execution.getStatus());
        dto.setLogFilePath(execution.getLogFilePath());
        dto.setExitCode(execution.getExitCode());
        dto.setSummary(execution.getSummary());
        dto.setPulledAt(execution.getPulledAt());
        dto.setStartedAt(execution.getStartedAt());
        dto.setFinishedAt(execution.getFinishedAt());
        dto.setCreatedAt(execution.getCreatedAt());
        
        // 计算执行耗时
        if (execution.getStartedAt() != null && execution.getFinishedAt() != null) {
            long durationMs = java.time.Duration.between(
                execution.getStartedAt(), 
                execution.getFinishedAt()
            ).toMillis();
            dto.setDurationMs(durationMs);
        }
        
        return dto;
    }

    @Override
    public List<TaskModels.TaskExecutionDTO> toDTOs(List<TaskExecution> executions) {
        return executions.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
}
