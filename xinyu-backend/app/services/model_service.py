"""AI 模型配置服务 — 对应 Java AiModelService + LlmConfigResolver 合并

关键设计:
- API Key 加密入库: 明文 → aes_encrypt → ai_model.api_key_encrypted
- 默认模型唯一性: setDefault 时先把同用户其他模型 is_default=0, 事务保证
- 删除默认模型: 自动把最近创建的一个置为默认, 避免用户无默认模型
- 掩码策略: 已配置 → "已配置 ****", 不泄露 key 片段
"""

from sqlalchemy.ext.asyncio import AsyncSession

from app.ai.types import ModelConfig
from app.core.exceptions import BizException, ResultCode
from app.core.security import aes_decrypt, aes_encrypt
from app.models import AiModel
from app.repositories import model_repo
from app.schemas.model import AiModelSaveDTO, AiModelVO


def _to_vo(model: AiModel) -> AiModelVO:
    return AiModelVO(
        id=str(model.id),
        provider=model.provider,
        modelCode=model.model_code,
        displayName=model.display_name,
        baseUrl=model.base_url,
        apiKeyMasked="已配置 ****" if model.api_key_encrypted else None,
        isDefault=model.is_default,
        enabled=model.enabled,
        createdAt=model.created_at,
    )


async def _require_owned(db: AsyncSession, model_id: int, user_id: int) -> AiModel:
    model = await model_repo.get_by_id_and_user(db, model_id, user_id)
    if model is None:
        raise BizException(ResultCode.NOT_FOUND, "模型不存在或无权访问")
    return model


async def list_by_user(db: AsyncSession, user_id: int) -> list[AiModelVO]:
    """列出当前用户的所有模型 (默认优先 → 创建时间倒序)"""
    models = await model_repo.list_by_user(db, user_id)
    return [_to_vo(m) for m in models]


async def create(db: AsyncSession, user_id: int, req: AiModelSaveDTO) -> AiModelVO:
    """添加模型"""
    is_default = 1 if req.isDefault else 0
    model = AiModel(
        user_id=user_id,
        provider=req.provider,
        model_code=req.modelCode,
        display_name=req.displayName,
        base_url=req.baseUrl,
        api_key_encrypted=aes_encrypt(req.apiKey),
        is_default=is_default,
        enabled=1,
    )
    # 若设为默认, 先清掉旧的默认
    if is_default == 1:
        await model_repo.clear_default(db, user_id)
    await model_repo.insert(db, model)
    await db.commit()
    return _to_vo(model)


async def update(db: AsyncSession, model_id: int, user_id: int, req: AiModelSaveDTO) -> AiModelVO:
    """编辑模型（apiKey 为空则保留原值）"""
    model = await _require_owned(db, model_id, user_id)
    model.provider = req.provider
    model.model_code = req.modelCode
    model.display_name = req.displayName
    model.base_url = req.baseUrl

    # apiKey 非空才覆盖, 空保留原值
    if req.apiKey and req.apiKey.strip():
        model.api_key_encrypted = aes_encrypt(req.apiKey)

    want_default = bool(req.isDefault)
    if want_default and model.is_default != 1:
        await model_repo.clear_default(db, user_id)
        model.is_default = 1
    elif not want_default:
        model.is_default = 0
    await model_repo.update_model(db, model)
    await db.commit()
    return _to_vo(model)


async def delete(db: AsyncSession, model_id: int, user_id: int) -> None:
    """删除模型（删除默认模型后, 自动选最近一个为默认）"""
    model = await _require_owned(db, model_id, user_id)
    was_default = model.is_default == 1
    await model_repo.soft_delete(db, model_id)

    # 删除的是默认模型 → 自动选最近创建的一个为默认
    if was_default:
        next_model = await model_repo.get_latest(db, user_id)
        if next_model is not None:
            await model_repo.set_default(db, next_model.id)
    await db.commit()


async def set_default(db: AsyncSession, model_id: int, user_id: int) -> None:
    """设为默认模型（先把该用户其他模型 is_default 置 0）"""
    await _require_owned(db, model_id, user_id)
    await model_repo.clear_default(db, user_id)
    await model_repo.set_default(db, model_id)
    await db.commit()


# ==================== 模型配置解析 (原 Java LlmConfigResolver) ====================


def _create_config(model: AiModel) -> ModelConfig:
    """解密 API Key, 构造运行时配置 (仅内存传递, 不落库不打日志)"""
    return ModelConfig(
        modelCode=model.model_code,
        baseUrl=model.base_url,
        apiKey=aes_decrypt(model.api_key_encrypted) or "",
    )


async def resolve_config(db: AsyncSession, model_id: int, user_id: int) -> ModelConfig:
    """按 modelId 解析模型配置 (解密 API Key)"""
    model = await _require_owned(db, model_id, user_id)
    return _create_config(model)


async def resolve_default_config(db: AsyncSession, user_id: int) -> ModelConfig | None:
    """解析用户默认模型配置; 无默认模型返回 None"""
    model = await model_repo.get_default(db, user_id)
    if model is None:
        return None
    return _create_config(model)
