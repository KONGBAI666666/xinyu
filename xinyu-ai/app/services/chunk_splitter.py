"""文本分块: 段落感知 + 硬切兜底"""

from app.config import settings


class ChunkSplitter:

    def __init__(self):
        self.chunk_size = settings.chunk_size
        self.chunk_overlap = settings.chunk_overlap

    def split(self, text: str) -> list[str]:
        if not text or not text.strip():
            return []
        # 优先按段落分
        paragraphs = text.split("\n\n")
        chunks: list[str] = []
        buf = ""
        for para in paragraphs:
            para = para.strip()
            if not para:
                continue
            if len(buf) + len(para) + 2 <= self.chunk_size:
                buf = f"{buf}\n\n{para}".strip()
            else:
                if buf:
                    chunks.append(buf)
                if len(para) <= self.chunk_size:
                    buf = para
                else:
                    # 段落超长, 硬切
                    buf = ""
                    self._hard_split(para, chunks)
        if buf:
            chunks.append(buf)
        return chunks

    def _hard_split(self, text: str, chunks: list[str]) -> None:
        start = 0
        while start < len(text):
            end = min(start + self.chunk_size, len(text))
            chunk = text[start:end]
            if chunk.strip():
                chunks.append(chunk.strip())
            start += self.chunk_size - self.chunk_overlap
