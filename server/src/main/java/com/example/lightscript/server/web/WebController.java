package com.example.lightscript.server.web;

import com.example.lightscript.server.entity.Agent;
import com.example.lightscript.server.entity.Task;
import com.example.lightscript.server.entity.TaskLog;
import com.example.lightscript.server.model.AgentModels.TaskSpec;
import com.example.lightscript.server.service.AgentService;
import com.example.lightscript.server.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
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
            @RequestBody TaskSpec taskSpec,
            Principal principal) {
        String taskId = taskService.createTask(agentId, taskSpec, principal.getName());
        return ResponseEntity.ok(Map.of("taskId", taskId));
    }
    
    @PostMapping("/tasks/batch")
    public ResponseEntity<Map<String, Object>> createBatchTasks(
            @RequestParam List<String> agentIds,
            @RequestBody TaskSpec taskSpec,
            Principal principal) {
        List<String> taskIds = taskService.createBatchTasks(agentIds, taskSpec, principal.getName());
        Map<String, Object> result = new HashMap<>();
        result.put("taskIds", taskIds);
        result.put("count", taskIds.size());
        return ResponseEntity.ok(result);
    }
}
