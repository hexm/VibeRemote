package com.example.lightscript.upgrader;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 独立的Agent升级器程序
 * 负责在主Agent进程退出后执行升级操作
 */
public class AgentUpgrader {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private UpgradeStatusReporter statusReporter;
    private String serverUrl;
    
    public static void main(String[] args) {
        if (args.length < 4) {
            System.err.println("Usage: java -jar upgrader.jar <new-version-path> <agent-home> <upgrade-log-id> <server-url>");
            System.err.println("  new-version-path: Path to the new agent JAR file");
            System.err.println("  agent-home: Agent installation directory");
            System.err.println("  upgrade-log-id: Upgrade log ID for status reporting");
            System.err.println("  server-url: Server URL for status reporting");
            System.exit(1);
        }
        
        String newVersionPath = args[0];
        String agentHome = args[1];
        Long upgradeLogId = Long.parseLong(args[2]);
        String serverUrl = args[3];
        
        System.out.println("========================================");
        System.out.println("LightScript Agent Upgrader");
        System.out.println("========================================");
        System.out.println("New version: " + newVersionPath);
        System.out.println("Agent home: " + agentHome);
        System.out.println("Upgrade log ID: " + upgradeLogId);
        System.out.println("Server URL: " + serverUrl);
        System.out.println("========================================");
        
        try {
            AgentUpgrader upgrader = new AgentUpgrader();
            upgrader.serverUrl = serverUrl;
            upgrader.statusReporter = new UpgradeStatusReporter(serverUrl, upgradeLogId);
            upgrader.performUpgrade(newVersionPath, agentHome);
            System.out.println("Upgrade completed successfully");
            System.exit(0);
        } catch (Exception e) {
            System.err.println("Upgrade failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private void performUpgrade(String newVersionPath, String agentHome) throws Exception {
        System.out.println("Starting Agent upgrade process...");
        String backupDir = null;
        
        try {
            // 1. 报告开始安装
            statusReporter.reportStatus("INSTALLING", null);
            
            // 2. 等待主进程完全退出
            waitForProcessExit();
            
            // 3. 备份当前版本
            backupDir = createBackup(agentHome);
            System.out.println("Backup created: " + backupDir);
            
            // 4. 替换主程序
            replaceMainJar(newVersionPath, agentHome);
            System.out.println("Main JAR replaced successfully");
            
            // 5. 启动新版本
            startNewVersion(agentHome);
            System.out.println("New version started");
            
            // 6. 验证启动成功
            if (verifyStartup(agentHome)) {
                System.out.println("Upgrade successful - new version is running");
                statusReporter.reportStatus("SUCCESS", null);
                cleanupTempFiles(newVersionPath);
            } else {
                throw new Exception("New version failed to start");
            }
            
        } catch (Exception e) {
            System.err.println("Upgrade failed, attempting rollback...");
            statusReporter.reportStatus("ROLLBACK", e.getMessage());
            
            if (backupDir != null) {
                try {
                    rollback(backupDir, agentHome);
                    System.out.println("Rollback completed");
                } catch (Exception rollbackError) {
                    System.err.println("Rollback also failed: " + rollbackError.getMessage());
                    rollbackError.printStackTrace();
                }
            }
            throw e;
        }
    }
    
    /**
     * 等待主进程退出
     */
    private void waitForProcessExit() throws InterruptedException {
        System.out.println("Waiting for main process to exit...");
        // 等待5秒确保主进程完全退出
        Thread.sleep(5000);
        
        // 额外检查并清理可能残留的锁文件
        try {
            String userHome = System.getProperty("user.home");
            Path lockFile = Paths.get(userHome, ".lightscript", ".agent.lock");
            if (Files.exists(lockFile)) {
                System.out.println("Cleaning up lock file: " + lockFile);
                Files.delete(lockFile);
            }
        } catch (Exception e) {
            System.out.println("Failed to clean lock file: " + e.getMessage());
        }
        
        System.out.println("Process exit wait completed");
    }
    
    /**
     * 创建备份
     */
    private String createBackup(String agentHome) throws IOException {
        Path backupDir = Paths.get(agentHome, "backup");
        
        // 清理旧备份，只保留最新的一个
        cleanupOldBackups(backupDir);
        
        // 创建新备份 - 使用固定名称 "current"
        Path currentBackupPath = backupDir.resolve("current");
        
        // 如果已存在备份，先删除
        if (Files.exists(currentBackupPath)) {
            deleteDirectory(currentBackupPath);
        }
        
        Files.createDirectories(currentBackupPath);
        
        // 只备份主程序JAR文件
        Path currentJar = Paths.get(agentHome, "agent.jar");
        if (Files.exists(currentJar)) {
            Files.copy(currentJar, currentBackupPath.resolve("agent.jar"), StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Backup created: " + currentBackupPath);
        } else {
            throw new IOException("Current agent.jar not found for backup");
        }
        
        return currentBackupPath.toString();
    }
    
    /**
     * 清理旧备份，只保留最新的一个
     */
    private void cleanupOldBackups(Path backupDir) throws IOException {
        if (!Files.exists(backupDir)) {
            return;
        }
        
        try {
            // 删除所有时间戳格式的旧备份目录
            Files.list(backupDir)
                .filter(Files::isDirectory)
                .filter(path -> path.getFileName().toString().matches("\\d{8}-\\d{6}"))
                .forEach(path -> {
                    try {
                        deleteDirectory(path);
                        System.out.println("Cleaned up old backup: " + path.getFileName());
                    } catch (IOException e) {
                        System.err.println("Failed to delete old backup: " + path + " - " + e.getMessage());
                    }
                });
        } catch (IOException e) {
            System.err.println("Failed to list backup directory: " + e.getMessage());
        }
    }
    
    /**
     * 递归删除目录
     */
    private void deleteDirectory(Path directory) throws IOException {
        if (Files.exists(directory)) {
            Files.walk(directory)
                .sorted((a, b) -> b.compareTo(a)) // 先删除文件，后删除目录
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        System.err.println("Failed to delete: " + path + " - " + e.getMessage());
                    }
                });
        }
    }
    
