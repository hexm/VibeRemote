package com.example.lightscript.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 健壮的批量日志收集器
 * 解决日志丢失、缓冲区溢出、多任务隔离等问题
 */
/**
 * 健壮的批量日志收集器
 * 方案1: 全任务失败策略 - 任何批次失败，整个任务日志都保存到本地
 */
public class RobustBatchLogCollector {
    private final String baseUrl;
    private final CloseableHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ExecutorService sendExecutor;
    private final ScheduledExecutorService flushScheduler;

    // 失败重试队列
    private final BlockingQueue<RetryLogBatch> retryQueue = new LinkedBlockingQueue<>();
    private final int maxRetries = 3;
    private final long retryDelayMs = 1000; // 1秒

    // 本地备份目录
    private final String backupDir = System.getProperty("user.home") + "/.lightscript/log-backup";

    // 任务失败状态跟踪 - 核心改进
    private final ConcurrentHashMap<Long, Boolean> taskFailureStatus = new ConcurrentHashMap<>();

    private volatile String agentId;
    private volatile String agentToken;
    private volatile boolean shutdown = false;

    /**
     * 重试批次
     */
    private static class RetryLogBatch {
        final Long executionId;
        final List<LogEntry> logs;
        final int attemptCount;
        final long nextRetryTime;

        RetryLogBatch(Long executionId, List<LogEntry> logs, int attemptCount) {
            this.executionId = executionId;
            this.logs = logs;
            this.attemptCount = attemptCount;
            this.nextRetryTime = System.currentTimeMillis() + (1000L << attemptCount); // 指数退避
        }
    }

