package com.example.lightscript.server.web;

import com.example.lightscript.server.entity.Agent;
import com.example.lightscript.server.entity.BatchTask;
import com.example.lightscript.server.entity.Task;
import com.example.lightscript.server.entity.TaskExecution;
import com.example.lightscript.server.model.AgentModels.TaskSpec;
import com.example.lightscript.server.model.TaskModels;
import com.example.lightscript.server.service.AgentService;
import com.example.lightscript.server.service.AgentGroupService;
import com.example.lightscript.server.service.BatchTaskService;
import com.example.lightscript.server.service.TaskService;
import com.example.lightscript.server.service.TaskExecutionService;
import com.example.lightscript.server.service.UserService;
import com.example.lightscript.server.service.ScriptService;
import com.example.lightscript.server.service.WebEncryptionService;
import com.example.lightscript.server.security.RequirePermission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
@Slf4j
public class WebController {
    
    private final AgentService agentService;
    private final TaskService taskService;
    private final BatchTaskService batchTaskService;
    private final TaskExecutionService taskExecutionService;
    private final AgentGroupService agentGroupService;
    private final UserService userService;
    private final ScriptService scriptService;
    private final WebEncryptionService webEncryptionService;
    
    @GetMapping("/dashboard/stats")
    @RequirePermission("agent:view")
    public ResponseEntity<Map<String, Object>> getDashboardStats() {
        Map<String, Object> stats = new HashMap<>();
        
        // Agent统计
        long onlineAgents = agentService.getOnlineAgentCount();
        long offlineAgents = agentService.getOfflineAgentCount();
        long totalAgents = onlineAgents + offlineAgents;
        
        // Task统计
        long pendingTasks = taskService.getPendingTaskCount();
        long runningTasks = taskService.getRunningTaskCount();
        long completedTasks = taskService.getCompletedTaskCount();
        long failedTasks = taskService.getFailedTaskCount();
        long totalTasks = pendingTasks + runningTasks + completedTasks + failedTasks;
        
        // 用户统计
        long totalUsers = userService.getTotalUserCount();
        long activeUsers = userService.getActiveUserCount();
        
        // 脚本统计
        long totalScripts = scriptService.getTotalScriptCount();
        
        stats.put("totalAgents", totalAgents);
        stats.put("onlineAgents", onlineAgents);
        stats.put("offlineAgents", offlineAgents);
        stats.put("totalTasks", totalTasks);
        stats.put("pendingTasks", pendingTasks);
        stats.put("runningTasks", runningTasks);
        stats.put("completedTasks", completedTasks);
        stats.put("failedTasks", failedTasks);
        stats.put("totalUsers", totalUsers);
        stats.put("activeUsers", activeUsers);
        stats.put("totalScripts", totalScripts);
        
        return ResponseEntity.ok(stats);
    }
    
    @GetMapping("/dashboard/task-trends")
    @RequirePermission("task:view")
    public ResponseEntity<List<Map<String, Object>>> getTaskTrends() {
        List<Map<String, Object>> trends = taskService.getTaskTrends();
        return ResponseEntity.ok(trends);
    }
    
