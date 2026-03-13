package com.example.lightscript.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * 升级状态报告器
 * 负责向服务端报告升级状态
 */
class UpgradeStatusReporter {
    private final String baseUrl;
    private final CloseableHttpClient httpClient;
    private final ObjectMapper mapper;
    private final String agentId;
    private final String agentToken;
    private Long currentUpgradeLogId;
    
    public UpgradeStatusReporter(String baseUrl, CloseableHttpClient httpClient, ObjectMapper mapper,
                                String agentId, String agentToken) {
        this.baseUrl = baseUrl;
        this.httpClient = httpClient;
        this.mapper = mapper;
        this.agentId = agentId;
        this.agentToken = agentToken;
    }
    
    /**
     * 报告升级开始
     */
    public void reportUpgradeStart(String fromVersion, String toVersion, boolean forceUpgrade) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("fromVersion", fromVersion);
            request.put("toVersion", toVersion);
            request.put("forceUpgrade", forceUpgrade);
            
            String url = baseUrl + "/api/agent/upgrade/start?agentId=" + agentId + "&agentToken=" + agentToken;
            HttpPost post = new HttpPost(url);
            post.setHeader("Content-Type", "application/json");
            post.setEntity(new StringEntity(mapper.writeValueAsString(request), "UTF-8"));
            
            try (CloseableHttpResponse response = httpClient.execute(post)) {
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode == 200) {
                    String responseBody = EntityUtils.toString(response.getEntity(), "UTF-8");
                    Map<String, Object> responseMap = mapper.readValue(responseBody, new TypeReference<Map<String, Object>>() {});
                    this.currentUpgradeLogId = ((Number) responseMap.get("upgradeLogId")).longValue();
                    
                    System.out.println("[UpgradeReporter] Reported upgrade start: " + fromVersion + " -> " + toVersion + 
                                     ", logId: " + currentUpgradeLogId);
                } else {
                    System.err.println("[UpgradeReporter] Failed to report upgrade start: " + statusCode);
                }
            }
        } catch (Exception e) {
            System.err.println("[UpgradeReporter] Failed to report upgrade start: " + e.getMessage());
        }
    }
    
    /**
     * 报告升级状态
     */
    public void reportUpgradeStatus(String status, String errorMessage) {
        if (currentUpgradeLogId == null) {
            System.err.println("[UpgradeReporter] No current upgrade log ID, skipping status report");
            return;
        }
        
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("upgradeLogId", currentUpgradeLogId);
            request.put("status", status);
            if (errorMessage != null) {
                request.put("errorMessage", errorMessage);
            }
            
            String url = baseUrl + "/api/agent/upgrade/status?agentId=" + agentId + "&agentToken=" + agentToken;
            HttpPost post = new HttpPost(url);
            post.setHeader("Content-Type", "application/json");
            post.setEntity(new StringEntity(mapper.writeValueAsString(request), "UTF-8"));
            
            try (CloseableHttpResponse response = httpClient.execute(post)) {
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode == 200) {
                    System.out.println("[UpgradeReporter] Reported upgrade status: " + status + " (logId: " + currentUpgradeLogId + ")");
                } else {
                    System.err.println("[UpgradeReporter] Failed to report upgrade status: " + statusCode);
                }
            }
            
            // 如果升级完成，清除当前升级ID
            if ("SUCCESS".equals(status) || "FAILED".equals(status) || "ROLLBACK".equals(status)) {
                currentUpgradeLogId = null;
            }
            
        } catch (Exception e) {
            System.err.println("[UpgradeReporter] Failed to report upgrade status: " + status + ", error: " + e.getMessage());
        }
    }
    
    /**
     * 获取当前升级日志ID
     */
    public Long getCurrentUpgradeLogId() {
        return currentUpgradeLogId;
    }
}