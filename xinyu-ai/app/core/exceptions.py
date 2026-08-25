"""AI 服务自定义异常"""

from dataclasses import dataclass


@dataclass
class AiError(Exception):
    """AI 服务统一异常, 携带 code + message"""

    code: int = 50000
    message: str = "AI 服务内部错误"

    def __init__(self, message: str = "AI 服务内部错误", code: int = 50000):
        self.code = code
        self.message = message
        super().__init__(self.message)


class LlmConnectError(AiError):
    def __init__(self, message: str = "LLM 连接失败"):
        super().__init__(message, code=51001)
