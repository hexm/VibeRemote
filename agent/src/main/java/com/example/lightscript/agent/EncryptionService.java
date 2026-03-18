package com.example.lightscript.agent;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * 加密服务接口实现
 * 提供AES-256-GCM对称加密和RSA-2048非对称加密功能
 */
public class EncryptionService {
    
    private static final String AES_ALGORITHM = "AES";
    private static final String AES_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String RSA_ALGORITHM = "RSA";
    private static final String RSA_TRANSFORMATION = "RSA/ECB/PKCS1Padding";
    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";
    
    private static final int AES_KEY_LENGTH = 256;
    private static final int RSA_KEY_LENGTH = 2048;
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 16;
    
    private final SecureRandom secureRandom;
    
    public EncryptionService() {
        this.secureRandom = new SecureRandom();
    }
    
    /**
     * 加密数据载荷
     */
    public static class EncryptedPayload {
        private String encryptedData;      // AES-256-GCM加密的数据
        private String encryptedKey;       // RSA加密的AES密钥
        private String iv;                 // AES初始化向量
        private String authTag;            // GCM认证标签
        private String signature;          // RSA完整性签名
        private long timestamp;            // 重放攻击防护
        
        // 默认构造函数（Jackson需要）
        public EncryptedPayload() {}
        
        public EncryptedPayload(String encryptedData, String encryptedKey, String iv, 
                              String authTag, String signature, long timestamp) {
            this.encryptedData = encryptedData;
            this.encryptedKey = encryptedKey;
            this.iv = iv;
            this.authTag = authTag;
            this.signature = signature;
            this.timestamp = timestamp;
        }
        
        // Getters
        public String getEncryptedData() { return encryptedData; }
        public String getEncryptedKey() { return encryptedKey; }
        public String getIv() { return iv; }
        public String getAuthTag() { return authTag; }
        public String getSignature() { return signature; }
        public long getTimestamp() { return timestamp; }
        
        // Setters（Jackson需要）
        public void setEncryptedData(String encryptedData) { this.encryptedData = encryptedData; }
        public void setEncryptedKey(String encryptedKey) { this.encryptedKey = encryptedKey; }
        public void setIv(String iv) { this.iv = iv; }
        public void setAuthTag(String authTag) { this.authTag = authTag; }
        public void setSignature(String signature) { this.signature = signature; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
        
        @com.fasterxml.jackson.annotation.JsonIgnore
        public boolean isComplete() {
            return encryptedData != null && encryptedKey != null && 
                   iv != null && authTag != null && signature != null && timestamp > 0;
        }
        
        @com.fasterxml.jackson.annotation.JsonIgnore
        public boolean isValid() {
            return isComplete() && 
                   Math.abs(System.currentTimeMillis() - timestamp) <= 300000; // 5分钟窗口
        }
    }
    
    /**
     * 加密批量数据
     */
    public EncryptedPayload encrypt(byte[] compressedData, String serverPublicKeyPem, String agentPrivateKeyPem) {
        try {
            // 1. 生成AES会话密钥
            SecretKey sessionKey = generateAESKey();
            
            // 2. 生成随机IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            
            // 3. 使用AES-256-GCM加密数据
            Cipher aesCipher = Cipher.getInstance(AES_TRANSFORMATION);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            aesCipher.init(Cipher.ENCRYPT_MODE, sessionKey, gcmSpec);
            
            byte[] encryptedData = aesCipher.doFinal(compressedData);
            
            // 4. 提取认证标签（GCM模式下包含在加密数据末尾）
            byte[] ciphertext = new byte[encryptedData.length - GCM_TAG_LENGTH];
            byte[] authTag = new byte[GCM_TAG_LENGTH];
            System.arraycopy(encryptedData, 0, ciphertext, 0, ciphertext.length);
            System.arraycopy(encryptedData, ciphertext.length, authTag, 0, GCM_TAG_LENGTH);
            
            // 5. 使用RSA加密会话密钥
            PublicKey serverPublicKey = parsePublicKey(serverPublicKeyPem);
            Cipher rsaCipher = Cipher.getInstance(RSA_TRANSFORMATION);
            rsaCipher.init(Cipher.ENCRYPT_MODE, serverPublicKey);
            byte[] encryptedKey = rsaCipher.doFinal(sessionKey.getEncoded());
            
            // 6. 生成完整性签名
            long timestamp = System.currentTimeMillis();
            String signatureData = Base64.getEncoder().encodeToString(ciphertext) + 
                                 Base64.getEncoder().encodeToString(encryptedKey) + 
                                 Base64.getEncoder().encodeToString(iv) + 
                                 timestamp;
            
            PrivateKey agentPrivateKey = parsePrivateKey(agentPrivateKeyPem);
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
            signature.initSign(agentPrivateKey);
            signature.update(signatureData.getBytes());
            byte[] signatureBytes = signature.sign();
            
            return new EncryptedPayload(
                Base64.getEncoder().encodeToString(ciphertext),
                Base64.getEncoder().encodeToString(encryptedKey),
                Base64.getEncoder().encodeToString(iv),
                Base64.getEncoder().encodeToString(authTag),
                Base64.getEncoder().encodeToString(signatureBytes),
                timestamp
            );
            
        } catch (Exception e) {
            throw new RuntimeException("批量数据加密失败", e);
        }
    }
    
