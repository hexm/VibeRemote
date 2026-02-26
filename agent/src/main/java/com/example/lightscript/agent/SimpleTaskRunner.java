package com.example.lightscript.agent;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

class SimpleTaskRunner {
    private final AgentApi api;
    private volatile String agentId;
    private volatile String agentToken;
    private volatile boolean shutdown = false;

    SimpleTaskRunner(AgentApi api, String agentId, String agentToken) {
        this.api = api;
        this.agentId = agentId;
        this.agentToken = agentToken;
    }

    void shutdown() {
        this.shutdown = true;
    }
    
    /**
     * 更新agent凭证（在重新注册后调用）
     */
    synchronized void updateCredentials(String newAgentId, String newAgentToken) {
        this.agentId = newAgentId;
        this.agentToken = newAgentToken;
        System.out.println("[TaskRunner] Credentials updated");
    }

    void runTask(Long executionId, String taskId, String scriptLang, String scriptContent, int timeoutSec) {
        int seq = 0;
        try {
            // 首先确认任务开始执行
            api.ackTask(agentId, agentToken, executionId);
            api.sendLog(agentId, agentToken, executionId, ++seq, "system", "Task started (lang: " + scriptLang + ")");
            
            // 构建命令
            ProcessBuilder pb;
            if (isWindows()) {
                if ("powershell".equalsIgnoreCase(scriptLang)) {
                    pb = new ProcessBuilder("powershell", "-Command", scriptContent);
                } else {
                    // 默认使用cmd，支持cmd或bat
                    pb = new ProcessBuilder("cmd", "/c", scriptContent);
                }
            } else {
                // Linux下默认使用bash
                pb = new ProcessBuilder("bash", "-c", scriptContent);
            }
            
            pb.redirectErrorStream(false);
            Process p = pb.start();
            
            // 读取输出
            final int finalSeq = seq;
            // Windows下使用GBK编码，Linux使用UTF-8
            String charset = isWindows() ? "GBK" : "UTF-8";
            
            Thread stdoutThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), charset))) {
                    String line;
                    int localSeq = finalSeq;
                    while ((line = reader.readLine()) != null && !shutdown) {
                        // 过滤空行，避免服务端验证失败
                        if (line.trim().isEmpty()) {
                            continue;
                        }
                        try {
                            api.sendLog(agentId, agentToken, executionId, ++localSeq, "stdout", line);
                        } catch (Exception e) {
                            System.err.println("Failed to send stdout log: " + e.getMessage());
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error reading stdout: " + e.getMessage());
                }
            });
            
            Thread stderrThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getErrorStream(), charset))) {
                    String line;
                    int localSeq = finalSeq + 1000; // 避免seq冲突
                    while ((line = reader.readLine()) != null && !shutdown) {
                        // 过滤空行，避免服务端验证失败
                        if (line.trim().isEmpty()) {
                            continue;
                        }
                        try {
                            api.sendLog(agentId, agentToken, executionId, ++localSeq, "stderr", line);
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
                api.sendLog(agentId, agentToken, executionId, ++seq, "system", "Process timeout after " + timeoutSec + " seconds");
                api.finish(agentId, agentToken, executionId, -1, "TIMEOUT", "Process timeout");
            } else {
                int exitCode = p.exitValue();
                String status = exitCode == 0 ? "SUCCESS" : "FAILED";
                api.sendLog(agentId, agentToken, executionId, ++seq, "system", "Process finished with exit code: " + exitCode);
                api.finish(agentId, agentToken, executionId, exitCode, status, "exitCode=" + exitCode);
            }
            
            // 等待日志线程完成
            stdoutThread.join(2000);
            stderrThread.join(2000);
            
        } catch (Exception e) {
            try {
                api.sendLog(agentId, agentToken, executionId, ++seq, "stderr", "Exception: " + e.getMessage());
                api.finish(agentId, agentToken, executionId, -2, "FAILED", e.toString());
            } catch (Exception ignored) {
                System.err.println("Failed to report task failure: " + ignored.getMessage());
            }
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}
