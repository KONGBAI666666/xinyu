"""消息 Schema — 对应 Java MessageVO + SSE 事件载荷 (契约二)"""

from typing import Literal

from pydantic import BaseModel

from app.schemas.common import DateTimeStr

MessageRoleStr = Literal["USER", "ASSISTANT", "SYSTEM"]
MessageStatusStr = Literal["GENERATING", "COMPLETED", "FAILED", "STOPPED"]


class MessageVO(BaseModel):
    id: str
    conversationId: str
    sequenceNo: int
    messageType: MessageRoleStr
    content: str
    status: MessageStatusStr
    promptTokens: int | None = None
    completionTokens: int | None = None
    modelCode: str | None = None
    createdAt: DateTimeStr


# ---------- SSE 事件载荷 (与前端 useSseChat 逐字段一致) ----------


class SseMetaEvent(BaseModel):
    """event:meta — 双消息 ID 回执"""

    userMessageId: str
    assistantMessageId: str


class SseDeltaEvent(BaseModel):
    """event:delta — 增量文本"""

    content: str


class SseDoneEvent(BaseModel):
    """event:done — 终态回执"""

    messageId: str
    promptTokens: int
    completionTokens: int
    status: MessageStatusStr


class SseErrorEvent(BaseModel):
    """event:error — LLM 异常 (51001~51004)"""

    code: int
    message: str
