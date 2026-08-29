"""Embedding 服务 — OpenAI 兼容协议"""

import httpx
from openai import AsyncOpenAI

from app.ai.types import ModelConfig


class EmbeddingService:
    def __init__(self, config: ModelConfig, model_name: str):
        self.config = config
        self.model_name = model_name
        self._client = AsyncOpenAI(
            api_key=config.apiKey,
            base_url=config.baseUrl,
            timeout=httpx.Timeout(connect=10.0, read=60.0, write=10.0, pool=5.0),
        )

    async def aclose(self) -> None:
        await self._client.close()

    async def embed(self, text: str) -> list[float]:
        """单条文本 → 向量"""
        resp = await self._client.embeddings.create(model=self.model_name, input=text)
        return resp.data[0].embedding

    async def embed_batch(self, texts: list[str], batch_size: int = 25) -> list[list[float]]:
        """批量嵌入, 每批最多 batch_size 条"""
        results: list[list[float]] = []
        for i in range(0, len(texts), batch_size):
            batch = texts[i : i + batch_size]
            resp = await self._client.embeddings.create(model=self.model_name, input=batch)
            # 按 index 排序确保顺序
            sorted_data = sorted(resp.data, key=lambda d: d.index)
            results.extend([d.embedding for d in sorted_data])
        return results
