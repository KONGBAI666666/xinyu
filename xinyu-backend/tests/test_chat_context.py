"""重新生成版本语义测试: 上下文组装排除旧版本 + 记忆 importance 白名单

重点回归:
- 同一 parent 的多条 ASSISTANT 是重新生成的不同版本, 上下文只应包含最新一条
- LLM 输出的 importance 脏值必须归一, 否则记忆列表 VO 的 Literal 校验会 500
"""

import unittest

from app.ai.memory.extractor import MemoryExtractor
from app.ai.types import ChatMessage
from app.models import AiCharacter, Message
from app.services.chat_service import _assemble_context


def _make_character() -> AiCharacter:
    return AiCharacter(
        name="屿屿",
        system_prompt="你是屿屿。",
        greeting="你好呀",
    )


def _make_message(
    message_id: int,
    sequence_no: int,
    message_type: str,
    content: str,
    status: str = "COMPLETED",
    parent_message_id: int | None = None,
    regenerate_count: int = 0,
) -> Message:
    return Message(
        id=message_id,
        conversation_id=1,
        sequence_no=sequence_no,
        message_type=message_type,
        content=content,
        status=status,
        parent_message_id=parent_message_id,
        regenerate_count=regenerate_count,
    )


class AssembleContextTest(unittest.TestCase):
    def test_single_version_context(self) -> None:
        """无版本冲突时上下文保持原序"""
        history = [
            _make_message(1, 1, "ASSISTANT", "你好呀"),
            _make_message(2, 2, "USER", "讲个故事"),
            _make_message(3, 3, "ASSISTANT", "从前有座岛"),
        ]
        messages = _assemble_context(_make_character(), "", history)
        self.assertEqual(
            [m.content for m in messages], ["你是屿屿。", "你好呀", "讲个故事", "从前有座岛"]
        )
        self.assertEqual(messages[0].role, "system")

    def test_old_regenerated_versions_excluded(self) -> None:
        """同 parent 的旧版本回复不进上下文, 只保留最新版本"""
        history = [
            _make_message(1, 1, "ASSISTANT", "你好呀"),
            _make_message(2, 2, "USER", "讲个故事"),
            _make_message(3, 3, "ASSISTANT", "版本1", parent_message_id=2),
            _make_message(4, 4, "ASSISTANT", "版本2", parent_message_id=2, regenerate_count=1),
        ]
        messages = _assemble_context(_make_character(), "", history)
        self.assertEqual(
            [m.content for m in messages], ["你是屿屿。", "你好呀", "讲个故事", "版本2"]
        )

    def test_generating_and_failed_excluded(self) -> None:
        """GENERATING 占位与 FAILED 消息不进上下文"""
        history = [
            _make_message(1, 1, "USER", "在吗"),
            _make_message(2, 2, "ASSISTANT", "", status="GENERATING", parent_message_id=1),
            _make_message(3, 3, "USER", "讲个故事"),
            _make_message(4, 4, "ASSISTANT", "", status="FAILED", parent_message_id=3),
        ]
        messages = _assemble_context(_make_character(), "", history)
        self.assertEqual([m.content for m in messages], ["你是屿屿。", "在吗", "讲个故事"])

    def test_memory_block_appended_to_system(self) -> None:
        """记忆块拼接到 system prompt 末尾"""
        history = [_make_message(1, 1, "USER", "我叫小明")]
        messages = _assemble_context(_make_character(), "\n[记忆] 用户叫小明", history)
        self.assertEqual(messages[0].content, "你是屿屿。\n[记忆] 用户叫小明")
        self.assertIsInstance(messages[0], ChatMessage)


class ImportanceWhitelistTest(unittest.TestCase):
    def test_valid_importance_kept(self) -> None:
        result = MemoryExtractor._parse_response(
            '[{"memory_key": "name", "content": "用户叫小明", "importance": "HIGH"}]'
        )
        self.assertEqual(result[0].importance, "HIGH")

    def test_dirty_importance_normalized(self) -> None:
        """LLM 输出 'high'/'CRITICAL' 等脏值归一为 MEDIUM, 不炸 Literal"""
        result = MemoryExtractor._parse_response(
            '[{"content": "a", "importance": "high"},'
            '{"content": "b", "importance": "CRITICAL"},'
            '{"content": "c"}]'
        )
        self.assertEqual([m.importance for m in result], ["MEDIUM", "MEDIUM", "MEDIUM"])

    def test_non_dict_items_skipped(self) -> None:
        result = MemoryExtractor._parse_response('["oops", {"content": "ok"}]')
        self.assertEqual(len(result), 1)
        self.assertEqual(result[0].content, "ok")


if __name__ == "__main__":
    unittest.main()
