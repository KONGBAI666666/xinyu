"""聊天 SSE 流式接口 — Java 调用, Python 返回 SSE 流"""

import json
from fastapi import APIRouter
from fastapi.responses import StreamingResponse

from app.models import ChatRequest
from app.services.llm_client import LlmClient, MockLlmClient
from app.services.rag_service import get_rag_service
from app.config import settings

router = APIRouter(prefix="/ai/chat", tags=["chat"])


def _sse_event(event: str, data: dict) -> str:
    """格式化 SSE 事件"""
    return f"event:{event}\ndata:{json.dumps(data, ensure_ascii=False)}\n\n"


@router.post("/stream")
async def chat_stream(req: ChatRequest):
    """SSE 流式聊天

    Java 传入的 messages 已组装好 [system: 角色人设+记忆] + history,
    本端只负责: RAG 检索并追加到 system 末尾 → 调用 LLM → 流式返回。
    不再二次注入 memoryBlock, 也不截断 history (截断会丢掉第 0 条的人设)。
    """

    async def event_generator():
        # 1. RAG 检索 (会话绑定了知识库时); search 内部失败降级为空, 不阻断聊天
        rag_block = ""
        if req.ragKbId and req.userQuery and req.modelConfig:
            _, rag_block = await get_rag_service().search(
                kb_id=req.ragKbId,
                query=req.userQuery,
                model_config=req.modelConfig,
                embedding_model=req.ragEmbeddingModel or None,
            )

        # 2. RAG 结果只追加一次到 Java 的 system 消息末尾
        messages = list(req.messages)
        if rag_block and messages and messages[0].role == "system":
            first = messages[0]
            messages[0] = first.model_copy(
                update={"content": first.content + "\n\n" + rag_block}
            )

        # 3. 选择 LLM 客户端
        if settings.mock_mode:
            client = MockLlmClient()
        else:
            client = LlmClient(req.modelConfig)

        # 4. 流式调用 (无论正常结束还是客户端断连, 都释放连接)
        try:
            async for event_type, payload in client.stream_chat(
                messages=messages,
                temperature=req.temperature,
                max_tokens=req.maxTokens,
            ):
                yield _sse_event(event_type, payload)
        finally:
            await client.aclose()

    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )
