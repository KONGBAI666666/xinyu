"""Memory 提取 — LLM 调用提取长期记忆 (原 xinyu-ai memory_extractor)"""

from pydantic import BaseModel

from app.ai.llm.client import LlmClient
from app.ai.memory.prompts import EXTRACT_PROMPT
from app.ai.types import ChatMessage, ModelConfig
from app.core.exceptions import AiError


class ExtractedMemory(BaseModel):
    memoryKey: str | None = None
    content: str
    importance: str = "MEDIUM"  # HIGH / MEDIUM / LOW


class MemoryExtractResult(BaseModel):
    memories: list[ExtractedMemory] = []


class MemoryExtractor:
    @staticmethod
    async def extract(model_config: ModelConfig, dialog: str) -> MemoryExtractResult:
        """调用 LLM 从对话文本中提取结构化记忆"""
        client = LlmClient(model_config)
        try:
            messages = [
                ChatMessage(role="system", content=EXTRACT_PROMPT),
                ChatMessage(role="user", content=dialog),
            ]
            content, _ = await client.chat(messages, temperature=0.3, max_tokens=1024)
            return MemoryExtractResult(memories=MemoryExtractor._parse_response(content))
        except AiError:
            raise
        except Exception as e:
            raise AiError(f"记忆提取失败: {e}", code=50000) from e
        finally:
            await client.aclose()

    @staticmethod
    def _parse_response(content: str) -> list[ExtractedMemory]:
        """解析 LLM 返回的 JSON 数组 (容忍代码块包裹与前后多余文字)"""

        # 去除可能的 markdown 代码块包裹
        text = content.strip()
        if text.startswith("```"):
            lines = text.split("\n")
            lines = [ln for ln in lines if not ln.startswith("```")]
            text = "\n".join(lines).strip()

        data = MemoryExtractor._try_load_array(text)
        if data is None:
            # 兜底: 模型可能在 JSON 前后输出多余文字, 截取首个 [ ... ] 片段再试
            start, end = text.find("["), text.rfind("]")
            if 0 <= start < end:
                data = MemoryExtractor._try_load_array(text[start : end + 1])
        if not isinstance(data, list):
            return []
        result = []
        for item in data:
            if not isinstance(item, dict):
                continue
            item_content = (item.get("content") or "").strip()
            if not item_content:
                continue
            importance = item.get("importance") or "MEDIUM"
            # LLM 输出不校验直接落库, 会让列表接口的 Literal["HIGH","MEDIUM","LOW"] 反序列化 500
            if importance not in ("HIGH", "MEDIUM", "LOW"):
                importance = "MEDIUM"
            result.append(
                ExtractedMemory(
                    memoryKey=item.get("memory_key") or item.get("memoryKey"),
                    content=item_content,
                    importance=importance,
                )
            )
        return result

    @staticmethod
    def _try_load_array(text: str):
        import json

        try:
            return json.loads(text)
        except (json.JSONDecodeError, TypeError):
            return None
