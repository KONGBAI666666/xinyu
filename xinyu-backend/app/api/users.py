"""用户接口（均需登录）— 对应 Java UserController"""

from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.deps import get_current_user_id
from app.core.database import get_db
from app.schemas.auth import UserVO
from app.schemas.common import Result
from app.schemas.user import UpdatePasswordDTO
from app.services import user_service

router = APIRouter(prefix="/api/users", tags=["users"])


@router.get("/me")
async def me(user_id: int = Depends(get_current_user_id), db: AsyncSession = Depends(get_db)) -> Result[UserVO]:
    """当前登录用户信息"""
    return Result.ok(await user_service.get_me(db, user_id))


@router.put("/me/password")
async def update_password(
    dto: UpdatePasswordDTO, user_id: int = Depends(get_current_user_id), db: AsyncSession = Depends(get_db)
) -> Result:
    """修改密码"""
    await user_service.update_password(db, user_id, dto)
    return Result.ok()
