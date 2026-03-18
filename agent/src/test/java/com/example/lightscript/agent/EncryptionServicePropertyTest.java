package com.example.lightscript.agent;

import net.jqwik.api.*;
import net.jqwik.api.constraints.Size;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.KeyPair;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 加密服务属性测试（PBT）
 * 验证设计文档中定义的正确性属性
 */
class EncryptionServicePropertyTest {

    static EncryptionService encryptionService;
    static KeyPair serverKeyPair;
    static KeyPair agentKeyPair;
    static String serverPublicKeyPem;
    static String serverPrivateKeyPem;
    static String agentPublicKeyPem;
    static String agentPrivateKeyPem;

    @BeforeAll
    static void setup() {
        encryptionService = new EncryptionService();
        serverKeyPair = encryptionService.generateKeyPair();
        agentKeyPair = encryptionService.generateKeyPair();
        serverPublicKeyPem = encryptionService.publicKeyToPem(serverKeyPair.getPublic());
        serverPrivateKeyPem = encryptionService.privateKeyToPem(serverKeyPair.getPrivate());
        agentPublicKeyPem = encryptionService.publicKeyToPem(agentKeyPair.getPublic());
        agentPrivateKeyPem = encryptionService.privateKeyToPem(agentKeyPair.getPrivate());
    }

    // -----------------------------------------------------------------------
    // 属性 4：AES-256-GCM 加密往返正确性（需求 5.4）
    // -----------------------------------------------------------------------

    @Property(tries = 20)
    void prop4_aesGcmRoundTrip(@ForAll @Size(min = 1, max = 1024) byte[] plaintext) {
        // Agent 用服务器公钥加密，服务器用自身私钥解密
        EncryptionService.EncryptedPayload payload =
                encryptionService.encrypt(plaintext, serverPublicKeyPem, agentPrivateKeyPem);

        byte[] decrypted = encryptionService.decrypt(payload, serverPrivateKeyPem, agentPublicKeyPem);

        assertArrayEquals(plaintext, decrypted,
                "属性4：AES-256-GCM 加密后解密应还原原始数据");
    }

    // -----------------------------------------------------------------------
    // 属性 5：RSA 密钥交换往返正确性（需求 6.3）
    // 通过 encrypt/decrypt 整体流程间接验证 RSA 会话密钥加解密
    // -----------------------------------------------------------------------

    @Property(tries = 10)
    void prop5_rsaKeyExchangeRoundTrip(@ForAll @Size(min = 1, max = 512) byte[] data) {
        EncryptionService.EncryptedPayload payload =
                encryptionService.encrypt(data, serverPublicKeyPem, agentPrivateKeyPem);

        // encryptedKey 字段非空，说明 RSA 加密了会话密钥
        assertNotNull(payload.getEncryptedKey(), "属性5：RSA 加密的会话密钥不应为空");
        assertFalse(payload.getEncryptedKey().isEmpty());

        // 解密成功即验证 RSA 密钥交换往返正确
        byte[] decrypted = encryptionService.decrypt(payload, serverPrivateKeyPem, agentPublicKeyPem);
        assertArrayEquals(data, decrypted, "属性5：RSA 密钥交换后解密应还原原始数据");
    }

    // -----------------------------------------------------------------------
    // 属性 6：数字签名验证一致性（需求 7.4）
    // -----------------------------------------------------------------------

    @Property(tries = 20)
    void prop6_signatureValid_onOriginalData(@ForAll @Size(min = 1, max = 512) byte[] data) {
        EncryptionService.EncryptedPayload payload =
                encryptionService.encrypt(data, serverPublicKeyPem, agentPrivateKeyPem);

        // 正常解密（含签名验证）应成功
        assertDoesNotThrow(
                () -> encryptionService.decrypt(payload, serverPrivateKeyPem, agentPublicKeyPem),
                "属性6：原始数据的签名验证应通过");
    }

    @Property(tries = 20)
    void prop6_signatureInvalid_onTamperedData(@ForAll @Size(min = 1, max = 512) byte[] data) {
        EncryptionService.EncryptedPayload payload =
                encryptionService.encrypt(data, serverPublicKeyPem, agentPrivateKeyPem);

        // 篡改签名
        EncryptionService.EncryptedPayload tampered = new EncryptionService.EncryptedPayload(
                payload.getEncryptedData(),
                payload.getEncryptedKey(),
                payload.getIv(),
                payload.getAuthTag(),
                "dGFtcGVyZWQ=", // base64("tampered")
                payload.getTimestamp()
        );

        assertThrows(SecurityException.class,
                () -> encryptionService.decrypt(tampered, serverPrivateKeyPem, agentPublicKeyPem),
                "属性6：篡改签名后验证应抛出 SecurityException");
    }

