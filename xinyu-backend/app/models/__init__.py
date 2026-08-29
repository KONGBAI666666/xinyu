"""SQLAlchemy 实体汇总导出

对应 Java 侧各模块 entity 包: 表结构与 scripts/schema.sql 逐字段对齐,
不建外键 (与原设计一致), 逻辑删除字段 deleted 由查询层显式过滤。
"""

from app.models.user import User
from app.models.character import AiCharacter, CharacterFavorite
from app.models.conversation import Conversation
from app.models.message import Message
from app.models.memory import Memory
from app.models.ai_model import AiModel
from app.models.knowledge import KnowledgeBase, KnowledgeDocument

__all__ = [
    "User",
    "AiCharacter",
    "CharacterFavorite",
    "Conversation",
    "Message",
    "Memory",
    "AiModel",
    "KnowledgeBase",
    "KnowledgeDocument",
]
