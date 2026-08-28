"""Java ↔ Python 通信的 Pydantic 模型"""

from typing import Optional
from pydantic import BaseModel, Field


# ---------- LLM 相关 ----------

class ModelConfig(BaseModel):
    """模型配置 (Java 解密后传明文)"""
    modelCode: str
    baseUrl: str
    apiKey: str


class ChatMessage(BaseModel):
    """单条 LLM 消息"""
    role: str  # system / user / assistant
    content: str


class ChatRequest(BaseModel):
    """Java → Python: 聊天请求"""
    conversationId: str
    userId: str
    characterId: str
    modelConfig: ModelConfig
    messages: list[ChatMessage]  # 已组装好的 context (system + history)
    temperature: float = 0.8
    maxTokens: int = 1024
    # RAG 检索参数 (可选)
    ragKbId: Optional[str] = None
    userQuery: Optional[str] = None  # 用于 RAG 检索的原始用户输入

# ---------- Memory 相关 ----------

class MemoryExtractRequest(BaseModel):
    """Java → Python: 记忆提取请求"""
    modelConfig: ModelConfig
    dialog: str  # 格式化好的对话文本
    userId: str
    characterId: str
    conversationId: str


class ExtractedMemory(BaseModel):
    memoryKey: Optional[str] = None
    content: str
    importance: str = "MEDIUM"  # HIGH / MEDIUM / LOW


class MemoryExtractResponse(BaseModel):
    memories: list[ExtractedMemory] = Field(default_factory=list)


# ---------- RAG 相关 ----------

class RagSearchRequest(BaseModel):
    """Java → Python: RAG 检索请求"""
    kbId: str
    query: str
    topK: int = 3
    scoreThreshold: float = 0.5
    modelConfig: Optional[ModelConfig] = None  # embedding 用的模型配置


class RagChunk(BaseModel):
    text: str
    score: float


class RagSearchResponse(BaseModel):
    chunks: list[RagChunk] = Field(default_factory=list)
    ragBlock: Optional[str] = None  # 格式化好的注入文本


class RagProcessRequest(BaseModel):
    """Java → Python: 文档向量化请求"""
    kbId: str
    docId: str
    fileName: str
    fileContentBase64: str  # 文件内容 (base64 编码)
    modelConfig: Optional[ModelConfig] = None


class RagProcessResponse(BaseModel):
    chunkCount: int
    status: str = "READY"
    errorMsg: Optional[str] = None
