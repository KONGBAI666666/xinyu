"""Memory 提取服务 — LLM 调用提取长期记忆"""

import json
from app.models import ModelConfig, ChatMessage, MemoryExtractResponse, ExtractedMemory
from app.services.llm_client import LlmClient
from app.core.exceptions import AiError

EXTRACT_PROMPT = """你是一个记忆提取助手。请从以下对话中提取关于用户的长期记忆信息。

输出要求:
1. 只提取关于用户的事实信息（如姓名、职业、位置、偏好、性格等）
2. 忽略 AI 的回复内容
3. 输出 JSON 数组格式: [{"memory_key": "name/job/location/hobby/preference/personality/relationship/goal/fact", "content": "记忆内容", "importance": "HIGH/MEDIUM/LOW"}]
4. 如果没有值得提取的记忆, 输出空数组 []
5. importance 判断标准: HIGH=核心个人信息, MEDIUM=偏好和习惯, LOW=一般事实

对话内容:
"""


class MemoryExtractor:

    @staticmethod
    async def extract(
        model_config: ModelConfig, dialog: str
    ) -> MemoryExtractResponse:
        """调用 LLM 提取记忆, 返回结构化结果"""
        try:
            client = LlmClient(model_config)
            messages = [
                ChatMessage(role="system", content=EXTRACT_PROMPT),
                ChatMessage(role="user", content=dialog),
            ]
            content, _ = await client.chat(messages, temperature=0.3, max_tokens=1024)
            memories = MemoryExtractor._parse_response(content)
            return MemoryExtractResponse(memories=memories)
        except AiError:
            raise
        except Exception as e:
            raise AiError(f"记忆提取失败: {e}", code=50000)

    @staticmethod
    def _parse_response(content: str) -> list[ExtractedMemory]:
        """解析 LLM 返回的 JSON 数组"""
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
                content = item.get("content", "").strip()
                if not content:
                    continue
                result.append(ExtractedMemory(
                    memoryKey=item.get("memory_key"),
                    content=content,
                    importance=item.get("importance", "MEDIUM"),
                ))
            return result
        except (json.JSONDecodeError, TypeError):
            return []
