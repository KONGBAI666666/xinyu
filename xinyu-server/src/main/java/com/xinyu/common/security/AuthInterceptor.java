package com.xinyu.common.security;

import com.xinyu.common.exception.BizException;
import com.xinyu.common.result.ResultCode;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录认证拦截器
 *
 * <p>与 {@link JwtAuthFilter} 职责分离：Filter 只负责"识别你是谁"（解析 token 写入上下文），
 * 本拦截器负责"这个接口是否必须登录"——受保护路径上若 {@link UserContext} 为空，
 * 抛出 40100 交给 GlobalExceptionHandler 统一返回。
 *
 * <p>白名单（登录/注册等匿名接口）通过 WebMvcConfig 的 excludePathPatterns 配置，
 * 本类不感知具体路径。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // SSE 等异步请求完成后容器会发起 ASYNC 二次派发: 鉴权已在首次 REQUEST 派发通过,
        // 且 JwtAuthFilter(OncePerRequestFilter) 不参与 ASYNC 派发, ThreadLocal 必为空,
        // 若不放行会误抛 40100 污染日志 (M1-6 修复)
        if (request.getDispatcherType() != DispatcherType.REQUEST) {
            return true;
        }
        // CORS 预检请求不携带业务 token, 直接放行
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        // 非 Controller 方法(如静态资源/未映射路径)不拦截, 让 404 逻辑正常返回 40400
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        if (UserContext.getUserId() == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        return true;
    }
}
