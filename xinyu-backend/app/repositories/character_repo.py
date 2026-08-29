"""角色/收藏数据访问"""

from sqlalchemy import delete, func, or_, select, text, update

from app.core.security import now_local
from app.models import AiCharacter, CharacterFavorite


async def get_by_id(db, character_id: int) -> AiCharacter | None:
    result = await db.execute(select(AiCharacter).where(AiCharacter.id == character_id, AiCharacter.deleted == 0))
    return result.scalar_one_or_none()


async def insert(db, character: AiCharacter) -> AiCharacter:
    db.add(character)
    await db.flush()
    return character


async def soft_delete(db, character_id: int) -> None:
    await db.execute(update(AiCharacter).where(AiCharacter.id == character_id).values(deleted=1))


async def update_character(db, character: AiCharacter) -> None:
    """按实体当前字段更新 (编辑/状态切换)"""
    await db.execute(
        update(AiCharacter)
        .where(AiCharacter.id == character.id)
        .values(
            name=character.name,
            avatar_url=character.avatar_url,
            intro=character.intro,
            system_prompt=character.system_prompt,
            greeting=character.greeting,
            temperature=character.temperature,
            max_tokens=character.max_tokens,
            status=character.status,
            updated_at=now_local(),
        )
    )


async def list_visible(db, user_id: int | None) -> list[AiCharacter]:
    """自己创建的(任意状态) + 官方 PUBLISHED; 官方优先 → 自建按创建时间倒序"""
    conditions = [AiCharacter.deleted == 0]
    visible = or_(
        (AiCharacter.creator_type == "OFFICIAL") & (AiCharacter.status == "PUBLISHED"),
        AiCharacter.creator_id == user_id,
    )
    conditions.append(visible)
    result = await db.execute(
        select(AiCharacter)
        .where(*conditions)
        .order_by(AiCharacter.creator_type.asc(), AiCharacter.created_at.desc())
    )
    return list(result.scalars())


async def list_square(db, keyword: str | None, sort: str) -> list[AiCharacter]:
    """广场: 仅 PUBLISHED, 支持 keyword 搜索 + RECOMMEND/HOT/LATEST 排序"""
    conditions = [AiCharacter.deleted == 0, AiCharacter.status == "PUBLISHED"]
    if keyword:
        conditions.append(or_(AiCharacter.name.like(f"%{keyword}%"), AiCharacter.intro.like(f"%{keyword}%")))

    order = []
    if sort == "HOT":
        order = [AiCharacter.favorite_count.desc(), AiCharacter.chat_count.desc()]
    elif sort == "LATEST":
        order = [AiCharacter.created_at.desc()]
    else:  # RECOMMEND
        order = [AiCharacter.creator_type.asc(), AiCharacter.chat_count.desc()]

    result = await db.execute(select(AiCharacter).where(*conditions).order_by(*order))
    return list(result.scalars())


async def find_favorite(db, user_id: int, character_id: int) -> CharacterFavorite | None:
    result = await db.execute(
        select(CharacterFavorite).where(
            CharacterFavorite.user_id == user_id, CharacterFavorite.character_id == character_id
        )
    )
    return result.scalar_one_or_none()


async def insert_favorite(db, favorite: CharacterFavorite) -> None:
    db.add(favorite)
    await db.flush()


async def delete_favorite(db, user_id: int, character_id: int) -> int:
    result = await db.execute(
        delete(CharacterFavorite).where(
            CharacterFavorite.user_id == user_id, CharacterFavorite.character_id == character_id
        )
    )
    return result.rowcount


async def list_favorites(db, user_id: int) -> list[CharacterFavorite]:
    result = await db.execute(
        select(CharacterFavorite)
        .where(CharacterFavorite.user_id == user_id)
        .order_by(CharacterFavorite.created_at.desc())
    )
    return list(result.scalars())


async def favorited_ids(db, user_id: int | None) -> set[int]:
    if user_id is None:
        return set()
    result = await db.execute(
        select(CharacterFavorite.character_id).where(CharacterFavorite.user_id == user_id)
    )
    return set(result.scalars())


async def get_by_ids(db, ids: list[int]) -> list[AiCharacter]:
    if not ids:
        return []
    result = await db.execute(select(AiCharacter).where(AiCharacter.id.in_(ids), AiCharacter.deleted == 0))
    return list(result.scalars())


async def increase_favorite_count(db, character_id: int, delta: int) -> None:
    """冗余计数原子增减 (delta 为负数时带下限保护)"""
    if delta >= 0:
        expr = text("favorite_count = favorite_count + :delta")
    else:
        expr = text("favorite_count = GREATEST(favorite_count + :delta, 0)")
    await db.execute(
        update(AiCharacter).where(AiCharacter.id == character_id).values(favorite_count=expr),
        {"delta": delta},
    )
