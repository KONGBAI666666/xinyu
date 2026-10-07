"""消息数据访问"""

from dataclasses import dataclass

from sqlalchemy import delete, desc, func, select, update

from app.core.security import now_local
from app.models import Message


@dataclass
class UsageSummary:
    call_count: int = 0
    prompt_tokens: int = 0
    completion_tokens: int = 0


async def get_by_id(db, message_id: int) -> Message | None:
    result = await db.execute(select(Message).where(Message.id == message_id, Message.deleted == 0))
    return result.scalar_one_or_none()


async def insert(db, message: Message) -> Message:
    db.add(message)
    await db.flush()
    return message


async def next_sequence_no(db, conversation_id: int) -> int:
    """计算会话内下一个业务序号 (MAX+1, 单用户写场景无并发问题)"""
    result = await db.execute(
        select(func.max(Message.sequence_no)).where(
            Message.conversation_id == conversation_id, Message.deleted == 0
        )
    )
    last = result.scalar_one_or_none()
    return (last or 0) + 1


async def list_history(db, conversation_id: int, before: int | None, size: int) -> list[Message]:
    """游标分页查历史: 取 before 之前(不含)最近 size 条, 按 sequence_no 升序返回"""
    conditions = [Message.conversation_id == conversation_id, Message.deleted == 0]
    if before is not None:
        conditions.append(Message.id < before)
    result = await db.execute(
        select(Message)
        .where(*conditions)
        .order_by(desc(Message.id))
        .limit(size)
    )
    page = list(result.scalars())
    # 倒序取一页再翻转为升序, 前端按时间正序渲染
    return sorted(page, key=lambda m: m.sequence_no)


async def finalize_assistant_message(
    db,
    message_id: int,
    content: str,
    status: str,
    prompt_tokens: int | None = None,
    completion_tokens: int | None = None,
) -> bool:
    """终态落库: 仅当消息仍为 GENERATING 时更新 (守卫, 保证停止/超时不被覆盖)

    返回 True=更新成功; False=消息已被停止/超时收尾, 调用方不应再刷新会话预览
    """
    values: dict = {"content": content, "status": status, "updated_at": now_local()}
    if prompt_tokens is not None:
        values["prompt_tokens"] = prompt_tokens
    if completion_tokens is not None:
        values["completion_tokens"] = completion_tokens
    result = await db.execute(
        update(Message)
        .where(Message.id == message_id, Message.status == "GENERATING", Message.deleted == 0)
        .values(**values)
    )
    return result.rowcount > 0


async def delete_by_conversation(db, conversation_id: int) -> None:
    """逻辑删除指定会话下的全部消息"""
    await db.execute(
        update(Message)
        .where(Message.conversation_id == conversation_id, Message.deleted == 0)
        .values(deleted=1, updated_at=now_local())
    )


async def summarize_usage(db, user_id: int, since=None) -> UsageSummary:
    """聚合统计用户在 [since, +∞) 时间段内的 LLM 用量

    口径: ASSISTANT 消息且 prompt/completion tokens 至少一个非空
    (即真实发生 LLM 调用, 排除 greeting 与中断占位)
    """
    conditions = [
        Message.user_id == user_id,
        Message.message_type == "ASSISTANT",
        Message.deleted == 0,
        (Message.prompt_tokens.isnot(None)) | (Message.completion_tokens.isnot(None)),
    ]
    if since is not None:
        conditions.append(Message.created_at >= since)

    result = await db.execute(
        select(
            func.count(Message.id),
            func.coalesce(func.sum(Message.prompt_tokens), 0),
            func.coalesce(func.sum(Message.completion_tokens), 0),
        ).where(*conditions)
    )
    row = result.one()
    # SUM() 经 aiomysql 返回 Decimal, 统一转 int (count 本身是 int, 转换无害)
    return UsageSummary(call_count=int(row[0]), prompt_tokens=int(row[1]), completion_tokens=int(row[2]))
