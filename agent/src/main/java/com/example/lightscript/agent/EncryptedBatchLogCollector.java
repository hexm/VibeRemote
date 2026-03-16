package com.example.lightscript.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPOutputStream;

/**
 * 加密批量日志收集器
 * 在RobustBatchLogCollector基础上添加加密功能
 */
public class EncryptedBatchLogCollector {
    
    private final String baseUrl;
    private final CloseableHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ExecutorService sendExecutor;
    
    private final EncryptionService encryptionService;
    private final AgentEncryptionContext encryptionContext;
    private final AgentApi agentApi;  // 新增：用于自动获取服务器公钥
    
    private volatile String agentId;
    private volatile boolean shutdown = false;
    
    // 加密配置
    private final boolean encryptionEnabled;
    
    public EncryptedBatchLogCollector(String baseUrl, CloseableHttpClient httpClient, 
                                    ObjectMapper objectMapper, boolean encryptionEnabled, AgentApi agentApi) {
        this.baseUrl = baseUrl;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.encryptionEnabled = encryptionEnabled;
        this.agentApi = agentApi;
        
        this.sendExecutor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "EncryptedBatchLogSender");
            t.setDaemon(true);
            return t;
        });
        
        if (encryptionEnabled) {
            this.encryptionService = new EncryptionService();
            this.encryptionContext = null; // 将在updateCredentials中初始化
            System.out.println("[EncryptedBatchLogCollector] 加密模式已启用");
        } else {
            this.encryptionService = null;
            this.encryptionContext = null;
            System.out.println("[EncryptedBatchLogCollector] 明文模式（加密未启用）");
        }
    }
    
    private AgentEncryptionContext encryptionContextInstance;
    
    public void updateCredentials(String agentId, String agentToken) {
        this.agentId = agentId;
        
        if (encryptionEnabled && encryptionService != null) {
            // 初始化加密上下文（使用新的构造函数）
            this.encryptionContextInstance = new AgentEncryptionContext(agentId, encryptionService, agentApi);
            // 设置凭证以启用自动密钥管理
            this.encryptionContextInstance.updateCredentials(agentId, agentToken);
            System.out.println("[EncryptedBatchLogCollector] 加密上下文已初始化，密钥年龄: " + 
                encryptionContextInstance.getKeyAgeDays() + " 天");
        }
    }
    
    /**
     * 发送批量日志（支持加密和明文模式）
     */
    public void sendBatch(Long executionId, List<LogEntry> logs) {
        if (logs.isEmpty()) return;
        
        CompletableFuture.runAsync(() -> {
            try {
                if (encryptionEnabled && isEncryptionConfigured()) {
                    sendEncryptedBatch(executionId, logs);
                } else {
                    sendPlaintextBatch(executionId, logs);
                }
                
                System.out.println("成功发送批量日志: " + logs.size() + " 条 (executionId: " + executionId + 
                    ", 模式: " + (encryptionEnabled && isEncryptionConfigured() ? "加密" : "明文") + ")");
                    
            } catch (Exception e) {
                System.err.println("批量日志发送失败: " + e.getMessage() + " (executionId: " + executionId + ")");
                // TODO: 添加重试机制
            }
        }, sendExecutor);
    }
    
    /**
     * 发送加密批量日志
     */
    private void sendEncryptedBatch(Long executionId, List<LogEntry> logs) throws Exception {
        if (!isEncryptionConfigured()) {
            throw new IllegalStateException("加密未正确配置");
        }
        
        // 1. 序列化日志批次
        String jsonData = objectMapper.writeValueAsString(logs);
        
        // 2. GZIP压缩
        byte[] compressedData = gzipCompress(jsonData.getBytes("UTF-8"));
        
        // 3. 加密压缩数据
        EncryptionService.EncryptedPayload payload = encryptionService.encrypt(
            compressedData, 
            encryptionContextInstance.getServerPublicKey(),
            encryptionContextInstance.getAgentPrivateKey()
        );
        
        // 4. 创建加密请求
        EncryptedBatchLogRequest request = EncryptedBatchLogRequest.fromPayload(
            agentId, executionId, payload, logs.size()
        );
        
        // 5. 发送HTTP请求
        HttpPost post = new HttpPost(baseUrl + "/api/agent/tasks/executions/" + executionId + "/encrypted-batch-log");
        post.setHeader("Content-Type", "application/json");
        post.setHeader("X-Batch-Size", String.valueOf(logs.size()));
        post.setHeader("X-Encryption-Version", "1.0");
        post.setHeader("X-Agent-Public-Key", encryptionContextInstance.getAgentPublicKey().replaceAll("\\s", ""));
        post.setEntity(new StringEntity(objectMapper.writeValueAsString(request), "UTF-8"));
        
        try (CloseableHttpResponse response = httpClient.execute(post)) {
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 403) {
                // 可能是密钥问题，触发密钥轮换
                System.err.println("加密认证失败，可能需要密钥轮换");
                throw new SecurityException("加密认证失败: " + statusCode);
            } else if (statusCode != 200) {
                throw new RuntimeException("服务器返回错误状态: " + statusCode);
            }
        }
    }
    
    /**
     * 发送明文批量日志（回退模式）
     */
    private void sendPlaintextBatch(Long executionId, List<LogEntry> logs) throws Exception {
        // 使用原有的明文批量传输逻辑
        BatchLogRequest request = new BatchLogRequest(agentId, null, executionId, logs);
        
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
     * GZIP压缩数据
     */
    private byte[] gzipCompress(byte[] data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipOut = new GZIPOutputStream(baos)) {
            gzipOut.write(data);
        }
        return baos.toByteArray();
    }
    
    /**
     * 检查加密是否已配置
     */
    private boolean isEncryptionConfigured() {
        return encryptionEnabled && 
               encryptionContextInstance != null && 
               encryptionContextInstance.isEncryptionConfigured();
    }
    
    /**
     * 更新服务器公钥
     */
    public void updateServerPublicKey(String serverPublicKey) {
        if (encryptionEnabled && encryptionContextInstance != null) {
            encryptionContextInstance.updateServerPublicKey(serverPublicKey);
            System.out.println("[EncryptedBatchLogCollector] 服务器公钥已更新");
        }
    }
    
    /**
     * 获取Agent公钥（用于注册）
     */
    public String getAgentPublicKey() {
        if (encryptionEnabled && encryptionContextInstance != null) {
            return encryptionContextInstance.getAgentPublicKey();
        }
        return null;
    }
    
    /**
     * 强制密钥轮换
     */
    public void rotateKeys() {
        if (encryptionEnabled && encryptionContextInstance != null) {
            encryptionContextInstance.rotateKeys();
            System.out.println("[EncryptedBatchLogCollector] 密钥轮换完成");
        }
    }
    
    /**
     * 获取加密状态信息
     */
    public String getEncryptionStatus() {
        if (!encryptionEnabled) {
            return "加密未启用";
        }
        
        if (encryptionContextInstance == null) {
            return "加密上下文未初始化";
        }
        
        if (!encryptionContextInstance.isEncryptionConfigured()) {
            return "加密未配置（缺少服务器公钥）";
        }
        
        return String.format("加密已启用（密钥年龄: %d 天）", encryptionContextInstance.getKeyAgeDays());
    }
    
    /**
     * 关闭收集器
     */
    public void shutdown() {
        shutdown = true;
        
        sendExecutor.shutdown();
        try {
            if (!sendExecutor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)) {
                sendExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // 清理加密上下文
        if (encryptionContextInstance != null) {
            encryptionContextInstance.cleanup();
        }
        
        System.out.println("[EncryptedBatchLogCollector] 已关闭");
    }
}