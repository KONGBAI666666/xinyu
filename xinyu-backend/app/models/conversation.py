"""会话实体 — conversation 表"""

from datetime import datetime

from sqlalchemy import BigInteger, DateTime, Index, Integer, String, func
from sqlalchemy.orm import Mapped, mapped_column

from app.core.security import now_local
from app.models.base import Base, id_primary_key


class Conversation(Base):
    __tablename__ = "conversation"
    __table_args__ = (Index("idx_user_last", "user_id", "last_message_at"),)

    id: Mapped[int] = id_primary_key()
    user_id: Mapped[int] = mapped_column(BigInteger, nullable=False)
    character_id: Mapped[int] = mapped_column(BigInteger, nullable=False)
    model_id: Mapped[int | None] = mapped_column(BigInteger, comment="会话级模型覆盖")
    kb_id: Mapped[int | None] = mapped_column(BigInteger, comment="绑定的知识库 (RAG)")
    title: Mapped[str] = mapped_column(String(50), nullable=False)
    pinned: Mapped[int] = mapped_column(
        Integer, nullable=False, default=0, server_default="0", comment="置顶: 0/1 (置顶组内按 last_message_at)"
    )
    last_message_at: Mapped[datetime | None] = mapped_column(DateTime)
    last_message_preview: Mapped[str | None] = mapped_column(String(100))
    created_at: Mapped[datetime] = mapped_column(
        DateTime, nullable=False, default=now_local, server_default=func.now()
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime, nullable=False, default=now_local, onupdate=now_local, server_default=func.now()
    )
    deleted: Mapped[int] = mapped_column(Integer, nullable=False, default=0, server_default="0")
