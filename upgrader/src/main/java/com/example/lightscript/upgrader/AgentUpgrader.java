package com.example.lightscript.upgrader;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 终极简化的Agent升级器程序
 * 只需要1个参数：新版本文件名
 * 主程序文件名固定为：agent.jar
 */
public class AgentUpgrader {
    private static final String AGENT_HOME_PROPERTY = "agent.home";
    private static final String UPGRADER_LAUNCHD_LABEL_PROPERTY = "upgrader.launchd.label";
    private static final String MAIN_JAR_NAME = "agent.jar";
    private static final String LOCK_DIR = ".lightscript";
    private static final String LOCK_FILE = ".agent.lock";
    private static final String UPGRADE_CONTEXT_FILE = ".upgrade-context.json";
    private static final String MACOS_LAUNCH_AGENT_LABEL = "com.viberemote.agent";
    private static final String LEGACY_MACOS_LAUNCH_AGENT_LABEL = "com.lightscript.agent";
    private static final long PROCESS_EXIT_SOFT_TIMEOUT_MS = 30000;
    private static final long PROCESS_EXIT_HARD_TIMEOUT_MS = 120000;
    private static final long PROCESS_EXIT_POLL_INTERVAL_MS = 1000;
    private static final long PROCESS_EXIT_SLOW_POLL_INTERVAL_MS = 60000;
    private static final long FORCE_KILL_WAIT_MS = 10000;
    private static final DateTimeFormatter LOG_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    
    private PrintWriter logWriter;
    private String agentHome;
    private Long oldAgentPid;
    private UpgradeStatusContext upgradeStatusContext;
    private String submittedLaunchLabel;
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar upgrader.jar <new-version-filename>");
            System.err.println("  new-version-filename: New agent JAR file name (e.g., agent-2.1.0.jar)");
            System.err.println("  Main JAR is fixed as: " + MAIN_JAR_NAME);
            System.exit(1);
        }
        
        String newVersionFilename = args[0];
        String agentHome = resolveAgentHome();
        
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
            boolean upgraded = upgrader.performUpgrade(newVersionFilename);
            if (upgraded) {
                upgrader.log("Upgrade completed successfully");
                System.out.println("Upgrade completed successfully");
            } else {
                upgrader.log("Upgrade exited without running because the launchd job was stale");
                System.out.println("Upgrade exited without running because the launchd job was stale");
            }
            System.exit(0);
        } catch (Exception e) {
            System.err.println("Upgrade failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static String resolveAgentHome() {
        String configuredHome = System.getProperty(AGENT_HOME_PROPERTY);
        if (configuredHome != null && !configuredHome.trim().isEmpty()) {
            return Paths.get(configuredHome).toAbsolutePath().normalize().toString();
        }

        try {
            Path codeSource = Paths.get(AgentUpgrader.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                .toAbsolutePath()
                .normalize();
            if (Files.isRegularFile(codeSource)) {
                return codeSource.getParent().toString();
            }
        } catch (Exception ignored) {
        }

        return Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize().toString();
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
        submittedLaunchLabel = resolveSubmittedLaunchLabel();
        if (submittedLaunchLabel != null) {
            log("Submitted launchctl job label: " + submittedLaunchLabel);
        }
        upgradeStatusContext = loadUpgradeStatusContext();
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
    private boolean performUpgrade(String newVersionFilename) throws Exception {
        log("Starting Agent upgrade process...");
        log("New version file: " + newVersionFilename);
        oldAgentPid = readPidFromFile();
        if (oldAgentPid != null) {
            log("Captured old agent PID before upgrade: " + oldAgentPid);
        } else {
            log("No old agent PID captured before upgrade; will rely on instance lock only");
        }

        String backupDir = null;
        
        try {
            if (shouldSkipStaleSubmittedRelaunch(newVersionFilename)) {
                log("Upgrade context is missing and this submitted launchctl job looks stale; skipping duplicate upgrader run");
                return false;
            }

            reportUpgradeStatus("INSTALLING", null);

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
                reportUpgradeStatus("SUCCESS", null);
                return true;
            } else {
                throw new Exception("New version failed to start");
            }
            
        } catch (Exception e) {
            logError("Upgrade failed, attempting rollback", e);
            
            if (backupDir != null) {
                try {
                    rollback(backupDir);
                    if (verifyStartup()) {
                        log("Rollback completed successfully and original version is running");
                        reportUpgradeStatus("ROLLBACK", e.getMessage());
                    } else {
                        throw new Exception("Rollback completed but original version failed to start");
                    }
                } catch (Exception rollbackError) {
                    logError("Rollback also failed", rollbackError);
                    reportUpgradeStatus("FAILED", rollbackError.getMessage());
                }
            } else {
                reportUpgradeStatus("FAILED", e.getMessage());
            }
            throw e;
        } finally {
            cleanupUpgradeStatusContext();
            cleanupSubmittedLaunchJob();
            if (logWriter != null) {
                log("=== Agent Upgrade Finished ===");
                logWriter.close();
            }
        }
    }

    private String resolveSubmittedLaunchLabel() {
        String explicit = System.getProperty(UPGRADER_LAUNCHD_LABEL_PROPERTY);
        if (explicit != null && !explicit.trim().isEmpty()) {
            return explicit.trim();
        }
        return null;
    }

    private boolean shouldSkipStaleSubmittedRelaunch(String newVersionFilename) {
        if (submittedLaunchLabel == null || upgradeStatusContext != null) {
            return false;
        }

        Path newVersionFile = Paths.get(agentHome, newVersionFilename);
        return !Files.exists(newVersionFile);
    }
    
    /**
     * 等待主进程退出
     */
    private void waitForProcessExit() throws InterruptedException {
        log("Waiting for main process to exit...");
        long startTime = System.currentTimeMillis();
        boolean forceKillAttempted = false;
        long lastSlowModeLogAt = -1L;
        long lastSlowModeCleanupAt = -1L;
        while (true) {
            if (canAcquireAgentLock()) {
                cleanupLockFileIfPresent();
                log("Process exit confirmed by released instance lock");
                return;
            }

            long elapsed = System.currentTimeMillis() - startTime;
            if (!forceKillAttempted && elapsed >= PROCESS_EXIT_SOFT_TIMEOUT_MS) {
                log("Main process still running after " + (PROCESS_EXIT_SOFT_TIMEOUT_MS / 1000) + " seconds, trying to stop it forcefully");
                forceKillAttempted = true;
                attemptToTerminateOldAgent();
            }

            if (elapsed >= PROCESS_EXIT_HARD_TIMEOUT_MS) {
                if (lastSlowModeLogAt < 0 || System.currentTimeMillis() - lastSlowModeLogAt >= PROCESS_EXIT_SLOW_POLL_INTERVAL_MS) {
                    log("Main process still not fully exited after " + (PROCESS_EXIT_HARD_TIMEOUT_MS / 1000) + " seconds; continuing to wait and checking once per minute");
                    lastSlowModeLogAt = System.currentTimeMillis();
                }
                if (lastSlowModeCleanupAt < 0 || System.currentTimeMillis() - lastSlowModeCleanupAt >= PROCESS_EXIT_SLOW_POLL_INTERVAL_MS) {
                    attemptToTerminateOldAgent();
                    lastSlowModeCleanupAt = System.currentTimeMillis();
                }
                Thread.sleep(PROCESS_EXIT_SLOW_POLL_INTERVAL_MS);
                continue;
            }

            Thread.sleep(PROCESS_EXIT_POLL_INTERVAL_MS);
        }
    }

    private boolean canAcquireAgentLock() {
        String userHome = System.getProperty("user.home");
        Path lockDir = Paths.get(userHome, LOCK_DIR);
        Path lockFile = lockDir.resolve(LOCK_FILE);

        try {
            Files.createDirectories(lockDir);
            try (FileChannel channel = FileChannel.open(lockFile,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.READ,
                    StandardOpenOption.WRITE)) {
                FileLock testLock = channel.tryLock();
                if (testLock == null) {
                    return false;
                }
                try {
                    channel.truncate(0);
                    channel.write(ByteBuffer.wrap(("Released checked at " + LocalDateTime.now()).getBytes()));
                    return true;
                } finally {
                    testLock.release();
                }
            }
        } catch (IOException e) {
            logError("Failed to check instance lock", e);
            return false;
        }
    }

    private void cleanupLockFileIfPresent() {
        try {
            String userHome = System.getProperty("user.home");
            Path lockFile = Paths.get(userHome, LOCK_DIR, LOCK_FILE);
            if (Files.exists(lockFile)) {
                Files.delete(lockFile);
                log("Cleaned up global lock file: " + lockFile);
            }
        } catch (Exception e) {
            logError("Failed to clean global lock file", e);
        }
    }

    private void attemptToTerminateOldAgent() {
        try {
            List<Long> pids = findOldAgentPids();
            if (pids.isEmpty()) {
                log("No running old agent process found, continuing to wait for lock release");
                return;
            }

            log("Found old agent process(es): " + pids);
            if (isWindows()) {
                for (Long pid : pids) {
                    runCommand(new String[] {"taskkill", "/PID", String.valueOf(pid), "/T", "/F"}, false);
                }
                return;
            }

            for (Long pid : pids) {
                runCommand(new String[] {"kill", "-TERM", String.valueOf(pid)}, true);
            }

            long termDeadline = System.currentTimeMillis() + FORCE_KILL_WAIT_MS;
            while (System.currentTimeMillis() < termDeadline) {
                if (findOldAgentPids().isEmpty()) {
                    log("Old agent process exited after TERM signal");
                    return;
                }
                Thread.sleep(PROCESS_EXIT_POLL_INTERVAL_MS);
            }

            List<Long> remaining = findOldAgentPids();
            if (!remaining.isEmpty()) {
                log("Old agent still running after TERM, sending KILL: " + remaining);
                for (Long pid : remaining) {
                    runCommand(new String[] {"kill", "-KILL", String.valueOf(pid)}, true);
                }
            }
        } catch (Exception e) {
            logError("Failed while forcefully stopping old agent process", e);
        }
    }

    private List<Long> findOldAgentPids() {
        List<Long> result = new ArrayList<Long>();

        if (oldAgentPid != null && isProcessAlive(oldAgentPid.longValue())) {
            result.add(oldAgentPid);
            return result;
        }

        if (isWindows()) {
            return result;
        }

        return result;
    }

    private Long readPidFromFile() {
        Path pidFile = Paths.get(agentHome, "agent.pid");
        if (!Files.exists(pidFile)) {
            return null;
        }

        try {
            String content = new String(Files.readAllBytes(pidFile), StandardCharsets.UTF_8).trim();
            if (content.isEmpty()) {
                return null;
            }
            return Long.parseLong(content);
        } catch (Exception e) {
            logError("Failed to read captured agent.pid", e);
            return null;
        }
    }

    private boolean isProcessAlive(long pid) {
        if (pid <= 0) {
            return false;
        }

        try {
            if (isWindows()) {
                String output = runCommand(new String[] {"cmd", "/c", "tasklist", "/FI", "PID eq " + pid}, true);
                return output.contains(String.valueOf(pid));
            }
            int exitCode = new ProcessBuilder("kill", "-0", String.valueOf(pid)).start().waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            logError("Failed to check whether PID is alive: " + pid, e);
            return false;
        }
    }

    private String runCommand(String[] command, boolean ignoreFailure) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).start();
        String stdout = readStream(process.getInputStream());
        String stderr = readStream(process.getErrorStream());
        int exitCode = process.waitFor();
        if (exitCode != 0 && !ignoreFailure) {
            throw new IOException("Command failed (" + String.join(" ", command) + "): " + stderr);
        }
        if (exitCode != 0 && ignoreFailure) {
            log("Command exited with code " + exitCode + ": " + String.join(" ", command) + ", stderr=" + stderr.trim());
        }
        return stdout;
    }

    private String readStream(InputStream inputStream) throws IOException {
        byte[] bytes = new byte[0];
        byte[] buffer = new byte[4096];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            byte[] merged = new byte[bytes.length + read];
            System.arraycopy(bytes, 0, merged, 0, bytes.length);
            System.arraycopy(buffer, 0, merged, bytes.length, read);
            bytes = merged;
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private UpgradeStatusContext loadUpgradeStatusContext() {
        Path contextFile = Paths.get(agentHome, UPGRADE_CONTEXT_FILE);
        if (!Files.exists(contextFile)) {
            log("Upgrade status context file not found: " + contextFile);
            return null;
        }

        try {
            byte[] jsonBytes = Files.readAllBytes(contextFile);
            Map<String, Object> context = new ObjectMapper().readValue(
                jsonBytes,
                new TypeReference<Map<String, Object>>() {}
            );

            UpgradeStatusContext loaded = new UpgradeStatusContext();
            loaded.serverUrl = stringValue(context.get("serverUrl"));
            loaded.agentId = stringValue(context.get("agentId"));
            loaded.agentToken = stringValue(context.get("agentToken"));
            loaded.upgradeLogId = longValue(context.get("upgradeLogId"));
            log("Loaded upgrade status context for logId=" + loaded.upgradeLogId);
            return loaded.isValid() ? loaded : null;
        } catch (Exception e) {
            logError("Failed to load upgrade status context", e);
            return null;
        }
    }

    private void reportUpgradeStatus(String status, String errorMessage) {
        if (upgradeStatusContext == null || !upgradeStatusContext.isValid()) {
            log("Skipping upgrade status report because context is unavailable, status=" + status);
            return;
        }

        CloseableHttpClient httpClient = HttpClients.createDefault();
        try {
            Map<String, Object> request = new HashMap<String, Object>();
            request.put("upgradeLogId", upgradeStatusContext.upgradeLogId);
            request.put("status", status);
            if (errorMessage != null && !errorMessage.trim().isEmpty()) {
                request.put("errorMessage", errorMessage);
            }

            String url = upgradeStatusContext.serverUrl
                + "/api/agent/upgrade/status?agentId="
                + upgradeStatusContext.agentId
                + "&agentToken="
                + upgradeStatusContext.agentToken;

            HttpPost post = new HttpPost(url);
            post.setHeader("Content-Type", "application/json");
            post.setEntity(new StringEntity(new ObjectMapper().writeValueAsString(request), "UTF-8"));

            CloseableHttpResponse response = httpClient.execute(post);
            try {
                int statusCode = response.getStatusLine().getStatusCode();
                String body = response.getEntity() == null ? "" : EntityUtils.toString(response.getEntity(), "UTF-8");
                if (statusCode >= 200 && statusCode < 300) {
                    log("Reported upgrade status successfully: " + status);
                } else {
                    log("Failed to report upgrade status " + status + ", httpStatus=" + statusCode + ", response=" + body);
                }
            } finally {
                response.close();
            }
        } catch (Exception e) {
            logError("Failed to report upgrade status: " + status, e);
        } finally {
            try {
                httpClient.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void cleanupUpgradeStatusContext() {
        Path contextFile = Paths.get(agentHome, UPGRADE_CONTEXT_FILE);
        try {
            if (Files.exists(contextFile)) {
                Files.delete(contextFile);
                log("Deleted upgrade status context file");
            }
        } catch (Exception e) {
            logError("Failed to delete upgrade status context file", e);
        }
    }

    private void cleanupSubmittedLaunchJob() {
        if (submittedLaunchLabel == null || submittedLaunchLabel.trim().isEmpty() || !isMacOS()) {
            return;
        }

        try {
            Process process = new ProcessBuilder("launchctl", "remove", submittedLaunchLabel)
                .start();
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                if (logWriter != null) {
                    logWriter.flush();
                }
            } else if (logWriter != null) {
                String stderr = readStream(process.getErrorStream()).trim();
                log("Failed to remove submitted launchctl job " + submittedLaunchLabel
                    + ", exitCode=" + exitCode + ", stderr=" + stderr);
            }
        } catch (Exception e) {
            if (logWriter != null) {
                logError("Failed to remove submitted launchctl job " + submittedLaunchLabel, e);
            }
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long longValue(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
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

        if (tryStartViaLaunchAgent()) {
            return;
        }

        String javaCommand = resolveJavaCommand();
        File logsDir = new File(agentHome, "logs");
        logsDir.mkdirs();
        File agentLogDir = logsDir.getAbsoluteFile();
        File startupLog = new File(logsDir, "agent-startup.log");
        File startupErrorLog = new File(logsDir, "agent-startup-error.log");
        ProcessBuilder pb = new ProcessBuilder();
        pb.directory(new File(agentHome));

        if (isWindows()) {
            List<String> command = new ArrayList<String>();
            command.add(javaCommand);
            command.add("-Xms32m");
            command.add("-Xmx128m");
            command.add("-XX:MaxMetaspaceSize=64m");
            command.add("-Dfile.encoding=UTF-8");
            command.add("-Dagent.home=" + agentHome);
            command.add("-Dlog.home=" + agentLogDir.getAbsolutePath());
            command.add("-jar");
            command.add(MAIN_JAR_NAME);
            pb.command(command);
            pb.redirectOutput(startupLog);
            pb.redirectError(startupErrorLog);
        } else {
            String shellCommand = String.format(
                "cd %s && nohup %s -Xms32m -Xmx128m -XX:MaxMetaspaceSize=64m -Dfile.encoding=UTF-8 -Dagent.home=%s -Dlog.home=%s -jar %s >> %s 2>> %s < /dev/null &",
                shellQuote(agentHome),
                shellQuote(javaCommand),
                shellQuote(agentHome),
                shellQuote(agentLogDir.getAbsolutePath()),
                shellQuote(MAIN_JAR_NAME),
                shellQuote(startupLog.getAbsolutePath()),
                shellQuote(startupErrorLog.getAbsolutePath())
            );
            pb.command("/bin/bash", "-lc", shellCommand);
        }

        pb.start();
        log("New agent process started with java: " + javaCommand);
    }

    private boolean tryStartViaLaunchAgent() throws IOException {
        if (!isMacOS()) {
            return false;
        }

        LaunchAgentSpec launchAgent = findLaunchAgentSpec();
        if (launchAgent == null) {
            return false;
        }

        try {
            if (!isLaunchAgentLoaded(launchAgent)) {
                String[] bootstrapCommand = new String[] {
                    "launchctl", "bootstrap", launchAgent.domain, launchAgent.plistPath.toString()
                };
                try {
                    runCommand(bootstrapCommand, false);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while bootstrapping LaunchAgent", e);
                }
            }

            String[] kickstartCommand = new String[] {
                "launchctl", "kickstart", "-k", launchAgent.domain + "/" + launchAgent.label
            };
            try {
                runCommand(kickstartCommand, false);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while kickstarting LaunchAgent", e);
            }

            log("LaunchAgent kickstarted successfully: " + launchAgent.domain + "/" + launchAgent.label);
            return true;
        } catch (IOException e) {
            logError("Failed to restart agent via LaunchAgent, falling back to direct java start", e);
            return false;
        }
    }

    private boolean isLaunchAgentLoaded(LaunchAgentSpec launchAgent) {
        try {
            Process process = new ProcessBuilder(
                "launchctl",
                "print",
                launchAgent.domain + "/" + launchAgent.label
            ).start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            logError("Failed to check whether LaunchAgent is loaded", e);
            return false;
        }
    }

    private LaunchAgentSpec findLaunchAgentSpec() {
        Path userAgentsDir = Paths.get(System.getProperty("user.home"), "Library", "LaunchAgents");
        String uid = resolveCurrentUid();
        if (uid != null) {
            Path plist = userAgentsDir.resolve(MACOS_LAUNCH_AGENT_LABEL + ".plist");
            if (Files.exists(plist)) {
                return new LaunchAgentSpec("gui/" + uid, MACOS_LAUNCH_AGENT_LABEL, plist);
            }

            Path legacyPlist = userAgentsDir.resolve(LEGACY_MACOS_LAUNCH_AGENT_LABEL + ".plist");
            if (Files.exists(legacyPlist)) {
                return new LaunchAgentSpec("gui/" + uid, LEGACY_MACOS_LAUNCH_AGENT_LABEL, legacyPlist);
            }
        }

        Path systemDaemon = Paths.get("/Library/LaunchDaemons", MACOS_LAUNCH_AGENT_LABEL + ".plist");
        if (Files.exists(systemDaemon)) {
            return new LaunchAgentSpec("system", MACOS_LAUNCH_AGENT_LABEL, systemDaemon);
        }

        Path legacySystemDaemon = Paths.get("/Library/LaunchDaemons", LEGACY_MACOS_LAUNCH_AGENT_LABEL + ".plist");
        if (Files.exists(legacySystemDaemon)) {
            return new LaunchAgentSpec("system", LEGACY_MACOS_LAUNCH_AGENT_LABEL, legacySystemDaemon);
        }

        return null;
    }

    private String resolveCurrentUid() {
        try {
            return runCommand(new String[] {"id", "-u"}, false).trim();
        } catch (Exception e) {
            logError("Failed to resolve current uid for LaunchAgent restart", e);
            return null;
        }
    }

    private static class UpgradeStatusContext {
        private String serverUrl;
        private String agentId;
        private String agentToken;
        private Long upgradeLogId;

        private boolean isValid() {
            return serverUrl != null && !serverUrl.trim().isEmpty()
                && agentId != null && !agentId.trim().isEmpty()
                && agentToken != null && !agentToken.trim().isEmpty()
                && upgradeLogId != null;
        }
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
        File agentLogFile = new File(logsDir, "agent.log");
        
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
        
        // 启动脚本 stdout 只包含少量 System.out，真正的生命周期日志在 agent.log。
        if (agentLogFile.exists() && agentLogFile.length() > 0) {
            try {
                String agentLogContent = new String(Files.readAllBytes(agentLogFile.toPath()), StandardCharsets.UTF_8);
                int start = Math.max(0, agentLogContent.length() - 4000);
                String tail = agentLogContent.substring(start);
                if (tail.contains("AGENT STARTUP COMPLETE")
                        || tail.contains("Agent started successfully. Waiting for tasks...")
                        || tail.contains("Heartbeat with system info sent successfully")) {
                    log("agent.log indicates successful start, verification passed");
                    return true;
                }
            } catch (IOException e) {
                logError("Failed to read agent.log during startup verification", e);
            }
        }

        // 检查启动日志是否存在且有内容
        if (logFile.exists() && logFile.length() > 0) {
            try {
                String logContent = new String(Files.readAllBytes(logFile.toPath()), StandardCharsets.UTF_8);
                log("Startup log content (" + logFile.length() + " bytes): " + logContent.substring(0, Math.min(500, logContent.length())));
                
                if (logContent.contains("Agent started. Waiting for tasks...") || 
                    logContent.contains("Agent registered successfully!") ||
                    (logContent.contains("Instance lock acquired")
                        && logContent.contains("Working directory:")
                        && (logContent.contains("Agent home:")
                            || logContent.contains("Loaded external configuration")))) {
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

    private String resolveJavaCommand() {
        Path bundledJava = Paths.get(agentHome, "jre", "bin", isWindows() ? "java.exe" : "java");
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

    private boolean isMacOS() {
        return System.getProperty("os.name").toLowerCase().contains("mac");
    }

    private static class LaunchAgentSpec {
        private final String domain;
        private final String label;
        private final Path plistPath;

        private LaunchAgentSpec(String domain, String label, Path plistPath) {
            this.domain = domain;
            this.label = label;
            this.plistPath = plistPath;
        }
    }

    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
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
