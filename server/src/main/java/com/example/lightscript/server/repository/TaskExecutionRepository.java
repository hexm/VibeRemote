package com.example.lightscript.server.repository;

import com.example.lightscript.server.entity.TaskExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TaskExecutionRepository extends JpaRepository<TaskExecution, Long> {
    
    /**
     * 查询指定任务的所有执行历史，按执行序号升序
     */
    List<TaskExecution> findByTaskIdOrderByExecutionSeqAsc(String taskId);
    
    /**
     * 查询指定任务的执行历史数量
     */
    int countByTaskId(String taskId);
    
    /**
     * 判断任务是否有执行历史
     */
    boolean existsByTaskId(String taskId);
    
    /**
     * 查询创建时间早于指定日期的记录（用于清理）
     */
    List<TaskExecution> findByCreatedAtBefore(LocalDateTime date);
}
