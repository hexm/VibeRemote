package com.example.lightscript.agent;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Agent配置管理类
 * 支持从多个来源读取配置：外部配置文件 > 内置配置文件 > 默认值
 */
public class AgentConfig {
    
    private static AgentConfig instance;
    private Properties properties;
    
    private AgentConfig() {
        loadConfiguration();
    }
    
    public static AgentConfig getInstance() {
        if (instance == null) {
            synchronized (AgentConfig.class) {
                if (instance == null) {
                    instance = new AgentConfig();
                }
            }
        }
        return instance;
    }
    
    private void loadConfiguration() {
        properties = new Properties();
        
        // 1. 加载内置默认配置
        loadDefaultConfig();
        
        // 2. 加载内置配置文件
        loadBuiltinConfig();
        
        // 3. 加载外部配置文件
        loadExternalConfig();
        
        // 4. 应用环境变量覆盖
        applyEnvironmentOverrides();
    }
    
    private void loadDefaultConfig() {
        // 设置默认值
        properties.setProperty("server.url", "http://localhost:8080");
        properties.setProperty("server.register.token", "dev-register-token");
        properties.setProperty("agent.name", getHostname());
        properties.setProperty("agent.labels", "");
        properties.setProperty("heartbeat.interval", "30000");
        properties.setProperty("heartbeat.system.info.interval", "600000");
        properties.setProperty("heartbeat.max.failures", "3");
        properties.setProperty("task.pull.max", "10");
        properties.setProperty("task.pull.interval", "5000");
        properties.setProperty("upgrade.backup.keep", "1");
        properties.setProperty("upgrade.verify.timeout", "15000");
        properties.setProperty("log.level", "INFO");
        properties.setProperty("log.file.max.size", "10MB");
        properties.setProperty("log.file.max.count", "5");
    }
    
    private void loadBuiltinConfig() {
        try (InputStream is = AgentConfig.class.getClassLoader().getResourceAsStream("agent.properties")) {
            if (is != null) {
                properties.load(is);
                System.out.println("[Config] Loaded builtin configuration");
            }
        } catch (IOException e) {
            System.err.println("[Config] Failed to load builtin configuration: " + e.getMessage());
        }
    }
    
    private void loadExternalConfig() {
        // 尝试加载外部配置文件
        String[] configPaths = {
            "agent.properties",           // 当前目录
            "config/agent.properties",   // config子目录
            System.getProperty("user.home") + "/.lightscript/agent.properties"  // 用户目录
        };
        
        for (String configPath : configPaths) {
            File configFile = new File(configPath);
            if (configFile.exists() && configFile.isFile()) {
                try (FileInputStream fis = new FileInputStream(configFile)) {
                    properties.load(fis);
                    System.out.println("[Config] Loaded external configuration: " + configFile.getAbsolutePath());
                    break; // 只加载第一个找到的外部配置文件
                } catch (IOException e) {
                    System.err.println("[Config] Failed to load external configuration " + configPath + ": " + e.getMessage());
                }
            }
        }
    }
    
    private void applyEnvironmentOverrides() {
        // 环境变量覆盖配置
        String[][] envMappings = {
            {"LIGHTSCRIPT_SERVER_URL", "server.url"},
            {"LIGHTSCRIPT_REGISTER_TOKEN", "server.register.token"},
            {"LIGHTSCRIPT_AGENT_NAME", "agent.name"},
            {"LIGHTSCRIPT_AGENT_LABELS", "agent.labels"},
            {"LIGHTSCRIPT_HEARTBEAT_INTERVAL", "heartbeat.interval"},
            {"LIGHTSCRIPT_LOG_LEVEL", "log.level"}
        };
        
        for (String[] mapping : envMappings) {
            String envValue = System.getenv(mapping[0]);
            if (envValue != null && !envValue.trim().isEmpty()) {
                properties.setProperty(mapping[1], envValue.trim());
                System.out.println("[Config] Applied environment override: " + mapping[1] + " = " + envValue);
            }
        }
    }
    
    private String getHostname() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown-host";
        }
    }
    
    // 配置获取方法
    public String getServerUrl() {
        return properties.getProperty("server.url");
    }
    
    public String getRegisterToken() {
        // 兼容两种 key 名：server.register.token（内置默认）和 register.token（安装脚本写入）
        String token = properties.getProperty("register.token");
        if (token != null && !token.trim().isEmpty()) {
            return token.trim();
        }
        return properties.getProperty("server.register.token");
    }
    
    public String getAgentName() {
        return properties.getProperty("agent.name");
    }
    
    public String getAgentLabels() {
        return properties.getProperty("agent.labels");
    }
    
    public long getHeartbeatInterval() {
        return Long.parseLong(properties.getProperty("heartbeat.interval"));
    }
    
    public long getSystemInfoInterval() {
        return Long.parseLong(properties.getProperty("heartbeat.system.info.interval"));
    }
    
    public int getMaxHeartbeatFailures() {
        return Integer.parseInt(properties.getProperty("heartbeat.max.failures"));
    }
    
    public int getTaskPullMax() {
        return Integer.parseInt(properties.getProperty("task.pull.max"));
    }
    
    public long getTaskPullInterval() {
        return Long.parseLong(properties.getProperty("task.pull.interval"));
    }
    
    public int getUpgradeBackupKeep() {
        return Integer.parseInt(properties.getProperty("upgrade.backup.keep"));
    }
    
    public long getUpgradeVerifyTimeout() {
        return Long.parseLong(properties.getProperty("upgrade.verify.timeout"));
    }
    
    public String getLogLevel() {
        return properties.getProperty("log.level");
    }
    
    // 通用配置获取方法
    public String getProperty(String key) {
        return properties.getProperty(key);
    }
    
    public String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }
    
    public int getIntProperty(String key, int defaultValue) {
        try {
            return Integer.parseInt(properties.getProperty(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    public long getLongProperty(String key, long defaultValue) {
        try {
            return Long.parseLong(properties.getProperty(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    public boolean getBooleanProperty(String key, boolean defaultValue) {
        String value = properties.getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }
    
    // 加密配置获取方法
    public boolean isEncryptionEnabled() {
        return getBooleanProperty("encryption.enabled", false);
    }
    
    public boolean isEncryptionRequired() {
        return getBooleanProperty("encryption.required", false);
    }
    
    public int getKeyRotationDays() {
        return getIntProperty("encryption.key.rotation.days", 30);
    }
    
    public String getEncryptionAlgorithm() {
        return getProperty("encryption.algorithm", "AES-256-GCM");
    }
    
    /**
     * 重新加载配置
     */
    public void reload() {
        loadConfiguration();
        System.out.println("[Config] Configuration reloaded");
    }
    
    /**
     * 打印当前配置
     */
    public void printConfig() {
        System.out.println("[Config] Current configuration:");
        properties.forEach((key, value) -> {
            // 隐藏敏感信息
            String displayValue = key.toString().toLowerCase().contains("token") || 
                                key.toString().toLowerCase().contains("password") ? 
                                "***" : value.toString();
            System.out.println("  " + key + " = " + displayValue);
        });
    }
}