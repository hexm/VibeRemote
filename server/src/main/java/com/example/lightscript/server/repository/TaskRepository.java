package com.example.lightscript.server.repository;

import com.example.lightscript.server.entity.Task;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<Task, String> {
    
    List<Task> findByAgentIdAndStatus(String agentId, String status);
    
    List<Task> findByAgentIdAndStatusOrderByCreatedAtAsc(String agentId, String status);
    
    Page<Task> findByAgentIdOrderByCreatedAtDesc(String agentId, Pageable pageable);
    
    Page<Task> findByCreatedByOrderByCreatedAtDesc(String createdBy, Pageable pageable);
    
    // 批量任务相关查询
    List<Task> findByBatchIdOrderByCreatedAtAsc(String batchId);
    
    Page<Task> findByBatchIdIsNullOrderByCreatedAtDesc(Pageable pageable); // 查询所有普通任务（不属于批量任务）
    
    @Query("SELECT t FROM Task t WHERE t.status IN :statuses ORDER BY t.createdAt DESC")
    Page<Task> findByStatusInOrderByCreatedAtDesc(@Param("statuses") List<String> statuses, Pageable pageable);
    
    @Query("SELECT COUNT(t) FROM Task t WHERE t.status = :status")
    long countByStatus(@Param("status") String status);
    
    @Query("SELECT t FROM Task t WHERE t.status = 'RUNNING' AND t.startedAt < :threshold")
    List<Task> findTimeoutTasks(@Param("threshold") LocalDateTime threshold);
    
    @Query("SELECT t FROM Task t WHERE t.status = 'PULLED' AND t.pulledAt < :threshold")
    List<Task> findPulledButNotAcked(@Param("threshold") LocalDateTime threshold);
    
    // 任务名称唯一性检查
    boolean existsByTaskName(String taskName);
    
    Task findByTaskName(String taskName);
}
