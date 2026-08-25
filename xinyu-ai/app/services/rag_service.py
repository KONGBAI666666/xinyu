"""RAG 服务: Embedding + Qdrant 检索 + 注入格式化"""

from app.models import ModelConfig, RagChunk
from app.services.embedding_service import EmbeddingService
from app.services.qdrant_service import QdrantService
from app.services.document_parser import DocumentParser
from app.services.chunk_splitter import ChunkSplitter
from app.config import settings
from app.core.exceptions import AiError
from app.models import RagProcessResponse


class RagService:

    def __init__(self):
        self._qdrant = QdrantService()

    async def ensure_collection(self):
        await self._qdrant.ensure_collection()

    async def search(
        self,
        kb_id: str,
        query: str,
        model_config: ModelConfig,
        top_k: int = None,
        score_threshold: float = None,
    ) -> tuple[list[RagChunk], str]:
        """检索知识库, 返回 (chunks, ragBlock)"""
        top_k = top_k or settings.retrieve_top_k
        score_threshold = score_threshold or settings.retrieve_score_threshold
        try:
            emb_svc = EmbeddingService(model_config)
            query_vec = await emb_svc.embed(query)
            chunks = await self._qdrant.search(kb_id, query_vec, top_k)
            # 分数过滤
            filtered = [c for c in chunks if c.score >= score_threshold]
            rag_block = self.format_injection(filtered) if filtered else None
            return filtered, rag_block or ""
        except Exception as e:
            # RAG 失败降级为空, 不阻断聊天
            return [], ""

    async def process_document(
        self, kb_id: str, doc_id: str, file_name: str, file_content: bytes,
        model_config: ModelConfig,
    ) -> RagProcessResponse:
        """文档向量化入库: 解析→分块→嵌入→写 Qdrant"""
        try:
            text = DocumentParser.parse(file_name, file_content)
            if not text.strip():
                return RagProcessResponse(chunkCount=0, status="ERROR", errorMsg="文档内容为空")

            chunks = ChunkSplitter().split(text)
            if not chunks:
                return RagProcessResponse(chunkCount=0, status="ERROR", errorMsg="分块结果为空")

            emb_svc = EmbeddingService(model_config)
            embeddings = await emb_svc.embed_batch(chunks)

            await self._qdrant.upsert_chunks(kb_id, doc_id, chunks, embeddings)
            return RagProcessResponse(chunkCount=len(chunks), status="READY")
        except Exception as e:
            return RagProcessResponse(chunkCount=0, status="ERROR", errorMsg=str(e))

    async def delete_doc(self, kb_id: str, doc_id: str):
        await self._qdrant.delete_by_doc(kb_id, doc_id)

    async def delete_kb(self, kb_id: str):
        await self._qdrant.delete_by_kb(kb_id)

    @staticmethod
    def format_injection(chunks: list[RagChunk]) -> str:
        if not chunks:
            return ""
        parts = ["[知识库参考材料]"]
        for i, c in enumerate(chunks, 1):
            parts.append(f"【片段{i}】\n{c.text}")
        return "\n\n".join(parts)