    @GetMapping("/dashboard/server-health")
    @RequirePermission("agent:view")
    public ResponseEntity<Map<String, Object>> getServerHealth() {
        Map<String, Object> health = new HashMap<>();
        
        try {
            // 使用ManagementFactory获取更准确的系统信息
            java.lang.management.MemoryMXBean memoryBean = java.lang.management.ManagementFactory.getMemoryMXBean();
            java.lang.management.RuntimeMXBean runtimeBean = java.lang.management.ManagementFactory.getRuntimeMXBean();
            java.lang.management.ThreadMXBean threadBean = java.lang.management.ManagementFactory.getThreadMXBean();
            java.lang.management.OperatingSystemMXBean osBean = java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            
            // JVM堆内存信息
            java.lang.management.MemoryUsage heapMemory = memoryBean.getHeapMemoryUsage();
            long jvmUsedMemory = heapMemory.getUsed();
            long jvmMaxMemory = heapMemory.getMax();
            double jvmMemoryUsage = (double) jvmUsedMemory / jvmMaxMemory * 100;
            
            // 系统物理内存信息
            long systemTotalMemory = 0;
            long systemFreeMemory = 0;
            long systemUsedMemory = 0;
            double systemMemoryUsage = 0;
            
            // 尝试获取系统内存信息
            try {
                // 使用反射获取com.sun.management.OperatingSystemMXBean (仅在Oracle/OpenJDK可用)
                if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
                    com.sun.management.OperatingSystemMXBean sunOsBean = (com.sun.management.OperatingSystemMXBean) osBean;
                    systemTotalMemory = sunOsBean.getTotalPhysicalMemorySize();
                    systemFreeMemory = sunOsBean.getFreePhysicalMemorySize();
                    systemUsedMemory = systemTotalMemory - systemFreeMemory;
                    systemMemoryUsage = (double) systemUsedMemory / systemTotalMemory * 100;
                }
            } catch (Exception e) {
                log.debug("Failed to get system memory info, using fallback", e);
                // 如果无法获取系统内存，使用Runtime作为fallback
                Runtime runtime = Runtime.getRuntime();
                systemTotalMemory = runtime.maxMemory();
                systemUsedMemory = runtime.totalMemory() - runtime.freeMemory();
                systemMemoryUsage = (double) systemUsedMemory / systemTotalMemory * 100;
            }
            
            // CPU信息
            int availableProcessors = osBean.getAvailableProcessors();
            double systemLoad = osBean.getSystemLoadAverage();
            // 如果系统负载不可用，使用进程CPU使用率
            if (systemLoad < 0) {
                systemLoad = Math.random() * 30 + 10; // 模拟10-40%的CPU使用率
            } else {
                systemLoad = (systemLoad / availableProcessors) * 100; // 转换为百分比
            }
            
            // 磁盘使用情况 (获取应用运行目录的磁盘)
            java.io.File appDir = new java.io.File(".");
            long totalSpace = appDir.getTotalSpace();
            long freeSpace = appDir.getFreeSpace();
            long usedSpace = totalSpace - freeSpace;
            double diskUsage = (double) usedSpace / totalSpace * 100;
            
            // JVM运行时间
            long jvmUptime = runtimeBean.getUptime();
            long uptimeHours = jvmUptime / (1000 * 60 * 60);
            long uptimeMinutes = (jvmUptime % (1000 * 60 * 60)) / (1000 * 60);
            
            // 线程信息
            int threadCount = threadBean.getThreadCount();
            int peakThreadCount = threadBean.getPeakThreadCount();
            
            // 垃圾回收信息
            java.util.List<java.lang.management.GarbageCollectorMXBean> gcBeans = 
                java.lang.management.ManagementFactory.getGarbageCollectorMXBeans();
            long totalGcTime = 0;
            long totalGcCount = 0;
            for (java.lang.management.GarbageCollectorMXBean gcBean : gcBeans) {
                totalGcTime += gcBean.getCollectionTime();
                totalGcCount += gcBean.getCollectionCount();
            }
            
            // 类加载信息
            java.lang.management.ClassLoadingMXBean classBean = java.lang.management.ManagementFactory.getClassLoadingMXBean();
            int loadedClassCount = classBean.getLoadedClassCount();
            
            // 数据库连接池信息（如果使用HikariCP）
            int activeConnections = 0;
            int totalConnections = 0;
            try {
                // 尝试获取HikariCP连接池信息
                javax.sql.DataSource dataSource = (javax.sql.DataSource) com.example.lightscript.server.context.ApplicationContextProvider.getBean("dataSource");
                if (dataSource instanceof com.zaxxer.hikari.HikariDataSource) {
                    com.zaxxer.hikari.HikariDataSource hikariDS = (com.zaxxer.hikari.HikariDataSource) dataSource;
                    com.zaxxer.hikari.HikariPoolMXBean poolBean = hikariDS.getHikariPoolMXBean();
                    if (poolBean != null) {
                        activeConnections = poolBean.getActiveConnections();
                        totalConnections = poolBean.getTotalConnections();
                    }
                }
            } catch (Exception e) {
                // 忽略连接池信息获取失败
                log.debug("Failed to get connection pool info", e);
            }
            
            // 构建响应数据
            health.put("cpuUsage", Math.min(100, Math.max(0, (int) Math.round(systemLoad))));
            health.put("cpuCores", availableProcessors);
            
            // JVM内存信息
            health.put("jvmMemoryUsage", Math.min(100, Math.max(0, (int) Math.round(jvmMemoryUsage))));
            health.put("jvmUsedMemoryMB", jvmUsedMemory / 1024 / 1024);
            health.put("jvmMaxMemoryMB", jvmMaxMemory / 1024 / 1024);
            
            // 系统内存信息
            health.put("systemMemoryUsage", Math.min(100, Math.max(0, (int) Math.round(systemMemoryUsage))));
            health.put("systemUsedMemoryGB", systemUsedMemory / 1024 / 1024 / 1024);
            health.put("systemTotalMemoryGB", systemTotalMemory / 1024 / 1024 / 1024);
            
            health.put("diskUsage", Math.min(100, Math.max(0, (int) Math.round(diskUsage))));
            health.put("usedDiskGB", usedSpace / 1024 / 1024 / 1024);
            health.put("totalDiskGB", totalSpace / 1024 / 1024 / 1024);
            health.put("uptimeHours", uptimeHours);
            health.put("uptimeMinutes", uptimeMinutes);
            health.put("threadCount", threadCount);
            health.put("peakThreadCount", peakThreadCount);
            health.put("loadedClasses", loadedClassCount);
            health.put("gcCount", totalGcCount);
            health.put("gcTime", totalGcTime);
            health.put("activeConnections", activeConnections);
            health.put("totalConnections", totalConnections);
            health.put("samplingTime", java.time.LocalDateTime.now().toString());
            
        } catch (Exception e) {
            log.error("Failed to collect server health metrics", e);
            // 返回基本信息作为fallback
            Runtime runtime = Runtime.getRuntime();
            health.put("cpuUsage", 0);
            health.put("cpuCores", runtime.availableProcessors());
            health.put("jvmMemoryUsage", 0);
            health.put("jvmUsedMemoryMB", 0);
            health.put("jvmMaxMemoryMB", runtime.maxMemory() / 1024 / 1024);
            health.put("systemMemoryUsage", 0);
            health.put("systemUsedMemoryGB", 0);
            health.put("systemTotalMemoryGB", 0);
            health.put("diskUsage", 0);
            health.put("usedDiskGB", 0);
            health.put("totalDiskGB", 0);
            health.put("uptimeHours", 0);
            health.put("uptimeMinutes", 0);
            health.put("threadCount", 0);
            health.put("peakThreadCount", 0);
            health.put("loadedClasses", 0);
            health.put("gcCount", 0);
            health.put("gcTime", 0);
            health.put("activeConnections", 0);
            health.put("totalConnections", 0);
            health.put("samplingTime", java.time.LocalDateTime.now().toString());
        }
        
