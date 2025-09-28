package com.example.lightscript.server.repository;

import com.example.lightscript.server.entity.TaskLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskLogRepository extends JpaRepository<TaskLog, Long> {
    
    List<TaskLog> findByTaskIdOrderBySeqNumAsc(String taskId);
    
    List<TaskLog> findByTaskIdAndStreamOrderBySeqNumAsc(String taskId, String stream);
    
    void deleteByTaskId(String taskId);
}
