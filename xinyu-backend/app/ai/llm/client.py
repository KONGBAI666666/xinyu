"""LLM 客户端 — OpenAI 兼容协议, 支持 SSE 流式和同步调用

错误码映射与原 xinyu-ai 保持一致 (51001 连接失败 / 51002 超时 / 51003 Token 超限)。
异常详情只进日志: baseUrl 由用户自填, 错误原文可能携带其指向的内网服务响应,
回显给前端会构成 SSRF 读通道。
"""

import logging
from collections.abc import AsyncIterator

import httpx
from openai import AsyncOpenAI

from app.ai.types import ChatMessage, ModelConfig
from app.core.exceptions import LlmConnectError

logger = logging.getLogger("xinyu.llm")


class LlmClient:
    """封装 OpenAI SDK, 每请求新建客户端, 用完必须 aclose"""

    def __init__(self, config: ModelConfig):
        self.config = config
        self._client = AsyncOpenAI(
            api_key=config.apiKey,
            base_url=config.baseUrl,
            timeout=httpx.Timeout(connect=10.0, read=120.0, write=10.0, pool=5.0),
        )

    async def aclose(self) -> None:
        await self._client.close()

    async def stream_chat(
        self,
        messages: list,
        temperature: float = 0.8,
        max_tokens: int = 1024,
        tools: list[dict] | None = None,
    ) -> AsyncIterator[tuple[str, dict]]:
        """流式聊天, yield (event_type, payload)

        event_type: "delta" | "tool_calls" | "done" | "error"
        - 传 tools 时模型可能请求函数调用: 收集完毕后 yield "tool_calls",
          且该轮不 yield "done" (由 agent 循环决定继续); usage 仍随 done 带出。
        - messages 元素可为 ChatMessage 或 OpenAI 格式 dict (tool 消息等)。
        """
        try:
            kwargs: dict = {
                "model": self.config.modelCode,
                "messages": [
                    m if isinstance(m, dict) else {"role": m.role, "content": m.content}
                    for m in messages
                ],
                "temperature": temperature,
                "max_tokens": max_tokens,
                "stream": True,
                "stream_options": {"include_usage": True},
            }
            if tools:
                kwargs["tools"] = tools

            stream = await self._client.chat.completions.create(**kwargs)

            prompt_tokens = 0
            completion_tokens = 0
            content_parts: list[str] = []
            tool_calls: dict[int, dict] = {}
            finish_reason = None

            async for chunk in stream:
                if chunk.usage:
                    prompt_tokens = chunk.usage.prompt_tokens or prompt_tokens
                    completion_tokens = chunk.usage.completion_tokens or completion_tokens

                if not chunk.choices:
                    continue

                choice = chunk.choices[0]
                if choice.finish_reason:
                    finish_reason = choice.finish_reason
                delta = choice.delta
                if delta and delta.content:
                    content_parts.append(delta.content)
                    yield "delta", {"content": delta.content}
                if delta and delta.tool_calls:
                    for tc in delta.tool_calls:
                        slot = tool_calls.setdefault(
                            tc.index, {"id": "", "name": "", "arguments": ""}
                        )
                        if tc.id:
                            slot["id"] = tc.id
                        if tc.function and tc.function.name:
                            slot["name"] = tc.function.name
                        if tc.function and tc.function.arguments:
                            slot["arguments"] += tc.function.arguments

            if finish_reason == "tool_calls" and tool_calls:
                calls = [
                    {"id": s["id"], "name": s["name"], "arguments": s["arguments"]}
                    for _, s in sorted(tool_calls.items())
                ]
                yield "tool_calls", {
                    "calls": calls,
                    "content": "".join(content_parts),
                    "promptTokens": prompt_tokens,
                    "completionTokens": completion_tokens,
                }
                return

            yield "done", {
                "promptTokens": prompt_tokens,
                "completionTokens": completion_tokens,
                "status": "COMPLETED",
            }

        except Exception as e:
            code, msg = self._map_error(e)
            logger.warning(
                "LLM 流式调用失败: baseUrl=%s, model=%s, code=%s, error=%r",
                self.config.baseUrl, self.config.modelCode, code, e,
            )
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
            logger.warning(
                "LLM 调用失败: baseUrl=%s, model=%s, code=%s, error=%r",
                self.config.baseUrl, self.config.modelCode, code, e,
            )
            raise LlmConnectError(f"[{code}] {msg}")

    @staticmethod
    def _map_error(e: Exception) -> tuple[int, str]:
        """将 OpenAI SDK 异常映射为业务错误码 (与原 xinyu-ai 一致)

        文案固定收敛, 不拼接异常原文 (详情由调用方记录日志)。
        """
        if isinstance(e, httpx.ConnectError | httpx.ConnectTimeout):
            return 51001, "无法连接模型服务, 请检查模型地址与网络"
        if isinstance(e, httpx.ReadTimeout):
            return 51002, "LLM 请求超时"
        if hasattr(e, "status_code"):
            sc = e.status_code
            if sc == 400:
                return 51003, "LLM Token 超限或请求格式错误"
            if sc in (401, 403):
                return 51001, "LLM 认证失败, 请检查 API Key"
            if sc == 429:
                return 51001, "LLM 请求频率超限, 请稍后重试"
        return 50000, "模型服务返回异常, 请稍后重试"


class MockLlmClient:
    """开发环境 Mock, 不调用真实 LLM"""

    async def aclose(self) -> None:
        pass

    async def stream_chat(
        self,
        messages: list,
        temperature: float = 0.8,
        max_tokens: int = 1024,
        tools: list[dict] | None = None,
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
