package com.xinyu.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具：生成 / 解析 access_token（HS256）
 *
 * <p>payload 只放 userId + username：userId 是后端识别当前用户的唯一依据，
 * username 仅用于日志排查，不作为权限依据。
 * 简化方案：无 refresh_token，过期后前端跳登录页重新登录（见 docs/design.md 3.1）。
 */
@Component
public class JwtUtil {

    /** 自定义声明: 用户ID */
    public static final String CLAIM_USER_ID = "userId";

    /** 自定义声明: 用户名 */
    public static final String CLAIM_USERNAME = "username";

    /** HS256 签名密钥 */
    private final SecretKey key;

    /** 有效期（毫秒） */
    private final long expireMs;

    /**
     * 构造器注入便于单元测试直接 new（不依赖 Spring 容器）
     *
     * @param secret   密钥原文, HS256 要求至少 32 字节
     * @param expireMs 有效期毫秒数
     */
    public JwtUtil(@Value("${xinyu.jwt.secret}") String secret,
                   @Value("${xinyu.jwt.expire-ms}") long expireMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireMs = expireMs;
    }

    /**
     * 生成 access_token
     *
     * @param userId   用户雪花ID
     * @param username 用户名
     * @return 形如 eyJhbGciOiJIUzI1NiJ9.xxx.yyy 的 JWT 字符串
     */
    public String generate(Long userId, String username) {
        Date now = new Date();
        return Jwts.builder()
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_USERNAME, username)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireMs))
                // 显式指定 HS256: jjwt 默认按密钥长度自动选算法, 不钉死会随密钥长度漂移成 HS384/512
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * 解析并校验 token（签名、过期时间）
     *
     * @param token JWT 字符串（不含 Bearer 前缀）
     * @return 解析出的 Claims
     * @throws JwtException 签名不合法 / 已过期 / 格式错误时抛出
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 从 Claims 中取 userId
     *
     * <p>JSON 反序列化时数值可能落成 Integer 或 Long（取决于大小），
     * 统一走 Number 转换避免类型转换异常。
     */
    public Long getUserId(Claims claims) {
        Number userId = claims.get(CLAIM_USER_ID, Number.class);
        return userId == null ? null : userId.longValue();
    }

    /** 从 Claims 中取 username（仅用于日志） */
    public String getUsername(Claims claims) {
        return claims.get(CLAIM_USERNAME, String.class);
    }
}
