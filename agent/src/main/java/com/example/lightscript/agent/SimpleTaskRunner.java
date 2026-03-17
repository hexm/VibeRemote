package com.example.lightscript.agent;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

class SimpleTaskRunner {
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
        
        // 检查是否启用加密
        AgentConfig config = AgentConfig.getInstance();
        this.encryptionEnabled = config.isEncryptionEnabled();

        // 初始化批量日志收集器
        if (encryptionEnabled) {
            this.encryptedBatchLogCollector = new EncryptedBatchLogCollector(
                api.getBaseUrl(), api.getHttpClient(), api.getObjectMapper(), true, api);
            this.encryptedBatchLogCollector.updateCredentials(agentId, agentToken);
            this.robustBatchLogCollector = null;
            System.out.println("[TaskRunner] 加密批量日志模式已启用");
        } else {
            this.robustBatchLogCollector = new RobustBatchLogCollector(
                api.getBaseUrl(), api.getHttpClient(), api.getObjectMapper());
            this.robustBatchLogCollector.updateCredentials(agentId, agentToken);
            this.encryptedBatchLogCollector = null;
            System.out.println("[TaskRunner] 健壮批量日志模式已启用 (支持重试、本地备份和定期补传)");
        }

        this.backupLogUploader = new BackupLogUploader(api.getBaseUrl(), api.getHttpClient(), api.getObjectMapper());
        this.backupLogUploader.updateCredentials(agentId, agentToken);

