"""RAG 服务: Embedding + Qdrant 检索 + 文档向量化入库 + 注入格式化"""

import asyncio
import logging
from dataclasses import dataclass

from app.ai.rag.chunk_splitter import ChunkSplitter
from app.ai.rag.document_parser import DocumentParser
from app.ai.rag.embedding_profiles import resolve_embedding_profile
from app.ai.rag.embedding_service import EmbeddingService
from app.ai.rag.qdrant_service import QdrantService
from app.ai.rag.rag_templates import format_rag_injection
from app.ai.types import ModelConfig, RagChunk
from app.core.config import settings
from app.core.exceptions import AiError

logger = logging.getLogger("xinyu.rag")


@dataclass
class RagProcessResult:
    """文档向量化结果 (对应原 /ai/rag/process 响应)"""

    chunk_count: int = 0
    status: str = "READY"
    error_msg: str | None = None
    embedding_model: str | None = None
    embedding_dim: int | None = None


class RagService:
    def __init__(self):
        self._qdrant = QdrantService()

    async def search(
        self,
        kb_id: str,
        query: str,
        model_config: ModelConfig,
        top_k: int | None = None,
        score_threshold: float | None = None,
        embedding_model: str | None = None,
    ) -> tuple[list[RagChunk], str]:
        """检索知识库, 返回 (chunks, ragBlock)

        embedding_model 为知识库锁定的向量化模型; 缺省时按当前模型配置解析。
        检索失败降级为空, 不阻断聊天, 但必须留痕。
        """
        top_k = top_k or settings.retrieve_top_k
        score_threshold = score_threshold or settings.retrieve_score_threshold
        try:
            model_name = embedding_model or resolve_embedding_profile(model_config).model
            emb_svc = EmbeddingService(model_config, model_name)
            try:
                query_vec = await emb_svc.embed(query)
            finally:
                await emb_svc.aclose()
            chunks = await self._qdrant.search(kb_id, query_vec, top_k)
            filtered = [c for c in chunks if c.score >= score_threshold]
            rag_block = format_rag_injection(filtered) if filtered else ""
            return filtered, rag_block
        except Exception as e:
            logger.warning("RAG 检索失败已降级为空结果: kb_id=%s, error=%s", kb_id, e)
            return [], ""

    async def process_document(
        self,
        kb_id: str,
        doc_id: str,
        file_name: str,
        file_content: bytes,
        model_config: ModelConfig,
        kb_embedding_model: str | None = None,
        kb_embedding_dim: int | None = None,
    ) -> RagProcessResult:
        """文档向量化入库: 解析→分块→嵌入→写 Qdrant

        知识库首次上传时锁定 Embedding 模型/维度; 后续上传必须与锁定值一致,
        否则拒绝, 避免同集合混入不同维度向量。
        """
        try:
            profile = resolve_embedding_profile(model_config)
        except AiError as e:
            return RagProcessResult(chunk_count=0, status="ERROR", error_msg=e.message)

        if kb_embedding_model and kb_embedding_model != profile.model:
            return RagProcessResult(
                chunk_count=0,
                status="ERROR",
                error_msg=(
                    f"知识库已使用 {kb_embedding_model} ({kb_embedding_dim or '?'}维) 向量化, "
                    f"与当前默认模型解析出的 {profile.model} ({profile.dim}维) 不一致。"
                    "请切换回原模型或新建知识库。"
                ),
            )

        try:
            # 解析/分块是 CPU 密集操作, 放线程池避免阻塞事件循环
            text = await asyncio.to_thread(DocumentParser.parse, file_name, file_content)
            if not text.strip():
                return RagProcessResult(chunk_count=0, status="ERROR", error_msg="文档内容为空")

            chunks = await asyncio.to_thread(ChunkSplitter().split, text)
            if not chunks:
                return RagProcessResult(chunk_count=0, status="ERROR", error_msg="分块结果为空")

            emb_svc = EmbeddingService(model_config, profile.model)
            try:
                embeddings = await emb_svc.embed_batch(chunks)
            finally:
                await emb_svc.aclose()

            await self._qdrant.ensure_kb_collection(kb_id, profile.dim)
            await self._qdrant.upsert_chunks(kb_id, doc_id, chunks, embeddings)
            return RagProcessResult(
                chunk_count=len(chunks),
                status="READY",
                embedding_model=profile.model,
                embedding_dim=profile.dim,
            )
        except Exception as e:
            logger.warning("文档向量化失败: kb_id=%s, doc_id=%s, error=%s", kb_id, doc_id, e)
            return RagProcessResult(chunk_count=0, status="ERROR", error_msg=str(e))

    async def delete_doc(self, kb_id: str, doc_id: str) -> None:
        await self._qdrant.delete_by_doc(kb_id, doc_id)

    async def delete_kb(self, kb_id: str) -> None:
        await self._qdrant.delete_by_kb(kb_id)

    def close(self) -> None:
        self._qdrant.close()


# 全局单例: QdrantClient 长连接复用, 避免每请求新建客户端导致连接泄漏
_shared_service: RagService | None = None


def get_rag_service() -> RagService:
    global _shared_service
    if _shared_service is None:
        _shared_service = RagService()
    return _shared_service
