"""世界书服务 — 角色绑定的关键词触发设定 (CRUD + 聊天注入)

注入策略: 仅匹配本轮用户输入; 命中的启用条目按 priority 降序注入,
总预算 MAX_INJECT_CHARS, 超预算的条目跳过 (尝试更小的下一条)。
"""

import logging
import re

from sqlalchemy.ext.asyncio import AsyncSession

from app.core.exceptions import BizException, ResultCode
from app.models import AiCharacter, LorebookEntry
from app.repositories import character_repo, lorebook_repo
from app.schemas.lorebook import LorebookEntryVO, LorebookSaveDTO

logger = logging.getLogger("xinyu.lorebook")

# 注入预算 (字符): 世界书与记忆块/RAG 共用 system prompt, 不能无限膨胀
MAX_INJECT_CHARS = 800

# 关键词分隔: 中英文逗号 / 分号 / 换行
_KEYWORD_SPLIT = re.compile(r"[,，;；\n]")


def _to_vo(e: LorebookEntry) -> LorebookEntryVO:
    return LorebookEntryVO(
        id=str(e.id),
        characterId=str(e.character_id),
        keywords=e.keywords,
        content=e.content,
        priority=e.priority,
        enabled=e.enabled,
        createdAt=e.created_at,
    )


async def _require_owned_character(db: AsyncSession, character_id: int, user_id: int) -> AiCharacter:
    """仅角色创建者可管理其世界书 (官方角色不可编辑)"""
    character = await character_repo.get_by_id(db, character_id)
    if character is None or character.creator_id != user_id or character.creator_type == "OFFICIAL":
        raise BizException(ResultCode.NOT_FOUND, "角色不存在或无权操作")
    return character


async def _require_owned_entry(
    db: AsyncSession, character_id: int, entry_id: int, user_id: int
) -> LorebookEntry:
    await _require_owned_character(db, character_id, user_id)
    entry = await lorebook_repo.get_by_id(db, entry_id)
    if entry is None or entry.character_id != character_id:
        raise BizException(ResultCode.NOT_FOUND, "世界书条目不存在")
    return entry


async def list_entries(db: AsyncSession, character_id: int, user_id: int) -> list[LorebookEntryVO]:
    await _require_owned_character(db, character_id, user_id)
    return [_to_vo(e) for e in await lorebook_repo.list_by_character(db, character_id)]


async def create(
    db: AsyncSession, character_id: int, user_id: int, dto: LorebookSaveDTO
) -> LorebookEntryVO:
    await _require_owned_character(db, character_id, user_id)
    entry = LorebookEntry(
        character_id=character_id,
        user_id=user_id,
        keywords=dto.keywords,
        content=dto.content,
        priority=dto.priority,
        enabled=1 if dto.enabled else 0,
    )
    await lorebook_repo.insert(db, entry)
    await db.commit()
    return _to_vo(entry)


async def update(
    db: AsyncSession, character_id: int, entry_id: int, user_id: int, dto: LorebookSaveDTO
) -> LorebookEntryVO:
    entry = await _require_owned_entry(db, character_id, entry_id, user_id)
    entry.keywords = dto.keywords
    entry.content = dto.content
    entry.priority = dto.priority
    entry.enabled = 1 if dto.enabled else 0
    await lorebook_repo.update_entry(db, entry)
    await db.commit()
    return _to_vo(entry)


async def delete(db: AsyncSession, character_id: int, entry_id: int, user_id: int) -> None:
    entry = await _require_owned_entry(db, character_id, entry_id, user_id)
    await lorebook_repo.soft_delete(db, entry.id)
    await db.commit()


def build_block(entries: list[LorebookEntry], query_text: str, max_chars: int = MAX_INJECT_CHARS) -> str:
    """关键词命中匹配 → 注入文本块 (纯函数, 便于单测)

    - 匹配: 任一关键词 (不区分大小写) 出现在本轮用户输入中即命中
    - 排序: priority 降序, 同级按创建序
    - 预算: 超过 max_chars 的条目跳过, 尝试更小的下一条
    """
    text = (query_text or "").lower()
    if not text:
        return ""
    matched = []
    for e in entries:
        if e.enabled != 1:
            continue
        keywords = [k.strip().lower() for k in _KEYWORD_SPLIT.split(e.keywords or "") if k.strip()]
        if any(k in text for k in keywords):
            matched.append(e)
    matched.sort(key=lambda e: (-e.priority, e.id))

    parts: list[str] = []
    used = 0
    for e in matched:
        content = e.content.strip()
        if not content:
            continue
        if used + len(content) > max_chars:
            continue
        parts.append(f"· {content}")
        used += len(content) + 2
    if not parts:
        return ""
    return "\n\n[世界书设定]\n" + "\n".join(parts)
