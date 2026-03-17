package com.example.lightscript.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

class SimpleTaskRunner {
    private static final Logger logger = LoggerFactory.getLogger(SimpleTaskRunner.class);
    
    private final AgentApi api;
    private volatile String agentId;
    private volatile String agentToken;
    private volatile boolean shutdown = false;
    private final TaskStatusMonitor taskStatusMonitor;
    private final RobustBatchLogCollector robustBatchLogCollector;
    private final EncryptedBatchLogCollector encryptedBatchLogCollector;
    private final BackupLogUploader backupLogUploader;
    private final boolean batchModeEnabled;
    private final boolean encryptionEnabled;

    SimpleTaskRunner(AgentApi api, String agentId, String agentToken, TaskStatusMonitor taskStatusMonitor) {
        this.api = api;
        this.agentId = agentId;
        this.agentToken = agentToken;
        this.taskStatusMonitor = taskStatusMonitor;
        
        logger.info("========================================");
        logger.info("TASK RUNNER INITIALIZATION");
        logger.info("========================================");
        
        // 检查是否启用加密
        AgentConfig config = AgentConfig.getInstance();
        this.encryptionEnabled = config.isEncryptionEnabled();

        // 初始化批量日志收集器 - 总是使用明文传输，因为服务端不支持加密
        this.robustBatchLogCollector = new RobustBatchLogCollector(
            api.getBaseUrl(), api.getHttpClient(), api.getObjectMapper());
        this.robustBatchLogCollector.updateCredentials(agentId, agentToken);
        this.encryptedBatchLogCollector = null;
        logger.info("✓ Robust batch log collector initialized (supports retry, local backup and periodic upload)");

        this.backupLogUploader = new BackupLogUploader(api.getBaseUrl(), api.getHttpClient(), api.getObjectMapper());
        this.backupLogUploader.updateCredentials(agentId, agentToken);
        logger.info("✓ Backup log uploader initialized");

        this.batchModeEnabled = true;
        logger.info("Agent ID: {}", agentId);
        logger.info("Encryption enabled: {}", encryptionEnabled);
        logger.info("Batch mode enabled: {}", batchModeEnabled);
        logger.info("========================================");
    }

    void shutdown() {
        logger.info("========================================");
        logger.info("TASK RUNNER SHUTDOWN");
        logger.info("========================================");
        this.shutdown = true;
        if (robustBatchLogCollector != null) {
            logger.info("Shutting down robust batch log collector...");
            robustBatchLogCollector.shutdown();
        }
        if (encryptedBatchLogCollector != null) {
            logger.info("Shutting down encrypted batch log collector...");
            encryptedBatchLogCollector.shutdown();
        }
        if (backupLogUploader != null) {
            logger.info("Shutting down backup log uploader...");
            backupLogUploader.shutdown();
        }
        logger.info("✓ Task runner shutdown complete");
        logger.info("========================================");
    }

    synchronized void updateCredentials(String newAgentId, String newAgentToken) {
        logger.info("========================================");
        logger.info("UPDATING TASK RUNNER CREDENTIALS");
        logger.info("========================================");
        logger.info("Old Agent ID: {}", this.agentId);
        logger.info("New Agent ID: {}", newAgentId);
        
        this.agentId = newAgentId;
        this.agentToken = newAgentToken;
        
        if (robustBatchLogCollector != null) {
            logger.info("Updating robust batch log collector credentials...");
            robustBatchLogCollector.updateCredentials(newAgentId, newAgentToken);
        }
        if (encryptedBatchLogCollector != null) {
            logger.info("Updating encrypted batch log collector credentials...");
            encryptedBatchLogCollector.updateCredentials(newAgentId, newAgentToken);
        }
        if (backupLogUploader != null) {
            logger.info("Updating backup log uploader credentials...");
            backupLogUploader.updateCredentials(newAgentId, newAgentToken);
        }
        logger.info("✓ Task runner credentials updated successfully");
        logger.info("========================================");
    }

    private LogBuffer createTaskLogBuffer(Long executionId) {
        LogBuffer taskBuffer = new LogBuffer();
        logger.info("[TASK-{}] ✓ Created independent log buffer", executionId);
        return taskBuffer;
    }

    private void sendLog(Long executionId, String stream, String data, LogBuffer taskBuffer) {
        if (batchModeEnabled && taskBuffer != null) {
            long timestamp = System.currentTimeMillis();
            LogEntry entry = new LogEntry(0, stream, data, timestamp);
            taskBuffer.addLogEntry(entry);

            if (taskBuffer.shouldFlush()) {
                flushTaskBuffer(executionId, taskBuffer);
            }
        } else {
            try {
                // 使用最新的凭证
                synchronized (this) {
                    api.sendLog(agentId, agentToken, executionId, 0, stream, data);
                }
            } catch (Exception e) {
                logger.error("Failed to send log: {}", e.getMessage());
            }
        }
    }

