"""用量统计 Schema — 对应 Java UsageStatsVO"""

from pydantic import BaseModel

from app.schemas.common import DateStr


class UsageStatsVO(BaseModel):
    date: DateStr
    todayCallCount: int
    todayPromptTokens: int
    todayCompletionTokens: int
    todayCost: float
    totalCallCount: int
    totalPromptTokens: int
    totalCompletionTokens: int
    totalCost: float
