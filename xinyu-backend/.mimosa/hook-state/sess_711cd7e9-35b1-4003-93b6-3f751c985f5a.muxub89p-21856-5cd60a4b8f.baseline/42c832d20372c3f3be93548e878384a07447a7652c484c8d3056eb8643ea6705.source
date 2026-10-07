"""通用 Schema: 统一响应包装 + 时间/ID 序列化约定

- Result{code,message,data}: HTTP 恒 200, 业务状态看 code (与 Java Result 一致)
- 雪花 ID 序列化为字符串 (JS Number 精度上限 2^53)
- datetime 序列化为 "yyyy-MM-dd HH:mm:ss" (与 Java Jackson 配置一致)
"""

from datetime import date, datetime
from typing import Generic, TypeVar

from pydantic import BaseModel, ConfigDict, PlainSerializer
from typing_extensions import Annotated

T = TypeVar("T")

DateTimeStr = Annotated[
    datetime,
    PlainSerializer(lambda v: v.strftime("%Y-%m-%d %H:%M:%S"), return_type=str, when_used="always"),
]
DateStr = Annotated[
    date,
    PlainSerializer(lambda v: v.strftime("%Y-%m-%d"), return_type=str, when_used="always"),
]


class Result(BaseModel, Generic[T]):
    model_config = ConfigDict(serialize_by_alias=True)

    code: int = 0
    message: str = "ok"
    data: T | None = None

    @classmethod
    def ok(cls, data: T | None = None) -> "Result[T]":
        return cls(code=0, message="ok", data=data)

    @classmethod
    def fail(cls, code: int, message: str) -> "Result":
        return cls(code=code, message=message, data=None)
