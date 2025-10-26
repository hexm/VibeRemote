package com.example.lightscript.server.service;

import com.example.lightscript.server.entity.BatchTask;
import com.example.lightscript.server.entity.Task;
import com.example.lightscript.server.repository.BatchTaskRepository;
import com.example.lightscript.server.repository.TaskRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class BatchTaskService {
    
    @Autowired
    private BatchTaskRepository batchTaskRepository;
    
    @Autowired
    private TaskRepository taskRepository;
    
    /**
     * 创建批量任务
     */
    @Transactional
    public BatchTask createBatchTask(String batchName, List<String> agentIds, 
                                    String scriptLang, String scriptContent, 
                                    Integer timeoutSec, String createdBy) {
        // 验证批量任务名称唯一性
        if (batchTaskRepository.existsByBatchName(batchName)) {
            throw new IllegalArgumentException("批量任务名称已存在: " + batchName);
        }
        
        // 创建批量任务记录
        BatchTask batchTask = new BatchTask();
        batchTask.setBatchId(UUID.randomUUID().toString());
        batchTask.setBatchName(batchName);
        batchTask.setScriptLang(scriptLang);
        batchTask.setScriptContent(scriptContent);
        batchTask.setTimeoutSec(timeoutSec);
        batchTask.setTargetAgentCount(agentIds.size());
        batchTask.setCreatedBy(createdBy);
        
        batchTaskRepository.save(batchTask);
        
        // 为每个Agent创建关联的普通任务
        int index = 1;
        for (String agentId : agentIds) {
            // 生成子任务名称，确保唯一性
            String taskName = batchName + "-任务" + index;
            
            Task task = new Task();
            task.setTaskId(UUID.randomUUID().toString());
            task.setAgentId(agentId);
            task.setBatchId(batchTask.getBatchId());
            task.setTaskName(taskName);
            task.setScriptLang(scriptLang);
            task.setScriptContent(scriptContent);
            task.setTimeoutSec(timeoutSec);
            task.setStatus("PENDING");
            task.setCreatedBy(createdBy);
            
            taskRepository.save(task);
            index++;
        }
        
        return batchTask;
    }
    
    /**
     * 获取批量任务列表（分页）
     */
    public Page<BatchTask> getBatchTasks(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<BatchTask> batchPage = batchTaskRepository.findAllByOrderByCreatedAtDesc(pageable);
        
        // 为每个批量任务计算统计信息
        batchPage.forEach(this::calculateBatchTaskStats);
        
        return batchPage;
    }
    
    /**
     * 获取批量任务详情
     */
    public BatchTask getBatchTaskDetail(String batchId) {
        Optional<BatchTask> batchTaskOpt = batchTaskRepository.findById(batchId);
        if (!batchTaskOpt.isPresent()) {
            return null;
        }
        
        BatchTask batchTask = batchTaskOpt.get();
        calculateBatchTaskStats(batchTask);
        
        return batchTask;
    }
    
    /**
     * 获取批量任务的所有子任务
     */
    public List<Task> getBatchTaskTasks(String batchId) {
        return taskRepository.findByBatchIdOrderByCreatedAtAsc(batchId);
    }
    
    /**
     * 取消批量任务（取消所有未完成的子任务）
     */
    @Transactional
    public void cancelBatchTask(String batchId) {
        List<Task> tasks = taskRepository.findByBatchIdOrderByCreatedAtAsc(batchId);
        
        for (Task task : tasks) {
            if ("PENDING".equals(task.getStatus()) || "RUNNING".equals(task.getStatus())) {
                task.setStatus("CANCELLED");
                task.setFinishedAt(LocalDateTime.now());
                taskRepository.save(task);
            }
        }
    }
    
    /**
     * 计算批量任务的统计信息和状态
     */
    private void calculateBatchTaskStats(BatchTask batchTask) {
        List<Task> tasks = taskRepository.findByBatchIdOrderByCreatedAtAsc(batchTask.getBatchId());
        
        batchTask.setTotalTasks(tasks.size());
        batchTask.setPendingTasks((int) tasks.stream().filter(t -> "PENDING".equals(t.getStatus())).count());
        batchTask.setRunningTasks((int) tasks.stream().filter(t -> "RUNNING".equals(t.getStatus()) || "PULLED".equals(t.getStatus())).count());
        batchTask.setSuccessTasks((int) tasks.stream().filter(t -> "SUCCESS".equals(t.getStatus())).count());
        batchTask.setFailedTasks((int) tasks.stream().filter(t -> "FAILED".equals(t.getStatus())).count());
        batchTask.setTimeoutTasks((int) tasks.stream().filter(t -> "TIMEOUT".equals(t.getStatus())).count());
        batchTask.setCancelledTasks((int) tasks.stream().filter(t -> "CANCELLED".equals(t.getStatus())).count());
        
        // 计算综合状态
        int completedTasks = batchTask.getSuccessTasks() + batchTask.getFailedTasks() + 
                            batchTask.getTimeoutTasks() + batchTask.getCancelledTasks();
        
        if (completedTasks == 0) {
            if (batchTask.getRunningTasks() > 0) {
                batchTask.setStatus("RUNNING");
            } else {
                batchTask.setStatus("PENDING");
            }
        } else if (completedTasks == batchTask.getTotalTasks()) {
            // 全部完成
            if (batchTask.getSuccessTasks() == batchTask.getTotalTasks()) {
                batchTask.setStatus("COMPLETED"); // 全部成功
            } else if (batchTask.getFailedTasks() + batchTask.getTimeoutTasks() == batchTask.getTotalTasks()) {
                batchTask.setStatus("FAILED"); // 全部失败
            } else {
                batchTask.setStatus("PARTIAL_FAILED"); // 部分失败
            }
        } else {
            // 部分完成
            batchTask.setStatus("RUNNING");
        }
        
        // 计算进度
        if (batchTask.getTotalTasks() > 0) {
            batchTask.setProgress((double) completedTasks / batchTask.getTotalTasks() * 100);
        } else {
            batchTask.setProgress(0.0);
        }
    }
}