        this.batchModeEnabled = true;
    }

    void shutdown() {
        this.shutdown = true;
        if (robustBatchLogCollector != null) {
            robustBatchLogCollector.shutdown();
        }
        if (encryptedBatchLogCollector != null) {
            encryptedBatchLogCollector.shutdown();
        }
        if (backupLogUploader != null) {
            backupLogUploader.shutdown();
        }
    }

    synchronized void updateCredentials(String newAgentId, String newAgentToken) {
        this.agentId = newAgentId;
        this.agentToken = newAgentToken;
        if (robustBatchLogCollector != null) {
            robustBatchLogCollector.updateCredentials(newAgentId, newAgentToken);
        }
        if (encryptedBatchLogCollector != null) {
            encryptedBatchLogCollector.updateCredentials(newAgentId, newAgentToken);
        }
        if (backupLogUploader != null) {
            backupLogUploader.updateCredentials(newAgentId, newAgentToken);
        }
        System.out.println("[TaskRunner] Credentials updated");
    }

    private LogBuffer createTaskLogBuffer(Long executionId) {
        LogBuffer taskBuffer = new LogBuffer();
        System.out.println("[TaskRunner] 为任务 " + executionId + " 创建独立日志缓冲区");
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
                api.sendLog(agentId, agentToken, executionId, 0, stream, data);
            } catch (Exception e) {
                System.err.println("Failed to send log: " + e.getMessage());
            }
        }
    }

    private void flushTaskBuffer(Long executionId, LogBuffer taskBuffer) {
        if (taskBuffer != null && taskBuffer.hasLogs()) {
            List<LogEntry> logs = taskBuffer.flush();
            if (!logs.isEmpty()) {
                if (encryptionEnabled && encryptedBatchLogCollector != null) {
                    encryptedBatchLogCollector.sendBatch(executionId, logs);
                } else if (robustBatchLogCollector != null) {
                    robustBatchLogCollector.sendBatch(executionId, logs);
                }
            }
        }
    }

    void runTask(Long executionId, String taskId, String scriptLang, String scriptContent, int timeoutSec) {
        runTask(executionId, taskId, "SCRIPT", scriptLang, scriptContent, timeoutSec, null, null, false, true);
    }

    void runTask(Long executionId, String taskId, String taskType, String scriptLang, String scriptContent,
                 int timeoutSec, String fileId, String targetPath, boolean overwriteExisting, boolean verifyChecksum) {
        if ("FILE_TRANSFER".equals(taskType)) {
            runFileTransferTask(executionId, taskId, fileId, targetPath, timeoutSec, overwriteExisting, verifyChecksum);
        } else {
            runScriptTask(executionId, taskId, scriptLang, scriptContent, timeoutSec);
        }
    }

    private void runFileTransferTask(Long executionId, String taskId, String fileId, String targetPath,
                                   int timeoutSec, boolean overwriteExisting, boolean verifyChecksum) {
        LogBuffer taskBuffer = createTaskLogBuffer(executionId);

        try {
            taskStatusMonitor.onTaskStart(executionId);
            api.ackTask(agentId, agentToken, executionId);
            sendLog(executionId, "system", "File transfer task started (fileId: " + fileId + ", target: " + targetPath + ")", taskBuffer);

            sendLog(executionId, "system", "Downloading file from server...", taskBuffer);
            boolean success = api.downloadFile(agentId, agentToken, fileId, targetPath, overwriteExisting, verifyChecksum);

            if (success) {
                sendLog(executionId, "system", "File transfer completed successfully", taskBuffer);
                api.finish(agentId, agentToken, executionId, 0, "SUCCESS", "File transferred successfully");
            } else {
                sendLog(executionId, "system", "File transfer failed", taskBuffer);
                api.finish(agentId, agentToken, executionId, 1, "FAILED", "File transfer failed");
            }

        } catch (Exception e) {
            try {
                sendLog(executionId, "stderr", "Exception: " + e.getMessage(), taskBuffer);
                api.finish(agentId, agentToken, executionId, -2, "FAILED", e.toString());
            } catch (Exception ignored) {
                System.err.println("Failed to report task failure: " + ignored.getMessage());
            }
        } finally {
            flushTaskBuffer(executionId, taskBuffer);
            onTaskComplete(executionId);
            taskStatusMonitor.onTaskComplete(executionId);
        }
    }

    private void runScriptTask(Long executionId, String taskId, String scriptLang, String scriptContent, int timeoutSec) {
        LogBuffer taskBuffer = createTaskLogBuffer(executionId);

        try {
            taskStatusMonitor.onTaskStart(executionId);
            api.ackTask(agentId, agentToken, executionId);
            sendLog(executionId, "system", "Task started (lang: " + scriptLang + ")", taskBuffer);

            ProcessBuilder pb;
            if (isWindows()) {
                if ("powershell".equalsIgnoreCase(scriptLang)) {
                    pb = new ProcessBuilder("powershell", "-Command", scriptContent);
                } else {
                    pb = new ProcessBuilder("cmd", "/c", scriptContent);
                }
            } else {
                pb = new ProcessBuilder("bash", "-c", scriptContent);
            }

            pb.redirectErrorStream(true);
            Process p = pb.start();

            String charset = isWindows() ? "GBK" : "UTF-8";

            Thread logReaderThread = new Thread(() -> {
                try {
                    SequentialLogReader.readMergedLogs(p, charset, (stream, data) -> {
                        sendLog(executionId, stream, data, taskBuffer);
                    });
                } catch (IOException e) {
                    System.err.println("Error reading logs: " + e.getMessage());
                }
            }, "LogReader-" + executionId);

            logReaderThread.start();

            boolean finished = p.waitFor(timeoutSec, TimeUnit.SECONDS);

            if (!finished) {
                p.destroyForcibly();
                sendLog(executionId, "system", "Process timeout after " + timeoutSec + " seconds", taskBuffer);
                api.finish(agentId, agentToken, executionId, -1, "TIMEOUT", "Process timeout");
            } else {
                int exitCode = p.exitValue();
                String status = exitCode == 0 ? "SUCCESS" : "FAILED";
                sendLog(executionId, "system", "Process finished with exit code: " + exitCode, taskBuffer);
                api.finish(agentId, agentToken, executionId, exitCode, status, "exitCode=" + exitCode);
            }

            logReaderThread.join(2000);

        } catch (Exception e) {
            try {
                sendLog(executionId, "stderr", "Exception: " + e.getMessage(), taskBuffer);
                api.finish(agentId, agentToken, executionId, -2, "FAILED", e.toString());
            } catch (Exception ignored) {
                System.err.println("Failed to report task failure: " + ignored.getMessage());
            }
        } finally {
            flushTaskBuffer(executionId, taskBuffer);
            onTaskComplete(executionId);
            taskStatusMonitor.onTaskComplete(executionId);
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