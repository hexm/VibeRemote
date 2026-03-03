package com.example.lightscript.server.web;

import com.example.lightscript.server.entity.Agent;
import com.example.lightscript.server.entity.BatchTask;
import com.example.lightscript.server.entity.Task;
import com.example.lightscript.server.entity.TaskExecution;
import com.example.lightscript.server.model.AgentModels.TaskSpec;
import com.example.lightscript.server.model.TaskModels;
import com.example.lightscript.server.service.AgentService;
import com.example.lightscript.server.service.BatchTaskService;
import com.example.lightscript.server.service.TaskService;
import com.example.lightscript.server.service.TaskExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
    private final TaskExecutionService taskExecutionService;
    
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
    public ResponseEntity<List<TaskModels.TaskExecutionDTO>> getAgentTasks(
            @PathVariable String agentId) {
        // 返回该代理的所有执行实例（按创建时间倒序）
        List<TaskExecution> executions = taskExecutionService.getExecutionsByAgentId(agentId);
        List<TaskModels.TaskExecutionDTO> dtos = taskExecutionService.toDTOs(executions);
        return ResponseEntity.ok(dtos);
    }

    /**
     * 获取Agent所属的分组
     */
    @GetMapping("/agents/{agentId}/groups")
    public ResponseEntity<?> getAgentGroups(@PathVariable String agentId) {
        List<com.example.lightscript.server.entity.AgentGroup> groups = agentGroupService.getAgentGroups(agentId);

        List<com.example.lightscript.server.model.AgentGroupModels.SimpleGroupDTO> dtoList = groups.stream()
            .map(group -> {
                com.example.lightscript.server.model.AgentGroupModels.SimpleGroupDTO dto =
                    new com.example.lightscript.server.model.AgentGroupModels.SimpleGroupDTO();
                dto.setId(group.getId());
                dto.setName(group.getName());
                dto.setType(group.getType());
                return dto;
            })
            .collect(java.util.stream.Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("groups", dtoList);
        return ResponseEntity.ok(response);
    }

    
    // ========== 任务管理API（新版 - 支持多代理）==========
    
    /**
     * 获取所有任务（含聚合状态）
     */
    @GetMapping("/tasks")
    public ResponseEntity<Page<TaskModels.TaskDTO>> getAllTasks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<TaskModels.TaskDTO> tasks;
        
        if (status != null && !status.isEmpty()) {
            tasks = taskService.getTasksByStatus(status, pageable);
        } else {
            tasks = taskService.getAllTasksWithStatus(pageable);
        }
        
        return ResponseEntity.ok(tasks);
    }
    
    /**
     * 获取任务详情（含聚合状态）
     */
    @GetMapping("/tasks/{taskId}")
    public ResponseEntity<TaskModels.TaskDTO> getTask(@PathVariable String taskId) {
        TaskModels.TaskDTO task = taskService.getTaskWithAggregatedStatus(taskId);
        if (task == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(task);
    }
    
    /**
     * 获取任务摘要（聚合状态和统计信息）
     */
    @GetMapping("/tasks/{taskId}/summary")
    public ResponseEntity<TaskModels.TaskSummaryDTO> getTaskSummary(@PathVariable String taskId) {
        TaskModels.TaskSummaryDTO summary = taskService.getTaskSummary(taskId);
        if (summary == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(summary);
    }
    
    /**
     * 创建多代理任务（新版）
     * 支持一个任务分发到多个代理执行
     * 支持通过分组ID选择代理
     */
    @PostMapping("/tasks/create")
    public ResponseEntity<Map<String, Object>> createTask(
            @RequestParam(required = false) List<String> agentIds,
            @RequestParam(required = false) Long groupId,
            @RequestParam String taskName,
            @RequestParam(defaultValue = "true") Boolean autoStart,
            @RequestBody TaskSpec taskSpec) {
        
        // 如果提供了groupId，从分组获取agentIds
        List<String> targetAgentIds = agentIds;
        if (groupId != null) {
            targetAgentIds = agentGroupService.getGroupAgentIds(groupId);
            if (targetAgentIds.isEmpty()) {
                throw new IllegalArgumentException("分组中没有Agent");
            }
        }
        
        // 验证参数
        if (targetAgentIds == null || targetAgentIds.isEmpty()) {
            throw new IllegalArgumentException("至少需要选择一个代理或指定一个分组");
        }
        
        // 使用固定的创建者，避免Principal相关问题
        String createdBy = "web-user";
        taskSpec.setTaskName(taskName);
        
        // 调用新的多代理任务创建方法
        TaskModels.CreateTaskResponse createResponse = taskService.createMultiAgentTask(
            targetAgentIds, taskSpec, createdBy, autoStart);
        
        Map<String, Object> response = new HashMap<>();
        response.put("taskId", createResponse.getTaskId());
        response.put("taskStatus", createResponse.getTaskStatus());
        response.put("targetAgentCount", createResponse.getTargetAgentCount());
        response.put("message", createResponse.getMessage());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 启动任务（草稿状态的任务）
     */
    @PostMapping("/tasks/{taskId}/start")
    public ResponseEntity<TaskModels.StartTaskResponse> startTask(
            @PathVariable String taskId,
            @RequestParam(required = false) List<String> agentIds) {
        
        TaskModels.StartTaskResponse response = taskService.startTask(taskId, agentIds);
        return ResponseEntity.ok(response);
    }
    
    /**
     * 停止任务（待执行或执行中的任务）
     */
    @PostMapping("/tasks/{taskId}/stop")
    public ResponseEntity<TaskModels.StopTaskResponse> stopTask(@PathVariable String taskId) {
        TaskModels.StopTaskResponse response = taskService.stopTask(taskId);
        return ResponseEntity.ok(response);
    }
    
    /**
     * 获取任务的所有执行实例
     */
    @GetMapping("/tasks/{taskId}/executions")
    public ResponseEntity<List<TaskExecution>> getTaskExecutions(@PathVariable String taskId) {
        List<TaskExecution> executions = taskService.getTaskExecutionHistory(taskId);
        return ResponseEntity.ok(executions);
    }
    
    /**
     * 重启任务
     * @param mode 重启模式：ALL（重启所有）或 FAILED_ONLY（仅重启失败的）
     */
    @PostMapping("/tasks/{taskId}/restart")
    public ResponseEntity<Map<String, Object>> restartTask(
            @PathVariable String taskId,
            @RequestParam(defaultValue = "ALL") String mode) {
        
        // 验证重启模式
        TaskModels.RestartMode restartMode;
        try {
            restartMode = TaskModels.RestartMode.valueOf(mode.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("无效的重启模式: " + mode + "，支持的模式: ALL, FAILED_ONLY");
        }
        
        // 执行重启
        TaskModels.RestartTaskResponse restartResponse = taskService.restartTask(taskId, restartMode);
        
        Map<String, Object> result = new HashMap<>();
        result.put("message", restartResponse.getMessage());
        result.put("taskId", restartResponse.getTaskId());
        result.put("mode", mode);
        result.put("newExecutionCount", restartResponse.getNewExecutionCount());
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * 取消任务（取消所有执行实例）
     */
    @PostMapping("/tasks/{taskId}/cancel")
    public ResponseEntity<Map<String, String>> cancelTask(@PathVariable String taskId) {
        taskService.cancelTask(taskId);
        Map<String, String> response = new HashMap<>();
        response.put("message", "任务已取消");
        response.put("taskId", taskId);
        return ResponseEntity.ok(response);
    }
    
    /**
     * 取消特定执行实例
     */
    @PostMapping("/tasks/executions/{executionId}/cancel")
    public ResponseEntity<Map<String, String>> cancelExecution(@PathVariable Long executionId) {
        taskService.cancelExecution(executionId);
        Map<String, String> response = new HashMap<>();
        response.put("message", "执行实例已取消");
        response.put("executionId", executionId.toString());
        return ResponseEntity.ok(response);
    }
    
    /**
     * 查看执行实例日志
     */
    @GetMapping("/tasks/executions/{executionId}/logs")
    public ResponseEntity<Map<String, Object>> getExecutionLogs(
            @PathVariable Long executionId,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "5000") int limit) {
        
        TaskExecution execution = taskService.getTaskExecution(executionId)
            .orElseThrow(() -> new IllegalArgumentException("执行记录不存在"));
        
        return readLogFile(execution.getLogFilePath(), offset, limit, execution.getStatus());
    }
    
    /**
     * 下载执行实例日志
     */
    @GetMapping("/tasks/executions/{executionId}/download")
    public ResponseEntity<Resource> downloadExecutionLog(@PathVariable Long executionId) {
        TaskExecution execution = taskService.getTaskExecution(executionId)
            .orElseThrow(() -> new IllegalArgumentException("执行记录不存在"));
        
        if (execution.getLogFilePath() == null || execution.getLogFilePath().isEmpty()) {
            throw new IllegalArgumentException("该执行记录无日志文件");
        }
        
        File logFile = new File(execution.getLogFilePath());
        if (!logFile.exists()) {
            throw new IllegalArgumentException("日志文件不存在: " + execution.getLogFilePath());
        }
        
        Resource resource = new FileSystemResource(logFile);
        String filename = logFile.getName();
        
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, 
                "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.TEXT_PLAIN)
            .body(resource);
    }
    
    // ========== 批量任务管理API（已弃用）==========
    
    /**
     * 创建批量任务（已弃用，请使用 /tasks/create 并传入多个 agentIds）
     */
    @Deprecated
    @PostMapping("/tasks/batch")
    public ResponseEntity<Map<String, Object>> createBatchTasks(
            @RequestParam List<String> agentIds,
            @RequestBody TaskSpec taskSpec) {
        String createdBy = "web-user";
        List<String> taskIds = taskService.createBatchTasks(agentIds, taskSpec, createdBy);
        Map<String, Object> result = new HashMap<>();
        result.put("taskIds", taskIds);
        result.put("count", taskIds.size());
        return ResponseEntity.ok(result);
    }
    
    /**
     * 创建批量任务（已弃用）
     */
    @Deprecated
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
     * 获取批量任务列表（已弃用）
     */
    @Deprecated
    @GetMapping("/batch-tasks")
    public ResponseEntity<Page<BatchTask>> getBatchTasks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<BatchTask> batchTasks = batchTaskService.getBatchTasks(page, size);
        return ResponseEntity.ok(batchTasks);
    }
    
    /**
     * 获取批量任务详情（已弃用）
     */
    @Deprecated
    @GetMapping("/batch-tasks/{batchId}")
    public ResponseEntity<BatchTask> getBatchTaskDetail(@PathVariable String batchId) {
        BatchTask batchTask = batchTaskService.getBatchTaskDetail(batchId);
        if (batchTask == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(batchTask);
    }
    
    /**
     * 获取批量任务的所有子任务（已弃用）
     */
    @Deprecated
    @GetMapping("/batch-tasks/{batchId}/tasks")
    public ResponseEntity<List<Task>> getBatchTaskTasks(@PathVariable String batchId) {
        List<Task> tasks = batchTaskService.getBatchTaskTasks(batchId);
        return ResponseEntity.ok(tasks);
    }
    
    /**
     * 取消批量任务（已弃用）
     */
    @Deprecated
    @PostMapping("/batch-tasks/{batchId}/cancel")
    public ResponseEntity<Map<String, String>> cancelBatchTask(@PathVariable String batchId) {
        batchTaskService.cancelBatchTask(batchId);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Batch task cancelled");
        return ResponseEntity.ok(response);
    }
    
    // ========== 工具方法 ==========
    
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


    private final com.example.lightscript.server.service.AgentGroupService agentGroupService;

}
