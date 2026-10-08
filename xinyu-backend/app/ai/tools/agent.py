"""Agent 循环 — 工具调用编排 (stream + function calling)

职责: 在 LlmClient 流式输出之上处理 "模型请求工具 → 执行 → 结果回灌 → 续写"
的多轮循环, 对上层 (chat_service._event_stream) 仍然呈现统一的事件流
("delta" | "tool" | "done" | "error"), 上层无感知。

边界:
- 轮次上限 MAX_TOOL_ROUNDS, 超限后强制发起一次无 tools 的最终回答,
  保证流一定收敛到 done/error;
- usage 跨轮累加, 最终 done 带总量;
- 工具执行异常由执行器内部消化 (返回错误文案), 这里只透传事件。
"""

import json
import logging
from collections.abc import AsyncIterator

from app.ai.tools.registry import ToolExecutor

logger = logging.getLogger("xinyu.tools")

MAX_TOOL_ROUNDS = 3


async def run_agent_stream(
    client,
    messages: list,
    tools: list[dict],
    executor: ToolExecutor,
    temperature: float = 0.8,
    max_tokens: int = 1024,
) -> AsyncIterator[tuple[str, dict]]:
    """带工具调用的流式代理循环, yield (event_type, payload)

    event_type: "delta" | "tool" | "done" | "error"
    messages 元素可为 ChatMessage 或 OpenAI 格式 dict (循环会原地扩充工作副本)。
    """
    working = [m if isinstance(m, dict) else {"role": m.role, "content": m.content} for m in messages]
    total_usage = {"promptTokens": 0, "completionTokens": 0}

    for round_no in range(MAX_TOOL_ROUNDS + 1):
        # 末轮强制不传 tools, 保证收敛 (模型再请求工具属于协议异常, 视作纯文本)
        round_tools = tools if round_no < MAX_TOOL_ROUNDS else None

        round_calls: list[dict] = []
        assistant_content = ""
        round_usage: dict = {}

        async for event, payload in client.stream_chat(
            messages=working, temperature=temperature, max_tokens=max_tokens, tools=round_tools
        ):
            if event == "delta":
                yield event, payload
            elif event == "tool_calls":
                round_calls = payload["calls"]
                assistant_content = payload.get("content", "")
                total_usage["promptTokens"] += payload.get("promptTokens", 0)
                total_usage["completionTokens"] += payload.get("completionTokens", 0)
            elif event == "done":
                round_usage = payload
            elif event == "error":
                yield event, payload
                return

        if not round_calls:
            total_usage["promptTokens"] += round_usage.get("promptTokens", 0)
            total_usage["completionTokens"] += round_usage.get("completionTokens", 0)
            yield "done", {**total_usage, "status": "COMPLETED"}
            return

        # 回灌: assistant(tool_calls) 消息 + 每个工具的 tool 结果消息
        working.append(
            {
                "role": "assistant",
                "content": assistant_content or None,
                "tool_calls": [
                    {
                        "id": c["id"],
                        "type": "function",
                        "function": {"name": c["name"], "arguments": c["arguments"]},
                    }
                    for c in round_calls
                ],
            }
        )
        for call in round_calls:
            try:
                arguments = json.loads(call["arguments"]) if call["arguments"].strip() else {}
                if not isinstance(arguments, dict):
                    arguments = {}
            except json.JSONDecodeError:
                arguments = {}
            result = await executor(call["name"], arguments)
            logger.info(
                "工具调用: name=%s, args=%s, result_len=%s",
                call["name"],
                call["arguments"][:120],
                len(result),
            )
            yield "tool", {"name": call["name"], "arguments": call["arguments"], "result": result[:300]}
            working.append({"role": "tool", "tool_call_id": call["id"], "content": result})

    # 兜底收敛: 末轮 (无 tools) 上游仍返回 tool_calls 属协议异常, 正常模型不会走到这;
    # 但必须发 done 收尾, 否则前端流悬空等超时
    logger.warning("agent 循环异常退出 (末轮仍请求工具), 强制收尾")
    yield "done", {**total_usage, "status": "COMPLETED"}
