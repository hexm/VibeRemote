package com.example.lightscript.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.FileInputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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
        runTask(executionId, taskId, "SCRIPT", scriptLang, scriptContent, timeoutSec, null, null, null, null, false, true, null, null, null);
    }

    void runTask(Long executionId, String taskId, String taskType, String scriptLang, String scriptContent,
                 int timeoutSec, String fileId, String targetPath, String sourcePath, Long maxUploadSizeBytes,
                 boolean overwriteExisting, boolean verifyChecksum, Long logCollectionId, Long logFileId,
                 String relativePath) {
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
        } else if ("FILE_UPLOAD".equals(taskType)) {
            logger.info("[TASK-{}] Source Path: {}", executionId, sourcePath);
            logger.info("[TASK-{}] Max Upload Size: {} bytes", executionId, maxUploadSizeBytes);
            runFileUploadTask(executionId, taskId, sourcePath, timeoutSec, maxUploadSizeBytes);
        } else if ("AGENT_LOG_INDEX".equals(taskType)) {
            logger.info("[TASK-{}] Log Collection ID: {}", executionId, logCollectionId);
            runAgentLogIndexTask(executionId, logCollectionId);
        } else if ("AGENT_LOG_UPLOAD".equals(taskType)) {
            logger.info("[TASK-{}] Log Collection ID: {}, Log File ID: {}, Relative Path: {}",
                executionId, logCollectionId, logFileId, relativePath);
            runAgentLogUploadTask(executionId, logCollectionId, logFileId, relativePath, maxUploadSizeBytes);
        } else {
            logger.info("[TASK-{}] Script Language: {}", executionId, scriptLang);
            logger.info("[TASK-{}] Script Content Length: {} chars", executionId, scriptContent != null ? scriptContent.length() : 0);
            logger.info("[TASK-{}] Timeout: {} seconds", executionId, timeoutSec);
            runScriptTask(executionId, taskId, scriptLang, scriptContent, timeoutSec);
        }
    }

    private void runAgentLogIndexTask(Long executionId, Long logCollectionId) {
        LogBuffer taskBuffer = createTaskLogBuffer(executionId);

        try {
            taskStatusMonitor.onTaskStart(executionId);
            synchronized (this) {
                api.ackTask(agentId, agentToken, executionId);
            }

            Path logDir = resolveLogDirectory();
            sendLog(executionId, "system", "Scanning agent log directory: " + logDir.toAbsolutePath(), taskBuffer);

            List<Map<String, Object>> files = new ArrayList<>();
            if (Files.exists(logDir)) {
                try (java.util.stream.Stream<Path> stream = Files.walk(logDir)) {
                    stream.filter(Files::isRegularFile)
                        .sorted()
                        .forEach(path -> files.add(toManifestItem(logDir, path)));
                }
            }

            synchronized (this) {
                api.submitLogManifest(agentId, agentToken, executionId, logCollectionId, files);
                api.finish(agentId, agentToken, executionId, 0, "SUCCESS", "Collected " + files.size() + " log files");
            }
            sendLog(executionId, "system", "Log manifest submitted, file count: " + files.size(), taskBuffer);
        } catch (Exception e) {
            logger.error("[TASK-{}] Failed to collect agent logs: {}", executionId, e.getMessage(), e);
            try {
                sendLog(executionId, "stderr", "Collect agent logs failed: " + e.getMessage(), taskBuffer);
                synchronized (this) {
                    api.finish(agentId, agentToken, executionId, -2, "FAILED", e.toString());
                }
            } catch (Exception ignored) {
                logger.error("[TASK-{}] Failed to report agent log index error", executionId, ignored);
            }
        } finally {
            flushTaskBuffer(executionId, taskBuffer);
            onTaskComplete(executionId);
            taskStatusMonitor.onTaskComplete(executionId);
        }
    }

    private void runAgentLogUploadTask(Long executionId, Long logCollectionId, Long logFileId,
                                       String relativePath, Long maxUploadSizeBytes) {
        LogBuffer taskBuffer = createTaskLogBuffer(executionId);

        try {
            taskStatusMonitor.onTaskStart(executionId);
            synchronized (this) {
                api.ackTask(agentId, agentToken, executionId);
            }

            Path logFilePath = resolveLogDirectory().resolve(relativePath).normalize();
            if (!logFilePath.startsWith(resolveLogDirectory())) {
                throw new RuntimeException("Invalid log file path: " + relativePath);
            }
            sendLog(executionId, "system", "Uploading agent log file: " + logFilePath, taskBuffer);

            if (!Files.exists(logFilePath) || !Files.isRegularFile(logFilePath)) {
                throw new RuntimeException("Log file not found: " + logFilePath);
            }
            if (maxUploadSizeBytes != null && maxUploadSizeBytes > 0 && Files.size(logFilePath) > maxUploadSizeBytes) {
                throw new RuntimeException("Log file exceeds upload limit: " + Files.size(logFilePath) + " bytes");
            }

            String storedPath;
            synchronized (this) {
                storedPath = api.uploadArtifact(agentId, agentToken, executionId, logFilePath.toFile());
                api.finish(agentId, agentToken, executionId, 0, "SUCCESS",
                    "Uploaded log file " + relativePath + " to " + storedPath);
            }
            sendLog(executionId, "system", "Log file uploaded successfully: " + storedPath, taskBuffer);
        } catch (Exception e) {
            logger.error("[TASK-{}] Failed to upload agent log file (collectionId={}, logFileId={}): {}",
                executionId, logCollectionId, logFileId, e.getMessage(), e);
            try {
                sendLog(executionId, "stderr", "Upload agent log failed: " + e.getMessage(), taskBuffer);
                synchronized (this) {
                    api.finish(agentId, agentToken, executionId, -2, "FAILED", e.toString());
                }
            } catch (Exception ignored) {
                logger.error("[TASK-{}] Failed to report agent log upload error", executionId, ignored);
            }
        } finally {
            flushTaskBuffer(executionId, taskBuffer);
            onTaskComplete(executionId);
            taskStatusMonitor.onTaskComplete(executionId);
        }
    }

    private void runFileUploadTask(Long executionId, String taskId, String sourcePath, int timeoutSec, Long maxUploadSizeBytes) {
        LogBuffer taskBuffer = createTaskLogBuffer(executionId);

        try {
            logger.info("[TASK-{}] Starting file upload task", executionId);
            taskStatusMonitor.onTaskStart(executionId);
            synchronized (this) {
                api.ackTask(agentId, agentToken, executionId);
            }
            sendLog(executionId, "system", "File upload task started (source: " + sourcePath + ")", taskBuffer);

            File source = new File(sourcePath);
            if (!source.exists()) {
                throw new RuntimeException("Source path not found: " + sourcePath);
            }

            File archiveFile = createArchive(source, executionId);
            sendLog(executionId, "system", "Archive created: " + archiveFile.getName(), taskBuffer);
            sendLog(executionId, "system", "Archive size: " + archiveFile.length() + " bytes", taskBuffer);

            if (maxUploadSizeBytes != null && maxUploadSizeBytes > 0 && archiveFile.length() > maxUploadSizeBytes) {
                throw new RuntimeException("Archive size exceeds limit: " + archiveFile.length() +
                    " bytes > " + maxUploadSizeBytes + " bytes");
            }

            String storedPath;
            synchronized (this) {
                storedPath = api.uploadArtifact(agentId, agentToken, executionId, archiveFile);
                api.finish(agentId, agentToken, executionId, 0, "SUCCESS",
                    "File uploaded successfully to " + storedPath);
            }
            sendLog(executionId, "system", "File uploaded successfully: " + storedPath, taskBuffer);
            logger.info("[TASK-{}] ✓ File upload completed successfully", executionId);

            if (!archiveFile.delete()) {
                logger.warn("[TASK-{}] Failed to delete temp archive: {}", executionId, archiveFile.getAbsolutePath());
            }
        } catch (Exception e) {
            logger.error("[TASK-{}] ✗ Exception during file upload: {}", executionId, e.getMessage(), e);
            try {
                sendLog(executionId, "stderr", "Exception: " + e.getMessage(), taskBuffer);
                synchronized (this) {
                    api.finish(agentId, agentToken, executionId, -2, "FAILED", e.toString());
                }
            } catch (Exception ignored) {
                logger.error("[TASK-{}] ✗ Failed to report upload task failure: {}", executionId, ignored.getMessage());
            }
        } finally {
            flushTaskBuffer(executionId, taskBuffer);
            onTaskComplete(executionId);
            taskStatusMonitor.onTaskComplete(executionId);
            logger.info("[TASK-{}] File upload task completed", executionId);
        }
    }

    private File createArchive(File source, Long executionId) throws IOException {
        String baseName = source.getName().isEmpty() ? "upload" : source.getName();
        File archive = File.createTempFile("vr_upload_" + executionId + "_", ".zip");

        try (ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(archive))) {
            Path sourcePath = source.toPath();
            if (source.isDirectory()) {
                try (java.util.stream.Stream<Path> paths = Files.walk(sourcePath)) {
                    paths.filter(path -> !Files.isDirectory(path))
                        .forEach(path -> writeZipEntry(zipOutputStream, sourcePath, path, baseName));
                }
            } else {
                writeZipEntry(zipOutputStream, sourcePath.getParent(), sourcePath, "");
            }
        }
        return archive;
    }

    private Path resolveLogDirectory() {
        String logHome = System.getProperty("log.home");
        if (logHome == null || logHome.trim().isEmpty()) {
            logHome = "./logs";
        }
        return Paths.get(logHome).toAbsolutePath().normalize();
    }

    private Map<String, Object> toManifestItem(Path logDir, Path filePath) {
        try {
            Map<String, Object> item = new HashMap<>();
            Path relative = logDir.relativize(filePath);
            FileTime lastModified = Files.getLastModifiedTime(filePath);
            item.put("fileName", filePath.getFileName().toString());
            item.put("relativePath", relative.toString().replace('\\', '/'));
            item.put("fileSize", Files.size(filePath));
            item.put("modifiedAt", Instant.ofEpochMilli(lastModified.toMillis()).toString());
            return item;
        } catch (IOException e) {
            throw new RuntimeException("Read log file metadata failed: " + filePath, e);
        }
    }

    private void writeZipEntry(ZipOutputStream zipOutputStream, Path rootPath, Path filePath, String prefix) {
        try (FileInputStream inputStream = new FileInputStream(filePath.toFile())) {
            Path relativePath = rootPath != null ? rootPath.relativize(filePath) : Paths.get(filePath.getFileName().toString());
            String entryName = prefix == null || prefix.isEmpty()
                ? relativePath.toString()
                : Paths.get(prefix).resolve(relativePath).toString();
            entryName = entryName.replace("\\", "/");
            zipOutputStream.putNextEntry(new ZipEntry(entryName));

            byte[] buffer = new byte[16 * 1024];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                zipOutputStream.write(buffer, 0, len);
            }
            zipOutputStream.closeEntry();
        } catch (IOException e) {
            throw new RuntimeException("Failed to add zip entry for " + filePath + ": " + e.getMessage(), e);
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
            File tempScriptFile = null;
            if (isWindows()) {
                if ("powershell".equalsIgnoreCase(scriptLang)) {
                    tempScriptFile = File.createTempFile("vr_task_", ".ps1");
                    writeScriptFile(tempScriptFile, scriptContent, "UTF-8");
                    pb = new ProcessBuilder("powershell", "-ExecutionPolicy", "Bypass", "-File", tempScriptFile.getAbsolutePath());
                    logger.info("[TASK-{}] Using PowerShell executor (temp file: {})", executionId, tempScriptFile.getAbsolutePath());
                } else {
                    tempScriptFile = File.createTempFile("vr_task_", ".bat");
                    writeScriptFile(tempScriptFile, scriptContent, "GBK");
                    pb = new ProcessBuilder("cmd", "/c", tempScriptFile.getAbsolutePath());
                    logger.info("[TASK-{}] Using CMD executor (temp file: {})", executionId, tempScriptFile.getAbsolutePath());
                }
            } else {
                pb = new ProcessBuilder("bash", "-c", scriptContent);
                logger.info("[TASK-{}] Using Bash executor", executionId);
            }

            final File tempFileToDelete = tempScriptFile;
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
            if (tempFileToDelete != null) {
                tempFileToDelete.delete();
            }

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

    private void writeScriptFile(File file, String content, String charset) throws IOException {
        OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), Charset.forName(charset));
        try {
            writer.write(content);
        } finally {
            writer.close();
        }
    }
}
