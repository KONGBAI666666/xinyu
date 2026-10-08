"""message 会话内序号唯一索引 + user 邮箱唯一索引

- uk_conv_seq (conversation_id, sequence_no): MAX+1 分配在多 worker 并发下可能撞号,
  由数据库唯一索引兜底响亮失败 (单进程内另有 _active_streams 会话级串行)
- uk_email (email): 邮箱将承载找回密码等邮件能力, 先补唯一约束; 可空字段多个 NULL 不冲突

Revision ID: a7f3c2d94e15
Revises: d32e9401e82e
Create Date: 2026-10-08 12:40:00

"""
from typing import Sequence, Union

from alembic import op


# revision identifiers, used by Alembic.
revision: str = 'a7f3c2d94e15'
down_revision: Union[str, None] = 'd32e9401e82e'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.create_index('uk_conv_seq', 'message', ['conversation_id', 'sequence_no'], unique=True)
    op.create_index('uk_email', 'user', ['email'], unique=True)


def downgrade() -> None:
    op.drop_index('uk_email', table_name='user')
    op.drop_index('uk_conv_seq', table_name='message')
