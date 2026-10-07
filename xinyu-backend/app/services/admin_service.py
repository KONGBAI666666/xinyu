"""管理后台服务 — 用户封禁 / 角色审核 / 平台概览 (user.role=ADMIN 专用)

写操作统一走 "加载实体 → 改属性 → commit" 的 ORM 路径, 不在本层拼查询。
"""

import logging

from sqlalchemy.ext.asyncio import AsyncSession

from app.models import AiCharacter, User
from app.repositories import character_repo, message_repo, user_repo
from app.schemas.admin import AdminCharacterVO, AdminUserVO, PlatformOverviewVO

logger = logging.getLogger("xinyu.admin")

# 角色审核分页每页条数 (页满即视为可能还有更多, 前端用 offset 续拉)
REVIEW_PAGE_SIZE = 30


def _user_to_vo(user: User) -> AdminUserVO:
    return AdminUserVO(
        id=str(user.id),
        username=user.username,
        nickname=user.nickname,
        email=user.email,
        role=user.role,
        status=user.status,
        lastLoginAt=user.last_login_at,
        createdAt=user.created_at,
    )


def _character_to_vo(c: AiCharacter) -> AdminCharacterVO:
    return AdminCharacterVO(
        id=str(c.id),
        name=c.name,
        intro=c.intro,
        creatorId=str(c.creator_id),
        creatorType=c.creator_type,
        status=c.status,
        chatCount=c.chat_count,
        favoriteCount=c.favorite_count,
        createdAt=c.created_at,
    )


async def find_user_by_username(db: AsyncSession, username: str) -> AdminUserVO | None:
    """按用户名精确查找 (v1 不做用户分页列表)"""
    user = await user_repo.get_by_username(db, username)
    return _user_to_vo(user) if user is not None else None


async def set_user_status(db: AsyncSession, operator_id: int, target_user_id: int, status: str) -> AdminUserVO:
    """封禁/解封用户; 不能操作自己 (防误操作把自己锁在门外)"""
    if target_user_id == operator_id:
        from app.core.exceptions import BizException, ResultCode

        raise BizException(ResultCode.PARAM_ERROR, "不能变更自己的状态")
    user = await user_repo.get_by_id(db, target_user_id)
    if user is None:
        from app.core.exceptions import BizException, ResultCode

        raise BizException(ResultCode.NOT_FOUND, "用户不存在")
    user.status = status
    await db.commit()
    logger.info("用户状态变更: operator=%s, target=%s, status=%s", operator_id, target_user_id, status)
    return _user_to_vo(user)


async def list_characters(
    db: AsyncSession, status: str | None, offset: int, limit: int
) -> list[AdminCharacterVO]:
    """审核队列分页 (id 倒序)"""
    characters = await character_repo.list_characters_page(db, status, offset, limit)
    return [_character_to_vo(c) for c in characters]


async def set_character_status(db: AsyncSession, character_id: int, status: str) -> AdminCharacterVO:
    """审核动作: 通过发布 / 下架 (官方与用户角色均可操作)"""
    character = await character_repo.get_by_id(db, character_id)
    if character is None:
        from app.core.exceptions import BizException, ResultCode

        raise BizException(ResultCode.NOT_FOUND, "角色不存在")
    character.status = status
    await db.commit()
    logger.info("角色审核状态变更: characterId=%s, status=%s", character_id, status)
    return _character_to_vo(character)


async def overview(db: AsyncSession) -> PlatformOverviewVO:
    """平台概览: 全站消息量与 token 消耗 (复用用量聚合, user_id=None 即全站口径)"""
    usage = await message_repo.summarize_usage(db, None)
    return PlatformOverviewVO(
        messageCount=usage.call_count,
        totalPromptTokens=usage.prompt_tokens,
        totalCompletionTokens=usage.completion_tokens,
    )
