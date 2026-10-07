"""AI 角色接口 — 对应 Java CharacterController

路径分两类:
- 管理类(/api/characters CRUD): 需登录, 仅操作自己创建的角色 + 官方 PUBLISHED 可见
- 广场类(/api/characters/square, /api/characters/{id}/detail): 游客可访问, 仅 PUBLISHED

注意路由注册顺序: /square、/favorites 等固定路径必须先于 /{id} 注册。
"""

from fastapi import APIRouter, Depends, Query
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.deps import get_current_user_id, get_optional_user_id, parse_id
from app.core.database import get_db
from app.schemas.character import CharacterSaveDTO, CharacterVO
from app.schemas.common import Result
from app.services import character_service

router = APIRouter(prefix="/api/characters", tags=["characters"])


# ---------- 广场（游客可访问） ----------


@router.get("/square")
async def square(
    keyword: str | None = None,
    sort: str | None = None,
    user_id: int | None = Depends(get_optional_user_id),
    db: AsyncSession = Depends(get_db),
) -> Result[list[CharacterVO]]:
    """广场列表: 仅 PUBLISHED 角色, 支持搜索 + 三种排序 (RECOMMEND/HOT/LATEST)"""
    return Result.ok(await character_service.list_square(db, user_id, keyword, sort))


@router.get("/favorites")
async def favorites(
    user_id: int = Depends(get_current_user_id), db: AsyncSession = Depends(get_db)
) -> Result[list[CharacterVO]]:
    """收藏列表: 按收藏时间倒序, 仅 PUBLISHED"""
    return Result.ok(await character_service.list_favorites(db, user_id))


@router.get("/{character_id}/detail")
async def square_detail(
    character_id: str,
    user_id: int | None = Depends(get_optional_user_id),
    db: AsyncSession = Depends(get_db),
) -> Result[CharacterVO | None]:
    """广场详情: 仅 PUBLISHED 角色对所有人可见"""
    return Result.ok(await character_service.get_square_detail(db, parse_id(character_id, "characterId"), user_id))


# ---------- 管理类（需登录） ----------


@router.get("")
async def list_characters(
    user_id: int = Depends(get_current_user_id), db: AsyncSession = Depends(get_db)
) -> Result[list[CharacterVO]]:
    """列出当前用户可见的角色"""
    return Result.ok(await character_service.list_visible(db, user_id))


@router.get("/{character_id}")
async def detail(
    character_id: str,
    user_id: int = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> Result[CharacterVO | None]:
    """角色详情（管理视角: 自建任意状态 + 官方 PUBLISHED）"""
    return Result.ok(await character_service.get_by_id_for_user(db, parse_id(character_id, "characterId"), user_id))


@router.post("")
async def create(
    req: CharacterSaveDTO,
    user_id: int = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> Result[CharacterVO]:
    """创建角色"""
    return Result.ok(await character_service.create(db, user_id, req))


@router.put("/{character_id}")
async def update(
    character_id: str,
    req: CharacterSaveDTO,
    user_id: int = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> Result[CharacterVO]:
    """编辑角色(仅自建)"""
    return Result.ok(await character_service.update(db, parse_id(character_id, "characterId"), user_id, req))


@router.delete("/{character_id}")
async def delete(
    character_id: str,
    user_id: int = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> Result:
    """删除角色(仅自建, 官方不可删)"""
    await character_service.delete(db, parse_id(character_id, "characterId"), user_id)
    return Result.ok()


@router.put("/{character_id}/status")
async def switch_status(
    character_id: str,
    status: str = Query(...),
    user_id: int = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> Result[CharacterVO]:
    """切换角色状态(DRAFT / PUBLISHED / OFFLINE)"""
    return Result.ok(await character_service.switch_status(db, parse_id(character_id, "characterId"), user_id, status))


# ---------- 收藏（需登录） ----------


@router.post("/{character_id}/favorite")
async def favorite(
    character_id: str,
    user_id: int = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> Result:
    """收藏角色(幂等)"""
    await character_service.favorite(db, user_id, parse_id(character_id, "characterId"))
    return Result.ok()


@router.delete("/{character_id}/favorite")
async def unfavorite(
    character_id: str,
    user_id: int = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> Result:
    """取消收藏(幂等)"""
    await character_service.unfavorite(db, user_id, parse_id(character_id, "characterId"))
    return Result.ok()
