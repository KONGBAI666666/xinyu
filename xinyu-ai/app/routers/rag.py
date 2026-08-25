"""RAG 接口 — 检索、文档向量化、删除向量"""

import base64
from fastapi import APIRouter

from app.models import (
    RagSearchRequest, RagSearchResponse,
    RagProcessRequest, RagProcessResponse,
)
from app.services.rag_service import RagService

router = APIRouter(prefix="/ai/rag", tags=["rag"])

_rag_service = RagService()


@router.on_event("startup")
async def _ensure_collection():
    await _rag_service.ensure_collection()


@router.post("/search", response_model=RagSearchResponse)
async def search(req: RagSearchRequest):
    """向量检索"""
    chunks, rag_block = await _rag_service.search(
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
    file_content = base64.b64decode(req.fileContentBase64)
    return await _rag_service.process_document(
        kb_id=req.kbId,
        doc_id=req.docId,
        file_name=req.fileName,
        file_content=file_content,
        model_config=req.modelConfig,
    )


@router.delete("/vectors")
async def delete_vectors(kbId: str, docId: str = None):
    """删除向量数据 (按文档或整库), 通过 query 参数传递"""
    if docId:
        await _rag_service.delete_doc(kbId, docId)
    else:
        await _rag_service.delete_kb(kbId)
    return {"deleted": True}
