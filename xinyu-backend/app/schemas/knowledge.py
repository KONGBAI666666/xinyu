"""知识库 Schema — 对应 Java KnowledgeBaseVO/KnowledgeDocumentVO/CreateRequest"""

from pydantic import BaseModel, field_validator

from app.schemas.common import DateTimeStr


class KnowledgeBaseCreateDTO(BaseModel):
    name: str
    description: str | None = None

    @field_validator("name")
    @classmethod
    def validate_name(cls, v: str) -> str:
        if not v or not v.strip():
            raise ValueError("知识库名称不能为空")
        if len(v) > 50:
            raise ValueError("知识库名称最长50个字符")
        return v


class KnowledgeDocumentVO(BaseModel):
    id: str
    kbId: str
    userId: str
    fileName: str
    fileType: str
    fileSize: int
    chunkCount: int
    status: str
    errorMsg: str | None = None
    createdAt: DateTimeStr
    updatedAt: DateTimeStr


class KnowledgeBaseVO(BaseModel):
    id: str
    userId: str
    name: str
    description: str | None = None
    docCount: int
    chunkCount: int
    status: str
    createdAt: DateTimeStr
    updatedAt: DateTimeStr
    documents: list[KnowledgeDocumentVO] | None = None  # 详情接口才填充
