"""SQLAlchemy 声明基类与主键约定

主键统一使用雪花 ID (对应 MyBatis-Plus id-type: assign_id),
created_at/updated_at 由应用层填充 (对应 MybatisMetaObjectHandler)。
"""

from sqlalchemy import BigInteger
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column

from app.core.config import settings
from app.core.snowflake import Snowflake

_snowflake = Snowflake(worker_id=settings.worker_id)


class Base(DeclarativeBase):
    pass


def id_primary_key() -> Mapped[int]:
    return mapped_column(BigInteger, primary_key=True, default=_snowflake.next_id)


def snowflake_id() -> int:
    """生成一个雪花 ID (供无 ORM 场景使用)"""
    return _snowflake.next_id()
