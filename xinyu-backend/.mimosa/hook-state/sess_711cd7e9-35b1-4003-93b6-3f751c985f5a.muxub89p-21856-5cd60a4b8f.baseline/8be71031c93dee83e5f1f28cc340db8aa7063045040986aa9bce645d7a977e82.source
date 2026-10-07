"""知识库/文档数据访问"""

from sqlalchemy import delete, select, text, update

from app.core.security import now_local
from app.models import KnowledgeBase, KnowledgeDocument


# ---------- 知识库 ----------


async def get_kb_by_id(db, kb_id: int) -> KnowledgeBase | None:
    result = await db.execute(select(KnowledgeBase).where(KnowledgeBase.id == kb_id, KnowledgeBase.deleted == 0))
    return result.scalar_one_or_none()


async def get_owned_kb(db, kb_id: int, user_id: int) -> KnowledgeBase | None:
    kb = await get_kb_by_id(db, kb_id)
    if kb is None or kb.user_id != user_id:
        return None
    return kb


async def insert_kb(db, kb: KnowledgeBase) -> KnowledgeBase:
    db.add(kb)
    await db.flush()
    return kb


async def list_kbs(db, user_id: int) -> list[KnowledgeBase]:
    result = await db.execute(
        select(KnowledgeBase)
        .where(KnowledgeBase.user_id == user_id, KnowledgeBase.deleted == 0)
        .order_by(KnowledgeBase.created_at.desc())
    )
    return list(result.scalars())


async def soft_delete_kb(db, kb_id: int) -> None:
    await db.execute(
        update(KnowledgeBase).where(KnowledgeBase.id == kb_id).values(deleted=1, updated_at=now_local())
    )


async def hard_delete_docs_by_kb(db, kb_id: int) -> None:
    """删除知识库时连同文档元数据一并物理删除 (与原 Java 行为一致)"""
    await db.execute(delete(KnowledgeDocument).where(KnowledgeDocument.kb_id == kb_id))


async def update_kb_counts_on_upload(db, kb_id: int, chunk_count: int) -> None:
    """文档上传成功后原子自增计数"""
    await db.execute(
        update(KnowledgeBase)
        .where(KnowledgeBase.id == kb_id)
        .values(
            doc_count=text("doc_count + 1"),
            chunk_count=text(f"chunk_count + {int(chunk_count)}"),
            updated_at=now_local(),
        )
    )


async def update_kb_counts_on_delete(db, kb_id: int, chunk_count: int) -> None:
    """删除文档后原子递减计数 (GREATEST 防负数)"""
    await db.execute(
        update(KnowledgeBase)
        .where(KnowledgeBase.id == kb_id)
        .values(
            doc_count=text("GREATEST(doc_count - 1, 0)"),
            chunk_count=text(f"GREATEST(chunk_count - {int(chunk_count)}, 0)"),
            updated_at=now_local(),
        )
    )


async def try_lock_embedding(db, kb_id: int, embedding_model: str, embedding_dim: int | None) -> bool:
    """首次上传锁定向量化配置 (条件更新: 并发时先成功者为准)"""
    result = await db.execute(
        update(KnowledgeBase)
        .where(KnowledgeBase.id == kb_id, KnowledgeBase.embedding_model.is_(None))
        .values(embedding_model=embedding_model, embedding_dim=embedding_dim, updated_at=now_local())
    )
    return result.rowcount > 0


# ---------- 文档 ----------


async def get_doc_by_id(db, doc_id: int) -> KnowledgeDocument | None:
    result = await db.execute(
        select(KnowledgeDocument).where(KnowledgeDocument.id == doc_id, KnowledgeDocument.deleted == 0)
    )
    return result.scalar_one_or_none()


async def insert_doc(db, doc: KnowledgeDocument) -> KnowledgeDocument:
    db.add(doc)
    await db.flush()
    return doc


async def list_docs(db, kb_id: int) -> list[KnowledgeDocument]:
    result = await db.execute(
        select(KnowledgeDocument)
        .where(KnowledgeDocument.kb_id == kb_id, KnowledgeDocument.deleted == 0)
        .order_by(KnowledgeDocument.created_at.desc())
    )
    return list(result.scalars())


async def update_doc(db, doc: KnowledgeDocument) -> None:
    await db.execute(
        update(KnowledgeDocument)
        .where(KnowledgeDocument.id == doc.id)
        .values(
            status=doc.status,
            chunk_count=doc.chunk_count,
            error_msg=doc.error_msg,
            updated_at=now_local(),
        )
    )


async def hard_delete_doc(db, doc_id: int) -> None:
    await db.execute(delete(KnowledgeDocument).where(KnowledgeDocument.id == doc_id))
