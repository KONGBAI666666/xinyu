"""长期记忆数据访问"""

from sqlalchemy import text, update

from sqlalchemy import select

from app.core.security import now_local
from app.models import Memory


async def get_by_id(db, memory_id: int) -> Memory | None:
    result = await db.execute(select(Memory).where(Memory.id == memory_id, Memory.deleted == 0))
    return result.scalar_one_or_none()


async def insert(db, memory: Memory) -> Memory:
    db.add(memory)
    await db.flush()
    return memory


async def list_by_user(db, user_id: int, character_id: int | None) -> list[Memory]:
    conditions = [Memory.user_id == user_id, Memory.deleted == 0]
    if character_id is not None:
        conditions.append(Memory.character_id == character_id)
    # importance 为字符串枚举, 用 FIELD 按权重排序 (MySQL 方言)
    result = await db.execute(
        select(Memory)
        .where(*conditions)
        .order_by(
            text("FIELD(importance, 'HIGH', 'MEDIUM', 'LOW')"),
            Memory.created_at.desc(),
        )
    )
    return list(result.scalars())


async def list_active_for_inject(db, user_id: int, character_id: int, limit: int) -> list[Memory]:
    """注入查询: ACTIVE + importance 权重降序 + created_at 降序, LIMIT K"""
    result = await db.execute(
        select(Memory)
        .where(
            Memory.user_id == user_id,
            Memory.character_id == character_id,
            Memory.status == "ACTIVE",
            Memory.deleted == 0,
        )
        .order_by(
            text("FIELD(importance, 'HIGH', 'MEDIUM', 'LOW')"),
            Memory.created_at.desc(),
        )
        .limit(max(1, limit))
    )
    return list(result.scalars())


async def find_by_key(db, user_id: int, character_id: int, memory_key: str) -> Memory | None:
    """同 (userId, characterId, memoryKey) 去重查询"""
    result = await db.execute(
        select(Memory)
        .where(
            Memory.user_id == user_id,
            Memory.character_id == character_id,
            Memory.memory_key == memory_key,
            Memory.deleted == 0,
        )
        .limit(1)
    )
    return result.scalar_one_or_none()


async def soft_delete(db, memory_id: int) -> None:
    await db.execute(update(Memory).where(Memory.id == memory_id).values(deleted=1, updated_at=now_local()))


async def update_memory(
    db,
    memory_id: int,
    content: str | None = None,
    importance: str | None = None,
    status: str | None = None,
    source_conversation_id: int | None = None,
) -> None:
    """按字段选择性更新 (用户编辑 / 提取去重覆盖共用)"""
    values: dict = {"updated_at": now_local()}
    if content is not None:
        values["content"] = content
    if importance is not None:
        values["importance"] = importance
    if status is not None:
        values["status"] = status
    if source_conversation_id is not None:
        values["source_conversation_id"] = source_conversation_id
    await db.execute(update(Memory).where(Memory.id == memory_id).values(**values))
