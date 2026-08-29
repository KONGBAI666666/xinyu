"""异步数据库引擎与会话管理

SQLAlchemy 2.x async + aiomysql, 对应 Java 侧的 Spring DataSource / MyBatis-Plus。
逻辑删除 (deleted=0) 由各 Repository 显式过滤 (对应 MP 的 logic-delete 全局配置)。
"""

from collections.abc import AsyncIterator

from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine

from app.core.config import settings

engine = create_async_engine(
    settings.database_url,
    pool_pre_ping=True,
    pool_size=10,
    max_overflow=20,
    pool_recycle=3600,
)

SessionFactory = async_sessionmaker(engine, expire_on_commit=False)


async def get_db() -> AsyncIterator[AsyncSession]:
    """FastAPI 依赖: 每请求一个会话, 结束自动归还连接池"""
    async with SessionFactory() as session:
        yield session
