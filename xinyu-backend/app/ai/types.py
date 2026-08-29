"""AI 层内部数据模型 (原 xinyu-ai app/models.py 的核心部分)"""

from pydantic import BaseModel


class ModelConfig(BaseModel):
    """运行时模型配置 (api_key 已解密, 仅在内存中传递, 不落库不打日志)"""

    modelCode: str
    baseUrl: str
    apiKey: str


class ChatMessage(BaseModel):
    """单条 LLM 消息"""

    role: str  # system / user / assistant
    content: str

    @classmethod
    def system(cls, content: str) -> "ChatMessage":
        return cls(role="system", content=content)

    @classmethod
    def user(cls, content: str) -> "ChatMessage":
        return cls(role="user", content=content)

    @classmethod
    def assistant(cls, content: str) -> "ChatMessage":
        return cls(role="assistant", content=content)


class RagChunk(BaseModel):
    text: str
    score: float
