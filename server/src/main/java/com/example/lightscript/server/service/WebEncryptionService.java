package com.example.lightscript.server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 前端-服务器通信加密服务
 * 使用 AES-256-GCM 对称加密，密钥随登录会话下发
 */
@Service
public class WebEncryptionService {

    private static final Logger log = LoggerFactory.getLogger(WebEncryptionService.class);

    private static final String AES_ALGORITHM = "AES";
    private static final String AES_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int AES_KEY_BITS = 256;
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 16;

    private final SecureRandom secureRandom = new SecureRandom();

    // 用户名 -> Base64 编码的 AES 密钥（内存中，重启失效）
    private final ConcurrentHashMap<String, String> sessionKeys = new ConcurrentHashMap<>();

    /**
     * 为用户生成并存储会话密钥，返回 Base64 编码的密钥供前端使用
     */
    public String generateSessionKey(String username) {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance(AES_ALGORITHM);
            keyGen.init(AES_KEY_BITS, secureRandom);
            SecretKey key = keyGen.generateKey();
            String keyBase64 = Base64.getEncoder().encodeToString(key.getEncoded());
            sessionKeys.put(username, keyBase64);
            log.debug("[WebEncryption] 为用户 {} 生成会话密钥", username);
            return keyBase64;
        } catch (Exception e) {
            throw new RuntimeException("生成会话密钥失败", e);
        }
    }

    /**
     * 使用用户会话密钥加密字符串，返回 "iv:ciphertext" 格式的 Base64 字符串
     */
    public String encrypt(String username, String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) return plaintext;
        String keyBase64 = sessionKeys.get(username);
        if (keyBase64 == null) {
            log.warn("[WebEncryption] 用户 {} 无会话密钥，跳过加密", username);
            return plaintext;
        }
        try {
            SecretKey key = new SecretKeySpec(Base64.getDecoder().decode(keyBase64), AES_ALGORITHM);
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes("UTF-8"));

            // 格式：Base64(iv) + ":" + Base64(ciphertext+authTag)
            return Base64.getEncoder().encodeToString(iv)
                    + ":" + Base64.getEncoder().encodeToString(ciphertext);
        } catch (Exception e) {
            log.error("[WebEncryption] 加密失败: {}", e.getMessage());
            throw new RuntimeException("加密失败", e);
        }
    }

    /**
     * 使用用户会话密钥解密 "iv:ciphertext" 格式的字符串
     */
    public String decrypt(String username, String encrypted) {
        if (encrypted == null || encrypted.isEmpty() || !encrypted.contains(":")) return encrypted;
        String keyBase64 = sessionKeys.get(username);
        if (keyBase64 == null) {
            log.warn("[WebEncryption] 用户 {} 无会话密钥，跳过解密", username);
            return encrypted;
        }
        try {
            String[] parts = encrypted.split(":", 2);
            byte[] iv = Base64.getDecoder().decode(parts[0]);
            byte[] ciphertext = Base64.getDecoder().decode(parts[1]);

            SecretKey key = new SecretKeySpec(Base64.getDecoder().decode(keyBase64), AES_ALGORITHM);
            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv));
            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, "UTF-8");
        } catch (Exception e) {
            log.error("[WebEncryption] 解密失败: {}", e.getMessage());
            throw new RuntimeException("解密失败", e);
        }
    }

    /**
     * 用户登出时清除会话密钥
     */
    public void removeSessionKey(String username) {
        sessionKeys.remove(username);
        log.debug("[WebEncryption] 清除用户 {} 的会话密钥", username);
    }

    /**
     * 检查用户是否有会话密钥
     */
    public boolean hasSessionKey(String username) {
        return sessionKeys.containsKey(username);
    }
}
