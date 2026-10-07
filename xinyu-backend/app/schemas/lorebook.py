"""世界书 Schema — LorebookEntry 的出入参"""

from pydantic import BaseModel, field_validator

from app.schemas.common import DateTimeStr


class LorebookSaveDTO(BaseModel):
    keywords: str
    content: str
    priority: int = 0
    enabled: bool = True

    @field_validator("keywords")
    @classmethod
    def validate_keywords(cls, v: str) -> str:
        v = v.strip()
        if not v:
            raise ValueError("触发关键词不能为空")
        if len(v) > 500:
            raise ValueError("关键词最长500个字符")
        return v

    @field_validator("content")
    @classmethod
    def validate_content(cls, v: str) -> str:
        v = v.strip()
        if not v:
            raise ValueError("设定内容不能为空")
        if len(v) > 2000:
            raise ValueError("设定内容最长2000个字符")
        return v

    @field_validator("priority")
    @classmethod
    def validate_priority(cls, v: int) -> int:
        if not 0 <= v <= 100:
            raise ValueError("优先级取值 0-100")
        return v


class LorebookEntryVO(BaseModel):
    id: str
    characterId: str
    keywords: str
    content: str
    priority: int
    enabled: int
    createdAt: DateTimeStr
