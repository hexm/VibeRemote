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

        logger.info("Starting LightScript Agent...");
        logger.info("Server: {}", server);
        logger.info("Register Token: {}", (registerToken.length() > 10 ? registerToken.substring(0, 10) + "..." : registerToken));
        
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

        // 注册Agent（带重试机制）
        String agentId = null;
        String agentToken = null;
        int retryCount = 0;
        int retryDelay = 1000; // 初始延迟1秒
        
        while (agentId == null) {
            try {
                logger.info("Registering agent" + (retryCount > 0 ? " (attempt " + (retryCount + 1) + ")..." : "..."));
                Map<String, Object> reg = api.register(registerToken, hostname, osType);
                agentId = String.valueOf(reg.get("agentId"));
                agentToken = String.valueOf(reg.get("agentToken"));
                
                logger.info("Agent registered successfully!");
                logger.info("Agent ID: {}", agentId);
                logger.info("Agent Token: {}", agentToken);
            } catch (Exception e) {
                retryCount++;
                logger.error("Failed to register agent: {}", e.getMessage());
                logger.info("Retrying in {} seconds...", (retryDelay / 1000));
                
                try {
                    Thread.sleep(retryDelay);
                } catch (InterruptedException ie) {
                    logger.info("Registration cancelled by user");
                    releaseLock();
                    return;
                }
                
                // 指数退避：1s -> 2s -> 5s -> 10s -> 30s (max)
                if (retryDelay < 2000) {
                    retryDelay = 2000;
                } else if (retryDelay < 5000) {
                    retryDelay = 5000;
                } else if (retryDelay < 10000) {
                    retryDelay = 10000;
                } else {
                    retryDelay = 30000; // 最大30秒
                }
            }
        }
        
        // 使用final变量以便在lambda中使用
        final String finalAgentId = agentId;
        final String finalAgentToken = agentToken;

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

        // 添加关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Agent shutting down...");
            
            // 主动向服务器报告离线状态
            try {
                if (currentAgentId[0] != null && currentAgentToken[0] != null) {
                    logger.info("Notifying server of agent shutdown...");
                    api.offline(currentAgentId[0], currentAgentToken[0]);
                    logger.info("Server notified successfully");
                }
            } catch (Exception e) {
                logger.warn("Failed to notify server of shutdown: {}", e.getMessage());
            }
            
            taskRunner.shutdown();
            taskExecutor.shutdown();
            upgradeScheduler.shutdown();
            try {
                if (client != null) {
                    client.close();
                }
            } catch (Exception e) {
                // 忽略关闭错误，避免在关闭过程中抛出异常
                logger.warn("Warning: Error closing HTTP client: {}", e.getMessage());
            }
            releaseLock(); // 释放文件锁
        }));

        logger.info("Agent started. Waiting for tasks...");

        // 主循环
        long lastHeartbeat = 0L;
        final long[] lastSystemInfoHeartbeat = {0L}; // 上次发送系统信息心跳的时间
        int heartbeatFailures = 0;
        final int MAX_HEARTBEAT_FAILURES = config.getMaxHeartbeatFailures(); // 从配置读取
        boolean reRegistered = false; // 是否刚刚重新注册
        
        while (!Thread.currentThread().isInterrupted()) {
            long now = System.currentTimeMillis();

            try {
                // 检查是否需要重新注册
                if (needReRegister[0]) {
                    logger.info("========================================");
                    logger.info("Connection lost. Re-registering agent...");
                    logger.info("========================================");
                    
                    retryCount = 0;
                    retryDelay = 1000;
                    
                    while (!reRegistered && !Thread.currentThread().isInterrupted()) {
                        try {
                            retryCount++;
                            logger.info("Re-registration attempt {}...", retryCount);
                            Map<String, Object> reg = api.register(registerToken, hostname, osType);
                            currentAgentId[0] = String.valueOf(reg.get("agentId"));
                            currentAgentToken[0] = String.valueOf(reg.get("agentToken"));
                            
                            // 更新任务执行器的凭证
                            taskRunner.updateCredentials(currentAgentId[0], currentAgentToken[0]);
                            
                            // 更新升级报告器的凭证
                            upgradeReporter = new UpgradeStatusReporter(server, client, MAPPER, currentAgentId[0], currentAgentToken[0]);
                            upgradeExecutor = new UpgradeExecutor(upgradeReporter, taskStatusMonitor, upgradeScheduler, server, currentAgentId[0], currentAgentToken[0]);
                            
                            logger.info("Agent re-registered successfully!");
                            logger.info("New Agent ID: {}", currentAgentId[0]);
                            logger.info("New Agent Token: {}", currentAgentToken[0]);
                            logger.info("========================================");
                            
                            needReRegister[0] = false;
                            heartbeatFailures = 0;
                            lastHeartbeat = 0; // 立即发送心跳
                            reRegistered = true;
                            
                        } catch (Exception e) {
                            logger.error("Re-registration failed: {}", e.getMessage());
                            logger.info("Retrying in {} seconds...", (retryDelay / 1000));
                            
                            Thread.sleep(retryDelay);
                            
                            // 指数退避
                            if (retryDelay < 2000) {
                                retryDelay = 2000;
                            } else if (retryDelay < 5000) {
                                retryDelay = 5000;
                            } else if (retryDelay < 10000) {
                                retryDelay = 10000;
                            } else {
                                retryDelay = 30000;
                            }
                        }
                    }
                }
                
                // 心跳检测 - 使用配置的间隔时间
                if (now - lastHeartbeat > config.getHeartbeatInterval()) {
                    try {
                        logger.debug("Sending heartbeat...");
                        // 使用配置的系统信息间隔时间
                        boolean includeSystemInfo = (now - lastSystemInfoHeartbeat[0] > config.getSystemInfoInterval()) || reRegistered;
                        
                        Map<String, Object> heartbeatResponse;
                        if (includeSystemInfo) {
                            heartbeatResponse = api.heartbeat(currentAgentId[0], currentAgentToken[0], true);
                            lastSystemInfoHeartbeat[0] = now;
                            logger.info("Heartbeat with system info sent at {}", new java.util.Date());
                        } else {
                            heartbeatResponse = api.heartbeat(currentAgentId[0], currentAgentToken[0], false);
                            logger.debug("Heartbeat sent at {}", new java.util.Date());
                        }
                        
                        // 处理心跳响应中的版本检查信息
                        handleHeartbeatResponse(heartbeatResponse, upgradeExecutor, api, currentAgentId[0], currentAgentToken[0], taskStatusMonitor);
                        
                        lastHeartbeat = now;
                        heartbeatFailures = 0; // 重置失败计数
                        reRegistered = false; // 重置重新注册标志
                    } catch (Exception e) {
                        heartbeatFailures++;
                        System.err.println("Heartbeat failed (" + heartbeatFailures + "/" + MAX_HEARTBEAT_FAILURES + "): " + e.getMessage());
                        
                        if (heartbeatFailures >= MAX_HEARTBEAT_FAILURES) {
                            System.err.println("Max heartbeat failures reached. Triggering re-registration...");
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

                if (tasks != null && !tasks.isEmpty()) {
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
                        Boolean overwriteExisting = (Boolean) task.getOrDefault("overwriteExisting", false);
                        Boolean verifyChecksum = (Boolean) task.getOrDefault("verifyChecksum", true);

                        logger.info("Received task: {} (executionId: {}, type: {})", taskId, executionId, taskType);
                        
                        // 检查基本字段
                        if (taskId == null || "null".equals(taskId) || executionId == null) {
                            System.err.println("ERROR: Invalid task data - taskId or executionId is null");
                            continue;
                        }
                        
                        // 根据任务类型检查必要字段
                        if ("FILE_TRANSFER".equals(taskType)) {
                            if (fileId == null || "null".equals(fileId) || targetPath == null || "null".equals(targetPath)) {
                                System.err.println("ERROR: Invalid file transfer task - fileId or targetPath is null");
                                continue;
                            }
                        } else {
                            if (scriptContent == null || "null".equals(scriptContent)) {
                                System.err.println("ERROR: Invalid script task - scriptContent is null");
                                continue;
                            }
                        }
                        
                        // 异步执行任务
                        final Long finalExecutionId = executionId;
                        final String finalTaskType = taskType;
                        final String finalFileId = fileId;
                        final String finalTargetPath = targetPath;
                        final boolean finalOverwriteExisting = overwriteExisting;
                        final boolean finalVerifyChecksum = verifyChecksum;
                        
                        taskExecutor.submit(() -> {
                            taskRunner.runTask(finalExecutionId, taskId, finalTaskType, scriptLang, scriptContent, 
                                             timeoutSec, finalFileId, finalTargetPath, finalOverwriteExisting, finalVerifyChecksum);
                        });
                    }
                }

                // 短暂休眠避免过度轮询 - 使用配置的间隔时间
                Thread.sleep(config.getTaskPullInterval());

            } catch (InterruptedException e) {
                logger.info("Agent interrupted, shutting down...");
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("Error in main loop: " + e.getMessage());
                
                // 检查是否是认证相关错误
                if (e.getMessage() != null && 
                    (e.getMessage().contains("400") || 
                     e.getMessage().contains("401") || 
                     e.getMessage().contains("403") ||
                     e.getMessage().contains("token"))) {
                    System.err.println("Authentication error detected. Triggering re-registration...");
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
                    e.printStackTrace();
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
                                               AgentApi agentApi, String agentId, String agentToken, TaskStatusMonitor taskStatusMonitor) {
        if (response == null || response.isEmpty()) {
            return;
        }
        
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