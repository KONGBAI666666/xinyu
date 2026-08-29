"""模型配置数据访问"""

from sqlalchemy import desc, select, update

from app.core.security import now_local
from app.models import AiModel


async def get_by_id(db, model_id: int) -> AiModel | None:
    result = await db.execute(select(AiModel).where(AiModel.id == model_id, AiModel.deleted == 0))
    return result.scalar_one_or_none()


async def get_by_id_and_user(db, model_id: int, user_id: int) -> AiModel | None:
    result = await db.execute(
        select(AiModel).where(AiModel.id == model_id, AiModel.user_id == user_id, AiModel.deleted == 0)
    )
    return result.scalar_one_or_none()


async def get_default(db, user_id: int) -> AiModel | None:
    result = await db.execute(
        select(AiModel)
        .where(AiModel.user_id == user_id, AiModel.is_default == 1, AiModel.deleted == 0)
        .limit(1)
    )
    return result.scalar_one_or_none()


async def list_by_user(db, user_id: int) -> list[AiModel]:
    result = await db.execute(
        select(AiModel)
        .where(AiModel.user_id == user_id, AiModel.deleted == 0)
        .order_by(desc(AiModel.is_default), desc(AiModel.created_at))
    )
    return list(result.scalars())


async def get_latest(db, user_id: int) -> AiModel | None:
    result = await db.execute(
        select(AiModel)
        .where(AiModel.user_id == user_id, AiModel.deleted == 0)
        .order_by(desc(AiModel.created_at))
        .limit(1)
    )
    return result.scalar_one_or_none()


async def insert(db, model: AiModel) -> AiModel:
    db.add(model)
    await db.flush()
    return model


async def soft_delete(db, model_id: int) -> None:
    await db.execute(update(AiModel).where(AiModel.id == model_id).values(deleted=1, updated_at=now_local()))


async def update_model(db, model: AiModel) -> None:
    """按实体当前字段更新 (编辑 / 设默认)"""
    await db.execute(
        update(AiModel)
        .where(AiModel.id == model.id)
        .values(
            provider=model.provider,
            model_code=model.model_code,
            display_name=model.display_name,
            base_url=model.base_url,
            api_key_encrypted=model.api_key_encrypted,
            is_default=model.is_default,
            updated_at=now_local(),
        )
    )


async def clear_default(db, user_id: int) -> None:
    await db.execute(
        update(AiModel)
        .where(AiModel.user_id == user_id, AiModel.is_default == 1)
        .values(is_default=0, updated_at=now_local())
    )


async def set_default(db, model_id: int) -> None:
    await db.execute(
        update(AiModel).where(AiModel.id == model_id).values(is_default=1, updated_at=now_local())
    )