    public RobustBatchLogCollector(String baseUrl, CloseableHttpClient httpClient, ObjectMapper objectMapper) {
        this.baseUrl = baseUrl;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;

        this.sendExecutor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "BatchLogSender");
            t.setDaemon(true);
            return t;
        });

        this.flushScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "LogRetryScheduler");
            t.setDaemon(true);
            return t;
        });

        // 启动重试调度器
        startRetryScheduler();

        // 确保备份目录存在
        new File(backupDir).mkdirs();
    }

    public void updateCredentials(String agentId, String agentToken) {
        this.agentId = agentId;
        this.agentToken = agentToken;
    }

    /**
     * 发送批量日志 - 方案1: 全任务失败策略
     */
    public void sendBatch(Long executionId, List<LogEntry> logs) {
        if (logs.isEmpty()) return;

        // 检查任务是否已经有失败批次
        if (taskFailureStatus.getOrDefault(executionId, false)) {
            System.out.println("任务 " + executionId + " 之前有批次失败，当前批次直接保存到本地: " + logs.size() + " 条");
            saveToLocalBackup(executionId, logs);
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                sendBatchToServer(executionId, logs);
                System.out.println("成功发送批量日志: " + logs.size() + " 条 (executionId: " + executionId + ")");
            } catch (Exception e) {
                System.err.println("批量日志发送失败: " + e.getMessage() + " (executionId: " + executionId + ")");
                handleSendFailure(executionId, logs, 1);
            }
        }, sendExecutor);
    }

    /**
     * 处理发送失败 - 方案1策略
     */
    private void handleSendFailure(Long executionId, List<LogEntry> logs, int attemptCount) {
        if (attemptCount <= maxRetries) {
            // 加入重试队列
            RetryLogBatch retryBatch = new RetryLogBatch(executionId, logs, attemptCount);
            retryQueue.offer(retryBatch);
            System.out.println("日志批次加入重试队列 (尝试 " + attemptCount + "/" + maxRetries + ")");
        } else {
            // 超过最大重试次数，标记整个任务失败
            System.err.println("批次超过最大重试次数，标记任务 " + executionId + " 为失败状态");
            markTaskAsFailed(executionId);
            saveToLocalBackup(executionId, logs);
        }
    }

    /**
     * 标记任务为失败状态
     */
    private void markTaskAsFailed(Long executionId) {
        taskFailureStatus.put(executionId, true);
        System.out.println("⚠️  任务 " + executionId + " 已标记为失败，后续所有批次将保存到本地");
    }

    /**
     * 任务完成时清理状态
     */
    public void onTaskComplete(Long executionId) {
        Boolean hasFailed = taskFailureStatus.remove(executionId);
        if (hasFailed != null && hasFailed) {
            System.out.println("📁 任务 " + executionId + " 完成，由于有批次失败，完整日志已保存到本地备份");
        } else {
            System.out.println("✅ 任务 " + executionId + " 完成，所有日志已成功上传到服务器");
        }
    }

    /**
     * 保存到本地备份文件 - 包含更多元信息用于补传
     */
    private void saveToLocalBackup(Long executionId, List<LogEntry> logs) {
        // 生成包含时间戳和日志数量的文件名
        String fileName = String.format("%s/failed_logs_%d_%d_count_%d.json",
            backupDir, executionId, System.currentTimeMillis(), logs.size());

        try (FileWriter writer = new FileWriter(fileName)) {
            BatchLogRequest request = new BatchLogRequest(agentId, agentToken, executionId, logs);
            writer.write(objectMapper.writeValueAsString(request));
            System.out.println("日志已备份到: " + fileName + " (" + logs.size() + " 条)");
        } catch (IOException e) {
            System.err.println("保存日志备份失败: " + e.getMessage());
        }
    }

    /**
     * 发送到服务器
     */
    private void sendBatchToServer(Long executionId, List<LogEntry> logs) throws Exception {
        if (agentId == null || agentToken == null) {
            throw new IllegalStateException("Agent凭证未设置");
        }

        BatchLogRequest request = new BatchLogRequest(agentId, agentToken, executionId, logs);

        HttpPost post = new HttpPost(baseUrl + "/api/agent/tasks/executions/" + executionId + "/batch-log");
        post.setHeader("Content-Type", "application/json");
        post.setHeader("X-Batch-Size", String.valueOf(logs.size()));
        post.setEntity(new StringEntity(objectMapper.writeValueAsString(request), "UTF-8"));

        try (CloseableHttpResponse response = httpClient.execute(post)) {
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != 200) {
                throw new RuntimeException("服务器返回错误状态: " + statusCode);
            }
        }
    }

    /**
     * 启动重试调度器
     */
    private void startRetryScheduler() {
        flushScheduler.scheduleWithFixedDelay(() -> {
            if (shutdown) return;

            try {
                RetryLogBatch retryBatch = retryQueue.poll();
                if (retryBatch != null && System.currentTimeMillis() >= retryBatch.nextRetryTime) {
                    // 重试发送
                    CompletableFuture.runAsync(() -> {
                        try {
                            sendBatchToServer(retryBatch.executionId, retryBatch.logs);
                            System.out.println("重试发送成功: " + retryBatch.logs.size() + " 条 (尝试 " + retryBatch.attemptCount + ")");
                        } catch (Exception e) {
                            System.err.println("重试发送失败: " + e.getMessage() + " (尝试 " + retryBatch.attemptCount + ")");
                            handleSendFailure(retryBatch.executionId, retryBatch.logs, retryBatch.attemptCount + 1);
                        }
                    }, sendExecutor);
                } else if (retryBatch != null) {
                    // 还没到重试时间，放回队列
                    retryQueue.offer(retryBatch);
                }
            } catch (Exception e) {
                System.err.println("重试调度器异常: " + e.getMessage());
            }
        }, 500, 500, TimeUnit.MILLISECONDS); // 每500ms检查一次
    }

    /**
     * 关闭收集器
     */
    public void shutdown() {
        shutdown = true;

        // 处理剩余的重试队列
        while (!retryQueue.isEmpty()) {
            RetryLogBatch retryBatch = retryQueue.poll();
            if (retryBatch != null) {
                try {
                    sendBatchToServer(retryBatch.executionId, retryBatch.logs);
                    System.out.println("关闭时发送剩余日志: " + retryBatch.logs.size() + " 条");
                } catch (Exception e) {
                    saveToLocalBackup(retryBatch.executionId, retryBatch.logs);
                }
            }
        }

        // 关闭线程池
        flushScheduler.shutdown();
        sendExecutor.shutdown();

        try {
            if (!sendExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
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
