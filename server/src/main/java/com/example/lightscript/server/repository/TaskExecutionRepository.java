package com.example.lightscript.server.repository;

import com.example.lightscript.server.entity.TaskExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TaskExecutionRepository extends JpaRepository<TaskExecution, Long> {
    
    /**
     * 查询任务的所有执行实例
     */
    List<TaskExecution> findByTaskIdOrderByCreatedAtAsc(String taskId);
    
    /**
     * 查询代理的所有执行实例
     */
    List<TaskExecution> findByAgentIdOrderByCreatedAtDesc(String agentId);
    
    /**
     * 查询代理的待处理执行实例
     */
    List<TaskExecution> findByAgentIdAndStatusOrderByCreatedAtAsc(String agentId, String status);
    
    /**
     * 查询任务在特定代理上的所有执行实例
     */
    List<TaskExecution> findByTaskIdAndAgentIdOrderByExecutionNumberDesc(String taskId, String agentId);
    
    /**
     * 查询任务在特定代理上的最新执行实例
     */
    Optional<TaskExecution> findFirstByTaskIdAndAgentIdOrderByExecutionNumberDesc(String taskId, String agentId);
    
    /**
     * 查询任务在特定代理上的特定执行次数的实例
     */
    Optional<TaskExecution> findByTaskIdAndAgentIdAndExecutionNumber(String taskId, String agentId, Integer executionNumber);
    
    /**
     * 统计任务的特定状态的执行实例数量
     */
    long countByTaskIdAndStatus(String taskId, String status);
    
    /**
     * 统计任务的总执行实例数量
     */
    long countByTaskId(String taskId);
    
    /**
     * 统计特定状态的所有执行实例数量
     */
    long countByStatus(String status);
    
    /**
     * 查询任务的特定状态的执行实例
     */
    List<TaskExecution> findByTaskIdAndStatus(String taskId, String status);
    
    /**
     * 查询任务的多个状态的执行实例
     */
    @Query("SELECT te FROM TaskExecution te WHERE te.taskId = :taskId AND te.status IN :statuses")
    List<TaskExecution> findByTaskIdAndStatusIn(@Param("taskId") String taskId, @Param("statuses") List<String> statuses);
    
    /**
     * 查询超时的执行实例
     */
    @Query("SELECT te FROM TaskExecution te WHERE te.status = 'RUNNING' AND te.startedAt < :threshold")
    List<TaskExecution> findTimeoutExecutions(@Param("threshold") LocalDateTime threshold);
    
    /**
     * 查询已拉取但未确认的执行实例
     */
    @Query("SELECT te FROM TaskExecution te WHERE te.status = 'PULLED' AND te.pulledAt < :threshold")
    List<TaskExecution> findPulledButNotAcked(@Param("threshold") LocalDateTime threshold);
    
    /**
     * 删除任务的所有执行实例
     */
    void deleteByTaskId(String taskId);
    
    /**
     * 检查执行实例是否存在
     */
    boolean existsByTaskIdAndAgentIdAndExecutionNumber(String taskId, String agentId, Integer executionNumber);
}
