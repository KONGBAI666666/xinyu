"""长期记忆服务 — 对应 Java MemoryService + MemoryInjector

职责:
- 用户视角的 CRUD（列表 / 编辑 / 删除）, 不暴露新增接口（提取为服务端内部行为）
- 注入查询: 按 importance Top-K 取 ACTIVE 记忆, 拼成文本块供上下文组装
- 批量保存提取结果（同 key 去重: 已有则更新 content, 无 key 直接插入）
"""

import logging

from sqlalchemy.ext.asyncio import AsyncSession

from app.ai.memory.extractor import ExtractedMemory
from app.ai.memory.prompts import format_memory_injection
from app.core.exceptions import BizException, ResultCode
from app.models import Memory
from app.repositories import character_repo, memory_repo
from app.schemas.memory import MemoryUpdateDTO, MemoryVO

logger = logging.getLogger("xinyu.memory")

# 注入上限: 最多 5 条, 控制 token 成本
INJECT_LIMIT = 5


def _to_vo(m: Memory, character_name: str) -> MemoryVO:
    return MemoryVO(
        id=str(m.id),
        userId=str(m.user_id),
        characterId=str(m.character_id),
        characterName=character_name,
        memoryKey=m.memory_key,
        content=m.content,
        importance=m.importance,
        status=m.status,
        sourceConversationId=str(m.source_conversation_id) if m.source_conversation_id is not None else None,
        createdAt=m.created_at,
        updatedAt=m.updated_at,
    )


async def _require_owned(db: AsyncSession, memory_id: int, user_id: int) -> Memory:
    """校验记忆归属: 不存在或越权统一 40400, 不暴露存在性"""
    memory = await memory_repo.get_by_id(db, memory_id)
    if memory is None or memory.user_id != user_id:
        raise BizException(ResultCode.NOT_FOUND, "记忆不存在或无权访问")
    return memory


async def _character_name(db: AsyncSession, character_id: int) -> str:
    character = await character_repo.get_by_id(db, character_id)
    return character.name if character is not None else "已删除角色"


async def list_by_user(db: AsyncSession, user_id: int, character_id: int | None) -> list[MemoryVO]:
    """列出用户的全部记忆, 可按角色筛选 (角色名批量补齐, 避免每条 JOIN)"""
    memories = await memory_repo.list_by_user(db, user_id, character_id)
    if not memories:
        return []
    # 批量查角色名, 避免每条 JOIN
    names: dict[int, str] = {}
    for m in memories:
        if m.character_id not in names:
            names[m.character_id] = await _character_name(db, m.character_id)
    return [_to_vo(m, names[m.character_id]) for m in memories]


async def update(db: AsyncSession, memory_id: int, user_id: int, req: MemoryUpdateDTO) -> MemoryVO:
    """用户编辑记忆（content / importance / status）"""
    memory = await _require_owned(db, memory_id, user_id)
    await memory_repo.update_memory(
        db, memory_id, content=req.content, importance=req.importance, status=req.status
    )
    await db.commit()
    # 回读实体字段以返回最新值
    memory.content = req.content
    memory.importance = req.importance
    memory.status = req.status
    return _to_vo(memory, await _character_name(db, memory.character_id))


async def delete(db: AsyncSession, memory_id: int, user_id: int) -> None:
    """用户删除记忆"""
    await _require_owned(db, memory_id, user_id)
    await memory_repo.soft_delete(db, memory_id)
    await db.commit()


async def build_memory_block(db: AsyncSession, user_id: int, character_id: int, limit: int = INJECT_LIMIT) -> str:
    """注入查询: 查 Top-K ACTIVE 记忆并拼成文本块; 无记忆返回空字符串"""
    memories = await memory_repo.list_active_for_inject(db, user_id, character_id, limit)
    return format_memory_injection([m.content for m in memories])


async def save_extracted(
    db: AsyncSession,
    user_id: int,
    character_id: int,
    conversation_id: int,
    extracted: list[ExtractedMemory],
) -> None:
    """批量保存提取的记忆

    同 key 去重: 已有则更新 content + importance + source; 无 key 直接插入。
    """
    if not extracted:
        return
    for em in extracted:
        content = (em.content or "").strip()
        if not content:
            continue
        # 同 key 去重: 已有则更新, 无 key 直接插入
        existing = None
        if em.memoryKey and em.memoryKey.strip():
            existing = await memory_repo.find_by_key(db, user_id, character_id, em.memoryKey.strip())
        if existing is not None:
            await memory_repo.update_memory(
                db,
                existing.id,
                content=content,
                importance=em.importance,
                status="ACTIVE",
                source_conversation_id=conversation_id,
            )
        else:
            await memory_repo.insert(
                db,
                Memory(
                    user_id=user_id,
                    character_id=character_id,
                    memory_key=em.memoryKey,
                    content=content,
                    importance=em.importance,
                    status="ACTIVE",
                    source_conversation_id=conversation_id,
                ),
            )
    logger.debug("记忆提取落库: userId=%s, characterId=%s, 条数=%s", user_id, character_id, len(extracted))
