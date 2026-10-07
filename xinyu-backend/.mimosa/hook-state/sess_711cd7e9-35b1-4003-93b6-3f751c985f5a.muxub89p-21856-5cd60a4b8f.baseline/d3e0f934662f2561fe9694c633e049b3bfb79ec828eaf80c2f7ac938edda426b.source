"""RAG 注入模板"""

from app.ai.types import RagChunk


def format_rag_injection(chunks: list[RagChunk]) -> str:
    """检索结果 → 追加到 system prompt 末尾的知识库文本块"""
    if not chunks:
        return ""
    parts = ["[知识库参考材料]"]
    for i, c in enumerate(chunks, 1):
        parts.append(f"【片段{i}】\n{c.text}")
    return "\n\n".join(parts)
