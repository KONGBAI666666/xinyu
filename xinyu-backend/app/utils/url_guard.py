"""出站 baseUrl 防护 — 用户自填的模型地址仅允许公网 http(s)

用户可自填 OpenAI 兼容 baseUrl; 若不限制, 可指向内网/环回地址形成 SSRF:
后端会携带上下文对其发请求, 且异常详情可能回显给前端。因此保存与使用模型时
双重校验 (存量数据由使用时校验兜底)。

校验策略:
- 仅允许 http/https;
- IP 字面量直接判定;
- 主机名做 DNS 解析后逐个 IP 判定 (防"域名解析到内网 IP"绕过)。
注意: 解析与实际请求之间仍存在 DNS 重绑定窗口, 完全封闭需传输层固定解析 IP,
此处不展开 —— 现有校验已消除直接的内网可达性。

dev 模式或显式设置 XINYU_LLM_ALLOW_PRIVATE_BASEURL=1 时跳过校验,
以便本机 Ollama 等内网自建服务联调。
"""

import asyncio
import ipaddress
import socket
from urllib.parse import urlparse

from app.core.exceptions import BizException, ResultCode

_ALLOWED_SCHEMES = ("http", "https")


def _ip_is_blocked(ip: ipaddress.IPv4Address | ipaddress.IPv6Address) -> bool:
    """非公网 IP (私网/环回/链路本地/保留段) 视为受阻止"""
    if isinstance(ip, ipaddress.IPv6Address) and ip.ipv4_mapped is not None:
        # ::ffff:127.0.0.1 之类先还原成 IPv4 再判, 不同 Python 版本对映射地址的判定不一致
        ip = ip.ipv4_mapped
    return not ip.is_global


def _reject(reason: str) -> None:
    raise BizException(ResultCode.PARAM_ERROR, f"模型 baseUrl 不可用: {reason}")


def _resolve_host(url: str) -> str:
    """提取 hostname, 并对 IP 字面量直接判定; 主机名原样返回交给 DNS 判定"""
    parsed = urlparse(url)
    if parsed.scheme not in _ALLOWED_SCHEMES:
        _reject("仅支持 http/https 地址")
    host = parsed.hostname
    if not host:
        _reject("缺少主机名")
    try:
        ip = ipaddress.ip_address(host)
    except ValueError:
        return host
    if _ip_is_blocked(ip):
        _reject("不允许指向内网/本机地址")
    return host


async def assert_public_baseurl(raw: str) -> str:
    """校验 baseUrl 指向公网; 通过返回 trim 后的地址, 不通过抛 42200"""
    from app.core.config import settings

    url = raw.strip()
    if settings.dev_mode or settings.llm_allow_private_baseurl:
        return url

    host = _resolve_host(url)
    # 主机名: DNS 解析后逐个 IP 判定, 任一解析结果落在内网即拒绝
    loop = asyncio.get_running_loop()
    try:
        infos = await loop.getaddrinfo(host, None)
    except socket.gaierror:
        _reject("域名无法解析")
    for info in infos:
        if _ip_is_blocked(ipaddress.ip_address(info[4][0])):
            _reject("不允许指向内网/本机地址")
    return url
