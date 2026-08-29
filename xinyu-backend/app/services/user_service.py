"""用户服务 — 对应 Java UserService"""

from sqlalchemy.ext.asyncio import AsyncSession

from app.core.exceptions import BizException, ResultCode
from app.core.security import hash_password, verify_password
from app.repositories import user_repo
from app.schemas.auth import UserVO
from app.schemas.user import UpdatePasswordDTO
from app.services.auth_service import to_user_vo


async def get_me(db: AsyncSession, user_id: int) -> UserVO:
    """获取当前登录用户信息"""
    user = await user_repo.get_by_id(db, user_id)
    if user is None:
        # token 有效但用户已被删除(逻辑删除后查不到)
        raise BizException(ResultCode.NOT_FOUND, "用户不存在")
    return to_user_vo(user)


async def update_password(db: AsyncSession, user_id: int, dto: UpdatePasswordDTO) -> None:
    """修改密码: 校验原密码 → BCrypt 重新哈希 → 更新"""
    user = await user_repo.get_by_id(db, user_id)
    if user is None:
        raise BizException(ResultCode.NOT_FOUND, "用户不存在")
    if not verify_password(dto.oldPassword, user.password):
        raise BizException(ResultCode.UNAUTHORIZED, "原密码错误")
    await user_repo.update_password(db, user_id, hash_password(dto.newPassword))
    await db.commit()