    // -----------------------------------------------------------------------
    // 属性 7：重放攻击防护边界（需求 8.2、8.3）
    // -----------------------------------------------------------------------

    @Test
    void prop7_replayAttack_timestampExpired() {
        byte[] data = "replay-test".getBytes();
        EncryptionService.EncryptedPayload payload =
                encryptionService.encrypt(data, serverPublicKeyPem, agentPrivateKeyPem);

        // 构造超出 5 分钟窗口的 payload
        EncryptionService.EncryptedPayload expired = new EncryptionService.EncryptedPayload(
                payload.getEncryptedData(),
                payload.getEncryptedKey(),
                payload.getIv(),
                payload.getAuthTag(),
                payload.getSignature(),
                System.currentTimeMillis() - 301_000L // 超出 5 分钟
        );

        assertThrows(SecurityException.class,
                () -> encryptionService.decrypt(expired, serverPrivateKeyPem, agentPublicKeyPem),
                "属性7：时间戳超出 5 分钟窗口应抛出 SecurityException");
    }

    @Test
    void prop7_replayAttack_timestampWithinWindow() {
        byte[] data = "valid-timestamp-test".getBytes();
        EncryptionService.EncryptedPayload payload =
                encryptionService.encrypt(data, serverPublicKeyPem, agentPrivateKeyPem);

        // 时间戳在窗口内，应正常解密
        assertDoesNotThrow(
                () -> encryptionService.decrypt(payload, serverPrivateKeyPem, agentPublicKeyPem),
                "属性7：时间戳在 5 分钟窗口内应正常解密");
    }

    // -----------------------------------------------------------------------
    // 属性 8：压缩 + 加密组合往返正确性（需求 5.4、6.3、2.4）
    // -----------------------------------------------------------------------

    @Property(tries = 20)
    void prop8_compressEncryptRoundTrip(@ForAll @Size(min = 1, max = 2048) byte[] original) throws IOException {
        // 压缩
        byte[] compressed = gzipCompress(original);

        // 加密
        EncryptionService.EncryptedPayload payload =
                encryptionService.encrypt(compressed, serverPublicKeyPem, agentPrivateKeyPem);

        // 解密
        byte[] decryptedCompressed = encryptionService.decrypt(payload, serverPrivateKeyPem, agentPublicKeyPem);

        // 解压缩
        byte[] restored = gzipDecompress(decryptedCompressed);

        assertArrayEquals(original, restored,
                "属性8：压缩→加密→解密→解压缩应还原原始数据");
    }

    // -----------------------------------------------------------------------
    // 属性 2：GZIP 压缩往返正确性（需求 2.4）
    // -----------------------------------------------------------------------

    @Property(tries = 50)
    void prop2_gzipRoundTrip(@ForAll @Size(min = 0, max = 4096) byte[] data) throws IOException {
        byte[] compressed = gzipCompress(data);
        byte[] restored = gzipDecompress(compressed);
        assertArrayEquals(data, restored, "属性2：GZIP 压缩后解压缩应还原原始数据");
    }

    // -----------------------------------------------------------------------
    // 属性 9：加密开关路由正确性（需求 11.1、11.2）
    // 通过 EncryptedBatchLogCollector.isEncryptionConfigured() 间接验证
    // -----------------------------------------------------------------------

    @Test
    void prop9_encryptionDisabled_collectorNotConfigured() {
        // encryption.enabled=false 时，收集器不应进入加密路径
        EncryptedBatchLogCollector collector = new EncryptedBatchLogCollector(
                "http://localhost:8080", null, null, false, null);
        // 加密未启用时 getAgentPublicKey() 应返回 null
        assertNull(collector.getAgentPublicKey(),
                "属性9：加密未启用时 agentPublicKey 应为 null");
    }

    // -----------------------------------------------------------------------
    // 辅助方法
    // -----------------------------------------------------------------------

    private byte[] gzipCompress(byte[] data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
            gzip.write(data);
        }
        return baos.toByteArray();
    }

    private byte[] gzipDecompress(byte[] compressed) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPInputStream gzip = new GZIPInputStream(
                new java.io.ByteArrayInputStream(compressed))) {
            byte[] buf = new byte[1024];
            int len;
            while ((len = gzip.read(buf)) != -1) {
                baos.write(buf, 0, len);
            }
        }
        return baos.toByteArray();
    }
}