    /**
     * 替换主程序JAR
     */
    private void replaceMainJar(String newVersionPath, String agentHome) throws IOException {
        Path newJar = Paths.get(newVersionPath);
        Path currentJar = Paths.get(agentHome, "agent.jar");
        
        if (!Files.exists(newJar)) {
            throw new IOException("New version file not found: " + newVersionPath);
        }
        
        // 替换主程序
        Files.copy(newJar, currentJar, StandardCopyOption.REPLACE_EXISTING);
        System.out.println("Replaced agent.jar with new version");
    }
    
    /**
     * 启动新版本
     */
    private void startNewVersion(String agentHome) throws IOException {
        // 确保启动脚本存在
        ensureStartScripts(agentHome);
        
        ProcessBuilder pb = new ProcessBuilder();
        
        // 根据操作系统选择启动脚本
        String startScript;
        if (isWindows()) {
            startScript = "start-agent.bat";
        } else {
            startScript = "./start-agent.sh";
        }
        
        // 检查启动脚本是否存在
        File scriptFile = new File(agentHome, startScript.replace("./", ""));
        if (!scriptFile.exists()) {
            throw new IOException("Start script not found: " + scriptFile.getAbsolutePath());
        }
        
        // 使用启动脚本启动新版本（不传递参数，配置由配置文件管理）
        pb.command(startScript);
        pb.directory(new File(agentHome));
        
        // 重定向输出到日志文件，用于启动验证
        File logsDir = new File(agentHome, "logs");
        logsDir.mkdirs(); // 确保logs目录存在
        pb.redirectOutput(new File(logsDir, "agent-startup.log"));
        pb.redirectError(new File(logsDir, "agent-startup-error.log"));
        
        Process process = pb.start();
        System.out.println("New agent process started using script: " + startScript);
    }
    
    /**
     * 验证启动成功
     */
    private boolean verifyStartup(String agentHome) {
        System.out.println("Verifying new version startup...");
        
        // 等待15秒让新版本启动并产生日志
        try {
            Thread.sleep(15000);
        } catch (InterruptedException e) {
            return false;
        }
        
        // 检查启动日志文件
        File logsDir = new File(agentHome, "logs");
        File logFile = new File(logsDir, "agent-startup.log");
        File errorLogFile = new File(logsDir, "agent-startup-error.log");
        
        // 如果有错误日志且内容不为空，说明启动失败
        if (errorLogFile.exists() && errorLogFile.length() > 0) {
            System.out.println("Startup error log found, verification failed");
            return false;
        }
        
        // 检查启动日志是否存在且有内容
        if (logFile.exists() && logFile.length() > 0) {
            try {
                // 读取日志内容，检查是否包含成功启动的标志
                String logContent = new String(Files.readAllBytes(logFile.toPath()));
                if (logContent.contains("Agent started. Waiting for tasks...") || 
                    logContent.contains("Agent registered successfully!")) {
                    System.out.println("Startup log indicates successful start, verification passed");
                    return true;
                }
            } catch (IOException e) {
                System.out.println("Failed to read startup log: " + e.getMessage());
            }
        }
        
        System.out.println("No valid startup log found, verification failed");
        return false;
    }
    
