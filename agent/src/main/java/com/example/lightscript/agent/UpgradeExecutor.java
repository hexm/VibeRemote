package com.example.lightscript.agent;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

/**
 * 升级执行器
 * 负责处理Agent升级流程
 */
class UpgradeExecutor {
    private static final String UPGRADER_JAR = "upgrader.jar";
    private final UpgradeStatusReporter statusReporter;
    private final TaskStatusMonitor taskStatusMonitor;
    private final ScheduledExecutorService scheduler;
    private final String baseUrl;
    private final String agentId;
    private final String agentToken;
    
    public UpgradeExecutor(UpgradeStatusReporter statusReporter, TaskStatusMonitor taskStatusMonitor, 
                          ScheduledExecutorService scheduler, String baseUrl, String agentId, String agentToken) {
        this.statusReporter = statusReporter;
        this.taskStatusMonitor = taskStatusMonitor;
        this.scheduler = scheduler;
        this.baseUrl = baseUrl;
        this.agentId = agentId;
        this.agentToken = agentToken;
    }
    
    /**
     * 执行升级
     */
    public void executeUpgrade(VersionInfo versionInfo) {
        String fromVersion = getCurrentVersion();
        String toVersion = versionInfo.getVersion();
        boolean forceUpgrade = versionInfo.isForceUpgrade();
        
        System.out.println("[UpgradeExecutor] Starting upgrade: " + fromVersion + " -> " + toVersion + 
                          " (force: " + forceUpgrade + ")");
        
        try {
            // 1. 报告升级开始
            statusReporter.reportUpgradeStart(fromVersion, toVersion, forceUpgrade);
            
            // 2. 检查升级器是否存在
            if (!Files.exists(Paths.get(UPGRADER_JAR))) {
                statusReporter.reportUpgradeStatus("FAILED", "Upgrader not found: " + UPGRADER_JAR);
                System.err.println("[UpgradeExecutor] Upgrader not found: " + UPGRADER_JAR);
                return;
            }
            
            // 3. 报告开始下载
            statusReporter.reportUpgradeStatus("DOWNLOADING", null);
            
            // 4. 下载新版本
            String newVersionPath = downloadNewVersion(versionInfo);
            if (newVersionPath == null) {
                statusReporter.reportUpgradeStatus("FAILED", "Failed to download new version");
                return;
            }
            
            // 5. 启动升级器
            startUpgrader(newVersionPath);
            
            // 6. 保存凭证供升级器使用
            saveCredentialsForUpgrader();
            
            // 7. 主程序退出
            System.out.println("[UpgradeExecutor] Upgrade initiated, main process exiting...");
            System.exit(0);
            
        } catch (Exception e) {
            System.err.println("[UpgradeExecutor] Upgrade failed: " + e.getMessage());
            e.printStackTrace();
            statusReporter.reportUpgradeStatus("FAILED", e.getMessage());
        }
    }
    
    /**
     * 检查是否可以升级（无正在执行的任务）
     */
    public boolean canUpgrade() {
        return !taskStatusMonitor.hasRunningTasks();
    }
    
    /**
     * 调度升级重试
     */
    public void scheduleUpgradeRetry(VersionInfo versionInfo) {
        // 普通升级：进入升级状态，等待任务完成
        // 注意：此时Agent状态应该已经设为UPGRADING，不再接收新任务
        
        System.out.println("[UpgradeExecutor] Entering upgrade waiting state, monitoring task completion...");
        
        // 启动任务完成监控
        scheduler.scheduleWithFixedDelay(() -> {
            if (canUpgrade()) {
                System.out.println("[UpgradeExecutor] All tasks completed, starting upgrade now");
                executeUpgrade(versionInfo);
                return; // 停止监控
            } else {
                System.out.println("[UpgradeExecutor] Still waiting for " + 
                                 taskStatusMonitor.getRunningTaskCount() + " tasks to complete...");
            }
        }, 10, 10, TimeUnit.SECONDS); // 每10秒检查一次任务状态
    }
    
