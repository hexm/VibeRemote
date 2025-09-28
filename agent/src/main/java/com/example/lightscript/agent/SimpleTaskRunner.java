package com.example.lightscript.agent;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

class SimpleTaskRunner {
    private final AgentApi api;
    private final String agentId;
    private final String agentToken;
    private volatile boolean shutdown = false;

    SimpleTaskRunner(AgentApi api, String agentId, String agentToken) {
        this.api = api;
        this.agentId = agentId;
        this.agentToken = agentToken;
    }

    void shutdown() {
        this.shutdown = true;
    }

    void runTask(String taskId, String scriptType, String scriptContent, int timeoutSec) {
        int seq = 0;
        try {
            api.sendLog(agentId, agentToken, taskId, ++seq, "system", "Task started");
            
            // 构建命令
            ProcessBuilder pb;
            if (isWindows()) {
                if ("powershell".equalsIgnoreCase(scriptType)) {
                    pb = new ProcessBuilder("powershell", "-Command", scriptContent);
                } else {
                    pb = new ProcessBuilder("cmd", "/c", scriptContent);
                }
            } else {
                pb = new ProcessBuilder("bash", "-c", scriptContent);
            }
            
            pb.redirectErrorStream(false);
            Process p = pb.start();
            
            // 读取输出
            final int finalSeq = seq;
            Thread stdoutThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String line;
                    int localSeq = finalSeq;
                    while ((line = reader.readLine()) != null && !shutdown) {
                        try {
                            api.sendLog(agentId, agentToken, taskId, ++localSeq, "stdout", line);
                        } catch (Exception e) {
                            System.err.println("Failed to send stdout log: " + e.getMessage());
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error reading stdout: " + e.getMessage());
                }
            });
            
            Thread stderrThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getErrorStream()))) {
                    String line;
                    int localSeq = finalSeq + 1000; // 避免seq冲突
                    while ((line = reader.readLine()) != null && !shutdown) {
                        try {
                            api.sendLog(agentId, agentToken, taskId, ++localSeq, "stderr", line);
                        } catch (Exception e) {
                            System.err.println("Failed to send stderr log: " + e.getMessage());
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error reading stderr: " + e.getMessage());
                }
            });
            
            stdoutThread.start();
            stderrThread.start();
            
            // 等待进程完成
            boolean finished = p.waitFor(timeoutSec, TimeUnit.SECONDS);
            
            if (!finished) {
                p.destroyForcibly();
                api.sendLog(agentId, agentToken, taskId, ++seq, "system", "Process timeout after " + timeoutSec + " seconds");
                api.finish(agentId, agentToken, taskId, -1, "TIMEOUT", "Process timeout");
            } else {
                int exitCode = p.exitValue();
                String status = exitCode == 0 ? "SUCCESS" : "FAILED";
                api.sendLog(agentId, agentToken, taskId, ++seq, "system", "Process finished with exit code: " + exitCode);
                api.finish(agentId, agentToken, taskId, exitCode, status, "exitCode=" + exitCode);
            }
            
            // 等待日志线程完成
            stdoutThread.join(2000);
            stderrThread.join(2000);
            
        } catch (Exception e) {
            try {
                api.sendLog(agentId, agentToken, taskId, ++seq, "stderr", "Exception: " + e.getMessage());
                api.finish(agentId, agentToken, taskId, -2, "FAILED", e.toString());
            } catch (Exception ignored) {
                System.err.println("Failed to report task failure: " + ignored.getMessage());
            }
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}
