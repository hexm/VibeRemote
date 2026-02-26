package com.example.lightscript.server.service;

import com.example.lightscript.server.entity.TaskExecution;
import com.example.lightscript.server.model.TaskModels;

import java.util.List;
import java.util.Optional;

/**
 * 任务执行服务接口
 */
public interface TaskExecutionService {
    
    /**
     * 创建任务执行实例
     */
    TaskExecution createExecution(String taskId, String agentId, Integer executionNumber);
    
    /**
     * 批量创建任务执行实例
     */
    List<TaskExecution> createExecutions(String taskId, List<String> agentIds);
    
    /**
     * 更新执行状态
     */
    void updateStatus(Long executionId, String status);
    
    /**
     * 更新执行状态（带时间戳）
     */
    void updateStatus(Long executionId, String status, Integer exitCode, String summary);
    
    /**
     * 获取任务的所有执行实例
     */
    List<TaskExecution> getExecutionsByTaskId(String taskId);
    
    /**
     * 获取任务的特定状态的执行实例
     */
    List<TaskExecution> getExecutionsByTaskIdAndStatus(String taskId, String status);
    
    /**
     * 获取代理的待处理执行实例
     */
    List<TaskExecution> getPendingExecutionsByAgentId(String agentId);
    
    /**
     * 获取代理的所有执行实例
     */
    List<TaskExecution> getExecutionsByAgentId(String agentId);
    
    /**
     * 获取执行实例
     */
    Optional<TaskExecution> getExecution(Long executionId);
    
    /**
     * 获取任务在特定代理上的最新执行实例
     */
    Optional<TaskExecution> getLatestExecution(String taskId, String agentId);
    
    /**
     * 获取任务在特定代理上的下一个执行次数
     */
    Integer getNextExecutionNumber(String taskId, String agentId);
    
    /**
     * 取消执行实例
     */
    void cancelExecution(Long executionId);
    
    /**
     * 取消任务的所有执行实例
     */
    void cancelTaskExecutions(String taskId);
    
    /**
     * 统计任务的执行实例数量
     */
    long countExecutionsByTaskId(String taskId);
    
    /**
     * 统计任务的特定状态的执行实例数量
     */
    long countExecutionsByTaskIdAndStatus(String taskId, String status);
    
    /**
     * 统计特定状态的所有执行实例数量
     */
    long countExecutionsByStatus(String status);
    
    /**
     * 转换为DTO
     */
    TaskModels.TaskExecutionDTO toDTO(TaskExecution execution);
    
    /**
     * 批量转换为DTO
     */
    List<TaskModels.TaskExecutionDTO> toDTOs(List<TaskExecution> executions);
}
