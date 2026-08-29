"""长期记忆 Schema — 对应 Java MemoryUpdateRequest/MemoryVO"""

from typing import Literal

from pydantic import BaseModel, field_validator

from app.schemas.common import DateTimeStr


class MemoryUpdateDTO(BaseModel):
    content: str
    importance: Literal["HIGH", "MEDIUM", "LOW"]
    status: Literal["ACTIVE", "DISABLED"]

    @field_validator("content")
    @classmethod
    def validate_content(cls, v: str) -> str:
        if not v or not v.strip():
            raise ValueError("记忆内容不能为空")
        if len(v) > 500:
            raise ValueError("记忆内容最长500个字符")
        return v


class MemoryVO(BaseModel):
    id: str
    userId: str
    characterId: str
    characterName: str
    memoryKey: str | None = None
    content: str
    importance: Literal["HIGH", "MEDIUM", "LOW"]
    status: Literal["ACTIVE", "DISABLED"]
    sourceConversationId: str | None = None
    createdAt: DateTimeStr
    updatedAt: DateTimeStr
