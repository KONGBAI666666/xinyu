"""长期记忆接口 — 对应 Java MemoryController

不暴露新增接口（POST）: 记忆由服务端在每轮对话后异步提取, 用户只能管理已有记忆。
"""

from fastapi import APIRouter, Depends, Query
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.deps import get_current_user_id, parse_id
from app.core.database import get_db
from app.schemas.common import Result
from app.schemas.memory import MemoryUpdateDTO, MemoryVO
from app.services import memory_service

router = APIRouter(prefix="/api/memories", tags=["memories"])


@router.get("")
async def list_memories(
    characterId: str | None = Query(default=None),
    user_id: int = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> Result[list[MemoryVO]]:
    """列出当前用户的记忆, 可按角色筛选"""
    character_id = parse_id(characterId, "characterId")
    return Result.ok(await memory_service.list_by_user(db, user_id, character_id))


@router.put("/{memory_id}")
async def update_memory(
    memory_id: str,
    req: MemoryUpdateDTO,
    user_id: int = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> Result[MemoryVO]:
    """编辑记忆（content / importance / status）"""
    return Result.ok(await memory_service.update(db, parse_id(memory_id, "memoryId"), user_id, req))


@router.delete("/{memory_id}")
async def delete_memory(
    memory_id: str,
    user_id: int = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> Result:
    """删除记忆"""
    await memory_service.delete(db, parse_id(memory_id, "memoryId"), user_id)
    return Result.ok()
