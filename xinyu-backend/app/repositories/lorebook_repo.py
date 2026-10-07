"""世界书条目数据访问"""

from sqlalchemy import select, update

from app.core.security import now_local
from app.models import LorebookEntry


async def list_by_character(db, character_id: int) -> list[LorebookEntry]:
    """角色的全部条目 (含停用, 管理视角); 注入时再过滤 enabled"""
    result = await db.execute(
        select(LorebookEntry)
        .where(LorebookEntry.character_id == character_id, LorebookEntry.deleted == 0)
        .order_by(LorebookEntry.priority.desc(), LorebookEntry.id.asc())
    )
    return list(result.scalars())


async def get_by_id(db, entry_id: int) -> LorebookEntry | None:
    result = await db.execute(
        select(LorebookEntry).where(LorebookEntry.id == entry_id, LorebookEntry.deleted == 0)
    )
    return result.scalar_one_or_none()


async def insert(db, entry: LorebookEntry) -> LorebookEntry:
    db.add(entry)
    await db.flush()
    return entry


async def update_entry(db, entry: LorebookEntry) -> None:
    await db.execute(
        update(LorebookEntry)
        .where(LorebookEntry.id == entry.id)
        .values(
            keywords=entry.keywords,
            content=entry.content,
            priority=entry.priority,
            enabled=entry.enabled,
            updated_at=now_local(),
        )
    )


async def soft_delete(db, entry_id: int) -> None:
    await db.execute(
        update(LorebookEntry).where(LorebookEntry.id == entry_id).values(deleted=1, updated_at=now_local())
    )