    /**
     * 回滚到备份版本
     */
    private void rollback(String backupDir, String agentHome) throws IOException {
        System.out.println("Rolling back to backup: " + backupDir);
        
        Path backupPath = Paths.get(backupDir);
        
        // 恢复主程序JAR文件
        Path backupJar = backupPath.resolve("agent.jar");
        if (Files.exists(backupJar)) {
            Path currentJar = Paths.get(agentHome, "agent.jar");
            Files.copy(backupJar, currentJar, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Agent JAR restored from backup");
        } else {
            throw new IOException("Backup JAR file not found: " + backupJar);
        }
        
        // 启动原版本
        startNewVersion(agentHome);
        System.out.println("Original version restored and started");
    }
    
    /**
     * 清理临时文件
     */
    private void cleanupTempFiles(String newVersionPath) {
        try {
            Files.deleteIfExists(Paths.get(newVersionPath));
            System.out.println("Temporary files cleaned up");
        } catch (IOException e) {
            System.err.println("Failed to cleanup temp files: " + e.getMessage());
        }
    }
    
    /**
     * 检查是否为Windows系统
     */
    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
    
    /**
     * 确保启动脚本存在，如果不存在则创建
     */
    private void ensureStartScripts(String agentHome) throws IOException {
        // 创建Unix/Linux/macOS启动脚本
        File shScript = new File(agentHome, "start-agent.sh");
        if (!shScript.exists()) {
            String shContent = "#!/bin/bash\n" +
                "# LightScript Agent 启动脚本 (Unix/Linux/macOS)\n\n" +
                "# 脚本目录\n" +
                "SCRIPT_DIR=\"$(cd \"$(dirname \"${BASH_SOURCE[0]}\")\" && pwd)\"\n" +
                "cd \"$SCRIPT_DIR\"\n\n" +
                "# 配置Java环境\n" +
                "if [ -n \"$JAVA_HOME\" ]; then\n" +
                "    JAVA_CMD=\"$JAVA_HOME/bin/java\"\n" +
                "elif command -v java >/dev/null 2>&1; then\n" +
                "    JAVA_CMD=\"java\"\n" +
                "else\n" +
                "    echo \"ERROR: Java not found. Please install Java or set JAVA_HOME environment variable.\"\n" +
                "    exit 1\n" +
                "fi\n\n" +
                "# Agent配置\n" +
                "AGENT_JAR=\"agent.jar\"\n\n" +
                "# JVM参数配置\n" +
                "JVM_OPTS=\"${LIGHTSCRIPT_JVM_OPTS:--Xmx512m -Xms128m}\"\n\n" +
                "# 检查Agent JAR文件\n" +
                "if [ ! -f \"$AGENT_JAR\" ]; then\n" +
                "    echo \"ERROR: Agent JAR file not found: $AGENT_JAR\"\n" +
                "    exit 1\n" +
                "fi\n\n" +
                "# 启动Agent\n" +
                "echo \"Starting LightScript Agent...\"\n" +
                "echo \"Java Command: $JAVA_CMD\"\n" +
                "echo \"JVM Options: $JVM_OPTS\"\n" +
                "echo \"Working Directory: $SCRIPT_DIR\"\n\n" +
                "exec \"$JAVA_CMD\" $JVM_OPTS -jar \"$AGENT_JAR\"\n";
            
            Files.write(shScript.toPath(), shContent.getBytes());
            // 设置执行权限
            shScript.setExecutable(true);
            System.out.println("Created start-agent.sh script");
        }
        
        // 创建Windows启动脚本
        File batScript = new File(agentHome, "start-agent.bat");
        if (!batScript.exists()) {
            String batContent = "@echo off\n" +
                "REM LightScript Agent 启动脚本 (Windows)\n\n" +
                "REM 切换到脚本目录\n" +
                "cd /d \"%~dp0\"\n\n" +
                "REM 配置Java环境\n" +
                "if defined JAVA_HOME (\n" +
                "    set \"JAVA_CMD=%JAVA_HOME%\\bin\\java.exe\"\n" +
                ") else (\n" +
                "    where java >nul 2>&1\n" +
                "    if %errorlevel% equ 0 (\n" +
                "        set \"JAVA_CMD=java\"\n" +
                "    ) else (\n" +
                "        echo ERROR: Java not found. Please install Java or set JAVA_HOME environment variable.\n" +
                "        exit /b 1\n" +
                "    )\n" +
                ")\n\n" +
                "REM Agent配置\n" +
                "set \"AGENT_JAR=agent.jar\"\n\n" +
                "REM JVM参数配置\n" +
                "if not defined LIGHTSCRIPT_JVM_OPTS (\n" +
                "    set \"JVM_OPTS=-Xmx512m -Xms128m\"\n" +
                ") else (\n" +
                "    set \"JVM_OPTS=%LIGHTSCRIPT_JVM_OPTS%\"\n" +
                ")\n\n" +
                "REM 检查Agent JAR文件\n" +
                "if not exist \"%AGENT_JAR%\" (\n" +
                "    echo ERROR: Agent JAR file not found: %AGENT_JAR%\n" +
                "    exit /b 1\n" +
                ")\n\n" +
                "REM 启动Agent\n" +
                "echo Starting LightScript Agent...\n" +
                "echo Java Command: %JAVA_CMD%\n" +
                "echo JVM Options: %JVM_OPTS%\n" +
                "echo Working Directory: %CD%\n\n" +
                "\"%JAVA_CMD%\" %JVM_OPTS% -jar \"%AGENT_JAR%\"\n";
            
            Files.write(batScript.toPath(), batContent.getBytes());
            System.out.println("Created start-agent.bat script");
        }
    }
    
    /**
     * 升级状态报告器（简化版）
     */
    private static class UpgradeStatusReporter {
        private final String serverUrl;
        private final Long upgradeLogId;
        private final CloseableHttpClient httpClient;
        private String agentId;
        private String agentToken;
        
        public UpgradeStatusReporter(String serverUrl, Long upgradeLogId) {
            this.serverUrl = serverUrl;
            this.upgradeLogId = upgradeLogId;
            this.httpClient = HttpClients.createDefault();
            
            // 尝试从环境变量获取Agent凭证
            this.agentId = System.getenv("AGENT_ID");
            this.agentToken = System.getenv("AGENT_TOKEN");
            
            // 如果环境变量没有，尝试从配置文件获取
            if (agentId == null || agentToken == null) {
                loadCredentialsFromFile();
            }
        }
        
        private void loadCredentialsFromFile() {
            try {
                // 尝试从.agent-credentials文件读取凭证
                Path credentialsFile = Paths.get(".agent-credentials");
                if (Files.exists(credentialsFile)) {
                    List<String> lines = Files.readAllLines(credentialsFile);
                    if (lines.size() >= 2) {
                        this.agentId = lines.get(0).trim();
                        this.agentToken = lines.get(1).trim();
                    }
                }
            } catch (Exception e) {
                System.err.println("[StatusReporter] Failed to load credentials from file: " + e.getMessage());
            }
        }
        
        public void reportStatus(String status, String errorMessage) {
            if (agentId == null || agentToken == null) {
                System.out.println("[StatusReporter] No credentials available, skipping status report: " + status + 
                                 (errorMessage != null ? " (" + errorMessage + ")" : ""));
                return;
            }
            
            try {
                Map<String, Object> request = new HashMap<>();
                request.put("upgradeLogId", upgradeLogId);
                request.put("status", status);
                if (errorMessage != null) {
                    request.put("errorMessage", errorMessage);
                }
                
                String url = serverUrl + "/api/agent/upgrade/status?agentId=" + agentId + "&agentToken=" + agentToken;
                HttpPost post = new HttpPost(url);
                post.setHeader("Content-Type", "application/json");
                post.setEntity(new StringEntity(MAPPER.writeValueAsString(request), "UTF-8"));
                
                try (CloseableHttpResponse response = httpClient.execute(post)) {
                    int statusCode = response.getStatusLine().getStatusCode();
                    if (statusCode == 200) {
                        System.out.println("[StatusReporter] Status reported successfully: " + status);
                    } else {
                        System.err.println("[StatusReporter] Failed to report status, HTTP " + statusCode);
                    }
                }
                
            } catch (Exception e) {
                System.err.println("[StatusReporter] Failed to report status: " + e.getMessage());
            }
        }
    }
}