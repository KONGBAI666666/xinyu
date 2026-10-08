"""出站 baseUrl 防护测试: 协议白名单 / 内网 IP 判定 / DNS 解析判定

重点回归: 用户自填模型 baseUrl 无校验时, 可指向内网/环回地址形成 SSRF
(后端携带上下文对其发请求, 且异常详情可能回显), 保存与使用模型时均需拦截。
"""

import asyncio
import ipaddress
import socket
import unittest
from unittest import mock

from app.core.exceptions import BizException
from app.utils import url_guard


class FakeSettings:
    def __init__(self, dev_mode: bool = False, allow_private: bool = False):
        self.dev_mode = dev_mode
        self.llm_allow_private_baseurl = allow_private


def _assert_baseurl(url: str) -> str:
    return asyncio.run(url_guard.assert_public_baseurl(url))


class IpBlockedTest(unittest.TestCase):
    def test_public_ips_pass(self) -> None:
        """公网 IPv4/IPv6 不拦截"""
        for ip in ("8.8.8.8", "1.1.1.1", "2606:4700::1111"):
            self.assertFalse(url_guard._ip_is_blocked(ipaddress.ip_address(ip)))

    def test_private_and_local_ips_blocked(self) -> None:
        """私网/环回/链路本地/未指定地址全部拦截"""
        for ip in (
            "127.0.0.1",
            "10.0.0.1",
            "192.168.1.1",
            "172.16.0.1",
            "169.254.169.254",
            "0.0.0.0",
            "::1",
            "fe80::1",
            "fc00::1",
        ):
            self.assertTrue(url_guard._ip_is_blocked(ipaddress.ip_address(ip)))

    def test_ipv4_mapped_loopback_blocked(self) -> None:
        """::ffff:127.0.0.1 映射形式同样拦截 (不同 Python 版本判定不一致, 需显式还原)"""
        self.assertTrue(url_guard._ip_is_blocked(ipaddress.ip_address("::ffff:127.0.0.1")))


class UrlShapeTest(unittest.TestCase):
    def test_non_http_scheme_rejected(self) -> None:
        for url in ("ftp://example.com", "file:///etc/passwd", "gopher://x.com"):
            with self.assertRaises(BizException):
                url_guard._resolve_host(url)

    def test_missing_host_rejected(self) -> None:
        with self.assertRaises(BizException):
            url_guard._resolve_host("http:///path")

    def test_loopback_literal_rejected(self) -> None:
        with self.assertRaises(BizException):
            url_guard._resolve_host("http://127.0.0.1:11434/v1")

    def test_public_literal_ok(self) -> None:
        self.assertEqual(url_guard._resolve_host("http://8.8.8.8/v1"), "8.8.8.8")


class AssertPublicBaseurlTest(unittest.TestCase):
    def test_dev_mode_skips_validation(self) -> None:
        """dev 模式跳过校验: 本机 Ollama 联调不被拦截"""
        with mock.patch("app.core.config.settings", FakeSettings(dev_mode=True)):
            out = _assert_baseurl("http://127.0.0.1:11434/v1")
        self.assertEqual(out, "http://127.0.0.1:11434/v1")

    def test_explicit_flag_allows_private(self) -> None:
        with mock.patch("app.core.config.settings", FakeSettings(allow_private=True)):
            out = _assert_baseurl("http://192.168.1.5:8080/v1")
        self.assertEqual(out, "http://192.168.1.5:8080/v1")

    def test_private_ip_rejected(self) -> None:
        with mock.patch("app.core.config.settings", FakeSettings()):
            with self.assertRaises(BizException):
                _assert_baseurl("http://10.1.2.3/v1")

    def test_public_ip_passes(self) -> None:
        with mock.patch("app.core.config.settings", FakeSettings()):
            out = _assert_baseurl("http://8.8.8.8/v1")
        self.assertEqual(out, "http://8.8.8.8/v1")

    def _hostname_case(self, resolved_ip: str, expect_reject: bool) -> None:
        """DNS 解析路径: mock 事件循环的 getaddrinfo, 不触网"""
        fake_loop = mock.Mock()

        async def fake_getaddrinfo(host, port):
            return [(2, 1, 6, "", (resolved_ip, 0))]

        fake_loop.getaddrinfo = fake_getaddrinfo
        with (
            mock.patch("app.core.config.settings", FakeSettings()),
            mock.patch("asyncio.get_running_loop", return_value=fake_loop),
        ):
            if expect_reject:
                with self.assertRaises(BizException):
                    _assert_baseurl("http://model.example.com/v1")
            else:
                self.assertEqual(
                    _assert_baseurl("http://model.example.com/v1"), "http://model.example.com/v1"
                )

    def test_hostname_resolving_to_private_rejected(self) -> None:
        """域名解析到内网 IP 时拒绝 (防"公网域名→内网 IP"绕过)"""
        self._hostname_case("192.168.0.9", expect_reject=True)

    def test_hostname_resolving_to_public_passes(self) -> None:
        self._hostname_case("93.184.216.34", expect_reject=False)

    def test_unresolvable_hostname_rejected(self) -> None:
        fake_loop = mock.Mock()

        async def fake_getaddrinfo(host, port):
            raise socket.gaierror(-2, "Name or service not known")

        fake_loop.getaddrinfo = fake_getaddrinfo
        with (
            mock.patch("app.core.config.settings", FakeSettings()),
            mock.patch("asyncio.get_running_loop", return_value=fake_loop),
        ):
            with self.assertRaises(BizException):
                _assert_baseurl("http://no-such-host.invalid/v1")


if __name__ == "__main__":
    unittest.main()
