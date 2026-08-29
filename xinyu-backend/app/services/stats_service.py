"""用量统计服务 — 对应 Java StatsService

当前用户视角: 仅统计本人调用量（数据按用户隔离的延续）。
两次聚合查询（今日 + 累计）+ 成本估算。时区固定 Asia/Shanghai,
与全项目统一时间基准一致, 避免"今日"边界漂移。
"""

from datetime import datetime
from decimal import Decimal
from zoneinfo import ZoneInfo

from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.repositories import message_repo
from app.schemas.stats import UsageStatsVO

STATS_ZONE = ZoneInfo("Asia/Shanghai")


def _estimate_cost(prompt_tokens: int | Decimal, completion_tokens: int | Decimal) -> float:
    """成本 = promptTokens/1000 * 输入单价 + completionTokens/1000 * 输出单价

    MySQL SUM() 经 aiomysql 返回 Decimal, 统一转 float 再参与运算。
    """
    input_cost = float(prompt_tokens) / 1000.0 * settings.llm_input_price_per_1k
    output_cost = float(completion_tokens) / 1000.0 * settings.llm_output_price_per_1k
    # 保留 4 位小数, 避免前端展示一长串浮点尾数
    return round(input_cost + output_cost, 4)


async def get_usage(db: AsyncSession, user_id: int) -> UsageStatsVO:
    """获取当前登录用户的用量统计（今日 + 累计 + 估算成本）"""
    today = datetime.now(STATS_ZONE).date()
    since_midnight = datetime.now(STATS_ZONE).replace(hour=0, minute=0, second=0, microsecond=0)

    today_summary = await message_repo.summarize_usage(db, user_id, since_midnight)
    total_summary = await message_repo.summarize_usage(db, user_id, None)

    return UsageStatsVO(
        date=today,
        todayCallCount=today_summary.call_count,
        todayPromptTokens=today_summary.prompt_tokens,
        todayCompletionTokens=today_summary.completion_tokens,
        todayCost=_estimate_cost(today_summary.prompt_tokens, today_summary.completion_tokens),
        totalCallCount=total_summary.call_count,
        totalPromptTokens=total_summary.prompt_tokens,
        totalCompletionTokens=total_summary.completion_tokens,
        totalCost=_estimate_cost(total_summary.prompt_tokens, total_summary.completion_tokens),
    )
