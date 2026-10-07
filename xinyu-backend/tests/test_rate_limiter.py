"""限流器测试: 锁定语义 + 内存回收不放过生效中的锁定

重点回归: 旧实现用 _events.clear() 回收内存, 会把正在生效的登录锁定一并重置,
攻击者刷满 MAX_TRACKED_KEYS 个不同用户名即可绕过全部限流。
"""

import time
import unittest
from collections import deque

from app.core.exceptions import BizException
from app.utils import rate_limiter as rl


class RateLimiterTest(unittest.TestCase):
    def setUp(self) -> None:
        # 每个用例从干净状态开始 (模块级字典, 需显式重置)
        rl._events.clear()

    def tearDown(self) -> None:
        rl._events.clear()

    # ---------- 基本锁定语义 ----------

    def test_login_locked_after_max_failures(self) -> None:
        """同用户名失败 5 次后第 6 次登录被拒绝"""
        for _ in range(rl.LOGIN_MAX_FAILURES):
            rl.record_login_failure("alice")
        with self.assertRaises(BizException):
            rl.check_login("alice", "1.1.1.1")

    def test_login_success_resets_failures(self) -> None:
        """登录成功后失败计数清零, 可继续登录"""
        for _ in range(rl.LOGIN_MAX_FAILURES - 1):
            rl.record_login_failure("bob")
        rl.reset_login_failures("bob")
        # 不抛异常即通过
        rl.check_login("bob", "1.1.1.1")

    def test_login_failures_are_per_username(self) -> None:
        """锁定按用户名隔离, 不影响其他用户"""
        for _ in range(rl.LOGIN_MAX_FAILURES):
            rl.record_login_failure("alice")
        rl.check_login("carol", "1.1.1.1")  # 不抛异常

    def test_login_ip_limit(self) -> None:
        """同 IP 超过尝试上限被拒"""
        for i in range(rl.LOGIN_IP_MAX):
            rl.check_login(f"user{i}", "9.9.9.9")
        with self.assertRaises(BizException):
            rl.check_login("user-over", "9.9.9.9")

    # ---------- 安全回归: 回收内存不得绕过锁定 ----------

    def test_eviction_keeps_active_lockout(self) -> None:
        """刷满 MAX_TRACKED_KEYS 个键后, 生效中的锁定依然有效 (旧实现会失效)"""
        # 先制造一个已锁定的用户名
        for _ in range(rl.LOGIN_MAX_FAILURES):
            rl.record_login_failure("victim")

        # 再用大量「已在窗口内活跃」的键顶到软上限之上
        now = time.time() * 1000
        for i in range(rl.MAX_TRACKED_KEYS + 10):
            rl._events[f"login-fail:flood{i}"] = deque([now])

        # 触发一次回收
        rl._deque_for("login-fail:trigger")

        # 关键断言: victim 的锁定必须还在, 仍能拒绝登录
        self.assertGreaterEqual(
            len(rl._events.get("login-fail:victim", deque())),
            rl.LOGIN_MAX_FAILURES,
            "回收内存时误清了生效中的登录锁定 (限流绕过漏洞)",
        )
        with self.assertRaises(BizException):
            rl.check_login("victim", "1.1.1.1")

    def test_eviction_removes_expired_keys(self) -> None:
        """过期键会被回收, 内存回到上限附近"""
        stale = time.time() * 1000 - rl.LOGIN_FAILURE_WINDOW_MS - 60_000
        for i in range(rl.MAX_TRACKED_KEYS + 10):
            rl._events[f"login-fail:old{i}"] = deque([stale])

        rl._deque_for("login-fail:new")

        self.assertLessEqual(len(rl._events), rl.MAX_TRACKED_KEYS + 1)

    def test_eviction_hard_cap_when_all_active(self) -> None:
        """所有键都在有效窗口内且均未锁定时, 仍按硬上限淘汰, 内存有界"""
        now = time.time() * 1000
        # 每个键只放 1 个事件 (远低于锁定阈值), 因此都不受保护
        for i in range(rl.MAX_TRACKED_KEYS * 2 + 100):
            rl._events[f"login-fail:hot{i}"] = deque([now])

        rl._deque_for("login-fail:trigger")

        self.assertLessEqual(len(rl._events), rl.MAX_TRACKED_KEYS + 1)

    def test_protected_lockouts_can_exceed_cap(self) -> None:
        """已锁定的记录受保护: 数量超上限时也不被淘汰 (Memory 换安全)"""
        now = time.time() * 1000
        # 制造 3 个已锁定用户 + 大量普通活跃键
        for i in range(3):
            for _ in range(rl.LOGIN_MAX_FAILURES):
                rl._events.setdefault(f"login-fail:locked{i}", deque()).append(now)
        for i in range(rl.MAX_TRACKED_KEYS + 100):
            rl._events[f"login-fail:hot{i}"] = deque([now])

        rl._deque_for("login-fail:trigger")

        for i in range(3):
            self.assertGreaterEqual(
                len(rl._events.get(f"login-fail:locked{i}", deque())),
                rl.LOGIN_MAX_FAILURES,
                f"受保护的锁定 locked{i} 被误清",
            )

    def test_register_ip_limit(self) -> None:
        """注册 IP 限流生效"""
        for _ in range(rl.REGISTER_IP_MAX):
            rl.check_register("8.8.8.8")
        with self.assertRaises(BizException):
            rl.check_register("8.8.8.8")


if __name__ == "__main__":
    unittest.main()
