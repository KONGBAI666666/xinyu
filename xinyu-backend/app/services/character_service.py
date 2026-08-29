"""AI 角色服务 — 对应 Java CharacterService

关键设计:
- 可见性: 自己创建的(任意状态) + 官方 PUBLISHED; 越权统一 40400 不暴露存在性
- 可聊性: 官方 PUBLISHED 任何人可聊, 用户自建任意状态创建者可聊
- 广场: 仅 PUBLISHED, 游客可访问; 排序 RECOMMEND/HOT/LATEST
- 收藏: 幂等 upsert/delete, uk(user_id, character_id) 防重复
"""

import logging
from typing import Literal

from sqlalchemy.ext.asyncio import AsyncSession

from app.core.exceptions import BizException, ResultCode
from app.models import AiCharacter, CharacterFavorite
from app.repositories import character_repo
from app.schemas.character import CharacterSaveDTO, CharacterVO

logger = logging.getLogger("xinyu.character")

SquareSort = Literal["RECOMMEND", "HOT", "LATEST"]


def _to_vo(c: AiCharacter, current_user_id: int | None, favorited_ids: set[int]) -> CharacterVO:
    return CharacterVO(
        id=str(c.id),
        name=c.name,
        avatarUrl=c.avatar_url,
        intro=c.intro,
        systemPrompt=c.system_prompt,
        greeting=c.greeting,
        temperature=float(c.temperature),
        maxTokens=c.max_tokens,
        modelId=str(c.model_id) if c.model_id is not None else None,
        creatorId=str(c.creator_id),
        creatorType=c.creator_type,
        status=c.status,
        chatCount=c.chat_count,
        favoriteCount=c.favorite_count,
        createdAt=c.created_at,
        updatedAt=c.updated_at,
        mine=current_user_id is not None and c.creator_id == current_user_id and c.creator_type == "USER",
        favorited=c.id in favorited_ids,
    )


def _is_visible(c: AiCharacter, user_id: int | None) -> bool:
    """官方 PUBLISHED 任何人可见; 其余仅创建者可见"""
    if c.creator_type == "OFFICIAL" and c.status == "PUBLISHED":
        return True
    return user_id is not None and c.creator_id == user_id


async def _require_owned(db: AsyncSession, character_id: int, user_id: int) -> AiCharacter:
    """校验角色归属: 不存在/非自建/官方统一 40400, 不暴露存在性"""
    character = await character_repo.get_by_id(db, character_id)
    if character is None or character.creator_id != user_id or character.creator_type == "OFFICIAL":
        raise BizException(ResultCode.NOT_FOUND, "角色不存在或无权操作")
    return character


async def list_visible(db: AsyncSession, user_id: int | None) -> list[CharacterVO]:
    """列出用户可见的角色: 自己创建的(任意状态) + 官方 PUBLISHED"""
    characters = await character_repo.list_visible(db, user_id)
    favorited = await character_repo.favorited_ids(db, user_id)
    return [_to_vo(c, user_id, favorited) for c in characters]


async def get_by_id_for_user(db: AsyncSession, character_id: int, user_id: int | None) -> CharacterVO | None:
    """角色详情(管理视角: 自建任意状态 + 官方 PUBLISHED); 不可见或不存在返回 None"""
    character = await character_repo.get_by_id(db, character_id)
    if character is None or not _is_visible(character, user_id):
        return None
    favorited = await character_repo.favorited_ids(db, user_id)
    return _to_vo(character, user_id, favorited)


async def create(db: AsyncSession, user_id: int, req: CharacterSaveDTO) -> CharacterVO:
    """创建角色, 默认状态 DRAFT(仅创建者可见)"""
    character = AiCharacter(
        name=req.name,
        avatar_url=req.avatarUrl,
        # intro 列为 NOT NULL 无默认值, 未提供时兜底空串避免插入失败
        intro=req.intro if req.intro and req.intro.strip() else "",
        system_prompt=req.systemPrompt,
        greeting=req.greeting,
        temperature=req.temperature,
        max_tokens=req.maxTokens,
        creator_id=user_id,
        creator_type="USER",
        status=req.status or "DRAFT",
        chat_count=0,
        favorite_count=0,
    )
    await character_repo.insert(db, character)
    await db.commit()
    logger.info("用户创建角色: userId=%s, characterId=%s, name=%s", user_id, character.id, character.name)
    return _to_vo(character, user_id, set())


async def update(db: AsyncSession, character_id: int, user_id: int, req: CharacterSaveDTO) -> CharacterVO:
    """编辑角色(仅创建者可编辑自己的角色)"""
    character = await _require_owned(db, character_id, user_id)
    character.name = req.name
    character.avatar_url = req.avatarUrl
    character.intro = req.intro or ""
    character.system_prompt = req.systemPrompt
    character.greeting = req.greeting
    character.temperature = req.temperature
    character.max_tokens = req.maxTokens
    if req.status:
        character.status = req.status
    await character_repo.update_character(db, character)
    await db.commit()
    favorited = await character_repo.favorited_ids(db, user_id)
    return _to_vo(character, user_id, favorited)


