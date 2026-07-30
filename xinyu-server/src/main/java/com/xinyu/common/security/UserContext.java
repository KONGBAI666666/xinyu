package com.xinyu.common.security;

/**
 * 当前登录用户上下文（ThreadLocal）
 *
 * <p>由 {@link JwtAuthFilter} 在请求进入时写入、请求结束时清理；
 * Controller / Service 通过 {@code UserContext.getUserId()} 获取当前用户，
 * 避免 userId 作为参数在调用链中层层传递。
 *
 * <p>注意：Tomcat 线程池会复用线程，Filter 必须在 finally 中调用 {@link #clear()}，
 * 否则下一个请求可能读到上一个用户的身份。
 */
public class UserContext {

    private static final ThreadLocal<Long> USER = new ThreadLocal<>();

    /** 工具类禁止实例化 */
    private UserContext() {
    }

    /** 绑定当前线程的用户ID（仅 JwtAuthFilter 调用） */
    public static void setUserId(Long userId) {
        USER.set(userId);
    }

    /** 获取当前登录用户ID；未登录（无 token / token 无效）返回 null */
    public static Long getUserId() {
        return USER.get();
    }

    /** 清理当前线程上下文（必须在请求结束时调用，防止线程复用串数据） */
    public static void clear() {
        USER.remove();
    }
}
