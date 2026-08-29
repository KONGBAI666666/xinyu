"""用户数据访问"""

from sqlalchemy import select, update

from app.core.security import now_local
from app.models import User


async def get_by_username(db, username: str) -> User | None:
    result = await db.execute(select(User).where(User.username == username, User.deleted == 0))
    return result.scalar_one_or_none()


async def get_by_id(db, user_id: int) -> User | None:
    result = await db.execute(select(User).where(User.id == user_id, User.deleted == 0))
    return result.scalar_one_or_none()


async def insert(db, user: User) -> User:
    db.add(user)
    await db.flush()
    return user


async def update_password(db, user_id: int, new_password_hash: str) -> None:
    await db.execute(
        update(User).where(User.id == user_id).values(password=new_password_hash, updated_at=now_local())
    )


async def update_last_login(db, user_id: int) -> None:
    await db.execute(
        update(User).where(User.id == user_id).values(last_login_at=now_local())
    )
