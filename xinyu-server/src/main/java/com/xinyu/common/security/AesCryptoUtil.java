package com.xinyu.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-GCM 对称加密工具（用于 API Key 等敏感数据入库加密）
 *
 * <p>密钥来源: 优先 xinyu.crypto.secret (独立密钥); 未配置时回退
 * xinyu.jwt.secret, 兼容与 JWT 共用密钥的存量部署。新部署建议配置
 * 独立密钥: JWT 密钥泄露不再连带暴露用户 API Key 的解密密钥。
 * 密钥变更会导致历史密文无法解密, 需同步重置。
 *
 * <p>采用 GCM 模式: 同时提供机密性 + 完整性认证, 比 ECB/CBC 更安全;
 * 输出格式: Base64(IV[12B] || ciphertext+tag), IV 每次随机。
 */
@Component
public class AesCryptoUtil {

    /** GCM 推荐 IV 长度: 12 字节 */
    private static final int IV_LENGTH = 12;

    /** GCM 认证标签长度: 128 bit */
    private static final int TAG_LENGTH_BIT = 128;

    private final SecretKeySpec keySpec;

    public AesCryptoUtil(@Value("${xinyu.crypto.secret:}") String cryptoSecret,
                         @Value("${xinyu.jwt.secret}") String jwtSecret) {
        String secret = (cryptoSecret == null || cryptoSecret.isBlank()) ? jwtSecret : cryptoSecret;
        // 取 secret 前 32 字节作 AES-256 密钥（不足 32 字节则右补 0, 但 JwtUtil 已要求 ≥32）
        byte[] keyBytes = new byte[32];
        byte[] src = secret.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(src, 0, keyBytes, 0, Math.min(src.length, 32));
        this.keySpec = new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * 加密明文 → Base64(IV || ciphertext+tag)
     *
     * @param plaintext 明文（如 API Key 原文）
     * @return Base64 密文, null 输入返回 null
     */
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BIT, iv));

            byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // 拼接 IV + cipherText, 一并 Base64
            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("AES 加密失败", e);
        }
    }

    /**
     * 解密 Base64(IV || ciphertext+tag) → 明文
     *
     * @param ciphertext Base64 密文
     * @return 明文, null/空输入返回 null
     */
    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isEmpty()) {
            return null;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(ciphertext);
            byte[] iv = new byte[IV_LENGTH];
            byte[] cipherText = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            System.arraycopy(combined, IV_LENGTH, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BIT, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("AES 解密失败: 密钥可能已变更或密文损坏", e);
        }
    }
}
