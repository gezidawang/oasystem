package com.employee.utils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256/GCM 字段级加解密工具（Phase 0-4）。
 * <p>
 * - 加密输出格式：Base64(IV[12B] + 密文 + Tag[16B])，解密反向。
 * - 密钥来源：application.yml 的 aes.key（任意长度，内部 SHA-256 派生为 32 字节）。
 * - GCM 模式自带认证标签，防篡改。
 * <p>
 * 初始化时机：EncryptConfig @PostConstruct 调用 {@link #init(String)}，
 * 之后由 EncryptedStringTypeHandler 在 MyBatis 读写时透明调用 encrypt/decrypt。
 */
public final class AesUtil {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;       // GCM 推荐 12 字节 IV
    private static final int TAG_LENGTH_BITS = 128; // 16 字节认证标签

    private static volatile SecretKeySpec secretKey;
    private static final SecureRandom RANDOM = new SecureRandom();

    private AesUtil() {}

    /**
     * 从配置字符串派生 256-bit AES 密钥（SHA-256 → 32 bytes）。
     * 应用启动时调用一次即可。
     */
    public static synchronized void init(String rawKey) {
        try {
            byte[] keyBytes = MessageDigest.getInstance("SHA-256")
                    .digest(rawKey.getBytes(StandardCharsets.UTF_8));
            secretKey = new SecretKeySpec(keyBytes, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("AES 密钥初始化失败", e);
        }
    }

    /** 加密：明文 → Base64(IV+密文+Tag) */
    public static String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            // 拼接 IV + cipherText(含Tag)
            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("加密失败", e);
        }
    }

    /** 解密：Base64(IV+密文+Tag) → 明文 */
    public static String decrypt(String cipherText) {
        if (cipherText == null || cipherText.isEmpty()) {
            return cipherText;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(cipherText);
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            byte[] encrypted = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, IV_LENGTH, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            // 解密失败：可能历史明文或密钥漂移，原样返回避免阻断业务
            return cipherText;
        }
    }

    /** 判断当前是否已初始化（供 TypeHandler 安全降级） */
    public static boolean isInitialized() {
        return secretKey != null;
    }
}
