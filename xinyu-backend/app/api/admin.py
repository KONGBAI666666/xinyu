"""管理后台接口 (user.role=ADMIN 专用, require_admin 依赖统一把守)"""

from fastapi import APIRouter, Depends, Query
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.deps import parse_id, require_admin
from app.core.database import get_db
from app.schemas.admin import (
    AdminCharacterVO,
    AdminUserVO,
    CharacterReviewDTO,
    PlatformOverviewVO,
    UserStatusDTO,
)
from app.schemas.common import Result
from app.services import admin_service

router = APIRouter(prefix="/api/admin", tags=["admin"])


@router.get("/users/by-username")
async def find_user(
    username: str = Query(..., min_length=1, max_length=32),
    _admin_id: int = Depends(require_admin),
    db: AsyncSession = Depends(get_db),
) -> Result[AdminUserVO | None]:
    """按用户名精确查找用户 (v1 用户管理: 找到后再执行封禁/解封)"""
    return Result.ok(await admin_service.find_user_by_username(db, username))


@router.put("/users/{user_id}/status")
async def set_user_status(
    user_id: str,
    dto: UserStatusDTO,
    admin_id: int = Depends(require_admin),
    db: AsyncSession = Depends(get_db),
) -> Result[AdminUserVO]:
    """封禁/解封用户 (封禁即时生效于登录, 存量 JWT 在有效期内仍可用)"""
    return Result.ok(
        await admin_service.set_user_status(db, admin_id, parse_id(user_id, "userId"), dto.status)
    )


@router.get("/characters")
async def list_characters(
    status: str | None = Query(default=None),
    offset: int = Query(default=0, ge=0),
    size: int = Query(default=30, ge=1, le=100),
    _admin_id: int = Depends(require_admin),
    db: AsyncSession = Depends(get_db),
) -> Result[list[AdminCharacterVO]]:
    """角色审核队列分页 (id 倒序; 页满即视为可能还有更多)"""
    return Result.ok(await admin_service.list_characters(db, status, offset, size))


@router.put("/characters/{character_id}/status")
async def review_character(
    character_id: str,
    dto: CharacterReviewDTO,
    admin_id: int = Depends(require_admin),
    db: AsyncSession = Depends(get_db),
) -> Result[AdminCharacterVO]:
    """审核动作: 通过发布 (PUBLISHED) / 下架 (OFFLINE)"""
    return Result.ok(
        await admin_service.set_character_status(db, parse_id(character_id, "characterId"), dto.status)
    )


@router.get("/overview")
async def overview(
    _admin_id: int = Depends(require_admin),
    db: AsyncSession = Depends(get_db),
) -> Result[PlatformOverviewVO]:
    """平台概览: 全站消息量与 token 消耗"""
    return Result.ok(await admin_service.overview(db))