    private void flushTaskBuffer(Long executionId, LogBuffer taskBuffer) {
        if (taskBuffer != null && taskBuffer.hasLogs()) {
            List<LogEntry> logs = taskBuffer.flush();
            if (!logs.isEmpty()) {
                logger.debug("[TASK-{}] Flushing log buffer with {} entries", executionId, logs.size());
                // 确保使用最新的凭证
                synchronized (this) {
                    if (encryptionEnabled && encryptedBatchLogCollector != null) {
                        encryptedBatchLogCollector.sendBatch(executionId, logs);
                    } else if (robustBatchLogCollector != null) {
                        robustBatchLogCollector.sendBatch(executionId, logs);
                    }
                }
                logger.debug("[TASK-{}] ✓ Log buffer flushed successfully", executionId);
            }
        }
    }

    void runTask(Long executionId, String taskId, String scriptLang, String scriptContent, int timeoutSec) {
        runTask(executionId, taskId, "SCRIPT", scriptLang, scriptContent, timeoutSec, null, null, false, true);
    }

    void runTask(Long executionId, String taskId, String taskType, String scriptLang, String scriptContent,
                 int timeoutSec, String fileId, String targetPath, boolean overwriteExisting, boolean verifyChecksum) {
        logger.info("========================================");
        logger.info("[TASK-{}] TASK EXECUTION STARTED", executionId);
        logger.info("========================================");
        logger.info("[TASK-{}] Task ID: {}", executionId, taskId);
        logger.info("[TASK-{}] Task Type: {}", executionId, taskType);
        
        if ("FILE_TRANSFER".equals(taskType)) {
            logger.info("[TASK-{}] File ID: {}", executionId, fileId);
            logger.info("[TASK-{}] Target Path: {}", executionId, targetPath);
            logger.info("[TASK-{}] Overwrite Existing: {}", executionId, overwriteExisting);
            logger.info("[TASK-{}] Verify Checksum: {}", executionId, verifyChecksum);
            runFileTransferTask(executionId, taskId, fileId, targetPath, timeoutSec, overwriteExisting, verifyChecksum);
        } else {
            logger.info("[TASK-{}] Script Language: {}", executionId, scriptLang);
            logger.info("[TASK-{}] Script Content Length: {} chars", executionId, scriptContent != null ? scriptContent.length() : 0);
            logger.info("[TASK-{}] Timeout: {} seconds", executionId, timeoutSec);
            runScriptTask(executionId, taskId, scriptLang, scriptContent, timeoutSec);
        }
    }

    private void runFileTransferTask(Long executionId, String taskId, String fileId, String targetPath,
                                   int timeoutSec, boolean overwriteExisting, boolean verifyChecksum) {
        LogBuffer taskBuffer = createTaskLogBuffer(executionId);

        try {
            logger.info("[TASK-{}] Starting file transfer task", executionId);
            taskStatusMonitor.onTaskStart(executionId);
            synchronized (this) {
                api.ackTask(agentId, agentToken, executionId);
            }
            sendLog(executionId, "system", "File transfer task started (fileId: " + fileId + ", target: " + targetPath + ")", taskBuffer);

            logger.info("[TASK-{}] Downloading file from server...", executionId);
            sendLog(executionId, "system", "Downloading file from server...", taskBuffer);
            synchronized (this) {
                boolean success = api.downloadFile(agentId, agentToken, fileId, targetPath, overwriteExisting, verifyChecksum);

                if (success) {
                    logger.info("[TASK-{}] ✓ File transfer completed successfully", executionId);
                    sendLog(executionId, "system", "File transfer completed successfully", taskBuffer);
                    api.finish(agentId, agentToken, executionId, 0, "SUCCESS", "File transferred successfully");
                } else {
                    logger.error("[TASK-{}] ✗ File transfer failed", executionId);
                    sendLog(executionId, "system", "File transfer failed", taskBuffer);
                    api.finish(agentId, agentToken, executionId, 1, "FAILED", "File transfer failed");
                }
            }

        } catch (Exception e) {
            logger.error("[TASK-{}] ✗ Exception during file transfer: {}", executionId, e.getMessage(), e);
            try {
                sendLog(executionId, "stderr", "Exception: " + e.getMessage(), taskBuffer);
                synchronized (this) {
                    api.finish(agentId, agentToken, executionId, -2, "FAILED", e.toString());
                }
            } catch (Exception ignored) {
                logger.error("[TASK-{}] ✗ Failed to report task failure: {}", executionId, ignored.getMessage());
            }
        } finally {
            flushTaskBuffer(executionId, taskBuffer);
            onTaskComplete(executionId);
            taskStatusMonitor.onTaskComplete(executionId);
            logger.info("[TASK-{}] File transfer task completed", executionId);
        }
    }

