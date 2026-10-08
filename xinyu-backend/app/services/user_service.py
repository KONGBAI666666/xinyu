"""用户服务 — 对应 Java UserService"""

from sqlalchemy.ext.asyncio import AsyncSession

from app.core.exceptions import BizException, ResultCode
from app.core.security import hash_password, verify_password
from app.repositories import user_repo
from app.schemas.auth import UserVO
from app.schemas.user import UpdatePasswordDTO
from app.services import file_service
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
        # 用 42200 而非 40100: 原密码错误属提交数据错误,
        # 40100 会被前端全局拦截器当作登录过期清凭证强制登出
        raise BizException(ResultCode.PARAM_ERROR, "原密码错误")
    await user_repo.update_password(db, user_id, hash_password(dto.newPassword))
    await db.commit()


async def update_email(db: AsyncSession, user_id: int, email: str | None) -> None:
    """绑定/更换邮箱 (传 None 清除); 找回密码等邮件能力的数据基础

    唯一性: 应用层预查重给出友好提示 (uk_email 唯一索引兜底并发窗口),
    统一小写存储, 避免大小写变体绕过查重。
    """
    user = await user_repo.get_by_id(db, user_id)
    if user is None:
        raise BizException(ResultCode.NOT_FOUND, "用户不存在")
    if email is not None:
        email = email.strip().lower()
        taken = await user_repo.get_by_email(db, email)
        if taken is not None and taken.id != user_id:
            raise BizException(ResultCode.PARAM_ERROR, "该邮箱已被其他账号绑定")
    user.email = email
    await db.commit()


async def update_avatar(db: AsyncSession, user_id: int, original_name: str, data: bytes) -> UserVO:
    """上传头像 (M5): 落盘 + file 表留痕 + user.avatar_url 指向新地址"""
    user = await user_repo.get_by_id(db, user_id)
    if user is None:
        raise BizException(ResultCode.NOT_FOUND, "用户不存在")

    record = await file_service.save_image(user_id, "USER_AVATAR", original_name, data)
    db.add(record)
    user_repo.update_avatar_fields(user, record.url)
    await db.commit()
    return to_user_vo(user)
