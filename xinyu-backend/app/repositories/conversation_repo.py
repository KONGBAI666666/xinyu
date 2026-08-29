"""会话数据访问"""

from sqlalchemy import select, update

from app.core.security import now_local
from app.models import Conversation


async def get_by_id(db, conversation_id: int) -> Conversation | None:
    result = await db.execute(
        select(Conversation).where(Conversation.id == conversation_id, Conversation.deleted == 0)
    )
    return result.scalar_one_or_none()


async def get_owned(db, conversation_id: int, user_id: int) -> Conversation | None:
    """查询归属指定用户的会话; 越权与不存在同样返回 None (不暴露存在性)"""
    conversation = await get_by_id(db, conversation_id)
    if conversation is None or conversation.user_id != user_id:
        return None
    return conversation


async def insert(db, conversation: Conversation) -> Conversation:
    db.add(conversation)
    await db.flush()
    return conversation


async def list_by_user(db, user_id: int, limit: int = 100) -> list[Conversation]:
    """按最新消息时间倒序, 上限 100 条 (侧边栏只展示最近会话)"""
    result = await db.execute(
        select(Conversation)
        .where(Conversation.user_id == user_id, Conversation.deleted == 0)
        .order_by(Conversation.last_message_at.desc())
        .limit(limit)
    )
    return list(result.scalars())


async def refresh_last_message(db, conversation_id: int, preview: str | None, message_at=None) -> None:
    """刷新冗余字段: 最新消息时间 + 摘要 (截断 100 字)"""
    truncated = preview[:100] if preview else preview
    await db.execute(
        update(Conversation)
        .where(Conversation.id == conversation_id)
        .values(last_message_at=message_at or now_local(), last_message_preview=truncated)
    )


async def rename(db, conversation: Conversation, new_title: str) -> None:
    await db.execute(
        update(Conversation)
        .where(Conversation.id == conversation.id)
        .values(title=new_title, updated_at=now_local())
    )
    conversation.title = new_title


async def soft_delete(db, conversation_id: int) -> None:
    await db.execute(
        update(Conversation).where(Conversation.id == conversation_id).values(deleted=1, updated_at=now_local())
    )


async def update_model_id(db, conversation_id: int, model_id: int | None) -> None:
    await db.execute(
        update(Conversation)
        .where(Conversation.id == conversation_id)
        .values(model_id=model_id, updated_at=now_local())
    )