    private void runScriptTask(Long executionId, String taskId, String scriptLang, String scriptContent, int timeoutSec) {
        logger.info("[TASK-{}] Starting script task execution", executionId);
        LogBuffer taskBuffer = createTaskLogBuffer(executionId);

        try {
            logger.info("[TASK-{}] Initializing task execution", executionId);
            taskStatusMonitor.onTaskStart(executionId);
            synchronized (this) {
                api.ackTask(agentId, agentToken, executionId);
            }
            sendLog(executionId, "system", "Task started (lang: " + scriptLang + ")", taskBuffer);

            ProcessBuilder pb;
            if (isWindows()) {
                if ("powershell".equalsIgnoreCase(scriptLang)) {
                    pb = new ProcessBuilder("powershell", "-Command", scriptContent);
                    logger.info("[TASK-{}] Using PowerShell executor", executionId);
                } else {
                    pb = new ProcessBuilder("cmd", "/c", scriptContent);
                    logger.info("[TASK-{}] Using CMD executor", executionId);
                }
            } else {
                pb = new ProcessBuilder("bash", "-c", scriptContent);
                logger.info("[TASK-{}] Using Bash executor", executionId);
            }

            pb.redirectErrorStream(true);
            logger.info("[TASK-{}] Starting process with timeout: {} seconds", executionId, timeoutSec);
            Process p = pb.start();

            String charset = isWindows() ? "GBK" : "UTF-8";
            logger.debug("[TASK-{}] Using charset: {}", executionId, charset);

            Thread logReaderThread = new Thread(() -> {
                try {
                    logger.debug("[TASK-{}] Log reader thread started", executionId);
                    SequentialLogReader.readMergedLogs(p, charset, (stream, data) -> {
                        sendLog(executionId, stream, data, taskBuffer);
                    });
                    logger.debug("[TASK-{}] Log reader thread completed", executionId);
                } catch (IOException e) {
                    logger.error("[TASK-{}] ✗ Error reading logs: {}", executionId, e.getMessage());
                }
            }, "LogReader-" + executionId);

            logReaderThread.start();

            boolean finished = p.waitFor(timeoutSec, TimeUnit.SECONDS);

            if (!finished) {
                logger.warn("[TASK-{}] ✗ Process timeout after {} seconds", executionId, timeoutSec);
                p.destroyForcibly();
                sendLog(executionId, "system", "Process timeout after " + timeoutSec + " seconds", taskBuffer);
                synchronized (this) {
                    api.finish(agentId, agentToken, executionId, -1, "TIMEOUT", "Process timeout");
                }
            } else {
                int exitCode = p.exitValue();
                String status = exitCode == 0 ? "SUCCESS" : "FAILED";
                logger.info("[TASK-{}] ✓ Process finished with exit code: {} ({})", executionId, exitCode, status);
                sendLog(executionId, "system", "Process finished with exit code: " + exitCode, taskBuffer);
                synchronized (this) {
                    api.finish(agentId, agentToken, executionId, exitCode, status, "exitCode=" + exitCode);
                }
            }

            logReaderThread.join(2000);

        } catch (Exception e) {
            logger.error("[TASK-{}] ✗ Exception during script execution: {}", executionId, e.getMessage(), e);
            try {
                sendLog(executionId, "stderr", "Exception: " + e.getMessage(), taskBuffer);
                synchronized (this) {
                    api.finish(agentId, agentToken, executionId, -2, "FAILED", e.toString());
                }
            } catch (Exception ignored) {
                logger.error("[TASK-{}] ✗ Failed to report task failure: {}", executionId, ignored.getMessage());
            }
        } finally {
            flushTaskBuffer(executionId, taskBuffer);
            onTaskComplete(executionId);
            taskStatusMonitor.onTaskComplete(executionId);
            logger.info("[TASK-{}] Script task execution completed", executionId);
        }
    }

    private void onTaskComplete(Long executionId) {
        if (encryptionEnabled && encryptedBatchLogCollector != null) {
            // EncryptedBatchLogCollector 没有 onTaskComplete 方法，因为它是自动管理的
        } else if (robustBatchLogCollector != null) {
            robustBatchLogCollector.onTaskComplete(executionId);
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}