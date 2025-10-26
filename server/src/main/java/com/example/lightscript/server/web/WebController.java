package com.example.lightscript.server.web;

import com.example.lightscript.server.entity.Agent;
import com.example.lightscript.server.entity.BatchTask;
import com.example.lightscript.server.entity.Task;
import com.example.lightscript.server.entity.TaskLog;
import com.example.lightscript.server.model.AgentModels.TaskSpec;
import com.example.lightscript.server.service.AgentService;
import com.example.lightscript.server.service.BatchTaskService;
import com.example.lightscript.server.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/web")
@RequiredArgsConstructor
public class WebController {
    
    private final AgentService agentService;
    private final TaskService taskService;
    private final BatchTaskService batchTaskService;
    
    @GetMapping("/dashboard/stats")
    public ResponseEntity<Map<String, Object>> getDashboardStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("onlineAgents", agentService.getOnlineAgentCount());
        stats.put("offlineAgents", agentService.getOfflineAgentCount());
        stats.put("pendingTasks", taskService.getPendingTaskCount());
        stats.put("runningTasks", taskService.getRunningTaskCount());
        stats.put("completedTasks", taskService.getCompletedTaskCount());
        stats.put("failedTasks", taskService.getFailedTaskCount());
        return ResponseEntity.ok(stats);
    }
    
    @GetMapping("/agents")
    public ResponseEntity<Page<Agent>> getAgents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Agent> agents = agentService.getAllAgents(pageable);
        return ResponseEntity.ok(agents);
    }
    
    @GetMapping("/agents/{agentId}")
    public ResponseEntity<Agent> getAgent(@PathVariable String agentId) {
        Optional<Agent> agent = agentService.getAgent(agentId);
        return agent.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }
    
    @GetMapping("/agents/{agentId}/tasks")
    public ResponseEntity<Page<Task>> getAgentTasks(
            @PathVariable String agentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Task> tasks = taskService.getTasksByAgent(agentId, pageable);
        return ResponseEntity.ok(tasks);
    }
    
    @GetMapping("/tasks")
    public ResponseEntity<Page<Task>> getAllTasks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Task> tasks = taskService.getAllTasks(pageable);
        return ResponseEntity.ok(tasks);
    }
    
    @GetMapping("/tasks/{taskId}")
    public ResponseEntity<Task> getTask(@PathVariable String taskId) {
        Optional<Task> task = taskService.getTask(taskId);
        return task.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }
    
    @GetMapping("/tasks/{taskId}/logs")
    public ResponseEntity<List<TaskLog>> getTaskLogs(@PathVariable String taskId) {
        List<TaskLog> logs = taskService.getTaskLogs(taskId);
        return ResponseEntity.ok(logs);
    }
    
    @PostMapping("/tasks/create")
    public ResponseEntity<Map<String, String>> createTask(
            @RequestParam String agentId,
            @RequestParam String taskName,
            @RequestBody TaskSpec taskSpec) {
        // 使用固定的创建者，避免Principal相关问题
        String createdBy = "web-user";
        taskSpec.setTaskName(taskName);
        String taskId = taskService.createTask(agentId, taskSpec, createdBy);
        return ResponseEntity.ok(Map.of("taskId", taskId));
    }
    
    @PostMapping("/tasks/batch")
    public ResponseEntity<Map<String, Object>> createBatchTasks(
            @RequestParam List<String> agentIds,
            @RequestBody TaskSpec taskSpec) {
        // 使用固定的创建者，避免Principal相关问题
        String createdBy = "web-user";
        List<String> taskIds = taskService.createBatchTasks(agentIds, taskSpec, createdBy);
        Map<String, Object> result = new HashMap<>();
        result.put("taskIds", taskIds);
        result.put("count", taskIds.size());
        return ResponseEntity.ok(result);
    }
    
    // ========== 批量任务管理API ==========
    
    /**
     * 创建批量任务（新版）
     */
    @PostMapping("/batch-tasks/create")
    public ResponseEntity<Map<String, Object>> createBatchTask(
            @RequestParam String batchName,
            @RequestParam List<String> agentIds,
            @RequestBody TaskSpec taskSpec) {
        String createdBy = "web-user";
        BatchTask batchTask = batchTaskService.createBatchTask(
            batchName, agentIds, 
            taskSpec.getScriptLang(), 
            taskSpec.getScriptContent(),
            taskSpec.getTimeoutSec(), 
            createdBy
        );
        
        Map<String, Object> result = new HashMap<>();
        result.put("batchId", batchTask.getBatchId());
        result.put("batchName", batchTask.getBatchName());
        result.put("targetAgentCount", batchTask.getTargetAgentCount());
        return ResponseEntity.ok(result);
    }
    
    /**
     * 获取批量任务列表（分页）
     */
    @GetMapping("/batch-tasks")
    public ResponseEntity<Page<BatchTask>> getBatchTasks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<BatchTask> batchTasks = batchTaskService.getBatchTasks(page, size);
        return ResponseEntity.ok(batchTasks);
    }
    
    /**
     * 获取批量任务详情
     */
    @GetMapping("/batch-tasks/{batchId}")
    public ResponseEntity<BatchTask> getBatchTaskDetail(@PathVariable String batchId) {
        BatchTask batchTask = batchTaskService.getBatchTaskDetail(batchId);
        if (batchTask == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(batchTask);
    }
    
    /**
     * 获取批量任务的所有子任务
     */
    @GetMapping("/batch-tasks/{batchId}/tasks")
    public ResponseEntity<List<Task>> getBatchTaskTasks(@PathVariable String batchId) {
        List<Task> tasks = batchTaskService.getBatchTaskTasks(batchId);
        return ResponseEntity.ok(tasks);
    }
    
    /**
     * 取消批量任务
     */
    @PostMapping("/batch-tasks/{batchId}/cancel")
    public ResponseEntity<Map<String, String>> cancelBatchTask(@PathVariable String batchId) {
        batchTaskService.cancelBatchTask(batchId);
        return ResponseEntity.ok(Map.of("message", "Batch task cancelled"));
    }
    
    @PostMapping("/tasks/{taskId}/cancel")
    public ResponseEntity<Map<String, String>> cancelTask(@PathVariable String taskId) {
        taskService.cancelTask(taskId);
        return ResponseEntity.ok(Map.of("message", "Task cancelled"));
    }
}
