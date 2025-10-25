package com.example.lightscript.agent;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;

/**
 * Agent ID 持久化存储工具
 * 将 Agent ID 和 Token 保存到本地文件，重启后可以复用
 */
public class AgentIdStore {
    private static final String STORE_FILE = ".agent_id";
    
    private final Path storeFilePath;
    
    public AgentIdStore() {
        // 保存在用户主目录的 .lightscript 目录下
        String userHome = System.getProperty("user.home");
        Path lightscriptDir = Paths.get(userHome, ".lightscript");
        
        try {
            Files.createDirectories(lightscriptDir);
        } catch (IOException e) {
            System.err.println("Warning: Failed to create .lightscript directory: " + e.getMessage());
        }
        
        this.storeFilePath = lightscriptDir.resolve(STORE_FILE);
    }
    
    /**
     * 保存 Agent ID 和 Token
     */
    public void save(String agentId, String agentToken) {
        Properties props = new Properties();
        props.setProperty("agentId", agentId);
        props.setProperty("agentToken", agentToken);
        
        try (OutputStream out = Files.newOutputStream(storeFilePath)) {
            props.store(out, "LightScript Agent ID - DO NOT DELETE");
            System.out.println("Agent ID saved to: " + storeFilePath);
        } catch (IOException e) {
            System.err.println("Warning: Failed to save agent ID: " + e.getMessage());
        }
    }
    
    /**
     * 读取保存的 Agent ID 和 Token
     * @return {agentId, agentToken} 如果存在，否则返回 null
     */
    public AgentCredentials load() {
        if (!Files.exists(storeFilePath)) {
            return null;
        }
        
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(storeFilePath)) {
            props.load(in);
            String agentId = props.getProperty("agentId");
            String agentToken = props.getProperty("agentToken");
            
            if (agentId != null && agentToken != null) {
                System.out.println("Found existing agent ID: " + agentId);
                return new AgentCredentials(agentId, agentToken);
            }
        } catch (IOException e) {
            System.err.println("Warning: Failed to load agent ID: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * 删除保存的 Agent ID（用于强制重新注册）
     */
    public void delete() {
        try {
            Files.deleteIfExists(storeFilePath);
            System.out.println("Agent ID file deleted");
        } catch (IOException e) {
            System.err.println("Warning: Failed to delete agent ID file: " + e.getMessage());
        }
    }
    
    /**
     * Agent 凭证数据类
     */
    public static class AgentCredentials {
        private final String agentId;
        private final String agentToken;
        
        public AgentCredentials(String agentId, String agentToken) {
            this.agentId = agentId;
            this.agentToken = agentToken;
        }
        
        public String getAgentId() {
            return agentId;
        }
        
        public String getAgentToken() {
            return agentToken;
        }
    }
}
