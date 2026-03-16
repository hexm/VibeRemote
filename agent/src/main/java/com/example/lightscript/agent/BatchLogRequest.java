package com.example.lightscript.agent;

import java.util.List;

/**
 * 批量日志请求 - 用于向服务器发送批量日志数据
 */
/**
 * 批量日志请求 - 增加批次序号和完整性验证
 */
/**
 * 批量日志请求
 */
public class BatchLogRequest {
    private String agentId;
    private String agentToken;
    private Long executionId;
    private List<LogEntry> logs;

    public BatchLogRequest() {}

    public BatchLogRequest(String agentId, String agentToken, Long executionId, List<LogEntry> logs) {
        this.agentId = agentId;
        this.agentToken = agentToken;
        this.executionId = executionId;
        this.logs = logs;
    }

    // Getters and Setters
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }

    public String getAgentToken() { return agentToken; }
    public void setAgentToken(String agentToken) { this.agentToken = agentToken; }

    public Long getExecutionId() { return executionId; }
    public void setExecutionId(Long executionId) { this.executionId = executionId; }

    public List<LogEntry> getLogs() { return logs; }
    public void setLogs(List<LogEntry> logs) { this.logs = logs; }
}
