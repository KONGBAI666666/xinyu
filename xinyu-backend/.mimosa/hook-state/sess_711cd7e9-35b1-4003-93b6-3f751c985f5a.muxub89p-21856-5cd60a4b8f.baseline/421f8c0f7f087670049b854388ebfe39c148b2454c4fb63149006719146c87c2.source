"""认证服务: 注册 / 登录 / JWT 签发 — 对应 Java AuthService"""

import logging

from sqlalchemy.ext.asyncio import AsyncSession

from app.core.exceptions import BizException, ResultCode
from app.core.security import create_token, hash_password, now_local, verify_password
from app.models import User
from app.repositories import user_repo
from app.schemas.auth import AuthResponse, LoginDTO, RegisterDTO, UserVO

logger = logging.getLogger("xinyu.auth")


def to_user_vo(user: User) -> UserVO:
    return UserVO(
        id=str(user.id),
        username=user.username,
        nickname=user.nickname,
        avatarUrl=user.avatar_url,
    )


async def register(db: AsyncSession, dto: RegisterDTO) -> AuthResponse:
    """注册（注册即登录, 直接返回 token 减少一次交互）"""
    if await user_repo.get_by_username(db, dto.username) is not None:
        # 格式合法但业务规则不允许, 属于校验类错误(42200), 非认证失败
        raise BizException(ResultCode.PARAM_ERROR, "用户名已存在")

    user = User(
        username=dto.username,
        # 只存 BCrypt 哈希, 明文密码不落库不打日志
        password=hash_password(dto.password),
        nickname=dto.nickname.strip() if dto.nickname and dto.nickname.strip() else dto.username,
        # 注册即登录, 直接记首次登录时间; role/status 走数据库默认值 USER/ACTIVE
        last_login_at=now_local(),
    )
    await user_repo.insert(db, user)
    await db.commit()

    logger.info("新用户注册: userId=%s, username=%s", user.id, user.username)
    return _build_login_vo(user)


async def login(db: AsyncSession, dto: LoginDTO) -> AuthResponse:
    """登录

    用户不存在与密码错误统一返回"用户名或密码错误"(40100),
    不区分两种情况, 防止用户名枚举攻击。
    """
    user = await user_repo.get_by_username(db, dto.username)
    # 用户不存在与密码错误合并为同一文案, 防用户名枚举
    if user is None or not verify_password(dto.password, user.password):
        raise BizException(ResultCode.UNAUTHORIZED, "用户名或密码错误")
    # 状态机接线: BANNED 用户禁止登录 (放在密码校验之后, 避免泄露账号状态)
    if user.status == "BANNED":
        raise BizException(ResultCode.FORBIDDEN, "账号已被禁用")

    await user_repo.update_last_login(db, user.id)
    await db.commit()
    return _build_login_vo(user)


def _build_login_vo(user: User) -> AuthResponse:
    """签发 token 并组装响应"""
    token = create_token(user.id, user.username)
    return AuthResponse(token=token, user=to_user_vo(user))
