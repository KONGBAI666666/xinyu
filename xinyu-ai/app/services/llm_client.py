"""LLM 客户端 — OpenAI 兼容协议, 支持 SSE 流式和同步调用"""

from typing import AsyncIterator
import httpx
from openai import AsyncOpenAI

from app.models import ModelConfig, ChatMessage
from app.core.exceptions import LlmConnectError


class LlmClient:
    """封装 OpenAI SDK, 支持流式 + 同步调用"""

    def __init__(self, config: ModelConfig):
        self.config = config
        self._client = AsyncOpenAI(
            api_key=config.apiKey,
            base_url=config.baseUrl,
            timeout=httpx.Timeout(connect=10.0, read=120.0, write=10.0, pool=5.0),
        )

    @property
    def model_code(self) -> str:
        return self.config.modelCode

    async def stream_chat(
        self, messages: list[ChatMessage], temperature: float = 0.8, max_tokens: int = 1024
    ) -> AsyncIterator[tuple[str, dict]]:
        """流式聊天, yield (event_type, payload_dict)

        event_type: "delta" | "done" | "error"
        """
        try:
            stream = await self._client.chat.completions.create(
                model=self.config.modelCode,
                messages=[{"role": m.role, "content": m.content} for m in messages],
                temperature=temperature,
                max_tokens=max_tokens,
                stream=True,
                stream_options={"include_usage": True},
            )

            prompt_tokens = 0
            completion_tokens = 0

            async for chunk in stream:
                if chunk.usage:
                    prompt_tokens = chunk.usage.prompt_tokens or prompt_tokens
                    completion_tokens = chunk.usage.completion_tokens or completion_tokens

                if not chunk.choices:
                    continue

                delta = chunk.choices[0].delta
                if delta and delta.content:
                    yield "delta", {"content": delta.content}

            yield "done", {
                "promptTokens": prompt_tokens,
                "completionTokens": completion_tokens,
                "status": "COMPLETED",
            }

        except Exception as e:
            code, msg = self._map_error(e)
            yield "error", {"code": code, "message": msg}

    async def chat(
        self, messages: list[ChatMessage], temperature: float = 0.8, max_tokens: int = 1024
    ) -> tuple[str, dict]:
        """同步聊天, 返回 (content, usage_dict)"""
        try:
            resp = await self._client.chat.completions.create(
                model=self.config.modelCode,
                messages=[{"role": m.role, "content": m.content} for m in messages],
                temperature=temperature,
                max_tokens=max_tokens,
            )
            content = resp.choices[0].message.content or ""
            usage = {
                "promptTokens": resp.usage.prompt_tokens if resp.usage else 0,
                "completionTokens": resp.usage.completion_tokens if resp.usage else 0,
            }
            return content, usage
        except Exception as e:
            code, msg = self._map_error(e)
            raise LlmConnectError(f"[{code}] {msg}")

    @staticmethod
    def _map_error(e: Exception) -> tuple[int, str]:
        """将 OpenAI SDK 异常映射为业务错误码"""
        import httpx as _httpx
        if isinstance(e, _httpx.ConnectError) or isinstance(e, _httpx.ConnectTimeout):
            return 51001, f"LLM 连接失败: {e}"
        if isinstance(e, _httpx.ReadTimeout):
            return 51002, "LLM 请求超时"
        if hasattr(e, "status_code"):
            sc = e.status_code
            if sc == 400:
                return 51003, f"LLM Token 超限或请求格式错误: {getattr(e, 'message', str(e))}"
            if sc in (401, 403):
                return 51001, "LLM 认证失败, 请检查 API Key"
            if sc == 429:
                return 51001, "LLM 请求频率超限, 请稍后重试"
        return 50000, str(e)


class MockLlmClient:
    """开发环境 Mock, 不调用真实 LLM"""

    async def stream_chat(
        self, messages: list[ChatMessage], temperature: float = 0.8, max_tokens: int = 1024
    ) -> AsyncIterator[tuple[str, dict]]:
        import asyncio
        segments = ["你好", "呀！", "我是", "心屿", "的 AI", "助手", "。", "有什么", "可以", "帮你的", "吗？"]
        for seg in segments:
            await asyncio.sleep(0.08)
            yield "delta", {"content": seg}
        full = "".join(segments)
        yield "done", {
            "promptTokens": len(str(messages)) // 4,
            "completionTokens": len(full) // 4,
            "status": "COMPLETED",
        }

    async def chat(
        self, messages: list[ChatMessage], temperature: float = 0.8, max_tokens: int = 1024
    ) -> tuple[str, dict]:
        content = "这是 Mock 模式的回复, 未调用真实 LLM。"
        return content, {"promptTokens": 10, "completionTokens": 15}
