package com.example.lightscript.server.repository;

import com.example.lightscript.server.entity.Task;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TaskRepository extends JpaRepository<Task, String> {
    
    // 基本查询
    Page<Task> findByCreatedByOrderByCreatedAtDesc(String createdBy, Pageable pageable);
    
    // 任务名称唯一性检查
    boolean existsByTaskName(String taskName);
    
    Task findByTaskName(String taskName);
}
