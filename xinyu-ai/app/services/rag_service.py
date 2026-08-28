"""RAG 服务: Embedding + Qdrant 检索 + 注入格式化"""

import logging

from app.core.exceptions import AiError
from app.models import ModelConfig, RagChunk
from app.services.embedding_profiles import resolve_embedding_profile
from app.services.embedding_service import EmbeddingService
from app.services.qdrant_service import QdrantService
from app.services.document_parser import DocumentParser
from app.services.chunk_splitter import ChunkSplitter
from app.config import settings
from app.models import RagProcessResponse

logger = logging.getLogger("xinyu-ai.rag")


class RagService:

    def __init__(self):
        self._qdrant = QdrantService()

    async def search(
        self,
        kb_id: str,
        query: str,
        model_config: ModelConfig,
        top_k: int = None,
        score_threshold: float = None,
        embedding_model: str | None = None,
    ) -> tuple[list[RagChunk], str]:
        """检索知识库, 返回 (chunks, ragBlock)

        embedding_model 为知识库锁定的向量化模型; 缺省时按当前模型配置解析。
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
            # 分数过滤
            filtered = [c for c in chunks if c.score >= score_threshold]
            rag_block = self.format_injection(filtered) if filtered else None
            return filtered, rag_block or ""
        except Exception as e:
            # RAG 失败降级为空, 不阻断聊天, 但必须留痕否则知识库"静默失效"无从排查
            logger.warning("RAG 检索失败已降级为空结果: kb_id=%s, error=%s", kb_id, e)
            return [], ""

    async def process_document(
        self, kb_id: str, doc_id: str, file_name: str, file_content: bytes,
        model_config: ModelConfig,
        kb_embedding_model: str | None = None,
        kb_embedding_dim: int | None = None,
    ) -> RagProcessResponse:
        """文档向量化入库: 解析→分块→嵌入→写 Qdrant

        知识库首次上传时锁定 Embedding 模型/维度 (回传由 Java 侧持久化);
        后续上传必须与锁定值一致, 否则拒绝, 避免同集合混入不同维度向量。
        """
        try:
            profile = resolve_embedding_profile(model_config)
        except AiError as e:
            return RagProcessResponse(chunkCount=0, status="ERROR", errorMsg=e.message)

        if kb_embedding_model and kb_embedding_model != profile.model:
            return RagProcessResponse(
                chunkCount=0, status="ERROR",
                errorMsg=(
                    f"知识库已使用 {kb_embedding_model} ({kb_embedding_dim or '?'}维) 向量化, "
                    f"与当前默认模型解析出的 {profile.model} ({profile.dim}维) 不一致。"
                    "请切换回原模型或新建知识库。"
                ),
            )

        try:
            text = DocumentParser.parse(file_name, file_content)
            if not text.strip():
                return RagProcessResponse(chunkCount=0, status="ERROR", errorMsg="文档内容为空")

            chunks = ChunkSplitter().split(text)
            if not chunks:
                return RagProcessResponse(chunkCount=0, status="ERROR", errorMsg="分块结果为空")

            emb_svc = EmbeddingService(model_config, profile.model)
            try:
                embeddings = await emb_svc.embed_batch(chunks)
            finally:
                await emb_svc.aclose()

            await self._qdrant.ensure_kb_collection(kb_id, profile.dim)
            await self._qdrant.upsert_chunks(kb_id, doc_id, chunks, embeddings)
            return RagProcessResponse(
                chunkCount=len(chunks), status="READY",
                embeddingModel=profile.model, embeddingDim=profile.dim,
            )
        except Exception as e:
            logger.warning("文档向量化失败: kb_id=%s, doc_id=%s, error=%s", kb_id, doc_id, e)
            return RagProcessResponse(chunkCount=0, status="ERROR", errorMsg=str(e))

    async def delete_doc(self, kb_id: str, doc_id: str):
        await self._qdrant.delete_by_doc(kb_id, doc_id)

    async def delete_kb(self, kb_id: str):
        await self._qdrant.delete_by_kb(kb_id)

    def close(self):
        """释放 Qdrant 连接 (进程退出时调用)"""
        self._qdrant.close()

    @staticmethod
    def format_injection(chunks: list[RagChunk]) -> str:
        if not chunks:
            return ""
        parts = ["[知识库参考材料]"]
        for i, c in enumerate(chunks, 1):
            parts.append(f"【片段{i}】\n{c.text}")
        return "\n\n".join(parts)


# 全局单例: QdrantClient 长连接复用, 避免每请求新建客户端导致连接泄漏
_shared_service: RagService | None = None


def get_rag_service() -> RagService:
    global _shared_service
    if _shared_service is None:
        _shared_service = RagService()
    return _shared_service
