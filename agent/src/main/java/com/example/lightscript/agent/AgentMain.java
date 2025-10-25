package com.example.lightscript.agent;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.http.HttpClient;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AgentMain {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static FileChannel lockChannel;
    private static FileLock lock;

    public static void main(String[] args) throws Exception {
        // ===== 单实例检查：防止同一台机器启动多个Agent =====
        if (!acquireLock()) {
            System.err.println("========================================");
            System.err.println("ERROR: Another Agent instance is already running on this machine!");
            System.err.println("Please stop the existing Agent before starting a new one.");
            System.err.println("========================================");
            System.exit(1);
        }
        
        String server = args.length > 0 ? args[0] : "http://localhost:8080";
        String registerToken = args.length > 1 ? args[1] : "dev-register-token";

        System.out.println("Starting LightScript Agent...");
        System.out.println("Server: " + server);
        System.out.println("Register Token: " + registerToken);

        // 获取主机信息
        String hostname = java.net.InetAddress.getLocalHost().getHostName();
        String osType = System.getProperty("os.name").toLowerCase().contains("win") ? "WINDOWS" : "LINUX";

        // 创建HTTP客户端
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        AgentApi api = new AgentApi(server, client, MAPPER);

        // 注册Agent
        System.out.println("Registering agent...");
        Map<String, Object> reg = api.register(registerToken, hostname, osType);
        String agentId = String.valueOf(reg.get("agentId"));
        String agentToken = String.valueOf(reg.get("agentToken"));
        
        System.out.println("Agent registered successfully!");
        System.out.println("Agent ID: " + agentId);
        System.out.println("Agent Token: " + agentToken);

        // 创建任务执行器
        SimpleTaskRunner taskRunner = new SimpleTaskRunner(api, agentId, agentToken);
        ExecutorService taskExecutor = Executors.newFixedThreadPool(2);

        // 添加关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Agent shutting down...");
            
            // 主动通知服务器下线
            try {
                System.out.println("Notifying server of shutdown...");
                api.offline(agentId, agentToken);
                System.out.println("Server notified successfully");
            } catch (Exception e) {
                System.err.println("Failed to notify server: " + e.getMessage());
                // 忽略错误，继续关闭流程
            }
            
            taskRunner.shutdown();
            taskExecutor.shutdown();
            releaseLock(); // 释放文件锁
        }));

        System.out.println("Agent started. Waiting for tasks...");

        // 主循环
        long lastHeartbeat = 0L;
        while (!Thread.currentThread().isInterrupted()) {
            long now = System.currentTimeMillis();

            try {
                // 心跳检测 - 每30秒一次
                if (now - lastHeartbeat > 30_000) {
                    System.out.println("Sending heartbeat...");
                    api.heartbeat(agentId, agentToken);
                    System.out.println("Heartbeat sent at " + new java.util.Date());
                    lastHeartbeat = now;
                }
                
                // 拉取任务（不打印日志避免刷屏）
                Map<String, Object> response = api.pull(agentId, agentToken, 1);
                
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> tasks = (List<Map<String, Object>>) response.get("tasks");

                if (tasks != null && !tasks.isEmpty()) {
                    for (Map<String, Object> task : tasks) {
                        // 调试：打印完整的任务数据
                        System.out.println("DEBUG: Received task data: " + task);
                        
                        // 使用与服务器端TaskSpec一致的字段名
                        String taskId = String.valueOf(task.get("taskId"));
                        String scriptLang = String.valueOf(task.get("scriptLang"));
                        String scriptContent = String.valueOf(task.get("scriptContent"));
                        Integer timeoutSec = (Integer) task.getOrDefault("timeoutSec", 300);

                        System.out.println("Received task: " + taskId + " (lang: " + scriptLang + ")");
                        
                        // 检查是否所有必要字段都存在
                        if (taskId == null || "null".equals(taskId) || scriptContent == null || "null".equals(scriptContent)) {
                            System.err.println("ERROR: Invalid task data - taskId or scriptContent is null");
                            System.err.println("  taskId: " + taskId);
                            System.err.println("  scriptLang: " + scriptLang);
                            System.err.println("  scriptContent: " + scriptContent);
                            continue;
                        }
                        
                        // 立即ACK确认收到任务
                        try {
                            api.ack(agentId, agentToken, taskId);
                            System.out.println("Task " + taskId + " acknowledged");
                        } catch (Exception e) {
                            System.err.println("Failed to ACK task " + taskId + ": " + e.getMessage());
                            // ACK失败，任务会被服务器回退到PENDING，跳过执行
                            continue;
                        }
                        
                        // 异步执行任务
                        taskExecutor.submit(() -> {
                            taskRunner.runTask(taskId, scriptLang, scriptContent, timeoutSec);
                        });
                    }
                }

                // 短暂休眠避免过度轮询
                Thread.sleep(5000);

            } catch (InterruptedException e) {
                System.out.println("Agent interrupted, shutting down...");
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("Error in main loop: " + e.getMessage());
                e.printStackTrace(); // 打印完整堆栈
                try {
                    Thread.sleep(10000); // 出错时等待更长时间
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        System.out.println("Agent main loop ended");
    }
    
    /**
     * 获取文件锁，确保单实例运行
     * @return true 如果成功获取锁，false 如果已有其他实例在运行
     */
    private static boolean acquireLock() {
        try {
            // 锁文件位置：用户目录/.lightscript/.agent.lock
            String userHome = System.getProperty("user.home");
            Path lockDir = Paths.get(userHome, ".lightscript");
            Files.createDirectories(lockDir);
            Path lockFile = lockDir.resolve(".agent.lock");
            
            // 打开文件通道（读写模式）
            lockChannel = FileChannel.open(lockFile, 
                StandardOpenOption.CREATE, 
                StandardOpenOption.READ, 
                StandardOpenOption.WRITE);
            
            // 尝试获取排他锁（非阻塞）
            lock = lockChannel.tryLock();
            
            if (lock == null) {
                // 锁已被其他进程持有
                lockChannel.close();
                return false;
            }
            
            // 写入当前进程信息（用于调试）
            lockChannel.truncate(0);
            String lockInfo = String.format("PID: %s, Started: %s", 
                ManagementFactory.getRuntimeMXBean().getName(),
                LocalDateTime.now()
            );
            lockChannel.write(ByteBuffer.wrap(lockInfo.getBytes()));
            
            System.out.println("Instance lock acquired: " + lockFile);
            return true;
            
        } catch (IOException e) {
            System.err.println("Failed to acquire instance lock: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 释放文件锁
     */
    private static void releaseLock() {
        try {
            if (lock != null) {
                lock.release();
                lock = null;
            }
            if (lockChannel != null) {
                lockChannel.close();
                lockChannel = null;
            }
            System.out.println("Instance lock released");
        } catch (IOException e) {
            System.err.println("Failed to release lock: " + e.getMessage());
        }
    }
}