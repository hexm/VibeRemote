package com.example.lightscript.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 备份日志上传器 - 简化版
 * 约定：通过检查日志中的 "[TASK_END]" 标记来判断任务是否完成
 */
public class BackupLogUploader {
    private final String baseUrl;
    private final CloseableHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService scheduler;
    private final String backupDir;
    
    private volatile String agentId;
    private volatile String agentToken;
    private volatile boolean shutdown = false;
    
    // 补传配置
    private final long checkIntervalMinutes = 5; // 每5分钟检查一次
    private final int maxRetryAttempts = 3;      // 每个文件最多重试3次
    private final long maxBackupAgeMs = 30L * 24 * 60 * 60 * 1000; // 30天过期时间
    
    // 任务结束标记 - 约定
    private static final String TASK_END_MARKER = "[TASK_END]";

    public BackupLogUploader(String baseUrl, CloseableHttpClient httpClient, ObjectMapper objectMapper) {
        this.baseUrl = baseUrl;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.backupDir = System.getProperty("user.home") + "/.lightscript/log-backup";
        
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "BackupLogUploader");
            t.setDaemon(true);
            return t;
        });
        
        // 启动定期检查
        startPeriodicUpload();
        
        // 启动时立即清理一次过期文件
        cleanupExpiredFiles();
        
        System.out.println("[BackupLogUploader] 定期补传服务已启动，每 " + checkIntervalMinutes + " 分钟检查一次");
        System.out.println("[BackupLogUploader] 任务结束标记: " + TASK_END_MARKER);
        System.out.println("[BackupLogUploader] 备份文件过期时间: " + (maxBackupAgeMs / (24 * 60 * 60 * 1000)) + " 天");
    }

    public void updateCredentials(String agentId, String agentToken) {
        this.agentId = agentId;
        this.agentToken = agentToken;
    }

    /**
     * 启动定期上传任务
     */
    private void startPeriodicUpload() {
        scheduler.scheduleWithFixedDelay(() -> {
            if (shutdown) return;
            
            try {
                checkAndUploadBackupFiles();
            } catch (Exception e) {
                System.err.println("[BackupLogUploader] 定期检查失败: " + e.getMessage());
            }
        }, checkIntervalMinutes, checkIntervalMinutes, TimeUnit.MINUTES);
    }

    /**
     * 检查并上传备份文件 - 增加30天过期清理
     */
    private void checkAndUploadBackupFiles() {
        File backupDirectory = new File(backupDir);
        if (!backupDirectory.exists() || !backupDirectory.isDirectory()) {
            return;
        }

        File[] backupFiles = backupDirectory.listFiles((dir, name) -> 
            (name.startsWith("failed_logs_") && name.endsWith(".json")) ||
            (name.startsWith("failed_logs_") && name.endsWith("_permanent_fail.json")));
        
        if (backupFiles == null || backupFiles.length == 0) {
            return;
        }

        System.out.println("[BackupLogUploader] 发现 " + backupFiles.length + " 个备份文件，检查过期和任务结束标记...");
        
        int processedCount = 0;
        int skippedCount = 0;
        int expiredCount = 0;
        long currentTime = System.currentTimeMillis();
        
        for (File backupFile : backupFiles) {
            try {
                // 检查文件是否过期（超过30天）
                long fileAge = currentTime - backupFile.lastModified();
                if (fileAge > maxBackupAgeMs) {
                    // 过期文件直接删除（包括永久失败文件）
                    if (backupFile.delete()) {
                        expiredCount++;
                        String fileType = backupFile.getName().contains("_permanent_fail") ? "永久失败" : "普通";
                        System.out.println("[BackupLogUploader] 删除过期" + fileType + "备份文件: " + backupFile.getName() + 
                            " (已过期 " + (fileAge / (24 * 60 * 60 * 1000)) + " 天)");
                    } else {
                        System.err.println("[BackupLogUploader] 删除过期文件失败: " + backupFile.getName());
                    }
                    continue;
                }
                
                // 跳过永久失败文件（不再尝试上传）
                if (backupFile.getName().contains("_permanent_fail")) {
                    continue;
                }
                
                if (hasTaskEndMarker(backupFile)) {
                    // 包含任务结束标记，可以补传
                    if (uploadBackupFile(backupFile)) {
                        processedCount++;
                    }
                } else {
                    // 没有任务结束标记，任务可能还在执行中
                    skippedCount++;
                    System.out.println("[BackupLogUploader] 跳过未完成任务的备份文件: " + backupFile.getName());
                }
            } catch (Exception e) {
                System.err.println("[BackupLogUploader] 处理备份文件失败: " + backupFile.getName() + ", " + e.getMessage());
            }
        }
        
        if (processedCount > 0 || skippedCount > 0 || expiredCount > 0) {
            System.out.println("[BackupLogUploader] 补传检查完成: 处理 " + processedCount + 
                " 个, 跳过 " + skippedCount + " 个, 删除过期 " + expiredCount + " 个");
        }
    }
    
    /**
     * 检查备份文件是否包含任务结束标记 - 核心方法
     */
    private boolean hasTaskEndMarker(File backupFile) {
        try {
            String jsonContent = new String(Files.readAllBytes(backupFile.toPath()));
            BatchLogRequest request = objectMapper.readValue(jsonContent, BatchLogRequest.class);
            
            // 检查日志中是否包含任务结束标记
            for (LogEntry log : request.getLogs()) {
                if (TASK_END_MARKER.equals(log.getData())) {
                    return true;
                }
            }
            
            return false;
        } catch (Exception e) {
            System.err.println("[BackupLogUploader] 检查任务结束标记失败: " + backupFile.getName() + ", " + e.getMessage());
            return false;
        }
    }

    /**
     * 上传单个备份文件
     */
    private boolean uploadBackupFile(File backupFile) throws IOException {
        if (agentId == null || agentToken == null) {
            System.err.println("[BackupLogUploader] Agent凭证未设置，跳过: " + backupFile.getName());
            return false;
        }

        // 读取备份文件内容
        String jsonContent = new String(Files.readAllBytes(backupFile.toPath()));
        BatchLogRequest request = objectMapper.readValue(jsonContent, BatchLogRequest.class);
        
        // 更新凭证（可能已经变化）
        request.setAgentId(agentId);
        request.setAgentToken(agentToken);
        
        try {
            // 尝试上传到服务器
            uploadToServer(request);
            
            // 上传成功，删除备份文件
            if (backupFile.delete()) {
                System.out.println("[BackupLogUploader] 补传成功并删除备份: " + backupFile.getName() + 
                    " (" + request.getLogs().size() + " 条日志)");
            } else {
                System.err.println("[BackupLogUploader] 补传成功但删除备份文件失败: " + backupFile.getName());
            }
            
            return true;
            
        } catch (Exception e) {
            // 上传失败，检查重试次数
            return handleUploadFailure(backupFile, e);
        }
    }

    /**
     * 处理上传失败
     */
    private boolean handleUploadFailure(File backupFile, Exception e) {
        String fileName = backupFile.getName();
        
        // 从文件名中提取重试次数
        int retryCount = extractRetryCount(fileName);
        
        if (retryCount >= maxRetryAttempts) {
            System.err.println("[BackupLogUploader] 备份文件超过最大重试次数，放弃: " + fileName + 
                " (重试 " + retryCount + " 次)");
            
            // 重命名为永久失败文件
            File permanentFailFile = new File(backupFile.getParent(), 
                fileName.replace(".json", "_permanent_fail.json"));
            backupFile.renameTo(permanentFailFile);
            
            return false;
        } else {
            // 增加重试次数并重命名文件
            String newFileName = incrementRetryCount(fileName, retryCount + 1);
            File newFile = new File(backupFile.getParent(), newFileName);
            backupFile.renameTo(newFile);
            
            System.err.println("[BackupLogUploader] 补传失败，将重试: " + newFileName + 
                " (第 " + (retryCount + 1) + " 次重试), 错误: " + e.getMessage());
            
            return false;
        }
    }

    /**
     * 从文件名中提取重试次数
     */
    private int extractRetryCount(String fileName) {
        if (fileName.contains("_retry_")) {
            try {
                String retryPart = fileName.substring(fileName.indexOf("_retry_") + 7);
                String retryCountStr = retryPart.substring(0, retryPart.indexOf("_"));
                return Integer.parseInt(retryCountStr);
            } catch (Exception e) {
                return 0;
            }
        }
        return 0;
    }

    /**
     * 增加重试次数到文件名
     */
    private String incrementRetryCount(String fileName, int newRetryCount) {
        if (fileName.contains("_retry_")) {
            // 替换现有的重试次数
            return fileName.replaceAll("_retry_\\d+_", "_retry_" + newRetryCount + "_");
        } else {
            // 添加重试次数
            return fileName.replace(".json", "_retry_" + newRetryCount + "_" + System.currentTimeMillis() + ".json");
        }
    }

    /**
     * 上传到服务器
     */
    private void uploadToServer(BatchLogRequest request) throws Exception {
        HttpPost post = new HttpPost(baseUrl + "/api/agent/tasks/executions/" + 
            request.getExecutionId() + "/batch-log");
        post.setHeader("Content-Type", "application/json");
        post.setHeader("X-Batch-Size", String.valueOf(request.getLogs().size()));
        post.setHeader("X-Backup-Upload", "true"); // 标记为备份补传
        post.setEntity(new StringEntity(objectMapper.writeValueAsString(request), "UTF-8"));
        
        try (CloseableHttpResponse response = httpClient.execute(post)) {
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != 200) {
                throw new RuntimeException("服务器返回错误状态: " + statusCode);
            }
        }
    }

    /**
     * 立即清理过期文件 - 启动时和手动调用
     */
    private void cleanupExpiredFiles() {
        File backupDirectory = new File(backupDir);
        if (!backupDirectory.exists() || !backupDirectory.isDirectory()) {
            return;
        }

        File[] allFiles = backupDirectory.listFiles((dir, name) -> 
            name.startsWith("failed_logs_"));
        
        if (allFiles == null || allFiles.length == 0) {
            return;
        }

        int expiredCount = 0;
        long currentTime = System.currentTimeMillis();
        
        for (File file : allFiles) {
            long fileAge = currentTime - file.lastModified();
            if (fileAge > maxBackupAgeMs) {
                if (file.delete()) {
                    expiredCount++;
                    String fileType = file.getName().contains("_permanent_fail") ? "永久失败" : "普通";
                    System.out.println("[BackupLogUploader] 清理过期" + fileType + "文件: " + file.getName() + 
                        " (已过期 " + (fileAge / (24 * 60 * 60 * 1000)) + " 天)");
                }
            }
        }
        
        if (expiredCount > 0) {
            System.out.println("[BackupLogUploader] 启动清理完成，删除 " + expiredCount + " 个过期文件");
        }
    }

    /**
     * 立即执行一次备份文件检查（用于测试）
     */
    public void checkNow() {
        System.out.println("[BackupLogUploader] 立即执行备份文件检查...");
        checkAndUploadBackupFiles();
    }

    /**
     * 关闭上传器
     */
    public void shutdown() {
        shutdown = true;
        
        // 执行最后一次检查
        System.out.println("[BackupLogUploader] 关闭前执行最后一次备份文件检查...");
        checkAndUploadBackupFiles();
        
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        System.out.println("[BackupLogUploader] 定期补传服务已关闭");
    }
}