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
            raise AiError(f"记忆提取失败: {e}", code=50000)
        finally:
            await client.aclose()

    @staticmethod
    def _parse_response(content: str) -> list[ExtractedMemory]:
        """解析 LLM 返回的 JSON 数组"""
        import json

        # 去除可能的 markdown 代码块包裹
        text = content.strip()
        if text.startswith("```"):
            lines = text.split("\n")
            lines = [l for l in lines if not l.startswith("```")]
            text = "\n".join(lines).strip()
        try:
            data = json.loads(text)
            if not isinstance(data, list):
                return []
            result = []
            for item in data:
                if not isinstance(item, dict):
                    continue
                item_content = (item.get("content") or "").strip()
                if not item_content:
                    continue
                result.append(
                    ExtractedMemory(
                        memoryKey=item.get("memory_key"),
                        content=item_content,
                        importance=item.get("importance", "MEDIUM"),
                    )
                )
            return result
        except (json.JSONDecodeError, TypeError):
            return []
