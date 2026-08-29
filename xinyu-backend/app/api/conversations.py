"""会话管理接口 — 对应 Java ConversationController

与 messages.py 共用 /api/conversations 前缀但按职责分开:
本模块负责会话本身的管理（列表 / 创建 / 删除 / 重命名 / 模型切换）,
messages.py 负责聊天链路（消息历史 / SSE / 停止生成）。
"""

from fastapi import APIRouter, Depends, Query
from pydantic import BaseModel
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.deps import get_current_user_id, parse_id
from app.core.database import get_db
from app.schemas.common import Result
from app.schemas.conversation import ConversationCreateDTO, ConversationVO
from app.services import chat_service, conversation_service

router = APIRouter(prefix="/api/conversations", tags=["conversations"])


class RenameBody(BaseModel):
    title: str


@router.get("")
async def list_conversations(
    user_id: int = Depends(get_current_user_id), db: AsyncSession = Depends(get_db)
) -> Result[list[ConversationVO]]:
    """会话列表: 仅当前用户的会话, 按最新消息时间倒序"""
    return Result.ok(await conversation_service.list_by_user(db, user_id))


@router.post("")
async def create_conversation(
    dto: ConversationCreateDTO,
    user_id: int = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> Result[ConversationVO]:
    """创建会话（同时写入角色开场白为首条 ASSISTANT 消息）"""
    return Result.ok(await chat_service.create_conversation(db, user_id, dto))


@router.delete("/{conversation_id}")
async def delete_conversation(
    conversation_id: str,
    user_id: int = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> Result:
    """删除会话: 逻辑删除会话及关联消息"""
    await conversation_service.delete(db, parse_id(conversation_id, "conversationId"), user_id)
    return Result.ok()


@router.put("/{conversation_id}/title")
async def rename_conversation(
    conversation_id: str,
    body: RenameBody,
    user_id: int = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> Result[ConversationVO]:
    """重命名会话"""
    return Result.ok(await conversation_service.rename(db, parse_id(conversation_id, "conversationId"), user_id, body.title))


@router.put("/{conversation_id}/model")
async def switch_model(
    conversation_id: str,
    modelId: str | None = Query(default=None),
    user_id: int = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> Result:
    """切换会话使用的模型（modelId 为空表示回到用户默认模型）"""
    await conversation_service.switch_model(
        db, parse_id(conversation_id, "conversationId"), user_id, parse_id(modelId, "modelId")
    )
    return Result.ok()
