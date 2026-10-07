"""认证限流器: 内存滑动窗口 (单实例部署) — 对应 Java AuthRateLimiter

防暴力破解与批量注册:
- 登录: 同用户名 15 分钟内失败 5 次即锁定; 同 IP 15 分钟最多尝试 30 次
- 注册: 同 IP 每小时最多 50 次 (无论成败均消耗配额)
登录成功清除该用户名的失败记录。实例重启后窗口清零, 属可接受的权衡。

内存回收策略 (重要): 键数超软上限时只淘汰「窗口内已无有效事件」的键,
绝不整体清空 —— 整体清空会把正在生效的登录锁定一并重置,
攻击者只要刷满 5 万个不同用户名即可绕过全部限流。
"""

import time
from collections import deque

from app.core.exceptions import BizException, ResultCode

LOGIN_MAX_FAILURES = 5
LOGIN_FAILURE_WINDOW_MS = 15 * 60 * 1000
LOGIN_IP_MAX = 30
LOGIN_IP_WINDOW_MS = 15 * 60 * 1000
REGISTER_IP_MAX = 50
REGISTER_IP_WINDOW_MS = 60 * 60 * 1000

# 键数软上限: 防止攻击者用海量用户名撑爆内存
MAX_TRACKED_KEYS = 50_000

# 各类键的窗口时长 (回收时用于判断事件是否已全部过期)
_KEY_WINDOWS_MS: dict[str, float] = {
    "login-fail": LOGIN_FAILURE_WINDOW_MS,
    "login-ip": LOGIN_IP_WINDOW_MS,
    "register-ip": REGISTER_IP_WINDOW_MS,
}

_events: dict[str, deque[float]] = {}


def _window_ms_for(key: str) -> float:
    """取键对应的窗口时长; 未知前缀按最长窗口保守处理"""
    return _KEY_WINDOWS_MS.get(key.split(":", 1)[0], REGISTER_IP_WINDOW_MS)


def _is_protected(key: str) -> bool:
    """是否为「必须保留」的键: 已锁定的登录失败记录

    这类键一旦被回收, 攻击者就能用海量无关 key 把锁定顶掉 —— 是可被构造的
    限流绕过。宁可牺牲一点内存也不清它, 等它自然过期。
    """
    if not key.startswith("login-fail:"):
        return False
    dq = _events.get(key)
    return dq is not None and len(dq) >= LOGIN_MAX_FAILURES


def _evict_expired() -> None:
    """回收内存: 移除「所有事件均已过期」的键, 保留仍在窗口内的记录

    已锁定的登录失败记录额外受保护 (见 _is_protected), 避免被批量 key 顶掉。
    """
    now = time.time() * 1000
    for key in [
        k for k, dq in _events.items()
        if (not dq or dq[-1] < now - _window_ms_for(k)) and not _is_protected(k)
    ]:
        _events.pop(key, None)

    # 极端情况: 未过期的键仍超上限 → 优先淘汰未被保护的、最久未活动的键
    if len(_events) > MAX_TRACKED_KEYS:
        candidates = [
            (k, dq[-1] if dq else 0.0)
            for k, dq in _events.items()
            if not _is_protected(k)
        ]
        candidates.sort(key=lambda kv: kv[1])
        overflow = len(_events) - MAX_TRACKED_KEYS
        for key, _ in candidates[:overflow]:
            _events.pop(key, None)


def _deque_for(key: str) -> deque[float]:
    if len(_events) > MAX_TRACKED_KEYS:
        _evict_expired()
    return _events.setdefault(key, deque())


def _purge(dq: deque[float], before: float) -> None:
    while dq and dq[0] < before:
        dq.popleft()


def _try_acquire(key: str, max_events: int, window_ms: float) -> bool:
    now = time.time() * 1000
    dq = _deque_for(key)
    _purge(dq, now - window_ms)
    if len(dq) >= max_events:
        return False
    dq.append(now)
    return True


def _count(key: str, window_ms: float) -> int:
    dq = _events.get(key)
    if dq is None:
        return 0
    _purge(dq, time.time() * 1000 - window_ms)
    return len(dq)


def _record(key: str) -> None:
    _deque_for(key).append(time.time() * 1000)


def check_login(username: str, ip: str) -> None:
    """登录前置检查: 用户名锁定或 IP 超限直接抛出"""
    if _count(f"login-fail:{username}", LOGIN_FAILURE_WINDOW_MS) >= LOGIN_MAX_FAILURES:
        raise BizException(ResultCode.PARAM_ERROR, "失败次数过多, 请 15 分钟后再试")
    if not _try_acquire(f"login-ip:{ip}", LOGIN_IP_MAX, LOGIN_IP_WINDOW_MS):
        raise BizException(ResultCode.PARAM_ERROR, "请求过于频繁, 请稍后再试")


def record_login_failure(username: str) -> None:
    """记录一次登录失败"""
    _record(f"login-fail:{username}")


def reset_login_failures(username: str) -> None:
    """登录成功后清除失败记录"""
    _events.pop(f"login-fail:{username}", None)


def check_register(ip: str) -> None:
    """注册前置检查: IP 限流, 消耗配额"""
    if not _try_acquire(f"register-ip:{ip}", REGISTER_IP_MAX, REGISTER_IP_WINDOW_MS):
        raise BizException(ResultCode.PARAM_ERROR, "注册请求过多, 请稍后再试")
