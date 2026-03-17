package com.example.lightscript.server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务器端加密上下文管理
 * 负责服务器密钥管理和Agent公钥存储
 */
@Component
public class ServerEncryptionContext {
    
    private static final Logger log = LoggerFactory.getLogger(ServerEncryptionContext.class);
    
    private final EncryptionService encryptionService;
    private final String configDir;
    
    // 密钥轮换配置
    private static final long KEY_ROTATION_INTERVAL_MS = 30L * 24 * 60 * 60 * 1000; // 30天
    private static final String SERVER_KEYS_FILE = "server-encryption-keys.properties";
    private static final String AGENT_KEYS_FILE = "agent-public-keys.properties";
    private static final String SERVER_PRIVATE_KEY = "server.private.key";
    private static final String SERVER_PUBLIC_KEY = "server.public.key";
    private static final String KEY_GENERATION_TIME = "key.generation.time";
    
    private String serverPrivateKey;
    private String serverPublicKey;
    private long keyGenerationTime;
    
    // Agent公钥存储
    private final ConcurrentHashMap<String, String> agentPublicKeys = new ConcurrentHashMap<>();
    
    // 损坏的Agent公钥记录
    private final ConcurrentHashMap<String, String> corruptedAgentKeys = new ConcurrentHashMap<>();
    
    @Value("${lightscript.encryption.enabled:false}")
    private boolean encryptionEnabled;
    
    @Value("${lightscript.encryption.key-rotation-days:30}")
    private int keyRotationDays;
    
    public ServerEncryptionContext(EncryptionService encryptionService) {
        this.encryptionService = encryptionService;
        this.configDir = System.getProperty("user.home") + "/.lightscript/server";
    }
    
    @PostConstruct
    public void initialize() {
        if (!encryptionEnabled) {
            log.info("[ServerEncryption] 加密功能未启用");
            return;
        }
        
        // 确保配置目录存在
        createConfigDirectory();
        
        // 加载或生成服务器密钥
        loadOrGenerateServerKeys();
        
        // 加载Agent公钥
        loadAgentPublicKeys();
        
        log.info("[ServerEncryption] 服务器加密上下文初始化完成");
        log.info("[ServerEncryption] 密钥年龄: {} 天", getKeyAgeDays());
        log.info("[ServerEncryption] 已注册Agent数量: {}", agentPublicKeys.size());
    }
    
    /**
     * 创建配置目录
     */
    private void createConfigDirectory() {
        try {
            Path configPath = Paths.get(configDir);
            if (!Files.exists(configPath)) {
                Files.createDirectories(configPath);
                log.info("[ServerEncryption] 创建加密配置目录: {}", configDir);
            }
        } catch (IOException e) {
            throw new RuntimeException("创建加密配置目录失败: " + configDir, e);
        }
    }
    
    /**
     * 加载或生成服务器密钥
     */
    private void loadOrGenerateServerKeys() {
        File keysFile = new File(configDir, SERVER_KEYS_FILE);
        
        if (keysFile.exists()) {
            loadServerKeysFromFile(keysFile);
            
            // 检查是否需要密钥轮换
            if (shouldRotateKeys()) {
                log.info("[ServerEncryption] 服务器密钥已过期，生成新密钥对");
                generateAndSaveServerKeys(keysFile);
            } else {
                log.info("[ServerEncryption] 加载现有服务器密钥对");
            }
        } else {
            log.info("[ServerEncryption] 首次运行，生成新服务器密钥对");
            generateAndSaveServerKeys(keysFile);
        }
    }
    
    /**
     * 从文件加载服务器密钥
     */
    private void loadServerKeysFromFile(File keysFile) {
        try (FileInputStream fis = new FileInputStream(keysFile)) {
            Properties props = new Properties();
            props.load(fis);
            
            this.serverPrivateKey = props.getProperty(SERVER_PRIVATE_KEY);
            this.serverPublicKey = props.getProperty(SERVER_PUBLIC_KEY);
            this.keyGenerationTime = Long.parseLong(props.getProperty(KEY_GENERATION_TIME, "0"));
            
        } catch (Exception e) {
            log.error("[ServerEncryption] 加载服务器密钥失败，将重新生成: {}", e.getMessage());
            generateAndSaveServerKeys(keysFile);
        }
    }
    
