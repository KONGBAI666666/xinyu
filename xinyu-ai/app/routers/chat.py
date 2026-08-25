"""聊天 SSE 流式接口 — Java 调用, Python 返回 SSE 流"""

import json
from fastapi import APIRouter
from fastapi.responses import StreamingResponse
import asyncio

from app.models import ChatRequest
from app.services.llm_client import LlmClient, MockLlmClient
from app.services.rag_service import RagService
from app.services.prompt_assembler import PromptAssembler
from app.config import settings

router = APIRouter(prefix="/ai/chat", tags=["chat"])


def _sse_event(event: str, data: dict) -> str:
    """格式化 SSE 事件"""
    return f"event:{event}\ndata:{json.dumps(data, ensure_ascii=False)}\n\n"


@router.post("/stream")
async def chat_stream(req: ChatRequest):
    """SSE 流式聊天

    Java 侧:
    1. 校验 auth + 归属
    2. 保存 USER 消息
    3. 创建 ASSISTANT 占位 (GENERATING)
    4. 组装 messages (含 system prompt + memory)
    5. 调用本接口, 转发 SSE 给前端
    6. done 事件后更新消息 + 触发记忆提取
    """

    async def event_generator():
        # 1. RAG 检索 (如果有知识库)
        rag_block = ""
        if req.ragKbId and req.userQuery and req.modelConfig:
            try:
                rag_svc = RagService()
                _, rag_block = await rag_svc.search(
                    kb_id=req.ragKbId,
                    query=req.userQuery,
                    model_config=req.modelConfig,
                )
            except Exception:
                # RAG 失败降级
                pass

        # 2. 组装完整 context
        messages = PromptAssembler.assemble(
            system_prompt="",  # messages[0] 已包含 system prompt
            history=req.messages,
            memory_block=req.memoryBlock or "",
            rag_block=rag_block,
        )

        # 如果 messages 第一条不是 system, 但 req.messages 已含 system, 直接用 req.messages
        # Java 传入的 messages 已含 system + history, 只需追加 RAG
        if rag_block and messages:
            first = messages[0]
            if first.role == "system":
                messages[0] = type(first)(
                    role="system",
                    content=first.content + "\n\n" + rag_block,
                )

        # 3. 选择 LLM 客户端
        if settings.mock_mode:
            client = MockLlmClient()
        else:
            client = LlmClient(req.modelConfig)

        # 4. 流式调用
        try:
            async for event_type, payload in client.stream_chat(
                messages=messages,
                temperature=req.temperature,
                max_tokens=req.maxTokens,
            ):
                yield _sse_event(event_type, payload)
        except Exception as e:
            yield _sse_event("error", {"code": 50000, "message": str(e)})

    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )
