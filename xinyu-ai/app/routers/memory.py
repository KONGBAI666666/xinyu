"""记忆提取接口 — Java 调用, LLM 提取长期记忆"""

from fastapi import APIRouter

from app.models import MemoryExtractRequest, MemoryExtractResponse
from app.services.memory_extractor import MemoryExtractor

router = APIRouter(prefix="/ai/memory", tags=["memory"])


@router.post("/extract", response_model=MemoryExtractResponse)
async def extract_memory(req: MemoryExtractRequest):
    """提取记忆: Java 传入对话文本 + 模型配置, 返回结构化记忆"""
    return await MemoryExtractor.extract(req.modelConfig, req.dialog)
