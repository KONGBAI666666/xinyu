"""client_ip 可信代理判定: 伪造 X-Real-IP 不得绕过 IP 限流"""

import unittest

from fastapi import Request

from app.api.deps import client_ip
from app.core.config import settings


def _request(peer_host: str, headers: dict[str, str]) -> Request:
    req = Request(scope={
        "type": "http",
        "method": "GET",
        "path": "/",
        "headers": [(k.lower().encode(), v.encode()) for k, v in headers.items()],
        "query_string": b"",
        "client": (peer_host, 51000) if peer_host else None,
    })
    return req


class ClientIpTest(unittest.TestCase):
    def test_untrusted_peer_fake_header_ignored(self) -> None:
        """非可信对端自带的 X-Real-IP 不被采信 (防直连伪造绕过限流)"""
        req = _request("203.0.113.7", {"X-Real-IP": "8.8.8.8"})
        self.assertEqual(client_ip(req), "203.0.113.7")

    def test_trusted_peer_header_trusted(self) -> None:
        """可信对端 (默认回环, 模拟 nginx) 覆写的 X-Real-IP 被采信"""
        req = _request("127.0.0.1", {"X-Real-IP": "198.51.100.9"})
        self.assertEqual(client_ip(req), "198.51.100.9")

    def test_untrusted_peer_no_header_falls_back(self) -> None:
        req = _request("203.0.113.7", {})
        self.assertEqual(client_ip(req), "203.0.113.7")

    def test_trusted_peer_no_header_falls_back_to_peer(self) -> None:
        req = _request("127.0.0.1", {})
        self.assertEqual(client_ip(req), "127.0.0.1")

    def test_extra_proxy_added_via_config(self) -> None:
        """容器化部署: 把代理网段加入配置后其来源头被采信"""
        original = settings.trusted_proxies
        try:
            settings.trusted_proxies = "127.0.0.1,::1,172.18.0.5"
            req = _request("172.18.0.5", {"X-Real-IP": "198.51.100.9"})
            self.assertEqual(client_ip(req), "198.51.100.9")
        finally:
            settings.trusted_proxies = original

    def test_no_client_connection(self) -> None:
        req = _request("", {})
        req.scope["client"] = None  # type: ignore[assignment]
        self.assertEqual(client_ip(req), "unknown")


if __name__ == "__main__":
    unittest.main()
