"""RAG 接口 — 检索、文档向量化、删除向量"""

import base64
from fastapi import APIRouter, Header, HTTPException

from app.models import (
    RagSearchRequest, RagSearchResponse,
    RagProcessRequest, RagProcessResponse,
)
from app.services.rag_service import get_rag_service
from app.config import settings

router = APIRouter(prefix="/ai/rag", tags=["rag"])

# 集合创建由 main.py lifespan 统一兜底 (on_event 在提供 lifespan 后不会执行)


@router.post("/search", response_model=RagSearchResponse)
async def search(req: RagSearchRequest):
    """向量检索"""
    chunks, rag_block = await get_rag_service().search(
        kb_id=req.kbId,
        query=req.query,
        model_config=req.modelConfig,
        top_k=req.topK,
        score_threshold=req.scoreThreshold,
    )
    return RagSearchResponse(chunks=chunks, ragBlock=rag_block)


@router.post("/process", response_model=RagProcessResponse)
async def process_document(req: RagProcessRequest):
    """文档向量化入库"""
    if not req.modelConfig:
        return RagProcessResponse(
            chunkCount=0, status="ERROR", errorMsg="缺少 embedding 模型配置"
        )
    try:
        file_content = base64.b64decode(req.fileContentBase64, validate=True)
    except (ValueError, TypeError):
        return RagProcessResponse(
            chunkCount=0, status="ERROR", errorMsg="文件内容不是合法的 Base64"
        )
    return await get_rag_service().process_document(
        kb_id=req.kbId,
        doc_id=req.docId,
        file_name=req.fileName,
        file_content=file_content,
        model_config=req.modelConfig,
    )


@router.delete("/vectors")
async def delete_vectors(
    kbId: str,
    docId: str = None,
    x_internal_token: str = Header(default="", alias="X-Internal-Token"),
):
    """删除向量数据 (按文档或整库), 通过 query 参数传递

    破坏性操作: 配置了 AI_INTERNAL_TOKEN 时必须携带匹配的 X-Internal-Token,
    防止内网任意调用方删除知识库向量。
    """
    if settings.internal_token and x_internal_token != settings.internal_token:
        raise HTTPException(status_code=403, detail="invalid internal token")
    if docId:
        await get_rag_service().delete_doc(kbId, docId)
    else:
        await get_rag_service().delete_kb(kbId)
    return {"deleted": True}
