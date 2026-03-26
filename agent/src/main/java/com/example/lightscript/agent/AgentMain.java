package com.example.lightscript.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class AgentMain {
    private static final Logger logger = LoggerFactory.getLogger(AgentMain.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static FileChannel lockChannel;
    private static FileLock lock;

    public static void main(String[] args) throws Exception {
        // ===== 单实例检查：防止同一台机器启动多个Agent =====
        if (!acquireLock()) {
            System.err.println("========================================");
            System.err.println("ERROR: Another Agent instance is already running on this machine!");
            System.err.println("Please stop the existing Agent before starting a new one.");
            System.err.println("========================================");
            System.exit(1);
        }
        
        System.out.println("Instance lock acquired");
        System.out.println("Working directory: " + System.getProperty("user.dir"));
        
        // 加载配置
        AgentConfig config = AgentConfig.getInstance();
        
        // 命令行参数可以覆盖配置文件
        String server = args.length > 0 ? args[0] : config.getServerUrl();
        String registerToken = args.length > 1 ? args[1] : config.getRegisterToken();

        logger.info("========================================");
        logger.info("Starting LightScript Agent...");
        logger.info("========================================");
        logger.info("Server: {}", server);
        logger.info("Register Token: {}", (registerToken.length() > 10 ? registerToken.substring(0, 10) + "..." : registerToken));
        logger.info("Working Directory: {}", System.getProperty("user.dir"));
        logger.info("Java Version: {}", System.getProperty("java.version"));
        logger.info("OS: {} {}", System.getProperty("os.name"), System.getProperty("os.version"));
        
        // 打印配置信息（调试模式）
        if ("DEBUG".equalsIgnoreCase(config.getLogLevel())) {
            config.printConfig();
        }

        // 获取主机信息
        String hostname = java.net.InetAddress.getLocalHost().getHostName();
        String osName = System.getProperty("os.name").toLowerCase();
        String osType;
        if (osName.contains("win")) {
            osType = "WINDOWS";
        } else if (osName.contains("mac")) {
            osType = "MACOS";
        } else {
            osType = "LINUX";
        }

        // 创建HTTP客户端
        CloseableHttpClient client = HttpClients.createDefault();
        AgentApi api = new AgentApi(server, client, MAPPER);

        // 初始化屏幕截图服务（仅 Windows / macOS）
        // 在注册完成后用 finalAgentId/finalAgentToken 重新初始化，此处先置 null
        final boolean[] screenCapableOs = {osName.contains("win") || osName.contains("mac")};

        // ===== 注册（幂等）=====
        // token 永远使用配置文件中的 register.token，不动态生成，不本地保存 token。
        // agentId 由服务端分配，保存到本地文件复用，避免重复创建记录。
        AgentIdStore idStore = new AgentIdStore();

        // 兼容旧版本：迁移 .lightscript/.agent_id -> .viberemote/.agent_id
        if (idStore.load() == null) {
            String legacyId = AgentIdStore.loadLegacy();
            if (legacyId != null) {
                logger.info("Migrating legacy agent ID: {}", legacyId);
                idStore.save(legacyId);
            }
        }

        String agentId = null;
        String agentToken = null;

        logger.info("========================================");
        logger.info("AGENT REGISTRATION (idempotent)");
        logger.info("========================================");
        logger.info("Hostname: {}, OS Type: {}", hostname, osType);

        int retryDelay = 1000;
        int retryCount = 0;
        while (agentId == null) {
            try {
                // 每次启动都调用 register，服务端幂等处理（hostname+osType 已存在则更新）
                Map<String, Object> reg = api.register(registerToken, hostname, osType);
                agentId = String.valueOf(reg.get("agentId"));
                agentToken = String.valueOf(reg.get("agentToken"));
                idStore.save(agentId);
                logger.info("✓ Agent registered. Agent ID: {}", agentId);
                logger.info("✓ Agent token received from server");
                logger.info("========================================");
            } catch (Exception e) {
                retryCount++;
                logger.error("✗ Registration attempt {} failed: {}", retryCount, e.getMessage());
                logger.info("Retrying in {} seconds...", retryDelay / 1000);
                try {
                    Thread.sleep(retryDelay);
                } catch (InterruptedException ie) {
                    // 清除中断标志，继续重试，不因系统信号而放弃注册
                    Thread.interrupted();
                    logger.warn("Sleep interrupted during registration retry, continuing...");
                }
                if (retryDelay < 2000) retryDelay = 2000;
                else if (retryDelay < 5000) retryDelay = 5000;
                else if (retryDelay < 10000) retryDelay = 10000;
                else retryDelay = 30000;
            }
        }

        // 使用final变量以便在lambda中使用
        final String finalAgentId = agentId;
        final String finalAgentToken = agentToken;

        // 初始化屏幕截图服务（仅 Windows / macOS）
        ScreenCaptureService screenCaptureService = null;
        if (screenCapableOs[0]) {
            try {
                screenCaptureService = new ScreenCaptureService(api, finalAgentId, finalAgentToken);
                logger.info("[Screen] ScreenCaptureService initialized for OS: {}", osType);
            } catch (Exception e) {
                logger.warn("[Screen] Failed to initialize ScreenCaptureService: {}", e.getMessage());
            }
        } else {
            logger.info("[Screen] Screen capture not supported on OS: {}", osType);
        }
        final ScreenCaptureService finalScreenCaptureService = screenCaptureService;

        // 初始化加密上下文（如果启用加密）
        AgentEncryptionContext encryptionContext = null;
        if (config.isEncryptionEnabled()) {
            logger.info("========================================");
            logger.info("ENCRYPTION INITIALIZATION");
            logger.info("========================================");
            try {
                EncryptionService encryptionService = new EncryptionService();
                encryptionContext = new AgentEncryptionContext(finalAgentId, encryptionService, api);
                api.setEncryptionContext(encryptionContext);
                
                // 设置凭证并注册公钥
                encryptionContext.updateCredentials(finalAgentId, finalAgentToken);
                
                logger.info("✓ Encryption context initialized for agent: {}", finalAgentId);
                logger.info("Encryption algorithm: {}", config.getProperty("encryption.algorithm", "AES-256-GCM"));
                logger.info("========================================");
            } catch (Exception e) {
                logger.error("✗ Failed to initialize encryption context: {}", e.getMessage());
                logger.info("========================================");
                // 如果加密初始化失败，可以选择继续运行（降级为明文）或退出
                if (config.isEncryptionRequired()) {
                    logger.error("Encryption is required but initialization failed. Exiting...");
                    releaseLock();
                    System.exit(1);
                }
            }
        } else {
            logger.info("========================================");
            logger.info("ENCRYPTION DISABLED");
            logger.info("========================================");
            logger.info("Encryption is disabled in configuration");
        }

        // 创建任务执行器和升级相关组件
        TaskStatusMonitor taskStatusMonitor = new TaskStatusMonitor();
        SimpleTaskRunner taskRunner = new SimpleTaskRunner(api, finalAgentId, finalAgentToken, taskStatusMonitor);
        ExecutorService taskExecutor = Executors.newFixedThreadPool(2);
        
        // 创建升级相关组件
        ScheduledExecutorService upgradeScheduler = Executors.newScheduledThreadPool(1);
        UpgradeStatusReporter upgradeReporter = new UpgradeStatusReporter(server, client, MAPPER, finalAgentId, finalAgentToken);
        UpgradeExecutor upgradeExecutor = new UpgradeExecutor(upgradeReporter, taskStatusMonitor, upgradeScheduler, server, finalAgentId, finalAgentToken);
        
        // 连接状态追踪
        final boolean[] needReRegister = {false};
        final String[] currentAgentId = {finalAgentId};
        final String[] currentAgentToken = {finalAgentToken};
        final AgentEncryptionContext[] currentEncryptionContext = {encryptionContext};

        // 添加关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("========================================");
            logger.info("AGENT SHUTDOWN INITIATED");
            logger.info("========================================");
            
            // 主动向服务器报告离线状态
            try {
                if (currentAgentId[0] != null && currentAgentToken[0] != null) {
                    logger.info("Notifying server of agent shutdown...");
                    api.offline(currentAgentId[0], currentAgentToken[0]);
                    logger.info("✓ Server notified successfully");
                }
            } catch (Exception e) {
                logger.warn("✗ Failed to notify server of shutdown: {}", e.getMessage());
            }
            
            logger.info("Shutting down task runner...");
            taskRunner.shutdown();
            logger.info("Shutting down task executor...");
            taskExecutor.shutdown();
            logger.info("Shutting down upgrade scheduler...");
            upgradeScheduler.shutdown();
            // 停止屏幕截图
            if (finalScreenCaptureService != null) {
                finalScreenCaptureService.stop();
            }
            try {
                if (client != null) {
                    logger.info("Closing HTTP client...");
                    client.close();
                    logger.info("✓ HTTP client closed");
                }
            } catch (Exception e) {
                // 忽略关闭错误，避免在关闭过程中抛出异常
                logger.warn("Warning: Error closing HTTP client: {}", e.getMessage());
            }
            releaseLock(); // 释放文件锁
            logger.info("✓ Agent shutdown complete");
            logger.info("========================================");
        }));

        logger.info("========================================");
        logger.info("AGENT STARTUP COMPLETE");
        logger.info("========================================");
        logger.info("Agent ID: {}", finalAgentId);
        logger.info("Heartbeat interval: {}ms", config.getHeartbeatInterval());
        logger.info("Task pull interval: {}ms", config.getTaskPullInterval());
        logger.info("Max heartbeat failures: {}", config.getMaxHeartbeatFailures());
        logger.info("Encryption enabled: {}", config.isEncryptionEnabled());
        logger.info("Agent started successfully. Waiting for tasks...");
        logger.info("========================================");

        // 主循环
        long lastHeartbeat = 0L;
        final long[] lastSystemInfoHeartbeat = {0L}; // 上次发送系统信息心跳的时间
        int heartbeatFailures = 0;
        final int MAX_HEARTBEAT_FAILURES = config.getMaxHeartbeatFailures();
        boolean reRegistered = false;
        
        while (!Thread.currentThread().isInterrupted()) {
            long now = System.currentTimeMillis();

            try {
                // 检查是否需要重新注册
                if (needReRegister[0]) {
                    logger.error("========================================");
                    logger.error("CONNECTION LOST - RE-REGISTERING");
                    logger.error("========================================");
                    
                    int reRegRetryCount = 0;
                    int reRegRetryDelay = 1000;
                    boolean reRegDone = false;
                    
                    while (!reRegDone && !Thread.currentThread().isInterrupted()) {
                        try {
                            reRegRetryCount++;
                            logger.info("Re-registration attempt {}...", reRegRetryCount);
                            Map<String, Object> reg = api.register(registerToken, hostname, osType);
                            currentAgentId[0] = String.valueOf(reg.get("agentId"));
                            // token 不变，仍是 registerToken
                            idStore.save(currentAgentId[0]);
                            
                            taskRunner.updateCredentials(currentAgentId[0], currentAgentToken[0]);
                            if (currentEncryptionContext[0] != null) {
                                currentEncryptionContext[0].updateCredentials(currentAgentId[0], currentAgentToken[0]);
                            }
                            upgradeReporter = new UpgradeStatusReporter(server, client, MAPPER, currentAgentId[0], currentAgentToken[0]);
                            upgradeExecutor = new UpgradeExecutor(upgradeReporter, taskStatusMonitor, upgradeScheduler, server, currentAgentId[0], currentAgentToken[0]);
                            
                            logger.info("✓ Re-registered. Agent ID: {}", currentAgentId[0]);
                            logger.info("========================================");
                            needReRegister[0] = false;
                            heartbeatFailures = 0;
                            lastHeartbeat = 0;
                            reRegDone = true;
                        } catch (Exception e) {
                            logger.error("✗ Re-registration attempt {} failed: {}", reRegRetryCount, e.getMessage());
                            Thread.sleep(reRegRetryDelay);
                            if (reRegRetryDelay < 2000) reRegRetryDelay = 2000;
                            else if (reRegRetryDelay < 5000) reRegRetryDelay = 5000;
                            else if (reRegRetryDelay < 10000) reRegRetryDelay = 10000;
                            else reRegRetryDelay = 30000;
                        }
                    }
                }
                
                // 心跳检测 - 使用配置的间隔时间
                if (now - lastHeartbeat > config.getHeartbeatInterval()) {
                    try {
                        logger.debug("Sending heartbeat... (failures: {}/{})", heartbeatFailures, MAX_HEARTBEAT_FAILURES);
                        // 使用配置的系统信息间隔时间
                        boolean includeSystemInfo = (now - lastSystemInfoHeartbeat[0] > config.getSystemInfoInterval());
                        
                        Map<String, Object> heartbeatResponse;
                        if (includeSystemInfo) {
                            heartbeatResponse = api.heartbeat(currentAgentId[0], currentAgentToken[0], true);
                            lastSystemInfoHeartbeat[0] = now;
                            logger.info("✓ Heartbeat with system info sent successfully");
                        } else {
                            heartbeatResponse = api.heartbeat(currentAgentId[0], currentAgentToken[0], false);
                            logger.debug("✓ Heartbeat sent successfully");
                        }
                        
                        // 处理心跳响应中的版本检查信息
                        handleHeartbeatResponse(heartbeatResponse, upgradeExecutor, api, currentAgentId[0], currentAgentToken[0], taskStatusMonitor);
                        
                        lastHeartbeat = now;
                        if (heartbeatFailures > 0) {
                            logger.info("✓ Connection restored after {} failures", heartbeatFailures);
                        }
                        heartbeatFailures = 0;
                    } catch (Exception e) {
                        heartbeatFailures++;
                        logger.warn("✗ Heartbeat failed ({}/{}): {}", heartbeatFailures, MAX_HEARTBEAT_FAILURES, e.getMessage());

                        String errMsg = e.getMessage() != null ? e.getMessage() : "";
                        boolean isAuthError = errMsg.contains("401") || errMsg.contains("403") || errMsg.contains("token invalid");

                        if (isAuthError) {
                            // 明确的认证失败，立即触发重注册
                            logger.error("✗ Auth error on heartbeat, triggering re-registration immediately");
                            needReRegister[0] = true;
                            heartbeatFailures = 0;
                        } else if (heartbeatFailures >= MAX_HEARTBEAT_FAILURES) {
                            // 连续多次网络失败，也触发重注册（服务端可能重启了）
                            logger.error("✗ Max heartbeat failures reached (network issue). Triggering re-registration...");
                            needReRegister[0] = true;
                            heartbeatFailures = 0;
                        } else {
                            // 更新lastHeartbeat避免频繁重试
                            lastHeartbeat = now;
                        }
                    }
                }
                
                // 拉取任务 - 使用配置的最大任务数
                Map<String, Object> response = api.pullTasks(currentAgentId[0], currentAgentToken[0]);
                
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> tasks = (List<Map<String, Object>>) response.get("tasks");

                // 处理屏幕截图指令（task pull 间隔 5s，比心跳 30s 快，用于快速触发）
                Object screenIntervalFromPull = response.get("screenCaptureInterval");
                if (screenIntervalFromPull != null && finalScreenCaptureService != null) {
                    finalScreenCaptureService.updateInterval(((Number) screenIntervalFromPull).intValue());
                }

                if (tasks != null && !tasks.isEmpty()) {
                    logger.info("========================================");
                    logger.info("RECEIVED {} TASKS FROM SERVER", tasks.size());
                    logger.info("========================================");
                    for (Map<String, Object> task : tasks) {
                        String taskId = String.valueOf(task.get("taskId"));
                        Long executionId = task.get("executionId") != null ? 
                            ((Number) task.get("executionId")).longValue() : null;
                        String taskType = String.valueOf(task.getOrDefault("taskType", "SCRIPT"));
                        String scriptLang = String.valueOf(task.get("scriptLang"));
                        String scriptContent = String.valueOf(task.get("scriptContent"));
                        Integer timeoutSec = (Integer) task.getOrDefault("timeoutSec", 300);
                        
                        // 文件传输相关字段
                        String fileId = String.valueOf(task.get("fileId"));
                        String targetPath = String.valueOf(task.get("targetPath"));
                        String sourcePath = String.valueOf(task.get("sourcePath"));
                        Long maxUploadSizeBytes = task.get("maxUploadSizeBytes") instanceof Number
                            ? ((Number) task.get("maxUploadSizeBytes")).longValue()
                            : null;
                        Boolean overwriteExisting = (Boolean) task.getOrDefault("overwriteExisting", false);
                        Boolean verifyChecksum = (Boolean) task.getOrDefault("verifyChecksum", true);

                        logger.info("Task received: {} (executionId: {}, type: {})", taskId, executionId, taskType);
                        logger.debug("Task details - scriptLang: {}, timeoutSec: {}", scriptLang, timeoutSec);
                        if ("SCRIPT".equals(taskType)) {
                            logger.debug("Script content length: {} chars", scriptContent != null && !"null".equals(scriptContent) ? scriptContent.length() : 0);
                        } else if ("FILE_TRANSFER".equals(taskType)) {
                            logger.debug("File transfer - fileId: {}, targetPath: {}", fileId, targetPath);
                        } else if ("FILE_UPLOAD".equals(taskType)) {
                            logger.debug("File upload - sourcePath: {}", sourcePath);
                            logger.debug("File upload - maxUploadSizeBytes: {}", maxUploadSizeBytes);
                        }
                        
                        // 检查基本字段
                        if (taskId == null || "null".equals(taskId) || executionId == null) {
                            logger.error("✗ INVALID TASK DATA - taskId or executionId is null");
                            logger.error("  taskId: {}, executionId: {}", taskId, executionId);
                            continue;
                        }
                        
                        // 根据任务类型检查必要字段，如果无效则报告失败
                        boolean isValidTask = true;
                        String errorMessage = null;
                        
                        if ("FILE_TRANSFER".equals(taskType)) {
                            if (fileId == null || "null".equals(fileId) || targetPath == null || "null".equals(targetPath)) {
                                isValidTask = false;
                                errorMessage = "Invalid file transfer task - fileId or targetPath is null. fileId: " + fileId + ", targetPath: " + targetPath;
                                logger.error("✗ INVALID FILE TRANSFER TASK");
                                logger.error("  fileId: {}, targetPath: {}", fileId, targetPath);
                            }
                        } else if ("FILE_UPLOAD".equals(taskType)) {
                            if (sourcePath == null || "null".equals(sourcePath) || sourcePath.trim().isEmpty()) {
                                isValidTask = false;
                                errorMessage = "Invalid file upload task - sourcePath is null";
                                logger.error("✗ INVALID FILE UPLOAD TASK");
                                logger.error("  sourcePath: {}", sourcePath);
                            }
                        } else {
                            if (scriptContent == null || "null".equals(scriptContent)) {
                                isValidTask = false;
                                errorMessage = "Invalid script task - scriptContent is null";
                                logger.error("✗ INVALID SCRIPT TASK - scriptContent is null");
                            }
                        }
                        
                        // 如果任务无效，立即报告失败并继续处理下一个任务
                        if (!isValidTask) {
                            final Long finalExecutionId = executionId;
                            final String finalErrorMessage = errorMessage;
                            
                            logger.info("Reporting invalid task failure for executionId: {}", finalExecutionId);
                            // 异步报告任务失败，避免阻塞主循环
                            taskExecutor.submit(() -> {
                                try {
                                    logger.info("[TASK-{}] Acknowledging invalid task", finalExecutionId);
                                    api.ackTask(currentAgentId[0], currentAgentToken[0], finalExecutionId);
                                    logger.info("[TASK-{}] Reporting failure: {}", finalExecutionId, finalErrorMessage);
                                    api.finish(currentAgentId[0], currentAgentToken[0], finalExecutionId, -3, "FAILED", finalErrorMessage);
                                    logger.info("[TASK-{}] ✓ Invalid task failure reported successfully", finalExecutionId);
                                } catch (Exception e) {
                                    logger.error("[TASK-{}] ✗ Failed to report invalid task failure: {}", finalExecutionId, e.getMessage());
                                }
                            });
                            continue;
                        }
                        
                        // 异步执行任务
                        final Long finalExecutionId = executionId;
                        final String finalTaskType = taskType;
                        final String finalFileId = fileId;
                        final String finalTargetPath = targetPath;
                        final String finalSourcePath = sourcePath;
                        final Long finalMaxUploadSizeBytes = maxUploadSizeBytes;
                        final boolean finalOverwriteExisting = overwriteExisting;
                        final boolean finalVerifyChecksum = verifyChecksum;
                        
                        logger.info("[TASK-{}] ✓ Submitting valid task to executor", executionId);
                        taskExecutor.submit(() -> {
                            logger.info("[TASK-{}] TaskExecutor thread started", finalExecutionId);
                            try {
                                taskRunner.runTask(finalExecutionId, taskId, finalTaskType, scriptLang, scriptContent, 
                                                 timeoutSec, finalFileId, finalTargetPath, finalSourcePath,
                                                 finalMaxUploadSizeBytes, finalOverwriteExisting, finalVerifyChecksum);
                            } catch (Exception e) {
                                logger.error("[TASK-{}] ✗ Error in taskExecutor thread: {}", finalExecutionId, e.getMessage(), e);
                            }
                        });
                    }
                    logger.info("========================================");
                }

                // 短暂休眠避免过度轮询 - 使用配置的间隔时间
                Thread.sleep(config.getTaskPullInterval());

            } catch (InterruptedException e) {
                logger.info("Agent interrupted, shutting down...");
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("========================================");
                logger.error("ERROR IN MAIN LOOP");
                logger.error("========================================");
                logger.error("Error details: {}", e.getMessage());
                
                // 检查是否是认证相关错误
                if (e.getMessage() != null && 
                    (e.getMessage().contains("400") || 
                     e.getMessage().contains("401") || 
                     e.getMessage().contains("403") ||
                     e.getMessage().contains("token"))) {
                    logger.error("✗ Authentication error detected: {}", e.getMessage());
                    logger.error("Triggering re-registration...");
                    logger.error("========================================");
                    needReRegister[0] = true;
                    // 短暂等待后重试
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    // 其他错误，打印堆栈并等待更长时间
                    logger.error("✗ Unexpected error in main loop", e);
                    logger.error("Waiting 10 seconds before retry...");
                    logger.error("========================================");
                    try {
                        Thread.sleep(10000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        
        logger.info("Agent main loop ended");
    }
    
    /**
     * 处理心跳响应中的版本检查信息
     */
    private static void handleHeartbeatResponse(Map<String, Object> response, UpgradeExecutor upgradeExecutor, 
                                               AgentApi agentApi, String agentId, String agentToken, 
                                               TaskStatusMonitor taskStatusMonitor) {
        if (response == null || response.isEmpty()) {
            return;
        }

        // 屏幕截图指令仅通过 pull 通道下发，此处不处理
        
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> versionCheck = (Map<String, Object>) response.get("versionCheck");
            
            if (versionCheck == null) {
                return; // 无版本检查信息
            }
            
            Boolean updateAvailable = (Boolean) versionCheck.get("updateAvailable");
            if (updateAvailable == null || !updateAvailable) {
                return; // 无更新可用
            }
            
            String message = (String) versionCheck.get("message");
            logger.info("[VersionCheck] Update available: {}", message);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> latestVersionMap = (Map<String, Object>) versionCheck.get("latestVersion");
            if (latestVersionMap == null) {
                System.err.println("[VersionCheck] No version info in response");
                return;
            }
            
            // 构建版本信息对象
            UpgradeExecutor.VersionInfo versionInfo = new UpgradeExecutor.VersionInfo();
            versionInfo.setVersion((String) latestVersionMap.get("version"));
            versionInfo.setDownloadUrl((String) latestVersionMap.get("downloadUrl"));
            versionInfo.setFileSize(latestVersionMap.get("fileSize") != null ? 
                ((Number) latestVersionMap.get("fileSize")).longValue() : null);
            versionInfo.setFileHash((String) latestVersionMap.get("fileHash"));
            versionInfo.setForceUpgrade(Boolean.TRUE.equals(latestVersionMap.get("forceUpgrade")));
            versionInfo.setReleaseNotes((String) latestVersionMap.get("releaseNotes"));
            
            // 根据强制升级标志决定升级策略
            if (versionInfo.isForceUpgrade()) {
                logger.info("[VersionCheck] Force upgrade detected, setting status to UPGRADING...");
                // 主动设置Agent状态为UPGRADING
                try {
                    agentApi.setUpgrading(agentId, agentToken);
                    logger.info("[VersionCheck] Status set to UPGRADING, stopping all tasks and starting upgrade immediately...");
                } catch (Exception e) {
                    System.err.println("[VersionCheck] Failed to set upgrading status: " + e.getMessage());
                    return;
                }
                
                // 强制升级：立即停止所有任务并开始升级
                taskStatusMonitor.stopAllTasks();
                upgradeExecutor.executeUpgrade(versionInfo);
            } else {
                logger.info("[VersionCheck] Normal upgrade detected, setting status to UPGRADING...");
                // 主动设置Agent状态为UPGRADING
                try {
                    agentApi.setUpgrading(agentId, agentToken);
                    logger.info("[VersionCheck] Status set to UPGRADING, entering upgrade waiting state...");
                } catch (Exception e) {
                    System.err.println("[VersionCheck] Failed to set upgrading status: " + e.getMessage());
                    return;
                }
                
                // 普通升级：进入升级状态，等待任务完成后升级
                if (upgradeExecutor.canUpgrade()) {
                    logger.info("[VersionCheck] No running tasks, starting upgrade immediately...");
                    upgradeExecutor.executeUpgrade(versionInfo);
                } else {
                    logger.info("[VersionCheck] Tasks are running, waiting for completion before upgrade...");
                    upgradeExecutor.scheduleUpgradeRetry(versionInfo);
                }
            }
            
        } catch (Exception e) {
            System.err.println("[VersionCheck] Failed to process version check response: " + e.getMessage());
        }
    }
    
    /**
     * 获取文件锁，确保单实例运行
     * 一台机器上只能启动一个Agent进程
     * @return true 如果成功获取锁，false 如果已有其他实例在运行
     */
    private static boolean acquireLock() {
        try {
            // 锁文件位置：用户目录/.lightscript/.agent.lock（全局唯一）
            String userHome = System.getProperty("user.home");
            Path lockDir = Paths.get(userHome, ".lightscript");
            Files.createDirectories(lockDir);
            Path lockFile = lockDir.resolve(".agent.lock");
            
            // 打开文件通道（读写模式）
            lockChannel = FileChannel.open(lockFile, 
                StandardOpenOption.CREATE, 
                StandardOpenOption.READ, 
                StandardOpenOption.WRITE);
            
            // 尝试获取排他锁（非阻塞）
            lock = lockChannel.tryLock();
            
            if (lock == null) {
                // 锁已被其他进程持有
                lockChannel.close();
                return false;
            }
            
            // 写入当前进程信息（用于调试）
            lockChannel.truncate(0);
            String workingDir = System.getProperty("user.dir");
            String lockInfo = String.format("PID: %s, Started: %s, WorkingDir: %s", 
                ManagementFactory.getRuntimeMXBean().getName(),
                LocalDateTime.now(),
                workingDir
            );
            lockChannel.write(ByteBuffer.wrap(lockInfo.getBytes()));
            
            System.out.println("Instance lock acquired: " + lockFile);
            System.out.println("Working directory: " + workingDir);
            return true;
            
        } catch (IOException e) {
            System.err.println("Failed to acquire instance lock: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 释放文件锁
     */
    private static void releaseLock() {
        try {
            if (lock != null) {
                lock.release();
                lock = null;
            }
            if (lockChannel != null) {
                lockChannel.close();
                lockChannel = null;
            }
            logger.info("Instance lock released");
        } catch (IOException e) {
            logger.error("Failed to release lock: {}", e.getMessage());
        }
    }
}
