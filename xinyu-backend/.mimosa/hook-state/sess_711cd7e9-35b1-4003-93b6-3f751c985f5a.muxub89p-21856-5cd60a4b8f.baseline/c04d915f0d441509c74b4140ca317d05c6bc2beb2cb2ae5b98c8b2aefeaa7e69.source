"""AI 模型配置接口（用户级 CRUD）— 对应 Java AiModelController"""

from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.deps import get_current_user_id, parse_id
from app.core.database import get_db
from app.schemas.common import Result
from app.schemas.model import AiModelSaveDTO, AiModelVO
from app.services import model_service

router = APIRouter(prefix="/api/models", tags=["models"])


@router.get("")
async def list_models(
    user_id: int = Depends(get_current_user_id), db: AsyncSession = Depends(get_db)
) -> Result[list[AiModelVO]]:
    """列出当前用户的所有模型"""
    return Result.ok(await model_service.list_by_user(db, user_id))


@router.post("")
async def create_model(
    req: AiModelSaveDTO,
    user_id: int = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> Result[AiModelVO]:
    """添加模型"""
    return Result.ok(await model_service.create(db, user_id, req))


@router.put("/{model_id}")
async def update_model(
    model_id: str,
    req: AiModelSaveDTO,
    user_id: int = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> Result[AiModelVO]:
    """编辑模型"""
    return Result.ok(await model_service.update(db, parse_id(model_id, "modelId"), user_id, req))


@router.delete("/{model_id}")
async def delete_model(
    model_id: str,
    user_id: int = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> Result:
    """删除模型"""
    await model_service.delete(db, parse_id(model_id, "modelId"), user_id)
    return Result.ok()


@router.put("/{model_id}/default")
async def set_default(
    model_id: str,
    user_id: int = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> Result:
    """设为默认模型"""
    await model_service.set_default(db, parse_id(model_id, "modelId"), user_id)
    return Result.ok()
