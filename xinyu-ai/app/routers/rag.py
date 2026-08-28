"""RAG 接口 — 检索、文档向量化、删除向量"""

import base64
from fastapi import APIRouter

from app.models import (
    RagSearchRequest, RagSearchResponse,
    RagProcessRequest, RagProcessResponse,
)
from app.services.rag_service import get_rag_service

router = APIRouter(prefix="/ai/rag", tags=["rag"])

# 集合按知识库创建 (ensure_kb_collection 在文档入库时保证), 无需全局初始化


@router.post("/search", response_model=RagSearchResponse)
async def search(req: RagSearchRequest):
    """向量检索"""
    chunks, rag_block = await get_rag_service().search(
        kb_id=req.kbId,
        query=req.query,
        model_config=req.modelConfig,
        top_k=req.topK,
        score_threshold=req.scoreThreshold,
        embedding_model=req.embeddingModel,
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
        kb_embedding_model=req.kbEmbeddingModel,
        kb_embedding_dim=req.kbEmbeddingDim,
    )


@router.delete("/vectors")
async def delete_vectors(kbId: str, docId: str = None):
    """删除向量数据 (按文档或整库), 通过 query 参数传递

    破坏性操作的令牌校验由 main.py 的路由级依赖统一完成。
    """
    if docId:
        await get_rag_service().delete_doc(kbId, docId)
    else:
        await get_rag_service().delete_kb(kbId)
    return {"deleted": True}
