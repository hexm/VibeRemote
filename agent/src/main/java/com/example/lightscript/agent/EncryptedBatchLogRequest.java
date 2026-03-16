package com.example.lightscript.agent;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 加密批量日志请求模型
 * 扩展基础BatchLogRequest，添加加密字段
 */
public class EncryptedBatchLogRequest {
    
    @JsonProperty("agentId")
    private String agentId;
    
    @JsonProperty("executionId")
    private Long executionId;
    
    @JsonProperty("encryptedData")
    private String encryptedData;        // AES-256-GCM加密的压缩日志
    
    @JsonProperty("encryptedKey")
    private String encryptedKey;         // RSA加密的AES密钥
    
    @JsonProperty("iv")
    private String iv;                   // AES初始化向量
    
    @JsonProperty("authTag")
    private String authTag;              // GCM认证标签
    
    @JsonProperty("signature")
    private String signature;            // RSA完整性签名
    
    @JsonProperty("timestamp")
    private long timestamp;              // 重放攻击防护
    
    @JsonProperty("batchSize")
    private int batchSize;               // 批次大小（用于统计）
    
    @JsonProperty("compressionType")
    private String compressionType;      // 压缩类型
    
    @JsonProperty("encryptionVersion")
    private String encryptionVersion;    // 加密版本标识
    
    // 默认构造函数
    public EncryptedBatchLogRequest() {
        this.compressionType = "gzip";
        this.encryptionVersion = "1.0";
    }
    
    // 完整构造函数
    public EncryptedBatchLogRequest(String agentId, Long executionId, 
                                  String encryptedData, String encryptedKey, 
                                  String iv, String authTag, String signature, 
                                  long timestamp, int batchSize) {
        this();
        this.agentId = agentId;
        this.executionId = executionId;
        this.encryptedData = encryptedData;
        this.encryptedKey = encryptedKey;
        this.iv = iv;
        this.authTag = authTag;
        this.signature = signature;
        this.timestamp = timestamp;
        this.batchSize = batchSize;
    }
    
    // 从EncryptedPayload创建请求
    public static EncryptedBatchLogRequest fromPayload(String agentId, Long executionId, 
                                                      EncryptionService.EncryptedPayload payload, 
                                                      int batchSize) {
        return new EncryptedBatchLogRequest(
            agentId, executionId,
            payload.getEncryptedData(),
            payload.getEncryptedKey(),
            payload.getIv(),
            payload.getAuthTag(),
            payload.getSignature(),
            payload.getTimestamp(),
            batchSize
        );
    }
    
    // 转换为EncryptedPayload
    public EncryptionService.EncryptedPayload toPayload() {
        return new EncryptionService.EncryptedPayload(
            encryptedData, encryptedKey, iv, authTag, signature, timestamp
        );
    }
    
    /**
     * 验证请求完整性
     */
    public boolean isValid() {
        return agentId != null && !agentId.trim().isEmpty() &&
               executionId != null && executionId > 0 &&
               encryptedData != null && !encryptedData.trim().isEmpty() &&
               encryptedKey != null && !encryptedKey.trim().isEmpty() &&
               iv != null && !iv.trim().isEmpty() &&
               authTag != null && !authTag.trim().isEmpty() &&
               signature != null && !signature.trim().isEmpty() &&
               timestamp > 0 && batchSize > 0;
    }
    
    /**
     * 检查时间戳是否在有效窗口内
     */
    public boolean isTimestampValid() {
        long currentTime = System.currentTimeMillis();
        return Math.abs(currentTime - timestamp) <= 300000; // 5分钟窗口
    }
    
    /**
     * 获取请求大小估算（用于监控）
     */
    public long getEstimatedSize() {
        long size = 0;
        if (encryptedData != null) size += encryptedData.length();
        if (encryptedKey != null) size += encryptedKey.length();
        if (iv != null) size += iv.length();
        if (authTag != null) size += authTag.length();
        if (signature != null) size += signature.length();
        return size;
    }
    
    // Getters and Setters
    public String getAgentId() {
        return agentId;
    }
    
    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }
    
    public Long getExecutionId() {
        return executionId;
    }
    
    public void setExecutionId(Long executionId) {
        this.executionId = executionId;
    }
    
    public String getEncryptedData() {
        return encryptedData;
    }
    
    public void setEncryptedData(String encryptedData) {
        this.encryptedData = encryptedData;
    }
    
    public String getEncryptedKey() {
        return encryptedKey;
    }
    
    public void setEncryptedKey(String encryptedKey) {
        this.encryptedKey = encryptedKey;
    }
    
    public String getIv() {
        return iv;
    }
    
    public void setIv(String iv) {
        this.iv = iv;
    }
    
    public String getAuthTag() {
        return authTag;
    }
    
    public void setAuthTag(String authTag) {
        this.authTag = authTag;
    }
    
    public String getSignature() {
        return signature;
    }
    
    public void setSignature(String signature) {
        this.signature = signature;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public int getBatchSize() {
        return batchSize;
    }
    
    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }
    
    public String getCompressionType() {
        return compressionType;
    }
    
    public void setCompressionType(String compressionType) {
        this.compressionType = compressionType;
    }
    
    public String getEncryptionVersion() {
        return encryptionVersion;
    }
    
    public void setEncryptionVersion(String encryptionVersion) {
        this.encryptionVersion = encryptionVersion;
    }
    
    @Override
    public String toString() {
        return String.format("EncryptedBatchLogRequest{agentId='%s', executionId=%d, batchSize=%d, " +
                           "timestamp=%d, encryptionVersion='%s', estimatedSize=%d bytes}",
                           agentId, executionId, batchSize, timestamp, encryptionVersion, getEstimatedSize());
    }
}