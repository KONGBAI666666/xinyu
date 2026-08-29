"""用量统计接口 — 对应 Java StatsController"""

from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.deps import get_current_user_id
from app.core.database import get_db
from app.schemas.common import Result
from app.schemas.stats import UsageStatsVO
from app.services import stats_service

router = APIRouter(prefix="/api/stats", tags=["stats"])


@router.get("/usage")
async def usage(
    user_id: int = Depends(get_current_user_id), db: AsyncSession = Depends(get_db)
) -> Result[UsageStatsVO]:
    """当前用户用量统计: 今日 + 累计 调用次数 / token / 估算成本"""
    return Result.ok(await stats_service.get_usage(db, user_id))
