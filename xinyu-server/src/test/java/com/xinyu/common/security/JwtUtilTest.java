package com.xinyu.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M1-1.3 验收测试 (JwtUtil 部分): 纯单元测试, 不依赖 Spring 容器与数据库
 *
 * <p>验收项:
 * 1. Token 生成: 输出标准三段式 JWT
 * 2. Token 解析: 还原 userId / username
 * 3. 过期测试: expire=1ms, 等待后解析抛 ExpiredJwtException
 * 4. 篡改测试: 错误密钥签发的 token 解析失败 (防伪造)
 */
class JwtUtilTest {

    /** 测试专用密钥, 与运行环境无关 (>=32字节, 满足HS256) */
    private static final String SECRET = "unit-test-secret-xinyu-0123456789abcdef0123456789abcdef";

    private static final long EXPIRE_2H = 7200000L;

    @Test
    @DisplayName("验收1+2: 生成三段式token并解析还原userId/username")
    void generateAndParse() {
        JwtUtil jwtUtil = new JwtUtil(SECRET, EXPIRE_2H);
        // 用真实雪花ID量级, 覆盖超出int范围的Long
        Long userId = 2082676040983887873L;

        String token = jwtUtil.generate(userId, "test");
        System.out.println("[验收1] token = " + token);
        assertNotNull(token);
        assertEquals(3, token.split("\\.").length, "JWT应为 header.payload.signature 三段式");
        // header 固定为 {"alg":"HS256"}, 防止算法随密钥长度漂移
        assertTrue(token.startsWith("eyJhbGciOiJIUzI1NiJ9"), "签名算法应钉死为 HS256");

        Claims claims = jwtUtil.parse(token);
        assertEquals(userId, jwtUtil.getUserId(claims));
        assertEquals("test", jwtUtil.getUsername(claims));
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
        // exp - iat 应等于配置的有效期 (秒级精度)
        long diffMs = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();
        assertTrue(Math.abs(diffMs - EXPIRE_2H) < 1000, "有效期应为2小时");
        System.out.println("[验收2] userId = " + jwtUtil.getUserId(claims)
                + ", username = " + jwtUtil.getUsername(claims)
                + ", iat = " + claims.getIssuedAt() + ", exp = " + claims.getExpiration());
    }

    @Test
    @DisplayName("验收3: 过期token解析抛ExpiredJwtException")
    void expiredTokenShouldFail() throws InterruptedException {
        JwtUtil shortLived = new JwtUtil(SECRET, 1L);
        String token = shortLived.generate(1L, "test");

        Thread.sleep(50);

        assertThrows(ExpiredJwtException.class, () -> shortLived.parse(token));
        System.out.println("[验收3] 过期token解析抛出 ExpiredJwtException ✓");
    }

    @Test
    @DisplayName("验收4: 错误密钥签发的token解析失败(防伪造)")
    void tamperedTokenShouldFail() {
        JwtUtil real = new JwtUtil(SECRET, EXPIRE_2H);
        JwtUtil attacker = new JwtUtil("attacker-fake-secret-0123456789abcdef0123456789", EXPIRE_2H);
        String fakeToken = attacker.generate(999L, "hacker");

        assertThrows(JwtException.class, () -> real.parse(fakeToken));
        System.out.println("[验收4] 伪造token解析抛出 JwtException ✓");
    }
}
