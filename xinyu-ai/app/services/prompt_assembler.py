"""Prompt 编排: 组装 system prompt + memory + RAG + history"""

from app.models import ChatMessage


class PromptAssembler:
    """组装发送给 LLM 的消息序列"""

    MAX_CONTEXT_MESSAGES = 20

    @staticmethod
    def assemble(
        system_prompt: str,
        history: list[ChatMessage],
        memory_block: str = "",
        rag_block: str = "",
    ) -> list[ChatMessage]:
        """组装完整 context

        结构: [system: systemPrompt + memory + RAG] + [user/assistant: history]
        """
        # 组装 system message
        system_parts = [system_prompt]
        if memory_block:
            system_parts.append(memory_block)
        if rag_block:
            system_parts.append(rag_block)
        system_content = "\n\n".join(system_parts)

        messages = [ChatMessage(role="system", content=system_content)]

        # 截取最近的历史消息
        recent = history[-PromptAssembler.MAX_CONTEXT_MESSAGES:]
        messages.extend(recent)

        return messages
