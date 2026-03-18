package com.example.lightscript.agent;

import java.io.*;
import java.nio.file.*;
import java.security.KeyPair;
import java.util.Map;
import java.util.Properties;

/**
 * Agent加密上下文 - 简化版，专注于公钥注册
 */
public class AgentEncryptionContext {
    
    private final String agentId;
    private final String configDir;
    private final EncryptionService encryptionService;
    private final AgentApi agentApi;
    
    // 密钥配置
    private static final String KEYS_FILE = "encryption-keys.properties";
    private static final String SERVER_PUBLIC_KEY = "server.public.key";
    private static final String AGENT_PRIVATE_KEY = "agent.private.key";
    private static final String AGENT_PUBLIC_KEY = "agent.public.key";
    private static final String KEY_GENERATION_TIME = "key.generation.time";
    private static final String SERVER_KEY_VERSION = "server.key.version";
    
    private String serverPublicKey;
    private String agentPrivateKey;
    private String agentPublicKey;
    private long keyGenerationTime;
    
    /** 服务器公钥版本号，从注册响应中获取并持久化 */
    private int serverKeyVersion;
    
    // Agent凭证
    private volatile String currentAgentId;
    private volatile String currentAgentToken;
    
    public AgentEncryptionContext(String agentId, EncryptionService encryptionService, AgentApi agentApi) {
        this.agentId = agentId;
        this.encryptionService = encryptionService;
        this.agentApi = agentApi;
        this.configDir = System.getProperty("user.home") + "/.lightscript/agent-" + agentId;
        
        // 确保配置目录存在
        createConfigDirectory();
        
        // 加载或生成密钥
        loadOrGenerateKeys();
        
        System.out.println("[EncryptionContext] 加密上下文初始化完成");
    }
    
    /**
     * 更新Agent凭证并注册公钥
     */
    public void updateCredentials(String agentId, String agentToken) {
        this.currentAgentId = agentId;
        this.currentAgentToken = agentToken;
        
        // 获取服务器公钥
        fetchServerPublicKey();
        
        // 注册Agent公钥到服务器
        registerPublicKeyToServer();
    }
    
    /**
     * 注册Agent公钥到服务器（公开方法，供外部触发重新注册）
     * 需求：12.3
     */
    public void registerPublicKey() {
        registerPublicKeyToServer();
    }