    /**
     * 解密数据载荷
     */
    public byte[] decrypt(EncryptedPayload payload, String agentPrivateKeyPem, String serverPublicKeyPem) {
        try {
            // 1. 验证时间戳（防重放攻击）
            long currentTime = System.currentTimeMillis();
            if (Math.abs(currentTime - payload.getTimestamp()) > 300000) { // 5分钟窗口
                throw new SecurityException("时间戳超出有效窗口，可能是重放攻击");
            }
            
            // 2. 验证签名完整性
            String signatureData = payload.getEncryptedData() + 
                                 payload.getEncryptedKey() + 
                                 payload.getIv() + 
                                 payload.getTimestamp();
            
            PublicKey serverPublicKey = parsePublicKey(serverPublicKeyPem);
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
            signature.initVerify(serverPublicKey);
            signature.update(signatureData.getBytes());
            
            boolean signatureValid;
            try {
                signatureValid = signature.verify(Base64.getDecoder().decode(payload.getSignature()));
            } catch (SignatureException e) {
                throw new SecurityException("签名验证失败，数据可能被篡改: " + e.getMessage());
            }
            if (!signatureValid) {
                throw new SecurityException("签名验证失败，数据可能被篡改");
            }
            
            // 3. 使用RSA解密会话密钥
            PrivateKey agentPrivateKey = parsePrivateKey(agentPrivateKeyPem);
            Cipher rsaCipher = Cipher.getInstance(RSA_TRANSFORMATION);
            rsaCipher.init(Cipher.DECRYPT_MODE, agentPrivateKey);
            byte[] sessionKeyBytes = rsaCipher.doFinal(Base64.getDecoder().decode(payload.getEncryptedKey()));
            SecretKey sessionKey = new SecretKeySpec(sessionKeyBytes, AES_ALGORITHM);
            
            // 4. 重构完整的加密数据（密文 + 认证标签）
            byte[] ciphertext = Base64.getDecoder().decode(payload.getEncryptedData());
            byte[] authTag = Base64.getDecoder().decode(payload.getAuthTag());
            byte[] encryptedData = new byte[ciphertext.length + authTag.length];
            System.arraycopy(ciphertext, 0, encryptedData, 0, ciphertext.length);
            System.arraycopy(authTag, 0, encryptedData, ciphertext.length, authTag.length);
            
            // 5. 使用AES-256-GCM解密数据
            byte[] iv = Base64.getDecoder().decode(payload.getIv());
            Cipher aesCipher = Cipher.getInstance(AES_TRANSFORMATION);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            aesCipher.init(Cipher.DECRYPT_MODE, sessionKey, gcmSpec);
            
            return aesCipher.doFinal(encryptedData);
            
        } catch (SecurityException e) {
            // 重新抛出安全异常
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("批量数据解密失败", e);
        }
    }
    
    /**
     * 生成RSA密钥对
     */
    public KeyPair generateKeyPair() {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance(RSA_ALGORITHM);
            keyGen.initialize(RSA_KEY_LENGTH, secureRandom);
            return keyGen.generateKeyPair();
        } catch (Exception e) {
            throw new RuntimeException("RSA密钥对生成失败", e);
        }
    }
    
    /**
     * 生成AES会话密钥
     */
    private SecretKey generateAESKey() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance(AES_ALGORITHM);
            keyGen.init(AES_KEY_LENGTH, secureRandom);
            return keyGen.generateKey();
        } catch (Exception e) {
            throw new RuntimeException("AES密钥生成失败", e);
        }
    }
    
    /**
     * 解析PEM格式公钥
     */
    private PublicKey parsePublicKey(String publicKeyPem) {
        try {
            String publicKeyContent = publicKeyPem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
            
            byte[] keyBytes = Base64.getDecoder().decode(publicKeyContent);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance(RSA_ALGORITHM);
            return keyFactory.generatePublic(keySpec);
        } catch (Exception e) {
            throw new RuntimeException("公钥解析失败", e);
        }
    }
    
    /**
     * 解析PEM格式私钥
     */
    private PrivateKey parsePrivateKey(String privateKeyPem) {
        try {
            String privateKeyContent = privateKeyPem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
            
            byte[] keyBytes = Base64.getDecoder().decode(privateKeyContent);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance(RSA_ALGORITHM);
            return keyFactory.generatePrivate(keySpec);
        } catch (Exception e) {
            throw new RuntimeException("私钥解析失败", e);
        }
    }
    
    /**
     * 将公钥转换为PEM格式
     */
    public String publicKeyToPem(PublicKey publicKey) {
        byte[] encoded = publicKey.getEncoded();
        String base64 = Base64.getEncoder().encodeToString(encoded);
        
        // 手动处理换行，确保格式正确
        StringBuilder pem = new StringBuilder();
        pem.append("-----BEGIN PUBLIC KEY-----\n");
        
        // 每64个字符换行
        for (int i = 0; i < base64.length(); i += 64) {
            int end = Math.min(i + 64, base64.length());
            pem.append(base64.substring(i, end)).append("\n");
        }
        
        pem.append("-----END PUBLIC KEY-----");
        return pem.toString();
    }
    
    /**
     * 将私钥转换为PEM格式
     */
    public String privateKeyToPem(PrivateKey privateKey) {
        byte[] encoded = privateKey.getEncoded();
        String base64 = Base64.getEncoder().encodeToString(encoded);
        
        // 手动处理换行，确保格式正确
        StringBuilder pem = new StringBuilder();
        pem.append("-----BEGIN PRIVATE KEY-----\n");
        
        // 每64个字符换行
        for (int i = 0; i < base64.length(); i += 64) {
            int end = Math.min(i + 64, base64.length());
            pem.append(base64.substring(i, end)).append("\n");
        }
        
        pem.append("-----END PRIVATE KEY-----");
        return pem.toString();
    }
}