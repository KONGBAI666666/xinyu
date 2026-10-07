"""会话服务 — 对应 Java ConversationService (会话列表/重命名/删除/模型切换)"""

from sqlalchemy.ext.asyncio import AsyncSession

from app.core.exceptions import BizException, ResultCode
from app.models import Conversation
from app.repositories import conversation_repo, model_repo
from app.schemas.conversation import ConversationVO


def to_vo(c: Conversation) -> ConversationVO:
    return ConversationVO(
        id=str(c.id),
        characterId=str(c.character_id),
        modelId=str(c.model_id) if c.model_id is not None else None,
        kbId=str(c.kb_id) if c.kb_id is not None else None,
        title=c.title,
        lastMessageAt=c.last_message_at,
        lastMessagePreview=c.last_message_preview,
        createdAt=c.created_at,
    )


async def require_owned(db: AsyncSession, conversation_id: int, user_id: int) -> Conversation:
    """校验会话归属; 越权与不存在统一 40400, 不暴露存在性"""
    conversation = await conversation_repo.get_owned(db, conversation_id, user_id)
    if conversation is None:
        raise BizException(ResultCode.NOT_FOUND, "会话不存在")
    return conversation


async def list_by_user(db: AsyncSession, user_id: int) -> list[ConversationVO]:
    """查询用户的全部会话, 按最新消息时间倒序 (上限 100 条, 侧边栏只展示最近会话)"""
    conversations = await conversation_repo.list_by_user(db, user_id)
    return [to_vo(c) for c in conversations]


async def rename(db: AsyncSession, conversation_id: int, user_id: int, new_title: str | None) -> ConversationVO:
    """重命名会话; 空标题直接返回原会话"""
    conversation = await require_owned(db, conversation_id, user_id)
    trimmed = new_title.strip() if new_title else ""
    if not trimmed:
        return to_vo(conversation)
    # 标题长度限制 50 字符
    await conversation_repo.rename(db, conversation, trimmed[:50])
    await db.commit()
    return to_vo(conversation)


async def delete(db: AsyncSession, conversation_id: int, user_id: int) -> None:
    """删除会话（逻辑删除会话 + 关联消息）"""
    await require_owned(db, conversation_id, user_id)
    from app.repositories import message_repo

    await message_repo.delete_by_conversation(db, conversation_id)
    await conversation_repo.soft_delete(db, conversation_id)
    await db.commit()


async def switch_model(db: AsyncSession, conversation_id: int, user_id: int, model_id: int | None) -> None:
    """切换会话使用的模型; model_id 为 None 表示清除会话级覆盖回到用户默认模型"""
    await require_owned(db, conversation_id, user_id)
    if model_id is not None:
        # 校验模型归属 (仅确认存在且属于该用户, 不解密)
        model = await model_repo.get_by_id_and_user(db, model_id, user_id)
        if model is None:
            raise BizException(ResultCode.NOT_FOUND, "模型不存在或无权访问")
    await conversation_repo.update_model_id(db, conversation_id, model_id)
    await db.commit()
