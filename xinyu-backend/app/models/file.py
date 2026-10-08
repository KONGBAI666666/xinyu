"""文件实体 — file 表 (M5 预留启用: 头像上传)"""

from datetime import datetime

from sqlalchemy import BigInteger, DateTime, Integer, String, func
from sqlalchemy.orm import Mapped, mapped_column

from app.core.security import now_local
from app.models.base import Base, id_primary_key


class File(Base):
    __tablename__ = "file"

    id: Mapped[int] = id_primary_key()
    user_id: Mapped[int] = mapped_column(BigInteger, nullable=False, comment="上传者")
    original_name: Mapped[str] = mapped_column(String(255), nullable=False, comment="原始文件名")
    storage_path: Mapped[str] = mapped_column(String(255), nullable=False, comment="存储路径")
    url: Mapped[str] = mapped_column(String(255), nullable=False, comment="访问地址")
    file_type: Mapped[str | None] = mapped_column(String(50), comment="MIME类型")
    file_size: Mapped[int | None] = mapped_column(BigInteger, comment="字节数")
    biz_type: Mapped[str] = mapped_column(String(30), nullable=False, comment="USER_AVATAR/CHARACTER_AVATAR/ATTACHMENT")
    created_at: Mapped[datetime] = mapped_column(
        DateTime, nullable=False, default=now_local, server_default=func.now()
    )
    deleted: Mapped[int] = mapped_column(Integer, nullable=False, default=0, server_default="0")
