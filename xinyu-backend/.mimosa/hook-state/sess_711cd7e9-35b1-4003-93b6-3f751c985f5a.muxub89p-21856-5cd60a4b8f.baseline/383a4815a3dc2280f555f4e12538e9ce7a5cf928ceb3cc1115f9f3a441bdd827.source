"""消息实体 — message 表 (核心表)"""

from datetime import datetime

from sqlalchemy import BigInteger, DateTime, Index, Integer, String, Text, UniqueConstraint, func
from sqlalchemy.orm import Mapped, mapped_column

from app.core.security import now_local
from app.models.base import Base, id_primary_key


class Message(Base):
    __tablename__ = "message"
    __table_args__ = (
        UniqueConstraint("user_id", "client_message_id", name="uk_client_msg"),
        Index("idx_conv", "conversation_id", "id"),
        Index("idx_user_created", "user_id", "created_at"),
    )

    id: Mapped[int] = id_primary_key()
    conversation_id: Mapped[int] = mapped_column(BigInteger, nullable=False)
    user_id: Mapped[int] = mapped_column(BigInteger, nullable=False)
    parent_message_id: Mapped[int | None] = mapped_column(BigInteger)
    sequence_no: Mapped[int] = mapped_column(Integer, nullable=False)
    client_message_id: Mapped[str | None] = mapped_column(String(64))
    message_type: Mapped[str] = mapped_column(String(20), nullable=False, comment="USER/ASSISTANT/SYSTEM")
    content: Mapped[str | None] = mapped_column(Text)
    status: Mapped[str] = mapped_column(
        String(20), nullable=False, default="COMPLETED", server_default="COMPLETED"
    )
    prompt_tokens: Mapped[int | None] = mapped_column(Integer)
    completion_tokens: Mapped[int | None] = mapped_column(Integer)
    model_code: Mapped[str | None] = mapped_column(String(50))
    feedback: Mapped[str] = mapped_column(String(20), nullable=False, default="NONE", server_default="NONE")
    regenerate_count: Mapped[int] = mapped_column(Integer, nullable=False, default=0, server_default="0")
    created_at: Mapped[datetime] = mapped_column(DateTime, nullable=False, default=now_local, server_default=func.now())
    updated_at: Mapped[datetime] = mapped_column(
        DateTime, nullable=False, default=now_local, onupdate=now_local, server_default=func.now()
    )
    deleted: Mapped[int] = mapped_column(Integer, nullable=False, default=0, server_default="0")