    /**
     * 生成并保存服务器密钥
     */
    private void generateAndSaveServerKeys(File keysFile) {
        try {
            // 生成服务器密钥对
            KeyPair serverKeyPair = encryptionService.generateKeyPair();
            this.serverPrivateKey = encryptionService.privateKeyToPem(serverKeyPair.getPrivate());
            this.serverPublicKey = encryptionService.publicKeyToPem(serverKeyPair.getPublic());
            this.keyGenerationTime = System.currentTimeMillis();
            
            // 保存到文件
            saveServerKeysToFile(keysFile);
            
            log.info("[ServerEncryption] 新服务器密钥对已生成并保存");
            
        } catch (Exception e) {
            throw new RuntimeException("生成服务器密钥对失败", e);
        }
    }
    
    /**
     * 保存服务器密钥到文件
     */
    private void saveServerKeysToFile(File keysFile) {
        try (FileOutputStream fos = new FileOutputStream(keysFile)) {
            Properties props = new Properties();
            
            props.setProperty(SERVER_PRIVATE_KEY, serverPrivateKey);
            props.setProperty(SERVER_PUBLIC_KEY, serverPublicKey);
            props.setProperty(KEY_GENERATION_TIME, String.valueOf(keyGenerationTime));
            
            props.store(fos, "LightScript Server Encryption Keys - Generated at " + 
                new java.util.Date(keyGenerationTime));
            
            // 设置文件权限（仅所有者可读写）
            keysFile.setReadable(false, false);
            keysFile.setReadable(true, true);
            keysFile.setWritable(false, false);
            keysFile.setWritable(true, true);
            
        } catch (IOException e) {
            throw new RuntimeException("保存服务器密钥文件失败", e);
        }
    }
    
    /**
     * 加载Agent公钥
     */
    private void loadAgentPublicKeys() {
        File agentKeysFile = new File(configDir, AGENT_KEYS_FILE);
        
        if (agentKeysFile.exists()) {
            try (FileInputStream fis = new FileInputStream(agentKeysFile)) {
                Properties props = new Properties();
                props.load(fis);
                
                for (String agentId : props.stringPropertyNames()) {
                    String publicKey = props.getProperty(agentId);
                    agentPublicKeys.put(agentId, publicKey);
                }
                
                log.info("[ServerEncryption] 加载了 {} 个Agent公钥", agentPublicKeys.size());
                
            } catch (Exception e) {
                log.error("[ServerEncryption] 加载Agent公钥失败: {}", e.getMessage());
            }
        }
    }
    
    /**
     * 保存Agent公钥
     */
    private void saveAgentPublicKeys() {
        File agentKeysFile = new File(configDir, AGENT_KEYS_FILE);
        
        try (FileOutputStream fos = new FileOutputStream(agentKeysFile)) {
            Properties props = new Properties();
            
            for (String agentId : agentPublicKeys.keySet()) {
                props.setProperty(agentId, agentPublicKeys.get(agentId));
            }
            
            props.store(fos, "LightScript Agent Public Keys - Updated at " + new java.util.Date());
            
        } catch (IOException e) {
            log.error("[ServerEncryption] 保存Agent公钥失败: {}", e.getMessage());
        }
    }
    
    /**
     * 注册Agent公钥
     */
    public void registerAgentPublicKey(String agentId, String publicKey) {
        if (!encryptionEnabled) {
            return;
        }
        
        // 验证公钥格式
        try {
            // 尝试解析公钥以验证格式
            encryptionService.parsePublicKey(publicKey);
            
            // 格式正确，注册公钥
            agentPublicKeys.put(agentId, publicKey);
            
            // 如果之前标记为损坏，现在移除标记
            corruptedAgentKeys.remove(agentId);
            
            saveAgentPublicKeys();
            
            log.info("[ServerEncryption] Agent公钥已注册: {}", agentId);
            
        } catch (Exception e) {
            // 公钥格式错误，记录但不影响其他Agent
            log.error("[ServerEncryption] Agent公钥格式错误，拒绝注册: agentId={}, error={}", 
                agentId, e.getMessage());
            
            // 标记为损坏
            corruptedAgentKeys.put(agentId, "Registration failed: " + e.getMessage());
            
            throw new RuntimeException("公钥格式错误: " + e.getMessage());
        }
    }
    
