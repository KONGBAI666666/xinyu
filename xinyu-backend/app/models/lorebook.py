"""世界书条目实体 — lorebook_entry 表 (角色绑定的关键词触发设定)"""

from datetime import datetime

from sqlalchemy import BigInteger, DateTime, Index, Integer, String, Text, func
from sqlalchemy.orm import Mapped, mapped_column

from app.core.security import now_local
from app.models.base import Base, id_primary_key


class LorebookEntry(Base):
    __tablename__ = "lorebook_entry"
    __table_args__ = (Index("idx_character", "character_id", "enabled"),)

    id: Mapped[int] = id_primary_key()
    character_id: Mapped[int] = mapped_column(BigInteger, nullable=False)
    user_id: Mapped[int] = mapped_column(BigInteger, nullable=False, comment="冗余: 创建者(权限校验免join)")
    # 触发关键词, 逗号分隔 (中英文逗号均可), 命中任一即注入
    keywords: Mapped[str] = mapped_column(String(500), nullable=False)
    content: Mapped[str] = mapped_column(Text, nullable=False)
    # 注入优先级: 大者先注入 (预算不足时低优先级先被裁掉)
    priority: Mapped[int] = mapped_column(Integer, nullable=False, default=0, server_default="0")
    enabled: Mapped[int] = mapped_column(Integer, nullable=False, default=1, server_default="1")
    created_at: Mapped[datetime] = mapped_column(DateTime, nullable=False, default=now_local, server_default=func.now())
    updated_at: Mapped[datetime] = mapped_column(
        DateTime, nullable=False, default=now_local, onupdate=now_local, server_default=func.now()
    )
    deleted: Mapped[int] = mapped_column(Integer, nullable=False, default=0, server_default="0")
