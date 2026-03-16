package com.example.lightscript.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 批量日志收集器 - 负责收集日志并批量发送到服务器
 * 使用统一缓冲区确保日志顺序正确
 */
public class BatchLogCollector {
    private final LogBuffer buffer;
    private final String baseUrl;
    private final CloseableHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ExecutorService sendExecutor;
    private final ScheduledExecutorService flushScheduler;
    
    // 全局序列号，确保跨线程的顺序
    private final AtomicInteger globalSeq = new AtomicInteger(0);
    
    private volatile String agentId;
    private volatile String agentToken;
    private volatile Long currentExecutionId;
    private volatile boolean shutdown = false;

    public BatchLogCollector(String baseUrl, CloseableHttpClient httpClient, ObjectMapper objectMapper) {
        this.baseUrl = baseUrl;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.buffer = new LogBuffer(); // 使用默认配置
        this.sendExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "BatchLogSender");
            t.setDaemon(true);
            return t;
        });
        this.flushScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "LogFlushScheduler");
            t.setDaemon(true);
            return t;
        });
        
        // 启动定时刷新任务
        startFlushScheduler();
    }

    /**
     * 更新Agent凭证
     */
    public void updateCredentials(String agentId, String agentToken) {
        this.agentId = agentId;
        this.agentToken = agentToken;
    }

    /**
     * 设置当前执行ID
     */
    public void setCurrentExecutionId(Long executionId) {
        this.currentExecutionId = executionId;
    }

    /**
     * 收集日志条目 - 使用全局序列号确保顺序
     */
    public void collectLog(String stream, String data) {
        if (shutdown || currentExecutionId == null) {
            return;
        }

        // 使用全局序列号和当前时间戳，确保跨线程的正确顺序
        long timestamp = System.currentTimeMillis();
        int seq = globalSeq.incrementAndGet();
        
        // 创建带有全局序列号的日志条目
        LogEntry entry = new LogEntry(seq, stream, data, timestamp);
        buffer.addLogEntry(entry);
        
        // 检查是否需要立即刷新
        if (buffer.shouldFlush()) {
            flushBuffer();
        }
    }

    /**
     * 强制刷新缓冲区
     */
    public void flushBuffer() {
        if (shutdown || currentExecutionId == null) {
            return;
        }

        List<LogEntry> logs = buffer.flush();
        if (!logs.isEmpty()) {
            sendBatchAsync(logs);
        }
    }

    /**
     * 异步发送批量日志
     */
    private void sendBatchAsync(List<LogEntry> logs) {
        CompletableFuture.runAsync(() -> {
            try {
                sendBatch(logs);
            } catch (Exception e) {
                System.err.println("批量日志发送失败: " + e.getMessage());
                // TODO: 实现重试机制 (Week 2)
            }
        }, sendExecutor);
    }

    /**
     * 发送批量日志到服务器
     */
    private void sendBatch(List<LogEntry> logs) throws Exception {
        if (agentId == null || agentToken == null || currentExecutionId == null) {
            throw new IllegalStateException("Agent凭证或执行ID未设置");
        }

        BatchLogRequest request = new BatchLogRequest(agentId, agentToken, currentExecutionId, logs);
        
        HttpPost post = new HttpPost(baseUrl + "/api/agent/tasks/executions/" + currentExecutionId + "/batch-log");
        post.setHeader("Content-Type", "application/json");
        post.setHeader("X-Batch-Size", String.valueOf(logs.size()));
        post.setEntity(new StringEntity(objectMapper.writeValueAsString(request), "UTF-8"));
        
        try (CloseableHttpResponse response = httpClient.execute(post)) {
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != 200) {
                throw new RuntimeException("服务器返回错误状态: " + statusCode);
            }
            System.out.println("成功发送批量日志: " + logs.size() + " 条");
        }
    }

    /**
     * 启动定时刷新调度器
     */
    private void startFlushScheduler() {
        flushScheduler.scheduleWithFixedDelay(() -> {
            if (!shutdown && buffer.hasLogs()) {
                flushBuffer();
            }
        }, 1, 1, TimeUnit.SECONDS); // 每秒检查一次
    }

    /**
     * 关闭收集器
     */
    public void shutdown() {
        shutdown = true;
        
        // 刷新剩余日志
        if (buffer.hasLogs()) {
            List<LogEntry> remainingLogs = buffer.flush();
            if (!remainingLogs.isEmpty()) {
                try {
                    sendBatch(remainingLogs);
                } catch (Exception e) {
                    System.err.println("关闭时发送剩余日志失败: " + e.getMessage());
                }
            }
        }
        
        // 关闭线程池
        flushScheduler.shutdown();
        sendExecutor.shutdown();
        
        try {
            if (!sendExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                sendExecutor.shutdownNow();
            }
            if (!flushScheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                flushScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}