    /**
     * 获取Agent公钥
     */
    public String getAgentPublicKey(String agentId) {
        // 检查是否被标记为损坏
        if (corruptedAgentKeys.containsKey(agentId)) {
            log.warn("[ServerEncryption] Agent公钥已被标记为损坏: agentId={}, reason={}", 
                agentId, corruptedAgentKeys.get(agentId));
            return null;
        }
        
        return agentPublicKeys.get(agentId);
    }
    
    /**
     * 标记Agent公钥为损坏
     */
    public void markAgentPublicKeyAsCorrupted(String agentId, String reason) {
        corruptedAgentKeys.put(agentId, reason);
        
        // 从正常公钥列表中移除
        agentPublicKeys.remove(agentId);
        saveAgentPublicKeys();
        
        log.error("[ServerEncryption] Agent公钥已标记为损坏: agentId={}, reason={}", agentId, reason);
    }
    
    /**
     * 检查Agent公钥是否损坏
     */
    public boolean isAgentPublicKeyCorrupted(String agentId) {
        return corruptedAgentKeys.containsKey(agentId);
    }
    
    /**
     * 获取损坏公钥的原因
     */
    public String getCorruptedKeyReason(String agentId) {
        return corruptedAgentKeys.get(agentId);
    }
    
    /**
     * 检查是否需要密钥轮换
     */
    private boolean shouldRotateKeys() {
        if (keyGenerationTime == 0) {
            return true;
        }
        
        long keyAge = System.currentTimeMillis() - keyGenerationTime;
        long rotationInterval = keyRotationDays * 24L * 60 * 60 * 1000;
        return keyAge > rotationInterval;
    }
    
    /**
     * 强制密钥轮换
     */
    public void rotateKeys() {
        if (!encryptionEnabled) {
            return;
        }
        
        File keysFile = new File(configDir, SERVER_KEYS_FILE);
        generateAndSaveServerKeys(keysFile);
        
        // 通知所有Agent密钥已轮换
        notifyAgentsOfKeyRotation();
        
        log.info("[ServerEncryption] 服务器密钥轮换完成");
    }
    
    /**
     * 密钥轮换时通知所有Agent
     */
    private void notifyAgentsOfKeyRotation() {
        if (agentPublicKeys.isEmpty()) {
            log.info("[ServerEncryption] 没有注册的Agent，跳过密钥轮换通知");
            return;
        }
        
        log.info("[ServerEncryption] 通知 {} 个Agent服务器密钥已轮换", agentPublicKeys.size());
        
        // 这里可以实现主动通知机制，比如：
        // 1. 通过WebSocket推送通知
        // 2. 在Agent下次请求时返回特殊头部
        // 3. 记录轮换事件，Agent定期检查
        
        // 当前实现：Agent会在定期检查中自动发现密钥更新
        // 因为Agent会比较keyGenerationTime来判断是否需要更新
    }
    
    /**
     * 检查加密是否已配置
     */
    public boolean isEncryptionConfigured() {
        return encryptionEnabled && 
               serverPrivateKey != null && 
               serverPublicKey != null;
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
    
    // Getters
    public boolean isEncryptionEnabled() {
        return encryptionEnabled;
    }
    
    public String getServerPrivateKey() {
        return serverPrivateKey;
    }
    
    public String getServerPublicKey() {
        return serverPublicKey;
    }
    
    public long getKeyGenerationTime() {
        return keyGenerationTime;
    }
    
    public int getRegisteredAgentCount() {
        return agentPublicKeys.size();
    }
}