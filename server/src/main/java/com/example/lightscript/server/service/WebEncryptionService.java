package com.example.lightscript.server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 前端-服务器通信加密服务
 * 使用 AES-256-GCM 对称加密，全局固定密钥，配置在 application.yml 中
 */
@Service
public class WebEncryptionService {

    private static final Logger log = LoggerFactory.getLogger(WebEncryptionService.class);

    private static final String AES_ALGORITHM = "AES";
    private static final String AES_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 16;

    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${lightscript.web.encryption.key}")
    private String globalKey;

    /**
     * 返回全局固定密钥（所有用户共用）
     */
    public String generateSessionKey(String username) {
        return globalKey;
    }

    /**
     * 使用全局密钥加密字符串，返回 "iv:ciphertext" 格式的 Base64 字符串
     */
    public String encrypt(String username, String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) return plaintext;
        try {
            SecretKeySpec key = new SecretKeySpec(Base64.getDecoder().decode(globalKey), AES_ALGORITHM);
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes("UTF-8"));

            return Base64.getEncoder().encodeToString(iv)
                    + ":" + Base64.getEncoder().encodeToString(ciphertext);
        } catch (Exception e) {
            log.error("[WebEncryption] 加密失败: {}", e.getMessage());
            throw new RuntimeException("加密失败", e);
        }
    }

    /**
     * 使用全局密钥解密 "iv:ciphertext" 格式的字符串
     */
    public String decrypt(String username, String encrypted) {
        if (encrypted == null || encrypted.isEmpty() || !encrypted.contains(":")) return encrypted;
        try {
            String[] parts = encrypted.split(":", 2);
            byte[] iv = Base64.getDecoder().decode(parts[0]);
            byte[] ciphertext = Base64.getDecoder().decode(parts[1]);

            SecretKeySpec key = new SecretKeySpec(Base64.getDecoder().decode(globalKey), AES_ALGORITHM);
            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv));
            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, "UTF-8");
        } catch (Exception e) {
            log.error("[WebEncryption] 解密失败: {}", e.getMessage());
            throw new RuntimeException("解密失败", e);
        }
    }

    public void removeSessionKey(String username) {
        // 全局密钥无需清除
    }

    public boolean hasSessionKey(String username) {
        return globalKey != null && !globalKey.isEmpty();
    }

    public String getSessionKey(String username) {
        return globalKey;
    }
}
