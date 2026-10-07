"""聊天链路接口 — 对应 Java ChatController

SSE 端点直接返回 StreamingResponse (text/event-stream);
业务异常(40400/42200 等)发生时全局异常处理器返回 JSON Result,
前端 useSseChat 的 onopen 按 Content-Type 区分两条路径。
"""

from fastapi import APIRouter, Depends, Query
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.deps import get_current_user_id, parse_id
from app.core.database import get_db
from app.schemas.common import Result
from app.schemas.conversation import ChatRequestDTO
from app.schemas.message import FeedbackDTO, MessageVO
from app.services import chat_service

router = APIRouter(prefix="/api/conversations", tags=["chat"])


@router.get("/{conversation_id}/messages")
async def list_messages(
    conversation_id: str,
    before: str | None = Query(default=None),
    size: int | None = Query(default=None),
    user_id: int = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> Result[list[MessageVO]]:
    """消息历史（游标分页: before=游标消息ID, 缺省取最新一页）"""
    return Result.ok(
        await chat_service.list_messages(
            db,
            user_id,
            parse_id(conversation_id, "conversationId"),
            parse_id(before, "before"),
            size,
        )
    )


@router.post("/{conversation_id}/chat")
async def chat(
    conversation_id: str,
    dto: ChatRequestDTO,
    user_id: int = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
):
    """SSE 流式聊天: meta → delta* → done | error"""
    return await chat_service.chat(db, user_id, parse_id(conversation_id, "conversationId"), dto)


@router.post("/{conversation_id}/stop")
async def stop(
    conversation_id: str,
    user_id: int = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> Result:
    """停止当前生成: 断开 AI 调用, 消息置 STOPPED 并保留已生成文本 (幂等)"""
    await chat_service.stop_generation(db, user_id, parse_id(conversation_id, "conversationId"))
    return Result.ok()


@router.post("/{conversation_id}/regenerate")
async def regenerate(
    conversation_id: str,
    user_id: int = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
):
    """重新生成最后一条回复 (SSE): 新版本挂靠最后一条 USER 消息, 旧版本保留可切换"""
    return await chat_service.regenerate(db, user_id, parse_id(conversation_id, "conversationId"))


@router.put("/{conversation_id}/messages/{message_id}/feedback")
async def set_feedback(
    conversation_id: str,
    message_id: str,
    dto: FeedbackDTO,
    user_id: int = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> Result:
    """消息反馈 (点赞/点踩, 再点一次取消)"""
    await chat_service.set_feedback(
        db,
        user_id,
        parse_id(conversation_id, "conversationId"),
        parse_id(message_id, "messageId"),
        dto.feedback,
    )
    return Result.ok()
