"""消息 Schema — 对应 Java MessageVO + SSE 事件载荷 (契约二)"""

from typing import Literal

from pydantic import BaseModel, field_validator

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
    # 挂靠的 USER 消息 (重新生成的多个版本共享同一 parent)
    parentMessageId: str | None = None
    # 重新生成的版本序号 (0=原始回复)
    regenerateCount: int = 0
    # LIKE / DISLIKE / NONE (仅 ASSISTANT 有意义)
    feedback: str = "NONE"
    createdAt: DateTimeStr

    @field_validator("feedback", mode="before")
    @classmethod
    def normalize_feedback(cls, v) -> str:
        # 列是自由 String, 脏值不能炸整个历史接口 (同记忆 importance 的教训)
        return v if v in ("LIKE", "DISLIKE", "NONE") else "NONE"


class FeedbackDTO(BaseModel):
    """消息反馈请求体"""

    feedback: Literal["LIKE", "DISLIKE", "NONE"]


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
