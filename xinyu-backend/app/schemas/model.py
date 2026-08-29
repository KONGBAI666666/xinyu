"""模型配置 Schema — 对应 Java AiModelSaveRequest/AiModelVO"""

from pydantic import BaseModel, field_validator

from app.schemas.common import DateTimeStr


class AiModelSaveDTO(BaseModel):
    provider: str
    modelCode: str
    displayName: str
    baseUrl: str
    apiKey: str | None = None  # 添加必填, 编辑可空 (空则保留原值)
    isDefault: bool | None = None

    @field_validator("provider", "modelCode", "displayName", "baseUrl")
    @classmethod
    def validate_not_blank(cls, v: str, info) -> str:
        if not v or not v.strip():
            raise ValueError(f"{info.field_name} 不能为空")
        return v


class AiModelVO(BaseModel):
    id: str
    provider: str
    modelCode: str
    displayName: str
    baseUrl: str
    apiKeyMasked: str | None = None
    isDefault: int
    enabled: int
    createdAt: DateTimeStr
