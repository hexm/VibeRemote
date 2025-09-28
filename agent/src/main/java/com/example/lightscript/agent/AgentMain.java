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
                    api.heartbeat(agentId, agentToken);
                    System.out.println("Heartbeat sent at " + new java.util.Date());
                    lastHeartbeat = now;
                }

                // 拉取任务
                Map<String, Object> response = api.pull(agentId, agentToken, 1);
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> tasks = (List<Map<String, Object>>) response.get("tasks");

                if (tasks != null && !tasks.isEmpty()) {
                    for (Map<String, Object> task : tasks) {
                        String taskId = String.valueOf(task.get("id"));
                        String scriptType = String.valueOf(task.get("scriptType"));
                        String scriptContent = String.valueOf(task.get("scriptContent"));
                        Integer timeoutSec = (Integer) task.getOrDefault("timeoutSec", 300);

                        System.out.println("Received task: " + taskId);
                        
                        // 异步执行任务
                        taskExecutor.submit(() -> {
                            taskRunner.runTask(taskId, scriptType, scriptContent, timeoutSec);
                        });
                    }
                }

                // 短暂休眠避免过度轮询
                Thread.sleep(5000);

            } catch (Exception e) {
                System.err.println("Error in main loop: " + e.getMessage());
                Thread.sleep(10000); // 出错时等待更长时间
            }
        }
    }
}