"""认证接口（白名单, 匿名可访问）— 对应 Java AuthController"""

from fastapi import APIRouter, Depends, Request
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.deps import client_ip
from app.core.database import get_db
from app.schemas.auth import AuthResponse, LoginDTO, RegisterDTO
from app.schemas.common import Result
from app.services import auth_service
from app.utils import rate_limiter

router = APIRouter(prefix="/api/auth", tags=["auth"])


@router.post("/register")
async def register(dto: RegisterDTO, request: Request, db: AsyncSession = Depends(get_db)) -> Result[AuthResponse]:
    """注册（注册即登录, 返回 token）"""
    rate_limiter.check_register(client_ip(request))
    return Result.ok(await auth_service.register(db, dto))


@router.post("/login")
async def login(dto: LoginDTO, request: Request, db: AsyncSession = Depends(get_db)) -> Result[AuthResponse]:
    """登录"""
    rate_limiter.check_login(dto.username, client_ip(request))
    try:
        vo = await auth_service.login(db, dto)
        rate_limiter.reset_login_failures(dto.username)
        return Result.ok(vo)
    except Exception:
        rate_limiter.record_login_failure(dto.username)
        raise
