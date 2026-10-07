"""世界书注入测试: 关键词匹配/优先级排序/预算裁剪/停用过滤"""

import unittest

from app.models import LorebookEntry
from app.services.lorebook_service import build_block


def _entry(entry_id: int, keywords: str, content: str, priority: int = 0, enabled: int = 1) -> LorebookEntry:
    return LorebookEntry(
        id=entry_id,
        character_id=1,
        user_id=1,
        keywords=keywords,
        content=content,
        priority=priority,
        enabled=enabled,
    )


class BuildBlockTest(unittest.TestCase):
    def test_keyword_hit_injected(self) -> None:
        block = build_block([_entry(1, "魔法, 法术", "这个世界魔法消耗体力。")], "魔法怎么学？")
        self.assertIn("魔法消耗体力", block)
        self.assertIn("[世界书设定]", block)

    def test_miss_not_injected(self) -> None:
        block = build_block([_entry(1, "魔法", "魔法设定")], "今天天气如何？")
        self.assertEqual(block, "")

    def test_case_insensitive(self) -> None:
        block = build_block([_entry(1, "Magic", "magic 值 7 点。")], "cast MAGIC!")
        self.assertIn("magic 值 7 点", block)

    def test_disabled_skipped(self) -> None:
        block = build_block([_entry(1, "魔法", "设定A", enabled=0)], "魔法怎么学？")
        self.assertEqual(block, "")

    def test_priority_order(self) -> None:
        block = build_block(
            [_entry(1, "魔法", "低优先", priority=0), _entry(2, "魔法", "高优先", priority=10)],
            "魔法",
        )
        self.assertLess(block.index("高优先"), block.index("低优先"))

    def test_budget_skips_oversized_keeps_smaller(self) -> None:
        """预算不足的条目跳过, 更小的后续条目仍能注入"""
        entries = [
            _entry(1, "魔法", "长" * 100, priority=10),
            _entry(2, "魔法", "短设定", priority=0),
        ]
        block = build_block(entries, "魔法", max_chars=50)
        self.assertNotIn("长" * 100, block)
        self.assertIn("短设定", block)

    def test_empty_query_returns_empty(self) -> None:
        self.assertEqual(build_block([_entry(1, "魔法", "设定")], ""), "")

    def test_multi_separator_keywords(self) -> None:
        """中英文逗号/分号/换行都是分隔符"""
        block = build_block([_entry(1, "剑；刀，\n枪", "武器设定")], "选一把剑")
        self.assertIn("武器设定", block)


if __name__ == "__main__":
    unittest.main()
