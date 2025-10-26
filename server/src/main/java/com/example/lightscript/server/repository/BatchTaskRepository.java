package com.example.lightscript.server.repository;

import com.example.lightscript.server.entity.BatchTask;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BatchTaskRepository extends JpaRepository<BatchTask, String> {
    
    /**
     * 按创建时间倒序查询所有批量任务
     */
    Page<BatchTask> findAllByOrderByCreatedAtDesc(Pageable pageable);
    
    /**
     * 按创建者查询批量任务
     */
    Page<BatchTask> findByCreatedByOrderByCreatedAtDesc(String createdBy, Pageable pageable);
    
    /**
     * 按时间范围查询批量任务
     */
    List<BatchTask> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime startTime, LocalDateTime endTime);
    
    /**
     * 批量任务名称唯一性检查
     */
    boolean existsByBatchName(String batchName);
    
    BatchTask findByBatchName(String batchName);
}
