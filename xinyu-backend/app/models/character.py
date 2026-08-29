"""角色实体 — character / character_favorite 表"""

from datetime import datetime
from decimal import Decimal

from sqlalchemy import BigInteger, DateTime, Integer, Numeric, String, Text, UniqueConstraint, func
from sqlalchemy.orm import Mapped, mapped_column

from app.core.security import now_local
from app.models.base import Base, id_primary_key


class AiCharacter(Base):
    __tablename__ = "character"

    id: Mapped[int] = id_primary_key()
    name: Mapped[str] = mapped_column(String(32), nullable=False)
    avatar_url: Mapped[str | None] = mapped_column(String(255))
    intro: Mapped[str] = mapped_column(String(200), nullable=False, comment="一句话介绍")
    system_prompt: Mapped[str] = mapped_column(Text, nullable=False)
    greeting: Mapped[str] = mapped_column(String(500), nullable=False)
    temperature: Mapped[Decimal] = mapped_column(Numeric(3, 2), nullable=False, default=Decimal("0.80"))
    max_tokens: Mapped[int] = mapped_column(Integer, nullable=False, default=1024)
    model_id: Mapped[int | None] = mapped_column(BigInteger)
    creator_id: Mapped[int] = mapped_column(BigInteger, nullable=False)
    creator_type: Mapped[str] = mapped_column(String(20), nullable=False, default="USER", server_default="USER")
    status: Mapped[str] = mapped_column(String(20), nullable=False, default="DRAFT", server_default="DRAFT")
    chat_count: Mapped[int] = mapped_column(Integer, nullable=False, default=0, server_default="0")
    favorite_count: Mapped[int] = mapped_column(Integer, nullable=False, default=0, server_default="0")
    created_at: Mapped[datetime] = mapped_column(DateTime, nullable=False, default=now_local, server_default=func.now())
    updated_at: Mapped[datetime] = mapped_column(
        DateTime, nullable=False, default=now_local, onupdate=now_local, server_default=func.now()
    )
    deleted: Mapped[int] = mapped_column(Integer, nullable=False, default=0, server_default="0")


class CharacterFavorite(Base):
    __tablename__ = "character_favorite"
    __table_args__ = (UniqueConstraint("user_id", "character_id", name="uk_user_char"),)

    id: Mapped[int] = id_primary_key()
    user_id: Mapped[int] = mapped_column(BigInteger, nullable=False, index=True)
    character_id: Mapped[int] = mapped_column(BigInteger, nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime, nullable=False, default=now_local, server_default=func.now())
