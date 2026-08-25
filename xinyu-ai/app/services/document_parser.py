"""文档解析: PDF / Markdown / TXT → 纯文本"""

from enum import Enum
from io import BytesIO


class FileType(str, Enum):
    PDF = "PDF"
    MARKDOWN = "MARKDOWN"
    TXT = "TXT"


class DocumentParser:
    """解析上传文件为纯文本"""

    @staticmethod
    def parse(file_name: str, file_content: bytes) -> str:
        """根据扩展名自动选择解析器"""
        ext = file_name.lower().rsplit(".", 1)[-1] if "." in file_name else ""
        if ext == "pdf":
            return DocumentParser._parse_pdf(file_content)
        elif ext in ("md", "markdown"):
            return file_content.decode("utf-8", errors="replace")
        elif ext in ("txt", "text", ""):
            return file_content.decode("utf-8", errors="replace")
        else:
            # 未知格式, 尝试当文本读
            return file_content.decode("utf-8", errors="replace")

    @staticmethod
    def _parse_pdf(content: bytes) -> str:
        import fitz  # PyMuPDF
        text_parts = []
        with fitz.open(stream=BytesIO(content), filetype="pdf") as doc:
            for page in doc:
                text_parts.append(page.get_text("text"))
        return "\n\n".join(text_parts).strip()
