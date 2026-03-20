package com.example.lightscript.agent;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;

/**
 * Agent ID 持久化存储
 * 只保存服务端分配的 agentId，token 直接使用配置文件中的 register.token，永不变更。
 */
public class AgentIdStore {
    private static final String STORE_FILE = ".agent_id";

    private final Path storeFilePath;

    public AgentIdStore() {
        String userHome = System.getProperty("user.home");
        Path dir = Paths.get(userHome, ".viberemote");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            System.err.println("Warning: Failed to create .viberemote directory: " + e.getMessage());
        }
        this.storeFilePath = dir.resolve(STORE_FILE);
    }

    /** 保存 agentId */
    public void save(String agentId) {
        Properties props = new Properties();
        props.setProperty("agentId", agentId);
        try (OutputStream out = Files.newOutputStream(storeFilePath)) {
            props.store(out, "ViberRemote Agent ID - DO NOT DELETE");
            System.out.println("Agent ID saved to: " + storeFilePath);
        } catch (IOException e) {
            System.err.println("Warning: Failed to save agent ID: " + e.getMessage());
        }
    }

    /** 读取已保存的 agentId，不存在返回 null */
    public String load() {
        if (!Files.exists(storeFilePath)) {
            return null;
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(storeFilePath)) {
            props.load(in);
            String agentId = props.getProperty("agentId");
            if (agentId != null && !agentId.trim().isEmpty()) {
                System.out.println("Found existing agent ID: " + agentId);
                return agentId.trim();
            }
        } catch (IOException e) {
            System.err.println("Warning: Failed to load agent ID: " + e.getMessage());
        }
        return null;
    }

    /** 删除保存的 agentId（强制重新注册） */
    public void delete() {
        try {
            Files.deleteIfExists(storeFilePath);
            System.out.println("Agent ID file deleted");
        } catch (IOException e) {
            System.err.println("Warning: Failed to delete agent ID file: " + e.getMessage());
        }
    }

    // ---- 兼容旧版本：读取旧路径 .lightscript/.agent_id ----
    public static String loadLegacy() {
        try {
            Path legacy = Paths.get(System.getProperty("user.home"), ".lightscript", ".agent_id");
            if (!Files.exists(legacy)) return null;
            Properties props = new Properties();
            try (InputStream in = Files.newInputStream(legacy)) {
                props.load(in);
                return props.getProperty("agentId");
            }
        } catch (Exception e) {
            return null;
        }
    }
}
