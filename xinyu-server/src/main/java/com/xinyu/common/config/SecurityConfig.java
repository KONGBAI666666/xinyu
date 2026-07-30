package com.xinyu.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 安全相关 Bean 配置
 *
 * <p>只使用 spring-security-crypto 的 BCrypt 做密码哈希，
 * 未引入完整 Spring Security（本项目认证由 JwtAuthFilter + AuthInterceptor 自行实现）。
 */
@Configuration
public class SecurityConfig {

    /**
     * BCrypt 密码编码器
     *
     * <p>特性: 自带随机盐、慢哈希抗暴力破解; 同一明文每次 encode 结果不同,
     * 校验必须用 {@code matches(raw, hash)} 而非比较字符串。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
