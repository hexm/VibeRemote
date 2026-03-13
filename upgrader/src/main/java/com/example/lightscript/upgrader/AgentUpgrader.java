package com.example.lightscript.upgrader;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 终极简化的Agent升级器程序
 * 只需要1个参数：新版本文件名
 * 主程序文件名固定为：agent.jar
 */
public class AgentUpgrader {
    
    private static final String MAIN_JAR_NAME = "agent.jar";
    private static final DateTimeFormatter LOG_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    
    private PrintWriter logWriter;
    private String agentHome;
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar upgrader.jar <new-version-filename>");
            System.err.println("  new-version-filename: New agent JAR file name (e.g., agent-2.1.0.jar)");
            System.err.println("  Main JAR is fixed as: " + MAIN_JAR_NAME);
            System.exit(1);
        }
        
        String newVersionFilename = args[0];
        String agentHome = System.getProperty("user.dir");
        
        System.out.println("========================================");
        System.out.println("LightScript Agent Upgrader (Ultimate)");
        System.out.println("========================================");
        System.out.println("Agent home: " + agentHome);
        System.out.println("New version file: " + newVersionFilename);
        System.out.println("Main JAR: " + MAIN_JAR_NAME + " (fixed)");
        System.out.println("========================================");
        
        try {
            AgentUpgrader upgrader = new AgentUpgrader();
            upgrader.agentHome = agentHome;
            upgrader.initializeLogging();
            upgrader.performUpgrade(newVersionFilename);
            upgrader.log("Upgrade completed successfully");
            System.out.println("Upgrade completed successfully");
            System.exit(0);
        } catch (Exception e) {
            System.err.println("Upgrade failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * 初始化日志记录
     */
    private void initializeLogging() throws IOException {
        File logsDir = new File(agentHome, "logs");
        logsDir.mkdirs();
        
        // 使用统一的agent.log文件，而不是创建新的升级日志文件
        File logFile = new File(logsDir, "agent.log");
        
        // 追加模式写入统一日志文件
        logWriter = new PrintWriter(new FileWriter(logFile, true));
        log("=== Agent Upgrade Started ===");
        log("Agent home: " + agentHome);
        log("Main JAR: " + MAIN_JAR_NAME);
    }
    
    /**
     * 记录日志
     */
    private void log(String message) {
        String timestamp = LocalDateTime.now().format(LOG_TIME_FORMAT);
        String logEntry = String.format("%s [main] INFO [UPGRADER] - %s", timestamp, message);
        
        // 同时输出到控制台和日志文件
        System.out.println(logEntry);
        if (logWriter != null) {
            logWriter.println(logEntry);
            logWriter.flush();
        }
    }
    
    /**
     * 记录错误日志
     */
    private void logError(String message, Exception e) {
        String timestamp = LocalDateTime.now().format(LOG_TIME_FORMAT);
        String logEntry = String.format("%s [main] ERROR [UPGRADER] - %s", timestamp, message);
        
        // 输出错误日志
        System.err.println(logEntry);
        if (logWriter != null) {
            logWriter.println(logEntry);
            if (e != null) {
                String exceptionEntry = String.format("%s [main] ERROR [UPGRADER] - Exception: %s", timestamp, e.getMessage());
                logWriter.println(exceptionEntry);
                e.printStackTrace(logWriter);
            }
            logWriter.flush();
        }
    }
    
    /**
     * 执行升级 - 只专注于文件操作
     */
    private void performUpgrade(String newVersionFilename) throws Exception {
        log("Starting Agent upgrade process...");
        log("New version file: " + newVersionFilename);
        
        String backupDir = null;
        
        try {
            // 1. 等待主进程完全退出
            waitForProcessExit();
            
            // 2. 验证新版本文件存在
            validateNewVersionFile(newVersionFilename);
            
            // 3. 备份当前版本
            backupDir = createBackup();
            log("Backup created: " + backupDir);
            
            // 4. 替换主程序
            replaceMainJar(newVersionFilename);
            log("Main JAR replaced successfully");
            
            // 5. 启动新版本
            startNewVersion();
            log("New version started");
            
            // 6. 检查启动是否成功
            if (verifyStartup()) {
                log("Upgrade successful - new version is running");
                cleanupFiles(newVersionFilename);
            } else {
                throw new Exception("New version failed to start");
            }
            
        } catch (Exception e) {
            logError("Upgrade failed, attempting rollback", e);
            
            if (backupDir != null) {
                try {
                    rollback(backupDir);
                    log("Rollback completed successfully");
                } catch (Exception rollbackError) {
                    logError("Rollback also failed", rollbackError);
                }
            }
            throw e;
        } finally {
            if (logWriter != null) {
                log("=== Agent Upgrade Finished ===");
                logWriter.close();
            }
        }
    }
    
    /**
     * 等待主进程退出
     */
    private void waitForProcessExit() throws InterruptedException {
        log("Waiting for main process to exit...");
        Thread.sleep(5000); // 等待5秒确保主进程完全退出
        
        // 清理可能残留的全局锁文件
        try {
            String userHome = System.getProperty("user.home");
            Path lockFile = Paths.get(userHome, ".lightscript", ".agent.lock");
            if (Files.exists(lockFile)) {
                Files.delete(lockFile);
                log("Cleaned up global lock file: " + lockFile);
            }
        } catch (Exception e) {
            logError("Failed to clean global lock file", e);
        }
        
        log("Process exit wait completed");
    }
    
    /**
     * 验证新版本文件存在
     */
    private void validateNewVersionFile(String newVersionFilename) throws IOException {
        Path newVersionFile = Paths.get(agentHome, newVersionFilename);
        if (!Files.exists(newVersionFile)) {
            throw new IOException("New version file not found: " + newVersionFile);
        }
        
        long fileSize = Files.size(newVersionFile);
        log("New version file validated: " + newVersionFile + " (size: " + fileSize + " bytes)");
    }
    
    /**
     * 创建备份
     */
    private String createBackup() throws IOException {
        log("Creating backup of current version...");
        
        Path backupDir = Paths.get(agentHome, "backup", "current");
        
        // 如果已存在备份，先删除
        if (Files.exists(backupDir)) {
            deleteDirectory(backupDir);
            log("Removed existing backup directory");
        }
        
        Files.createDirectories(backupDir);
        log("Created backup directory: " + backupDir);
        
        // 备份主程序JAR文件
        Path currentJar = Paths.get(agentHome, MAIN_JAR_NAME);
        if (Files.exists(currentJar)) {
            Path backupJar = backupDir.resolve(MAIN_JAR_NAME);
            Files.copy(currentJar, backupJar, StandardCopyOption.REPLACE_EXISTING);
            
            long originalSize = Files.size(currentJar);
            long backupSize = Files.size(backupJar);
            log("Backup created: " + backupJar + " (original: " + originalSize + " bytes, backup: " + backupSize + " bytes)");
            
            if (originalSize != backupSize) {
                throw new IOException("Backup file size mismatch");
            }
        } else {
            throw new IOException("Current main JAR not found for backup: " + currentJar);
        }
        
        return backupDir.toString();
    }
    
    /**
     * 替换主程序JAR
     */
    private void replaceMainJar(String newVersionFilename) throws IOException {
        log("Replacing main JAR file...");
        
        Path newJar = Paths.get(agentHome, newVersionFilename);
        Path currentJar = Paths.get(agentHome, MAIN_JAR_NAME);
        
        long newFileSize = Files.size(newJar);
        log("New version file size: " + newFileSize + " bytes");
        
        // 替换主程序
        Files.copy(newJar, currentJar, StandardCopyOption.REPLACE_EXISTING);
        
        long replacedFileSize = Files.size(currentJar);
        log("Replaced " + MAIN_JAR_NAME + " with " + newVersionFilename + " (new size: " + replacedFileSize + " bytes)");
        
        if (newFileSize != replacedFileSize) {
            throw new IOException("File replacement size mismatch");
        }
    }
    
    /**
     * 启动新版本
     */
    private void startNewVersion() throws IOException {
        log("Starting new version...");
        
        // 确保启动脚本存在
        ensureStartScripts();
        
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
        
        log("Using start script: " + startScript);
        
        // 使用启动脚本启动新版本
        pb.command(startScript);
        pb.directory(new File(agentHome));
        
        // 重定向输出到日志文件，用于启动验证
        File logsDir = new File(agentHome, "logs");
        pb.redirectOutput(new File(logsDir, "agent-startup.log"));
        pb.redirectError(new File(logsDir, "agent-startup-error.log"));
        
        Process process = pb.start();
        log("New agent process started using script: " + startScript);
    }
    
    /**
     * 验证启动成功
     */
    private boolean verifyStartup() {
        log("Verifying new version startup...");
        
        // 等待15秒让新版本启动并产生日志
        try {
            for (int i = 1; i <= 15; i++) {
                Thread.sleep(1000);
                if (i % 5 == 0) {
                    log("Waiting for startup... (" + i + "/15 seconds)");
                }
            }
        } catch (InterruptedException e) {
            logError("Startup verification interrupted", e);
            return false;
        }
        
        // 检查启动日志文件
        File logsDir = new File(agentHome, "logs");
        File logFile = new File(logsDir, "agent-startup.log");
        File errorLogFile = new File(logsDir, "agent-startup-error.log");
        
        // 如果有错误日志且内容不为空，说明启动失败
        if (errorLogFile.exists() && errorLogFile.length() > 0) {
            log("Startup error log found (" + errorLogFile.length() + " bytes), verification failed");
            try {
                String errorContent = new String(Files.readAllBytes(errorLogFile.toPath()));
                log("Error log content: " + errorContent);
            } catch (IOException e) {
                logError("Failed to read error log", e);
            }
            return false;
        }
        
        // 检查启动日志是否存在且有内容
        if (logFile.exists() && logFile.length() > 0) {
            try {
                String logContent = new String(Files.readAllBytes(logFile.toPath()));
                log("Startup log content (" + logFile.length() + " bytes): " + logContent.substring(0, Math.min(500, logContent.length())));
                
                if (logContent.contains("Agent started. Waiting for tasks...") || 
                    logContent.contains("Agent registered successfully!")) {
                    log("Startup log indicates successful start, verification passed");
                    return true;
                }
            } catch (IOException e) {
                logError("Failed to read startup log", e);
            }
        }
        
        log("No valid startup log found, verification failed");
        return false;
    }
    
    /**
     * 回滚到备份版本
     */
    private void rollback(String backupDir) throws IOException {
        log("Rolling back to backup: " + backupDir);
        
        Path backupPath = Paths.get(backupDir);
        
        // 恢复主程序JAR文件
        Path backupJar = backupPath.resolve(MAIN_JAR_NAME);
        if (Files.exists(backupJar)) {
            Path currentJar = Paths.get(agentHome, MAIN_JAR_NAME);
            
            long backupSize = Files.size(backupJar);
            Files.copy(backupJar, currentJar, StandardCopyOption.REPLACE_EXISTING);
            long restoredSize = Files.size(currentJar);
            
            log("Main JAR restored from backup: " + MAIN_JAR_NAME + " (backup: " + backupSize + " bytes, restored: " + restoredSize + " bytes)");
            
            if (backupSize != restoredSize) {
                throw new IOException("Rollback file size mismatch");
            }
        } else {
            throw new IOException("Backup JAR file not found: " + backupJar);
        }
        
        // 启动原版本
        startNewVersion();
        log("Original version restored and started");
    }
    
    /**
     * 清理文件
     */
    private void cleanupFiles(String newVersionFilename) {
        log("Cleaning up temporary files...");
        
        try {
            // 清理下载的新版本文件
            Path newVersionFile = Paths.get(agentHome, newVersionFilename);
            if (Files.exists(newVersionFile)) {
                Files.delete(newVersionFile);
                log("Temporary file cleaned up: " + newVersionFilename);
            }
        } catch (IOException e) {
            logError("Failed to cleanup temporary files", e);
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
                        log("Failed to delete: " + path + " - " + e.getMessage());
                    }
                });
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
    private void ensureStartScripts() throws IOException {
        log("Ensuring start scripts exist...");
        
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
                "AGENT_JAR=\"" + MAIN_JAR_NAME + "\"\n\n" +
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
            shScript.setExecutable(true);
            log("Created start-agent.sh script");
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
                "set \"AGENT_JAR=" + MAIN_JAR_NAME + "\"\n\n" +
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
            log("Created start-agent.bat script");
        }
        
        log("Start scripts are ready");
    }
}