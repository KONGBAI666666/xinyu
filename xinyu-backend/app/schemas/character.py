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
    # 角色级默认模型: 必须是本人启用中的模型; 空串/null 清除绑定; 新会话继承
    modelId: str | None = None

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


# ---------- 角色卡导入导出 ----------


class CardLorebookEntry(BaseModel):
    """角色卡内嵌的世界书条目"""

    keywords: str
    content: str
    priority: int = 0
    enabled: bool = True

    @field_validator("keywords")
    @classmethod
    def validate_keywords(cls, v: str) -> str:
        v = v.strip()
        if not v or len(v) > 500:
            raise ValueError("关键词须为 1-500 个字符")
        return v

    @field_validator("content")
    @classmethod
    def validate_content(cls, v: str) -> str:
        v = v.strip()
        if not v or len(v) > 2000:
            raise ValueError("设定内容须为 1-2000 个字符")
        return v


class CharacterCardDTO(BaseModel):
    """导入的角色卡 (与导出格式一致)"""

    name: str
    avatarUrl: str | None = None
    intro: str | None = None
    systemPrompt: str
    greeting: str
    temperature: float = 0.8
    maxTokens: int = 1024
    lorebook: list[CardLorebookEntry] = []

    @field_validator("name")
    @classmethod
    def validate_name(cls, v: str) -> str:
        v = v.strip()
        if not v or len(v) > 32:
            raise ValueError("角色名须为 1-32 个字符")
        return v

    @field_validator("systemPrompt")
    @classmethod
    def validate_system_prompt(cls, v: str) -> str:
        v = v.strip()
        if not v or len(v) > 10000:
            raise ValueError("人设 Prompt 须为 1-10000 个字符")
        return v

    @field_validator("greeting")
    @classmethod
    def validate_greeting(cls, v: str) -> str:
        v = v.strip()
        if not v or len(v) > 500:
            raise ValueError("开场白须为 1-500 个字符")
        return v

    @field_validator("temperature")
    @classmethod
    def validate_temperature(cls, v: float) -> float:
        if not 0.0 <= v <= 2.0:
            raise ValueError("温度取值 0-2")
        return v

    @field_validator("maxTokens")
    @classmethod
    def validate_max_tokens(cls, v: int) -> int:
        if not 1 <= v <= 8192:
            raise ValueError("maxTokens 取值 1-8192")
        return v

    @field_validator("lorebook")
    @classmethod
    def validate_lorebook(cls, v: list[CardLorebookEntry]) -> list[CardLorebookEntry]:
        if len(v) > 50:
            raise ValueError("世界书条目最多 50 条")
        return v


class CharacterCardVO(CharacterCardDTO):
    """导出的角色卡 (版本号标识格式)"""

    version: int = 1
