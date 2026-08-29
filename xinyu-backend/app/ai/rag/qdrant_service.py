"""Qdrant 向量库客户端封装

集合策略: 每个知识库一个集合 ({collection}_{kb_id}), 因为不同知识库可能
使用不同 Embedding 模型, 向量维度不一致时无法共存于同一集合。
"""

import asyncio
import re
import uuid

from qdrant_client import QdrantClient
from qdrant_client.models import Distance, FieldCondition, Filter, MatchValue, PointStruct, VectorParams

from app.ai.types import RagChunk
from app.core.config import settings


class QdrantService:
    def __init__(self):
        self._client = QdrantClient(url=settings.qdrant_url)
        self._collection = settings.qdrant_collection

    def _collection_for(self, kb_id: str) -> str:
        sanitized = re.sub(r"[^0-9a-zA-Z_]", "_", str(kb_id))
        return f"{self._collection}_{sanitized}"

    # ——— 同步实现 ———

    def _ensure_kb_collection_sync(self, kb_id: str, dim: int) -> None:
        name = self._collection_for(kb_id)
        if not self._client.collection_exists(name):
            self._client.create_collection(
                collection_name=name,
                vectors_config=VectorParams(size=dim, distance=Distance.COSINE),
            )

    def _upsert_chunks_sync(self, kb_id: str, doc_id: str, chunks: list[str], embeddings: list[list[float]]) -> None:
        name = self._collection_for(kb_id)
        points = [
            PointStruct(
                id=str(uuid.uuid4()),
                vector=vec,
                payload={"doc_id": doc_id, "chunk_index": i, "text": text},
            )
            for i, (text, vec) in enumerate(zip(chunks, embeddings))
        ]
        self._client.upsert(collection_name=name, points=points)

    def _search_sync(self, kb_id: str, query_vec: list[float], top_k: int = 3) -> list[RagChunk]:
        name = self._collection_for(kb_id)
        if not self._client.collection_exists(name):
            return []
        results = self._client.query_points(
            collection_name=name,
            query=query_vec,
            limit=top_k,
            with_payload=True,
        ).points
        return [RagChunk(text=r.payload.get("text", ""), score=r.score) for r in results]

    def _delete_by_doc_sync(self, kb_id: str, doc_id: str) -> None:
        name = self._collection_for(kb_id)
        if not self._client.collection_exists(name):
            return
        self._client.delete(
            collection_name=name,
            points_selector=Filter(must=[FieldCondition(key="doc_id", match=MatchValue(value=doc_id))]),
        )

    def _delete_by_kb_sync(self, kb_id: str) -> None:
        name = self._collection_for(kb_id)
        # 集合不存在才视为已删除; 其余异常必须上抛, 否则会留下孤儿向量
        if not self._client.collection_exists(name):
            return
        self._client.delete_collection(name)

    # ——— 对外异步接口 (线程池包装, 避免阻塞事件循环) ———

    async def ensure_kb_collection(self, kb_id: str, dim: int) -> None:
        await asyncio.to_thread(self._ensure_kb_collection_sync, kb_id, dim)

    async def upsert_chunks(self, kb_id: str, doc_id: str, chunks: list[str], embeddings: list[list[float]]) -> None:
        await asyncio.to_thread(self._upsert_chunks_sync, kb_id, doc_id, chunks, embeddings)

    async def search(self, kb_id: str, query_vec: list[float], top_k: int = 3) -> list[RagChunk]:
        return await asyncio.to_thread(self._search_sync, kb_id, query_vec, top_k)

    async def delete_by_doc(self, kb_id: str, doc_id: str) -> None:
        await asyncio.to_thread(self._delete_by_doc_sync, kb_id, doc_id)

    async def delete_by_kb(self, kb_id: str) -> None:
        await asyncio.to_thread(self._delete_by_kb_sync, kb_id)

    def close(self) -> None:
        self._client.close()
