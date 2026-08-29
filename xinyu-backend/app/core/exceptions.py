"""业务异常与统一状态码

对应 Java 侧的 ResultCode / BizException / GlobalExceptionHandler,
code 分段与原系统完全一致 (前端按 code 做角色化文案降级)。
"""

from dataclasses import dataclass
from enum import IntEnum


class ResultCode(IntEnum):
    """全局业务状态码 (与 Java ResultCode 逐项对齐)"""

    SUCCESS = 0

    UNAUTHORIZED = 40100  # 未登录或登录已过期
    FORBIDDEN = 40300  # 无权限访问
    NOT_FOUND = 40400  # 资源不存在
    PARAM_ERROR = 42200  # 请求参数校验失败
    SYSTEM_ERROR = 50000  # 系统内部异常 (兜底)

    LLM_CONNECT_ERROR = 51001  # LLM 服务连接失败
    LLM_TIMEOUT = 51002  # LLM 响应超时
    LLM_TOKEN_LIMIT = 51003  # 上下文 Token 超出模型限制
    LLM_CONTENT_BLOCKED = 51004  # 内容被安全策略拦截


@dataclass
class BizException(Exception):
    """业务异常: 携带 code + message, 全局处理器统一转 Result JSON"""

    code: int = ResultCode.SYSTEM_ERROR
    message: str = "系统异常，请稍后重试"

    def __init__(self, code: int | ResultCode = ResultCode.SYSTEM_ERROR, message: str = "系统异常，请稍后重试"):
        self.code = int(code)
        self.message = message
        super().__init__(message)


@dataclass
class AiError(Exception):
    """AI 能力层异常 (LLM/Embedding/Qdrant), 携带 code + message"""

    code: int = 50000
    message: str = "AI 服务内部错误"

    def __init__(self, message: str = "AI 服务内部错误", code: int = 50000):
        self.code = code
        self.message = message
        super().__init__(message)


class LlmConnectError(AiError):
    def __init__(self, message: str = "LLM 连接失败"):
        super().__init__(message, code=ResultCode.LLM_CONNECT_ERROR)
