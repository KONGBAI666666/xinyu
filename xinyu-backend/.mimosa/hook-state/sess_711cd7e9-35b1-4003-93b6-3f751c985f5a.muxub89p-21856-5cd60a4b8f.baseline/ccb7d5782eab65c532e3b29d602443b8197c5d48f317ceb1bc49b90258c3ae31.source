"""角色 Schema — 对应 Java CharacterSaveRequest/CharacterVO/SquareQuery"""

from typing import Literal

from pydantic import BaseModel, field_validator

from app.schemas.common import DateTimeStr

CharacterStatusStr = Literal["DRAFT", "PENDING", "PUBLISHED", "OFFLINE"]


class CharacterSaveDTO(BaseModel):
    name: str
    avatarUrl: str | None = None
    intro: str | None = None
    systemPrompt: str
    greeting: str
    temperature: float
    maxTokens: int
    status: CharacterStatusStr | None = None

    @field_validator("name")
    @classmethod
    def validate_name(cls, v: str) -> str:
        if not v or not v.strip():
            raise ValueError("角色名不能为空")
        if len(v) > 32:
            raise ValueError("角色名最长32个字符")
        return v

    @field_validator("avatarUrl")
    @classmethod
    def validate_avatar(cls, v: str | None) -> str | None:
        if v and len(v) > 255:
            raise ValueError("头像 URL 过长")
        return v

    @field_validator("intro")
    @classmethod
    def validate_intro(cls, v: str | None) -> str | None:
        if v and len(v) > 200:
            raise ValueError("介绍最长200个字符")
        return v

    @field_validator("systemPrompt")
    @classmethod
    def validate_prompt(cls, v: str) -> str:
        if not v or not v.strip():
            raise ValueError("人设 Prompt 不能为空")
        if len(v) > 10000:
            raise ValueError("人设 Prompt 最长10000字")
        return v

    @field_validator("greeting")
    @classmethod
    def validate_greeting(cls, v: str) -> str:
        if not v or not v.strip():
            raise ValueError("开场白不能为空")
        if len(v) > 500:
            raise ValueError("开场白最长500个字符")
        return v

    @field_validator("temperature")
    @classmethod
    def validate_temperature(cls, v: float) -> float:
        if not 0.0 <= v <= 2.0:
            raise ValueError("温度不能小于 0 或大于 2")
        return v

    @field_validator("maxTokens")
    @classmethod
    def validate_max_tokens(cls, v: int) -> int:
        if not 1 <= v <= 8192:
            raise ValueError("maxTokens 不能小于 1 或大于 8192")
        return v


class CharacterVO(BaseModel):
    id: str
    name: str
    avatarUrl: str | None = None
    intro: str | None = None
    systemPrompt: str
    greeting: str
    temperature: float
    maxTokens: int
    modelId: str | None = None
    creatorId: str
    creatorType: Literal["OFFICIAL", "USER"]
    status: CharacterStatusStr
    chatCount: int
    favoriteCount: int
    createdAt: DateTimeStr
    updatedAt: DateTimeStr
    mine: bool = False
    favorited: bool = False
