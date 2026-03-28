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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 升级执行器
 * 负责处理Agent升级流程
 */
class UpgradeExecutor {
    private static final Logger logger = LoggerFactory.getLogger(UpgradeExecutor.class);
    private static final String UPGRADER_JAR = "upgrader.jar";
    private static final String UPGRADE_CONTEXT_FILE = ".upgrade-context.json";
    private static final String UPGRADER_LAUNCHD_LABEL_PROPERTY = "upgrader.launchd.label";
    private static final String MACOS_LAUNCH_AGENT_LABEL = "com.viberemote.agent";
    private static final String LEGACY_MACOS_LAUNCH_AGENT_LABEL = "com.lightscript.agent";
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
    /**
     * 执行升级
     */
    public void executeUpgrade(VersionInfo versionInfo) {
        String fromVersion = getCurrentVersion();
        String toVersion = versionInfo.getVersion();
        boolean forceUpgrade = versionInfo.isForceUpgrade();

        logger.info("Starting upgrade: {} -> {} (force: {})", fromVersion, toVersion, forceUpgrade);

        try {
            Path upgraderPath = AgentPaths.getAgentHome().resolve(UPGRADER_JAR);

            // 1. 报告升级开始
            statusReporter.reportUpgradeStart(fromVersion, toVersion, forceUpgrade);
            logger.info("Upgrade status reported to server");

            // 2. 检查升级器是否存在
            if (!Files.exists(upgraderPath)) {
                statusReporter.reportUpgradeStatus("FAILED", "Upgrader not found: " + upgraderPath);
                logger.error("Upgrader not found: {}", upgraderPath);
                return;
            }
            logger.info("Upgrader found: {}", upgraderPath);

            // 3. 报告开始下载
            statusReporter.reportUpgradeStatus("DOWNLOADING", null);
            logger.info("Starting new version download");

            // 4. 下载新版本
            String newVersionPath = downloadNewVersion(versionInfo);
            if (newVersionPath == null) {
                statusReporter.reportUpgradeStatus("FAILED", "Failed to download new version");
                logger.error("Failed to download new version");
                return;
            }
            logger.info("New version downloaded: {}", newVersionPath);

            // 5. 将上下文传给升级器，并把状态切到安装阶段
            saveUpgradeContext(fromVersion, toVersion, forceUpgrade);
            saveCredentialsForUpgrader();
            statusReporter.reportUpgradeStatus("INSTALLING", null);

            // 6. 启动升级器（只传递必要参数）
            logger.info("Starting upgrader process with new version: {}", newVersionPath);
            startUpgrader(newVersionPath);

            // 7. 主程序退出
            logger.info("Upgrade initiated, main process exiting...");
            System.exit(0);

        } catch (Exception e) {
            logger.error("Upgrade failed: {}", e.getMessage(), e);
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
        
        logger.info("Entering upgrade waiting state, monitoring task completion...");
        
        // 启动任务完成监控
        scheduler.scheduleWithFixedDelay(() -> {
            if (canUpgrade()) {
                logger.info("All tasks completed, starting upgrade now");
                executeUpgrade(versionInfo);
                return; // 停止监控
            } else {
                logger.info("Still waiting for {} tasks to complete...", taskStatusMonitor.getRunningTaskCount());
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
            String currentDir = AgentPaths.getAgentHome().toString();
            String downloadPath = currentDir + File.separator + fileName;
            
            logger.info("Downloading new version from: {}", downloadUrl);
            logger.info("Saving to: {}", downloadPath);
            
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
                    
                    logger.info("New version downloaded and verified: {}", downloadPath);
                    return downloadPath;
                }
            }
            
        } catch (Exception e) {
            logger.error("Failed to download new version: {}", e.getMessage());
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
     * 启动升级器（只传递新版本文件名）
     */
    private void startUpgrader(String newVersionPath) throws IOException {
        // 从完整路径中提取文件名
        String newVersionFilename = Paths.get(newVersionPath).getFileName().toString();

        String workingDir = AgentPaths.getAgentHome().toString();
        File logsDir = new File(workingDir, "logs");
        logsDir.mkdirs();

        String javaCommand = resolveJavaCommand(workingDir);
        ProcessBuilder pb = new ProcessBuilder();
        pb.directory(new File(workingDir));

        if (isWindows()) {
            pb.command(
                javaCommand,
                "-Dagent.home=" + workingDir,
                "-jar",
                UPGRADER_JAR,
                newVersionFilename
            );
            pb.redirectOutput(new File(logsDir, "upgrade.log"));
            pb.redirectError(new File(logsDir, "upgrade-error.log"));
        } else if (isMacLaunchAgentInstall(workingDir)) {
            String launchctlLabel = MACOS_LAUNCH_AGENT_LABEL + ".upgrader." + System.currentTimeMillis();
            String shellCommand = String.format(
                "cd %s && exec %s -Dagent.home=%s -D%s=%s -jar %s %s >> %s 2>> %s",
                shellQuote(workingDir),
                shellQuote(javaCommand),
                shellQuote(workingDir),
                UPGRADER_LAUNCHD_LABEL_PROPERTY,
                shellQuote(launchctlLabel),
                shellQuote(UPGRADER_JAR),
                shellQuote(newVersionFilename),
                shellQuote(new File(logsDir, "upgrade.log").getAbsolutePath()),
                shellQuote(new File(logsDir, "upgrade-error.log").getAbsolutePath())
            );
            pb.command("launchctl", "submit", "-l", launchctlLabel, "--", "/bin/bash", "-lc", shellCommand);
            logger.info("Submitting upgrader via launchctl label={} for macOS LaunchAgent install", launchctlLabel);
        } else {
            // launchd 管理的进程退出时可能会连带清理子进程，使用 shell + nohup 将升级器彻底脱离。
            String shellCommand = String.format(
                "cd %s && nohup %s -Dagent.home=%s -jar %s %s >> %s 2>> %s < /dev/null &",
                shellQuote(workingDir),
                shellQuote(javaCommand),
                shellQuote(workingDir),
                shellQuote(UPGRADER_JAR),
                shellQuote(newVersionFilename),
                shellQuote(new File(logsDir, "upgrade.log").getAbsolutePath()),
                shellQuote(new File(logsDir, "upgrade-error.log").getAbsolutePath())
            );
            pb.command("/bin/bash", "-lc", shellCommand);
        }

        pb.start();
        logger.info("Upgrader started with java={}, newVersionFile={}", javaCommand, newVersionFilename);
    }

    private String resolveJavaCommand(String workingDir) {
        Path bundledJava = Paths.get(workingDir, "jre", "bin", isWindows() ? "java.exe" : "java");
        if (Files.isRegularFile(bundledJava) && Files.isExecutable(bundledJava)) {
            return bundledJava.toAbsolutePath().toString();
        }

        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome != null && !javaHome.trim().isEmpty()) {
            Path javaHomeBin = Paths.get(javaHome, "bin", isWindows() ? "java.exe" : "java");
            if (Files.isRegularFile(javaHomeBin) && Files.isExecutable(javaHomeBin)) {
                return javaHomeBin.toAbsolutePath().toString();
            }
        }

        return "java";
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    private boolean isMacLaunchAgentInstall(String workingDir) {
        if (!System.getProperty("os.name").toLowerCase().contains("mac")) {
            return false;
        }

        Path home = Paths.get(System.getProperty("user.home"), "Library", "LaunchAgents");
        return Files.exists(home.resolve(MACOS_LAUNCH_AGENT_LABEL + ".plist"))
            || Files.exists(home.resolve(LEGACY_MACOS_LAUNCH_AGENT_LABEL + ".plist"))
            || Files.exists(Paths.get(workingDir, MACOS_LAUNCH_AGENT_LABEL + ".plist"))
            || Files.exists(Paths.get(workingDir, LEGACY_MACOS_LAUNCH_AGENT_LABEL + ".plist"));
    }

    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }
    
    /**
     * 获取当前主程序JAR文件名
     */
    private String getMainJarName() {
        // 方法1: 从系统属性获取（如果启动时设置了）
        String jarName = System.getProperty("agent.jar.name");
        if (jarName != null && !jarName.isEmpty()) {
            return jarName;
        }
        
        // 方法2: 从当前运行的JAR路径推断
        try {
            String jarPath = AgentMain.class.getProtectionDomain()
                .getCodeSource().getLocation().getPath();
            if (jarPath.endsWith(".jar")) {
                return Paths.get(jarPath).getFileName().toString();
            }
        } catch (Exception e) {
            logger.debug("Failed to get JAR name from code source: {}", e.getMessage());
        }
        
        // 方法3: 检查常见的JAR文件名
        String[] commonNames = {"agent.jar", "lightscript-agent.jar", "app.jar"};
        String currentDir = AgentPaths.getAgentHome().toString();
        for (String name : commonNames) {
            if (Files.exists(Paths.get(currentDir, name))) {
                logger.info("Found main JAR: {}", name);
                return name;
            }
        }
        
        // 默认值
        logger.warn("Could not determine main JAR name, using default: agent.jar");
        return "agent.jar";
    }
    
    /**
     * 保存升级上下文信息
     */
    private void saveUpgradeContext(String fromVersion, String toVersion, boolean forceUpgrade) throws IOException {
        Map<String, Object> context = new HashMap<>();
        context.put("fromVersion", fromVersion);
        context.put("toVersion", toVersion);
        context.put("forceUpgrade", forceUpgrade);
        context.put("agentId", agentId);
        context.put("agentToken", agentToken);
        context.put("serverUrl", baseUrl);
        context.put("upgradeLogId", statusReporter.getCurrentUpgradeLogId());
        context.put("timestamp", System.currentTimeMillis());

        Path contextFile = AgentPaths.getAgentHome().resolve(UPGRADE_CONTEXT_FILE);
        try {
            String json = new ObjectMapper().writeValueAsString(context);
            Files.write(contextFile, json.getBytes(StandardCharsets.UTF_8));
            logger.info("Upgrade context saved");
        } catch (Exception e) {
            throw new IOException("Failed to save upgrade context", e);
        }
    }
    
    /**
     * 获取当前版本
     */
    private String getCurrentVersion() {
        return VersionUtil.getCurrentVersion();
    }
    
    /**
     * 保存凭证供升级器使用（保持兼容性）
     */
    private void saveCredentialsForUpgrader() {
        try {
            Path credentialsFile = Paths.get(".agent-credentials");
            List<String> lines = Arrays.asList(agentId, agentToken);
            Files.write(credentialsFile, lines, StandardCharsets.UTF_8);
            logger.info("Credentials saved for upgrader");
        } catch (Exception e) {
            logger.error("Failed to save credentials: {}", e.getMessage());
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
