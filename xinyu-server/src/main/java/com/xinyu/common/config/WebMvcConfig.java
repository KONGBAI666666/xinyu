package com.xinyu.common.config;

import com.xinyu.common.security.AuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC 配置: 注册认证拦截器与白名单
 *
 * <p>白名单与 docs/design.md 3.1 保持一致：登录/注册/health 匿名可访问；
 * M2 角色广场上线时在此追加 /api/characters 的游客路径。
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    /** 匿名可访问的路径白名单 */
    private static final String[] AUTH_WHITELIST = {
            "/api/auth/register",
            "/api/auth/login",
            "/api/health",
            // M2.3 角色广场: 游客可逛 PUBLISHED 角色列表与详情; 收藏/开聊由前端引导登录
            "/api/characters/square",
            "/api/characters/*/detail"
    };

    private final AuthInterceptor authInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(AUTH_WHITELIST);
    }
}