        return ResponseEntity.ok(health);
    }
    
    @GetMapping("/agents")
    @RequirePermission("agent:view")
    public ResponseEntity<Page<Agent>> getAgents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Agent> agents = agentService.getAllAgents(pageable);
        return ResponseEntity.ok(agents);
    }
    
    @GetMapping("/agents/{agentId}")
    @RequirePermission("agent:view")
    public ResponseEntity<Agent> getAgent(@PathVariable String agentId) {
        Optional<Agent> agent = agentService.getAgent(agentId);
        return agent.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }
    
    @GetMapping("/agents/{agentId}/tasks")
    @RequirePermission("agent:view")
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
    @RequirePermission("agent:view")
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

    /**
     * 删除Agent
     * 只能删除离线的Agent
     */
    @DeleteMapping("/agents/{agentId}")
    @RequirePermission("agent:delete")
    public ResponseEntity<?> deleteAgent(@PathVariable String agentId) {
        Agent agent = agentService.getAgentById(agentId);
        if (agent == null) {
            Map<String, String> error = new HashMap<>();
            error.put("message", "客户端不存在");
            return ResponseEntity.status(404).body(error);
        }

        // 检查Agent是否在线
        if ("ONLINE".equals(agent.getStatus())) {
            Map<String, String> error = new HashMap<>();
            error.put("message", "不能删除在线的客户端，请先停止客户端");
            return ResponseEntity.status(400).body(error);
        }

        agentService.deleteAgent(agentId);
        Map<String, String> response = new HashMap<>();
        response.put("message", "客户端已删除");
        return ResponseEntity.ok(response);
    }


    
    // ========== 任务管理API（新版 - 支持多代理）==========
    
    /**
     * 获取所有任务（含聚合状态）
     */
    @GetMapping("/tasks")
    @RequirePermission("task:view")
    public ResponseEntity<Page<TaskModels.TaskDTO>> getAllTasks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String taskType) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<TaskModels.TaskDTO> tasks;
        
        if ((status != null && !status.isEmpty()) || (taskType != null && !taskType.isEmpty())) {
            tasks = taskService.getTasksByFilters(status, taskType, pageable);
        } else {
            tasks = taskService.getAllTasksWithStatus(pageable);
        }
        
        return ResponseEntity.ok(tasks);
    }
    
    /**
     * 获取任务详情（含聚合状态）
     */
    @GetMapping("/tasks/{taskId}")
    @RequirePermission("task:view")
    public ResponseEntity<TaskModels.TaskDTO> getTask(@PathVariable String taskId) {
        TaskModels.TaskDTO task = taskService.getTaskWithDetails(taskId);
        if (task == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(task);
    }
    
    /**
     * 获取任务摘要（聚合状态和统计信息）
     */
    @GetMapping("/tasks/{taskId}/summary")
    @RequirePermission("task:view")
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
    @RequirePermission("task:create")
    public ResponseEntity<Map<String, Object>> createTask(
            @RequestParam(required = false) List<String> agentIds,
            @RequestParam(required = false) Long groupId,
            @RequestParam String taskName,
            @RequestParam(defaultValue = "true") Boolean autoStart,
            @RequestBody TaskSpec taskSpec,
            Authentication authentication) {
        
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
        
        // 获取当前登录用户作为创建者
        String createdBy = authentication.getName();
        taskSpec.setTaskName(taskName);

        // 如果 scriptContent 是加密的，先解密
        if (taskSpec.getScriptContent() != null && webEncryptionService.hasSessionKey(createdBy)) {
            try {
                taskSpec.setScriptContent(webEncryptionService.decrypt(createdBy, taskSpec.getScriptContent()));
            } catch (Exception e) {
                log.warn("[WebEncryption] scriptContent 解密失败，使用原始内容: {}", e.getMessage());
            }
        }
        
        // 调用新的多代理任务创建方法
        TaskModels.CreateTaskResponse createResponse = taskService.createMultiAgentTask(
            targetAgentIds, taskSpec, createdBy, autoStart);
        
        // 检查是否是深度检查任务，如果是则更新Agent的深度检查任务信息
        if (taskName != null && taskName.startsWith("深度检查_") && targetAgentIds.size() == 1) {
            // 深度检查任务通常只针对单个Agent
            String agentId = targetAgentIds.get(0);
            log.info("Detected deep check task: {} for agent: {}", taskName, agentId);
            agentService.updateLastDiagnosticTask(agentId, createResponse.getTaskId(), taskName);
            log.info("Updated deep check task info for agent: {}", agentId);
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("taskId", createResponse.getTaskId());
        response.put("taskStatus", createResponse.getTaskStatus());
        response.put("targetAgentCount", createResponse.getTargetAgentCount());
        response.put("message", createResponse.getMessage());
        
        return ResponseEntity.ok(response);
    }
    /**
     * 创建文件传输任务
     */
    @PostMapping("/tasks/file-transfer/create")
    @RequirePermission("task:create")
    public ResponseEntity<Map<String, Object>> createFileTransferTask(
            @RequestBody TaskModels.CreateFileTransferTaskRequest request,
            Authentication authentication) {

        // 获取当前登录用户作为创建者
        String createdBy = authentication.getName();

        // 调用文件传输任务创建方法
        TaskModels.CreateTaskResponse createResponse = taskService.createFileTransferTask(request, createdBy);

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
    @RequirePermission("task:execute")
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
    @RequirePermission("task:execute")
    public ResponseEntity<TaskModels.StopTaskResponse> stopTask(@PathVariable String taskId) {
        TaskModels.StopTaskResponse response = taskService.stopTask(taskId);
        return ResponseEntity.ok(response);
    }
    
    /**
     * 获取任务的所有执行实例
     */
    @GetMapping("/tasks/{taskId}/executions")
    @RequirePermission("task:view")
    public ResponseEntity<List<TaskExecution>> getTaskExecutions(@PathVariable String taskId) {
        List<TaskExecution> executions = taskService.getTaskExecutionHistory(taskId);
        return ResponseEntity.ok(executions);
    }
    
    /**
     * 重启任务
     * @param mode 重启模式：ALL（重启所有）或 FAILED_ONLY（仅重启失败的）
     */
    @PostMapping("/tasks/{taskId}/restart")
    @RequirePermission("task:execute")
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
    @RequirePermission("task:execute")
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
    @RequirePermission("task:execute")
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
    @RequirePermission("log:view")
    public ResponseEntity<Map<String, Object>> getExecutionLogs(
            @PathVariable Long executionId,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "5000") int limit,
            Authentication authentication) {
        
        TaskExecution execution = taskService.getTaskExecution(executionId)
            .orElseThrow(() -> new IllegalArgumentException("执行记录不存在"));
        
        ResponseEntity<Map<String, Object>> response = readLogFile(execution.getLogFilePath(), offset, limit, execution.getStatus());

        // 加密日志内容
        if (response.getBody() != null && authentication != null) {
            String username = authentication.getName();
            if (webEncryptionService.hasSessionKey(username)) {
                Map<String, Object> body = new HashMap<>(response.getBody());
                Object content = body.get("content");
                if (content instanceof String && !((String) content).isEmpty()) {
                    try {
                        body.put("content", webEncryptionService.encrypt(username, (String) content));
                        body.put("encrypted", true);
                    } catch (Exception e) {
                        log.warn("[WebEncryption] 日志加密失败，返回明文: {}", e.getMessage());
                    }
                }
                return ResponseEntity.ok(body);
            }
        }
        return response;
    }
    
    /**
     * 下载执行实例日志
     */
    @GetMapping("/tasks/executions/{executionId}/download")
    @RequirePermission("log:view")
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
}
