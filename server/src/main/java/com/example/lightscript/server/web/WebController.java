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
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
    public ResponseEntity<Map<String, Object>> getTaskLogs(
            @PathVariable String taskId,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "5000") int limit) {
        
        Task task = taskService.getTask(taskId)
            .orElseThrow(() -> new IllegalArgumentException("任务不存在"));
        
        String logFilePath = task.getLogFilePath();
        
        // 任务还未启动，没有日志文件
        if (logFilePath == null || logFilePath.isEmpty()) {
            Map<String, Object> result = new HashMap<>();
            result.put("content", "");
            result.put("totalLines", 0);
            result.put("offset", 0);
            result.put("limit", limit);
            result.put("hasMore", false);
            result.put("status", task.getStatus());
            return ResponseEntity.ok(result);
        }
        
        return readLogFile(logFilePath, offset, limit, task.getStatus());
    }
    
    @GetMapping("/tasks/{taskId}/logs/download")
    public ResponseEntity<Resource> downloadTaskLog(@PathVariable String taskId) {
        Task task = taskService.getTask(taskId)
            .orElseThrow(() -> new IllegalArgumentException("任务不存在"));
        
        if (task.getLogFilePath() == null || task.getLogFilePath().isEmpty()) {
            throw new IllegalArgumentException("任务还未启动，无日志文件");
        }
        
        File logFile = new File(task.getLogFilePath());
        if (!logFile.exists()) {
            throw new IllegalArgumentException("日志文件不存在: " + task.getLogFilePath());
        }
        
        Resource resource = new FileSystemResource(logFile);
        String filename = logFile.getName();
        
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, 
                "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.TEXT_PLAIN)
            .body(resource);
    }
    
    /**
     * 统一的日志文件读取方法
     */
    private ResponseEntity<Map<String, Object>> readLogFile(
            String logFilePath, int offset, int limit, String status) {
        
        File logFile = new File(logFilePath);
        
        if (!logFile.exists()) {
            Map<String, Object> result = new HashMap<>();
            result.put("content", "日志文件不存在: " + logFilePath);
            result.put("totalLines", 0);
            result.put("offset", 0);
            result.put("limit", limit);
            result.put("hasMore", false);
            result.put("status", status != null ? status : "UNKNOWN");
            return ResponseEntity.ok(result);
        }
        
        try {
            List<String> allLines = Files.readAllLines(logFile.toPath(), StandardCharsets.UTF_8);
            int totalLines = allLines.size();
            int fromIndex = Math.min(offset, totalLines);
            int toIndex = Math.min(offset + limit, totalLines);
            
            List<String> pageLines = allLines.subList(fromIndex, toIndex);
            
            Map<String, Object> result = new HashMap<>();
            result.put("content", String.join("\n", pageLines));
            result.put("totalLines", totalLines);
            result.put("offset", offset);
            result.put("limit", limit);
            result.put("hasMore", toIndex < totalLines);
            result.put("status", status != null ? status : "UNKNOWN");
            result.put("fileSize", logFile.length());
            
            return ResponseEntity.ok(result);
            
        } catch (IOException e) {
            throw new RuntimeException("读取日志文件失败: " + logFilePath, e);
        }
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
