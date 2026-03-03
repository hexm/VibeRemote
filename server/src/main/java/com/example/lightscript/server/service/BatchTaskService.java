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

/**
 * 批量任务服务
 * @deprecated 已废弃，请使用 TaskService.createMultiAgentTask 创建多代理任务
 */
@Deprecated
@Service
public class BatchTaskService {
    
    @Autowired
    private BatchTaskRepository batchTaskRepository;
    
    @Autowired
    private TaskRepository taskRepository;
    
    @Autowired
    private TaskService taskService;
    
    /**
     * 创建批量任务
     * @deprecated 使用 TaskService.createMultiAgentTask 代替
     */
    @Deprecated
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
        
        // 使用新的多代理任务API创建任务
        com.example.lightscript.server.model.AgentModels.TaskSpec taskSpec = 
            new com.example.lightscript.server.model.AgentModels.TaskSpec();
        taskSpec.setScriptLang(scriptLang);
        taskSpec.setScriptContent(scriptContent);
        taskSpec.setTimeoutSec(timeoutSec);
        
        // 创建多代理任务（默认autoStart=true）
        taskService.createMultiAgentTask(agentIds, taskSpec, createdBy, true);
        
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
     * @deprecated 批量任务功能已废弃
     */
    @Deprecated
    public List<Task> getBatchTaskTasks(String batchId) {
        // 返回空列表，因为批量任务功能已废弃
        return new ArrayList<>();
    }
    
    /**
     * 取消批量任务（取消所有未完成的子任务）
     * @deprecated 批量任务功能已废弃
     */
    @Deprecated
    @Transactional
    public void cancelBatchTask(String batchId) {
        // 批量任务功能已废弃，不执行任何操作
    }
    
    /**
     * 计算批量任务的统计信息和状态
     * @deprecated 批量任务功能已废弃
     */
    @Deprecated
    private void calculateBatchTaskStats(BatchTask batchTask) {
        // 批量任务功能已废弃，设置默认值
        batchTask.setTotalTasks(0);
        batchTask.setPendingTasks(0);
        batchTask.setRunningTasks(0);
        batchTask.setSuccessTasks(0);
        batchTask.setFailedTasks(0);
        batchTask.setTimeoutTasks(0);
        batchTask.setCancelledTasks(0);
        batchTask.setStatus("DEPRECATED");
    }
}