    /**
     * 下载新版本
     */
    private String downloadNewVersion(VersionInfo versionInfo) {
        try {
            String downloadUrl = versionInfo.getDownloadUrl();
            String fileName = "agent-" + versionInfo.getVersion() + ".jar";
            
            // 使用当前工作目录（Agent启动目录）而不是系统临时目录
            String currentDir = System.getProperty("user.dir");
            String downloadPath = currentDir + File.separator + fileName;
            
            System.out.println("[UpgradeExecutor] Downloading new version from: " + downloadUrl);
            System.out.println("[UpgradeExecutor] Saving to: " + downloadPath);
            
            // 使用HTTP客户端下载文件
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                HttpGet get = new HttpGet(downloadUrl);
                // 添加Agent-ID头部用于Agent专用下载接口
                get.setHeader("Agent-ID", agentId);
                
                try (CloseableHttpResponse response = httpClient.execute(get)) {
                    int statusCode = response.getStatusLine().getStatusCode();
                    if (statusCode != 200) {
                        throw new IOException("Download failed with status: " + statusCode);
                    }
                    
                    // 获取文件大小
                    long contentLength = response.getEntity().getContentLength();
                    if (versionInfo.getFileSize() != null && contentLength != versionInfo.getFileSize()) {
                        throw new IOException("File size mismatch: expected " + versionInfo.getFileSize() + 
                                            ", got " + contentLength);
                    }
                    
                    // 下载文件到Agent启动目录
                    Path downloadFile = Paths.get(downloadPath);
                    try (InputStream inputStream = response.getEntity().getContent()) {
                        Files.copy(inputStream, downloadFile, StandardCopyOption.REPLACE_EXISTING);
                    }
                    
                    // 验证文件哈希（如果提供）
                    if (versionInfo.getFileHash() != null) {
                        String actualHash = calculateFileHash(downloadPath);
                        if (!versionInfo.getFileHash().equalsIgnoreCase(actualHash)) {
                            throw new IOException("File hash mismatch: expected " + versionInfo.getFileHash() + 
                                                ", got " + actualHash);
                        }
                    }
                    
                    System.out.println("[UpgradeExecutor] New version downloaded and verified: " + downloadPath);
                    return downloadPath;
                }
            }
            
        } catch (Exception e) {
            System.err.println("[UpgradeExecutor] Failed to download new version: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 计算文件SHA256哈希
     */
    private String calculateFileHash(String filePath) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream fis = Files.newInputStream(Paths.get(filePath))) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }
        
        byte[] hashBytes = digest.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : hashBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
    
    /**
     * 启动升级器
     */
    private void startUpgrader(String newVersionPath) throws IOException {
        String agentHome = System.getProperty("user.dir");
        Long upgradeLogId = statusReporter.getCurrentUpgradeLogId();
        
        if (upgradeLogId == null) {
            throw new RuntimeException("No upgrade log ID available");
        }
        
        ProcessBuilder pb = new ProcessBuilder();
        pb.command("java", "-jar", UPGRADER_JAR, 
                  newVersionPath, agentHome, 
                  upgradeLogId.toString(),
                  baseUrl);
        pb.directory(new File(agentHome));
        
        // 重定向输出到日志文件
        File logsDir = new File("logs");
        logsDir.mkdirs(); // 确保logs目录存在
        pb.redirectOutput(new File(logsDir, "upgrade.log"));
        pb.redirectError(new File(logsDir, "upgrade-error.log"));
        
        Process process = pb.start();
        System.out.println("[UpgradeExecutor] Upgrader started");
    }
    
    /**
     * 获取当前版本
     */
    private String getCurrentVersion() {
        return VersionUtil.getCurrentVersion();
    }
    
    /**
     * 保存凭证供升级器使用
     */
    private void saveCredentialsForUpgrader() {
        try {
            Path credentialsFile = Paths.get(".agent-credentials");
            List<String> lines = Arrays.asList(agentId, agentToken);
            Files.write(credentialsFile, lines, StandardCharsets.UTF_8);
            System.out.println("[UpgradeExecutor] Credentials saved for upgrader");
        } catch (Exception e) {
            System.err.println("[UpgradeExecutor] Failed to save credentials: " + e.getMessage());
        }
    }
    public static class VersionInfo {
        private String version;
        private String downloadUrl;
        private Long fileSize;
        private String fileHash;
        private boolean forceUpgrade;
        private String releaseNotes;
        
        // Getters and setters
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        
        public String getDownloadUrl() { return downloadUrl; }
        public void setDownloadUrl(String downloadUrl) { this.downloadUrl = downloadUrl; }
        
        public Long getFileSize() { return fileSize; }
        public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
        
        public String getFileHash() { return fileHash; }
        public void setFileHash(String fileHash) { this.fileHash = fileHash; }
        
        public boolean isForceUpgrade() { return forceUpgrade; }
        public void setForceUpgrade(boolean forceUpgrade) { this.forceUpgrade = forceUpgrade; }
        
        public String getReleaseNotes() { return releaseNotes; }
        public void setReleaseNotes(String releaseNotes) { this.releaseNotes = releaseNotes; }
    }
}