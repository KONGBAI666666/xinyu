"""会话 Schema — 对应 Java ConversationCreateDTO/ConversationVO + 聊天请求"""

from pydantic import BaseModel, field_validator

from app.schemas.common import DateTimeStr


class ConversationCreateDTO(BaseModel):
    characterId: str
    kbId: str | None = None
    title: str | None = None


class ConversationVO(BaseModel):
    id: str
    characterId: str
    modelId: str | None = None
    kbId: str | None = None
    title: str
    lastMessageAt: DateTimeStr | None = None
    lastMessagePreview: str | None = None
    createdAt: DateTimeStr


class RenameConversationDTO(BaseModel):
    title: str


class ChatRequestDTO(BaseModel):
    content: str
    clientMessageId: str | None = None

    @field_validator("content")
    @classmethod
    def validate_content(cls, v: str) -> str:
        if not v or not v.strip():
            raise ValueError("消息内容不能为空")
        if len(v) > 2000:
            raise ValueError("单条消息最长2000字")
        return v

    @field_validator("clientMessageId")
    @classmethod
    def validate_client_id(cls, v: str | None) -> str | None:
        if v and len(v) > 64:
            raise ValueError("clientMessageId 最长64字符")
        return v
