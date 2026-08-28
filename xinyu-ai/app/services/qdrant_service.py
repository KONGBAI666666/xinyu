"""Qdrant 向量库客户端封装

qdrant-client 1.x 提供同步客户端, 这里用 asyncio.to_thread 包装成异步接口,
避免阻塞事件循环。
"""

import asyncio
from qdrant_client import QdrantClient
from qdrant_client.models import (
    Distance, VectorParams, PointStruct, Filter, FieldCondition, MatchValue,
)

from app.config import settings
from app.models import RagChunk


class QdrantService:

    def __init__(self):
        self._client = QdrantClient(url=settings.qdrant_url)
        self._collection = settings.qdrant_collection
        self._dim = settings.embedding_dim

    # ——— 内部同步方法 ———

    def _ensure_collection_sync(self) -> None:
        collections = self._client.get_collections()
        names = [c.name for c in collections.collections]
        if self._collection not in names:
            self._client.create_collection(
                collection_name=self._collection,
                vectors_config=VectorParams(size=self._dim, distance=Distance.COSINE),
            )

    def _upsert_chunks_sync(
        self, kb_id: str, doc_id: str, chunks: list[str], embeddings: list[list[float]]
    ) -> None:
        import uuid
        points = []
        for i, (text, vec) in enumerate(zip(chunks, embeddings)):
            points.append(PointStruct(
                id=str(uuid.uuid4()),
                vector=vec,
                payload={"kb_id": kb_id, "doc_id": doc_id, "chunk_index": i, "text": text},
            ))
        self._client.upsert(collection_name=self._collection, points=points)

    def _search_sync(
        self, kb_id: str, query_vec: list[float], top_k: int = 3
    ) -> list[RagChunk]:
        results = self._client.query_points(
            collection_name=self._collection,
            query=query_vec,
            query_filter=Filter(
                must=[FieldCondition(key="kb_id", match=MatchValue(value=kb_id))]
            ),
            limit=top_k,
            with_payload=True,
        ).points
        return [
            RagChunk(text=r.payload.get("text", ""), score=r.score)
            for r in results
        ]

    def _delete_by_doc_sync(self, kb_id: str, doc_id: str) -> None:
        self._client.delete(
            collection_name=self._collection,
            points_selector=Filter(
                must=[
                    FieldCondition(key="kb_id", match=MatchValue(value=kb_id)),
                    FieldCondition(key="doc_id", match=MatchValue(value=doc_id)),
                ]
            ),
        )

    def _delete_by_kb_sync(self, kb_id: str) -> None:
        self._client.delete(
            collection_name=self._collection,
            points_selector=Filter(
                must=[FieldCondition(key="kb_id", match=MatchValue(value=kb_id))]
            ),
        )

    # ——— 对外异步接口 (线程池包装) ———

    async def ensure_collection(self) -> None:
        """确保集合存在 (幂等)"""
        await asyncio.to_thread(self._ensure_collection_sync)

    async def upsert_chunks(
        self, kb_id: str, doc_id: str, chunks: list[str], embeddings: list[list[float]]
    ) -> None:
        """批量写入向量点"""
        await asyncio.to_thread(self._upsert_chunks_sync, kb_id, doc_id, chunks, embeddings)

    async def search(
        self, kb_id: str, query_vec: list[float], top_k: int = 3
    ) -> list[RagChunk]:
        """向量检索: 按 kb_id 过滤, cosine 相似度排序"""
        return await asyncio.to_thread(self._search_sync, kb_id, query_vec, top_k)

    async def delete_by_doc(self, kb_id: str, doc_id: str) -> None:
        """删除某文档的全部向量点"""
        await asyncio.to_thread(self._delete_by_doc_sync, kb_id, doc_id)

    async def delete_by_kb(self, kb_id: str) -> None:
        """删除某知识库的全部向量点"""
        await asyncio.to_thread(self._delete_by_kb_sync, kb_id)

    def close(self) -> None:
        """关闭客户端连接"""
        self._client.close()
