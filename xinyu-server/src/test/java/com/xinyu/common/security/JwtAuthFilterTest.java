package com.xinyu.common.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * M1-1.3 验收测试 (JwtAuthFilter 部分): 用 Mock 请求直接驱动过滤器, 不起容器
 *
 * <p>验收项:
 * 1. 带合法 Bearer token: 请求处理期间 UserContext 能取到 token 里的 userId
 * 2. 无 Authorization 头: 放行, UserContext 为 null (不拦截, 401交给后续权限层)
 * 3. 带非法 token: 放行, UserContext 为 null
 * 4. 请求结束后: UserContext 必须被清理 (防线程池复用串号)
 */
class JwtAuthFilterTest {

    private static final String SECRET = "unit-test-secret-xinyu-0123456789abcdef0123456789abcdef";

    private final JwtUtil jwtUtil = new JwtUtil(SECRET, 7200000L);

    private final JwtAuthFilter filter = new JwtAuthFilter(jwtUtil);

    @AfterEach
    void cleanUp() {
        UserContext.clear();
    }

    @Test
    @DisplayName("验收1+4: 合法token → 链内取到userId, 链外已清理")
    void validTokenShouldBindUserContext() throws Exception {
        Long userId = 2082676040983887873L;
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + jwtUtil.generate(userId, "test"));

        // 用 FilterChain 捕获"业务处理期间"的上下文值 (模拟Controller读取)
        AtomicReference<Long> userIdInChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> userIdInChain.set(UserContext.getUserId());

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertEquals(userId, userIdInChain.get(), "链内应取到token中的userId");
        assertNull(UserContext.getUserId(), "请求结束后上下文必须被清理");
        System.out.println("[验收1] 链内 UserContext.getUserId() = " + userIdInChain.get());
        System.out.println("[验收4] 链外 UserContext.getUserId() = " + UserContext.getUserId() + " (已清理)");
    }

    @Test
    @DisplayName("验收2: 无Authorization头 → 放行且上下文为null")
    void missingHeaderShouldPassThrough() throws Exception {
        AtomicReference<Long> userIdInChain = new AtomicReference<>();
        AtomicReference<Boolean> chainReached = new AtomicReference<>(false);
        FilterChain chain = (req, res) -> {
            chainReached.set(true);
            userIdInChain.set(UserContext.getUserId());
        };

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        assertEquals(true, chainReached.get(), "无token必须放行(登录接口需匿名访问)");
        assertNull(userIdInChain.get());
        System.out.println("[验收2] 无token放行, 链内 userId = null ✓");
    }

    @Test
    @DisplayName("验收3: 非法token → 放行且上下文为null")
    void invalidTokenShouldPassThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer not.a.jwt");

        AtomicReference<Long> userIdInChain = new AtomicReference<>();
        AtomicReference<Boolean> chainReached = new AtomicReference<>(false);
        FilterChain chain = (req, res) -> {
            chainReached.set(true);
            userIdInChain.set(UserContext.getUserId());
        };

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertEquals(true, chainReached.get(), "非法token不在Filter层拒绝, 401交给后续权限层");
        assertNull(userIdInChain.get());
        System.out.println("[验收3] 非法token放行, 链内 userId = null ✓");
    }
}
