package com.xinyu.auth.service;

import com.xinyu.common.exception.BizException;
import com.xinyu.common.result.ResultCode;
import org.springframework.stereotype.Component;

import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 认证限流器: 内存滑动窗口 (单实例部署)
 *
 * <p>防暴力破解与批量注册:
 * <ul>
 *   <li>登录: 同用户名 15 分钟内失败 5 次即锁定; 同 IP 15 分钟最多尝试 30 次</li>
 *   <li>注册: 同 IP 每小时最多 10 次 (无论成败均消耗配额)</li>
 * </ul>
 * 登录成功清除该用户名的失败记录。实例重启后窗口清零, 属可接受的权衡。
 */
@Component
public class AuthRateLimiter {

    private static final int LOGIN_MAX_FAILURES = 5;
    private static final long LOGIN_FAILURE_WINDOW_MS = 15 * 60 * 1000L;
    private static final int LOGIN_IP_MAX = 30;
    private static final long LOGIN_IP_WINDOW_MS = 15 * 60 * 1000L;
    private static final int REGISTER_IP_MAX = 10;
    private static final long REGISTER_IP_WINDOW_MS = 60 * 60 * 1000L;

    /** 键数软上限: 防止攻击者用海量用户名撑爆内存 */
    private static final int MAX_TRACKED_KEYS = 50_000;

    private final Map<String, Deque<Long>> events = new ConcurrentHashMap<>();

    /** 登录前置检查: 用户名锁定或 IP 超限直接抛出 */
    public void checkLogin(String username, String ip) {
        if (count("login-fail:" + username, LOGIN_FAILURE_WINDOW_MS) >= LOGIN_MAX_FAILURES) {
            throw new BizException(ResultCode.PARAM_ERROR, "失败次数过多, 请 15 分钟后再试");
        }
        if (!tryAcquire("login-ip:" + ip, LOGIN_IP_MAX, LOGIN_IP_WINDOW_MS)) {
            throw new BizException(ResultCode.PARAM_ERROR, "请求过于频繁, 请稍后再试");
        }
    }

    /** 记录一次登录失败 */
    public void recordLoginFailure(String username) {
        record("login-fail:" + username);
    }

    /** 登录成功后清除失败记录 */
    public void resetLoginFailures(String username) {
        events.remove("login-fail:" + username);
    }

    /** 注册前置检查: IP 限流, 消耗配额 */
    public void checkRegister(String ip) {
        if (!tryAcquire("register-ip:" + ip, REGISTER_IP_MAX, REGISTER_IP_WINDOW_MS)) {
            throw new BizException(ResultCode.PARAM_ERROR, "注册过于频繁, 请一小时后再试");
        }
    }

    private boolean tryAcquire(String key, int maxEvents, long windowMs) {
        long now = System.currentTimeMillis();
        Deque<Long> deque = dequeFor(key);
        purge(deque, now - windowMs);
        if (deque.size() >= maxEvents) {
            return false;
        }
        deque.addLast(now);
        return true;
    }

    private int count(String key, long windowMs) {
        Deque<Long> deque = events.get(key);
        if (deque == null) {
            return 0;
        }
        purge(deque, System.currentTimeMillis() - windowMs);
        return deque.size();
    }

    private void record(String key) {
        dequeFor(key).addLast(System.currentTimeMillis());
    }

    private Deque<Long> dequeFor(String key) {
        if (events.size() > MAX_TRACKED_KEYS) {
            events.clear();
        }
        return events.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
    }

    private void purge(Deque<Long> deque, long before) {
        Long first;
        while ((first = deque.peekFirst()) != null && first < before) {
            deque.pollFirst();
        }
    }
}
