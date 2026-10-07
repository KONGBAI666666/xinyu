"""管理后台 Schema — user.role=ADMIN 专用接口的出入参"""

from typing import Literal

from pydantic import BaseModel

from app.schemas.common import DateTimeStr


class AdminUserVO(BaseModel):
    id: str
    username: str
    nickname: str
    email: str | None = None
    role: str
    status: str
    lastLoginAt: DateTimeStr | None = None
    createdAt: DateTimeStr


class UserStatusDTO(BaseModel):
    status: Literal["ACTIVE", "BANNED"]


class AdminCharacterVO(BaseModel):
    id: str
    name: str
    intro: str | None = None
    creatorId: str
    creatorType: str
    status: str
    chatCount: int
    favoriteCount: int
    createdAt: DateTimeStr


class CharacterReviewDTO(BaseModel):
    """审核动作: 通过发布 / 下架"""

    status: Literal["PUBLISHED", "OFFLINE"]


class PlatformOverviewVO(BaseModel):
    """平台概览 (v1: 消息/Token 维度; 用户/角色总量指标待仓库层补齐后开放)"""

    messageCount: int
    totalPromptTokens: int
    totalCompletionTokens: int