async def delete(db: AsyncSession, character_id: int, user_id: int) -> None:
    """删除角色(仅创建者可删除, 官方不可删)"""
    character = await _require_owned(db, character_id, user_id)
    if character.creator_type == "OFFICIAL":
        raise BizException(ResultCode.PARAM_ERROR, "官方角色不可删除")
    await character_repo.soft_delete(db, character_id)
    await db.commit()
    logger.info("用户删除角色: userId=%s, characterId=%s", user_id, character_id)


async def switch_status(db: AsyncSession, character_id: int, user_id: int, status: str) -> CharacterVO:
    """切换角色状态(DRAFT / PUBLISHED / OFFLINE)"""
    character = await _require_owned(db, character_id, user_id)
    if status not in ("DRAFT", "PENDING", "PUBLISHED", "OFFLINE"):
        raise BizException(ResultCode.PARAM_ERROR, f"角色状态非法: {status}")
    character.status = status
    await character_repo.update_character(db, character)
    await db.commit()
    favorited = await character_repo.favorited_ids(db, user_id)
    return _to_vo(character, user_id, favorited)


async def get_chattable(db: AsyncSession, character_id: int, user_id: int) -> AiCharacter | None:
    """校验角色可被用于创建会话

    规则: 官方 PUBLISHED 任何人可聊; 用户自建角色任意状态创建者均可聊;
    其余情况(他人 DRAFT / 官方 OFFLINE 等)不可聊。
    """
    character = await character_repo.get_by_id(db, character_id)
    if character is None:
        return None
    # 用户自建角色: 创建者任意状态可聊
    if character.creator_type == "USER" and character.creator_id == user_id:
        return character
    # 官方角色: 仅 PUBLISHED 任何人可聊
    if character.creator_type == "OFFICIAL" and character.status == "PUBLISHED":
        return character
    return None


def _parse_sort(sort: str | None) -> SquareSort:
    if not sort or not sort.strip():
        return "RECOMMEND"
    upper = sort.strip().upper()
    if upper not in ("RECOMMEND", "HOT", "LATEST"):
        raise BizException(ResultCode.PARAM_ERROR, f"排序方式非法: {sort}")
    return upper  # type: ignore[return-value]


async def list_square(
    db: AsyncSession, user_id: int | None, keyword: str | None, sort: str | None
) -> list[CharacterVO]:
    """广场列表: 仅 PUBLISHED 角色, 支持搜索 + 三种排序; 游客可访问"""
    sort_key = _parse_sort(sort)
    kw = keyword.strip() if keyword and keyword.strip() else None
    characters = await character_repo.list_square(db, kw, sort_key)
    favorited = await character_repo.favorited_ids(db, user_id)
    return [_to_vo(c, user_id, favorited) for c in characters]


async def get_square_detail(db: AsyncSession, character_id: int, user_id: int | None) -> CharacterVO | None:
    """广场详情: 仅 PUBLISHED 角色对所有人可见 (不暴露自建 DRAFT)"""
    character = await character_repo.get_by_id(db, character_id)
    if character is None or character.status != "PUBLISHED":
        return None
    favorited = await character_repo.favorited_ids(db, user_id)
    return _to_vo(character, user_id, favorited)


async def favorite(db: AsyncSession, user_id: int, character_id: int) -> None:
    """收藏角色(幂等: 已收藏不重复插入)"""
    if await character_repo.find_favorite(db, user_id, character_id) is not None:
        return
    await character_repo.insert_favorite(db, CharacterFavorite(user_id=user_id, character_id=character_id))
    # 冗余计数 +1（乐观: 不加锁, 偶发不准可接受）
    await character_repo.increase_favorite_count(db, character_id, 1)
    await db.commit()


async def unfavorite(db: AsyncSession, user_id: int, character_id: int) -> None:
    """取消收藏(幂等: 未收藏不报错)"""
    deleted = await character_repo.delete_favorite(db, user_id, character_id)
    if deleted > 0:
        # 冗余计数 -1, 带下限保护
        await character_repo.increase_favorite_count(db, character_id, -1)
    await db.commit()


async def list_favorites(db: AsyncSession, user_id: int) -> list[CharacterVO]:
    """列出当前用户收藏的角色 (按收藏时间倒序, 跳过已下架/删除的)"""
    favs = await character_repo.list_favorites(db, user_id)
    if not favs:
        return []
    ids = [f.character_id for f in favs]
    # 只取 PUBLISHED, 避免下架角色出现在收藏列表
    published = {c.id: c for c in await character_repo.get_by_ids(db, ids) if c.status == "PUBLISHED"}
    return [
        _to_vo(published[f.character_id], user_id, {f.character_id})
        for f in favs
        if f.character_id in published
    ]
