package com.xinyu.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 认证过滤器
 *
 * <p>职责单一：尝试从 Authorization 头解析 Bearer token，
 * 成功则把 userId 写入 {@link UserContext}，失败或缺失一律放行——
 * 本过滤器只负责"识别你是谁"，"未登录拦截"由后续拦截器/权限层处理（M1-2），
 * 因为登录、注册等白名单接口本身必须允许匿名访问。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    /** Authorization 头的 Bearer 前缀 */
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            String token = authorization.substring(BEARER_PREFIX.length());
            try {
                Claims claims = jwtUtil.parse(token);
                UserContext.setUserId(jwtUtil.getUserId(claims));
                log.debug("JWT解析成功: userId={}, username={}",
                        jwtUtil.getUserId(claims), jwtUtil.getUsername(claims));
            } catch (JwtException | IllegalArgumentException e) {
                // token 无效(过期/伪造/格式错误): 不写上下文、不在此处返回401, 仅记录
                log.debug("JWT解析失败: {}", e.getMessage());
            }
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            // Tomcat 线程池复用线程, 必须清理, 否则下个请求可能串到上个用户身份
            UserContext.clear();
        }
    }
}
