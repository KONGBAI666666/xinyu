"""conversation 置顶字段 (M5 体验完善)

Revision ID: b5e8f7a21c04
Revises: a7f3c2d94e15
Create Date: 2026-10-08 17:05:00

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = 'b5e8f7a21c04'
down_revision: Union[str, None] = 'a7f3c2d94e15'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.add_column(
        'conversation',
        sa.Column('pinned', sa.Integer(), server_default='0', nullable=False, comment='置顶: 0/1 (置顶组内按 last_message_at)'),
    )


def downgrade() -> None:
    op.drop_column('conversation', 'pinned')