    /**
     * 注册Agent公钥到服务器
     */
    private void registerPublicKeyToServer() {
        if (currentAgentId == null || currentAgentToken == null) {
            System.err.println("[EncryptionContext] Agent凭证未设置，无法注册公钥");
            return;
        }
        
        if (agentPublicKey == null) {
            System.err.println("[EncryptionContext] Agent公钥未生成，无法注册");
            return;
        }
        
        try {
            System.out.println("[EncryptionContext] 注册Agent公钥到服务器...");
            Map<String, Object> response = agentApi.registerAgentPublicKey(currentAgentId, currentAgentToken, agentPublicKey);
            System.out.println("[EncryptionContext] Agent公钥注册成功");
            
            // 从注册响应中获取服务器公钥和版本号
            if (response != null) {
                String respServerPublicKey = (String) response.get("serverPublicKey");
                if (respServerPublicKey != null && !respServerPublicKey.isEmpty()) {
                    this.serverPublicKey = respServerPublicKey;
                    
                    Object versionObj = response.get("keyVersion");
                    if (versionObj instanceof Number) {
                        this.serverKeyVersion = ((Number) versionObj).intValue();
                    }
                    
                    // 持久化
                    File keysFile = new File(configDir, KEYS_FILE);
                    saveKeysToFile(keysFile);
                    
                    System.out.println("[EncryptionContext] 从注册响应中获取服务器公钥，版本号: " + this.serverKeyVersion);
                }
            }
        } catch (Exception e) {
            System.err.println("[EncryptionContext] 注册Agent公钥失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取服务器公钥
     */
    private void fetchServerPublicKey() {
        try {
            if (serverPublicKey == null) {
                System.out.println("[EncryptionContext] 获取服务器公钥...");
                Map<String, Object> response = agentApi.getServerPublicKey(currentAgentId, currentAgentToken);
                this.serverPublicKey = (String) response.get("serverPublicKey");
                
                // 读取服务器公钥版本号
                Object versionObj = response.get("keyVersion");
                if (versionObj instanceof Number) {
                    this.serverKeyVersion = ((Number) versionObj).intValue();
                }
                
                // 保存到文件
                File keysFile = new File(configDir, KEYS_FILE);
                saveKeysToFile(keysFile);
                
                System.out.println("[EncryptionContext] 服务器公钥获取成功，版本号: " + this.serverKeyVersion);
            }
        } catch (Exception e) {
            System.err.println("[EncryptionContext] 获取服务器公钥失败: " + e.getMessage());
        }
    }
    
    /**
     * 创建配置目录
     */
    private void createConfigDirectory() {
        try {
            Path configPath = Paths.get(configDir);
            if (!Files.exists(configPath)) {
                Files.createDirectories(configPath);
                System.out.println("[EncryptionContext] 创建加密配置目录: " + configDir);
            }
        } catch (IOException e) {
            throw new RuntimeException("创建加密配置目录失败: " + configDir, e);
        }
    }
    
    /**
     * 加载或生成密钥
     */
    private void loadOrGenerateKeys() {
        File keysFile = new File(configDir, KEYS_FILE);
        
        if (keysFile.exists()) {
            loadKeysFromFile(keysFile);
            System.out.println("[EncryptionContext] 加载现有密钥对");
        } else {
            System.out.println("[EncryptionContext] 首次运行，生成新密钥对");
            generateAndSaveKeys(keysFile);
        }
    }
    
    /**
     * 从文件加载密钥
     */
    private void loadKeysFromFile(File keysFile) {
        try (FileInputStream fis = new FileInputStream(keysFile)) {
            Properties props = new Properties();
            props.load(fis);
            
            this.serverPublicKey = props.getProperty(SERVER_PUBLIC_KEY);
            this.agentPrivateKey = props.getProperty(AGENT_PRIVATE_KEY);
            this.agentPublicKey = props.getProperty(AGENT_PUBLIC_KEY);
            this.keyGenerationTime = Long.parseLong(props.getProperty(KEY_GENERATION_TIME, "0"));
            this.serverKeyVersion = Integer.parseInt(props.getProperty(SERVER_KEY_VERSION, "0"));
            
        } catch (Exception e) {
            System.err.println("[EncryptionContext] 加载密钥失败，将重新生成: " + e.getMessage());
            generateAndSaveKeys(keysFile);
        }
    }
    
    /**
     * 生成并保存密钥
     */
    private void generateAndSaveKeys(File keysFile) {
        try {
            // 生成Agent密钥对
            KeyPair agentKeyPair = encryptionService.generateKeyPair();
            this.agentPrivateKey = encryptionService.privateKeyToPem(agentKeyPair.getPrivate());
            this.agentPublicKey = encryptionService.publicKeyToPem(agentKeyPair.getPublic());
            this.keyGenerationTime = System.currentTimeMillis();
            
            // 保存到文件
            saveKeysToFile(keysFile);
            
            System.out.println("[EncryptionContext] 新密钥对已生成并保存");
            
        } catch (Exception e) {
            throw new RuntimeException("生成密钥对失败", e);
        }
    }
    
    /**
     * 保存密钥到文件
     */
    private void saveKeysToFile(File keysFile) {
        try (FileOutputStream fos = new FileOutputStream(keysFile)) {
            Properties props = new Properties();
            
            if (serverPublicKey != null) {
                props.setProperty(SERVER_PUBLIC_KEY, serverPublicKey);
            }
            props.setProperty(AGENT_PRIVATE_KEY, agentPrivateKey);
            props.setProperty(AGENT_PUBLIC_KEY, agentPublicKey);
            props.setProperty(KEY_GENERATION_TIME, String.valueOf(keyGenerationTime));
            props.setProperty(SERVER_KEY_VERSION, String.valueOf(serverKeyVersion));
            
            props.store(fos, "LightScript Agent Encryption Keys - Generated at " + 
                new java.util.Date(keyGenerationTime));
            
            // 设置文件权限（仅所有者可读写）
            keysFile.setReadable(false, false);
            keysFile.setReadable(true, true);
            keysFile.setWritable(false, false);
            keysFile.setWritable(true, true);
            
        } catch (IOException e) {
            throw new RuntimeException("保存密钥文件失败", e);
        }
    }
    
    /**
     * 检查加密是否已配置
     */
    public boolean isEncryptionConfigured() {
        return serverPublicKey != null && 
               agentPrivateKey != null && 
               agentPublicKey != null;
    }
    
    // Getters
    public String getAgentId() {
        return agentId;
    }
    
    public String getServerPublicKey() {
        return serverPublicKey;
    }
    
    public String getAgentPrivateKey() {
        return agentPrivateKey;
    }
    
    public String getAgentPublicKey() {
        return agentPublicKey;
    }

    /**
     * 获取密钥年龄（天数）
     */
    public long getKeyAgeDays() {
        if (keyGenerationTime == 0) {
            return 0;
        }
        return (System.currentTimeMillis() - keyGenerationTime) / (24 * 60 * 60 * 1000);
    }

    /**
     * 更新服务器公钥
     */
    public void updateServerPublicKey(String serverPublicKey) {
        this.serverPublicKey = serverPublicKey;

        // 保存到文件
        File keysFile = new File(configDir, KEYS_FILE);
        saveKeysToFile(keysFile);

        System.out.println("[EncryptionContext] 服务器公钥已更新");
    }

    /**
     * 更新服务器公钥（带版本号）
     */
    public void updateServerPublicKey(String serverPublicKey, int keyVersion) {
        this.serverPublicKey = serverPublicKey;
        this.serverKeyVersion = keyVersion;

        // 保存到文件
        File keysFile = new File(configDir, KEYS_FILE);
        saveKeysToFile(keysFile);

        System.out.println("[EncryptionContext] 服务器公钥已更新，版本号: " + keyVersion);
    }

    /**
     * 获取服务器公钥版本号
     */
    public int getServerKeyVersion() {
        return serverKeyVersion;
    }

    /**
     * 密钥轮换（简化版）
     */
    public void rotateKeys() {
        File keysFile = new File(configDir, KEYS_FILE);
        generateAndSaveKeys(keysFile);
        System.out.println("[EncryptionContext] 密钥轮换完成");
    }

    /**
     * 检查密钥是否需要轮换，超过30天则自动轮换并重新注册
     * 需求：10.1、10.2
     *
     * @return true 表示发生了轮换
     */
    public boolean checkAndRotateIfNeeded() {
        long ageDays = getKeyAgeDays();
        if (ageDays < 30) {
            return false;
        }

        System.out.println("[EncryptionContext] 密钥已使用 " + ageDays + " 天，触发自动轮换...");

        // 生成新密钥对并持久化（需求 10.1、10.2）
        rotateKeys();

        // 重新向服务器注册新公钥
        registerPublicKeyToServer();

        System.out.println("[EncryptionContext] 密钥自动轮换完成，新密钥已持久化并注册");
        return true;
    }
    
    /**
     * 清理敏感数据
     */
    public void cleanup() {
        if (agentPrivateKey != null) {
            agentPrivateKey = null;
        }
        if (serverPublicKey != null) {
            serverPublicKey = null;
        }
        System.gc();
        System.out.println("[EncryptionContext] 敏感数据已清理");
    }
}