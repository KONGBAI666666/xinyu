"""文档解析: PDF / Markdown / TXT → 纯文本"""

from io import BytesIO


class DocumentParser:
    @staticmethod
    def parse(file_name: str, file_content: bytes) -> str:
        """根据扩展名自动选择解析器"""
        ext = file_name.lower().rsplit(".", 1)[-1] if "." in file_name else ""
        if ext == "pdf":
            return DocumentParser._parse_pdf(file_content)
        if ext in ("md", "markdown", "txt", "text"):
            return file_content.decode("utf-8", errors="replace")
        # 未知格式, 尝试当文本读 (扩展名白名单在上层校验)
        return file_content.decode("utf-8", errors="replace")

    @staticmethod
    def _parse_pdf(content: bytes) -> str:
        import fitz  # PyMuPDF

        text_parts = []
        with fitz.open(stream=BytesIO(content), filetype="pdf") as doc:
            for page in doc:
                text_parts.append(page.get_text("text"))
        return "\n\n".join(text_parts).strip()
