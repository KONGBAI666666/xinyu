"""通用依赖: JWT 认证 (对应 Java JwtAuthFilter + AuthInterceptor)

- get_current_user_id: 强制登录, 无效/缺失 token 抛 40100
- get_optional_user_id: 白名单路径 (角色广场等), 游客返回 None
- get_db: 每请求一个数据库会话 (见 core.database)
"""

from fastapi import Request

from app.core.exceptions import BizException, ResultCode
from app.core.security import get_user_id_from_claims, parse_token

_BEARER_PREFIX = "Bearer "


def _parse_bearer(request: Request) -> dict | None:
    """从 Authorization 头解析 Bearer token; 无 token 或无效返回 None (不抛错)"""
    authorization = request.headers.get("Authorization", "")
    if not authorization.startswith(_BEARER_PREFIX):
        return None
    try:
        return parse_token(authorization[len(_BEARER_PREFIX):])
    except Exception:
        # token 无效(过期/伪造/格式错误): 视同未登录
        return None


async def get_current_user_id(request: Request) -> int:
    """强制登录依赖: token 缺失或无效统一 40100"""
    claims = _parse_bearer(request)
    if claims is None:
        raise BizException(ResultCode.UNAUTHORIZED, "未登录或登录已过期")
    user_id = get_user_id_from_claims(claims)
    if user_id is None:
        raise BizException(ResultCode.UNAUTHORIZED, "未登录或登录已过期")
    return user_id


async def get_optional_user_id(request: Request) -> int | None:
    """可选登录依赖: 白名单路径游客可访问, 无 token 返回 None"""
    claims = _parse_bearer(request)
    if claims is None:
        return None
    return get_user_id_from_claims(claims)


def client_ip(request: Request) -> str:
    """客户端真实 IP: 经 nginx 代理时取 X-Forwarded-For 首段"""
    xff = request.headers.get("X-Forwarded-For", "")
    if xff.strip():
        return xff.split(",")[0].strip()
    return request.client.host if request.client else "unknown"


def parse_id(value: str | None, name: str = "id") -> int | None:
    """路径/查询参数 ID 解析: 非数字抛 42200 (对齐 Java 类型不匹配行为)"""
    if value is None or value == "":
        return None
    try:
        return int(value)
    except ValueError:
        raise BizException(ResultCode.PARAM_ERROR, f"参数 {name} 类型错误") from None
