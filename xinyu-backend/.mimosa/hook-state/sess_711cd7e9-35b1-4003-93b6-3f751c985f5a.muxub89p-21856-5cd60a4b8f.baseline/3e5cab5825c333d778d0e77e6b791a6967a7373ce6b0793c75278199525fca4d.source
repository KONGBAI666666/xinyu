"""RAG 文本分块器测试: 段落感知合并 + 超长段落硬切兜底"""

import unittest

from app.ai.rag.chunk_splitter import ChunkSplitter


def make_splitter(chunk_size: int = 100, overlap: int = 10) -> ChunkSplitter:
    """固定参数的分块器 (与全局 settings 解耦, 便于用小文本断言)"""
    s = ChunkSplitter()
    s.chunk_size = chunk_size
    s.chunk_overlap = overlap
    return s


class ChunkSplitterTest(unittest.TestCase):
    def test_empty_text(self):
        """空文本 / 纯空白返回空列表"""
        self.assertEqual(make_splitter().split(""), [])
        self.assertEqual(make_splitter().split("   \n\n  \n"), [])

    def test_single_small_paragraph(self):
        """单个短段落 → 单个分块, 无多余空白"""
        chunks = make_splitter().split("你好，心屿。")
        self.assertEqual(chunks, ["你好，心屿。"])

    def test_paragraphs_merged(self):
        """多个短段落合并进同一块 (总长不超过 chunk_size)"""
        paras = ["第一段。", "第二段。", "第三段。"]
        chunks = make_splitter(chunk_size=30).split("\n\n".join(paras))
        self.assertEqual(len(chunks), 1)
        self.assertIn("第一段", chunks[0])
        self.assertIn("第三段", chunks[0])

    def test_paragraph_boundary_split(self):
        """累计超限时在段落边界切开, 而不是把段落切碎"""
        a = "A" * 50
        b = "B" * 50
        chunks = make_splitter(chunk_size=60).split(f"{a}\n\n{b}")
        self.assertEqual(chunks, [a, b])

    def test_oversized_paragraph_hard_split(self):
        """超长段落硬切: 每块不超过 chunk_size, 且保留 overlap 重叠"""
        para = "字" * 250
        chunks = make_splitter(chunk_size=100, overlap=10).split(para)
        self.assertGreater(len(chunks), 1)
        for c in chunks:
            self.assertLessEqual(len(c), 100, "任何分块不得超过 chunk_size")
        # 相邻块之间应有 overlap 的上下文重叠 (第 i 块结尾 == 第 i+1 块开头)
        for i in range(len(chunks) - 1):
            self.assertEqual(chunks[i][-10:], chunks[i + 1][:10], "相邻块应保留 overlap 重叠")
        # 硬切拼回 (去除重叠) 应还原原文
        self.assertEqual(chunks[0] + "".join(c[10:] for c in chunks[1:]), para)

    def test_mixed_paragraphs(self):
        """短段 + 超长段混合: 短段正常合并, 超长段独立硬切"""
        short = "短段落"
        long_para = "长" * 150
        text = f"{short}\n\n{long_para}\n\n{short}"
        chunks = make_splitter(chunk_size=100, overlap=10).split(text)
        self.assertGreaterEqual(len(chunks), 3)
        for c in chunks:
            self.assertLessEqual(len(c), 100)

    def test_full_text_coverage(self):
        """所有分块拼接后覆盖原文全部非空白内容 (信息不丢失)"""
        text = "\n\n".join(f"第{i}段，内容填充。" + "细" * 60 for i in range(10))
        chunks = make_splitter(chunk_size=80, overlap=8).split(text)
        self.assertGreater(len(chunks), 1)
        joined = "".join(chunks)
        for i in range(10):
            self.assertIn(f"第{i}段", joined, "每个段落的信息都必须保留")


if __name__ == "__main__":
    unittest.main()
