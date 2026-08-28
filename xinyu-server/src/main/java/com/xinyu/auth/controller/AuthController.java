package com.xinyu.auth.controller;

import com.xinyu.auth.dto.LoginDTO;
import com.xinyu.auth.dto.RegisterDTO;
import com.xinyu.auth.service.AuthRateLimiter;
import com.xinyu.auth.service.AuthService;
import com.xinyu.auth.vo.LoginVO;
import com.xinyu.common.exception.BizException;
import com.xinyu.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口（白名单, 匿名可访问）
 *
 * <p>登录/注册均挂限流防暴力破解与批量注册, 见 {@link AuthRateLimiter}。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthRateLimiter rateLimiter;

    /** 注册（注册即登录, 返回 token） */
    @PostMapping("/register")
    public Result<LoginVO> register(@Valid @RequestBody RegisterDTO dto, HttpServletRequest request) {
        rateLimiter.checkRegister(clientIp(request));
        return Result.success(authService.register(dto));
    }

    /** 登录 */
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto, HttpServletRequest request) {
        rateLimiter.checkLogin(dto.getUsername(), clientIp(request));
        try {
            LoginVO vo = authService.login(dto);
            rateLimiter.resetLoginFailures(dto.getUsername());
            return Result.success(vo);
        } catch (BizException e) {
            rateLimiter.recordLoginFailure(dto.getUsername());
            throw e;
        }
    }

    /** 客户端真实 IP: 经 nginx 代理时取 X-Forwarded-For 首段 */
    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
