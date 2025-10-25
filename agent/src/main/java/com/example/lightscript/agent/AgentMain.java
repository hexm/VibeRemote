package com.example.lightscript.agent;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AgentMain {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) throws Exception {
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
            taskRunner.shutdown();
            taskExecutor.shutdown();
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
}