"""知识库服务 — 对应 Java KnowledgeBaseService

重构后 AI 能力 (解析/分块/向量化/Qdrant) 直接进程内调用 RagService,
不再经过 HTTP 边界; 本模块只负责 MySQL 元数据管理和权限校验。

注意: upload_document 刻意拆成多个独立提交 —— 向量化可能耗时数分钟,
长事务会占住数据库连接; 且失败路径需要把 ERROR 文档记录落库展示失败原因。
"""

import logging

from sqlalchemy.ext.asyncio import AsyncSession

from app.ai.rag.rag_service import get_rag_service
from app.ai.types import ModelConfig
from app.core.exceptions import BizException, ResultCode
from app.models import KnowledgeBase, KnowledgeDocument
from app.repositories import knowledge_repo
from app.schemas.knowledge import KnowledgeBaseCreateDTO, KnowledgeBaseVO, KnowledgeDocumentVO
from app.services import model_service

logger = logging.getLogger("xinyu.rag")

# 允许入库的文档扩展名 (与 DocumentParser 支持的格式一致)
ALLOWED_DOC_EXTENSIONS = {"PDF", "MD", "MARKDOWN", "TXT", "TEXT"}


def _kb_to_vo(kb: KnowledgeBase, documents: list[KnowledgeDocument] | None = None) -> KnowledgeBaseVO:
    return KnowledgeBaseVO(
        id=str(kb.id),
        userId=str(kb.user_id),
        name=kb.name,
        description=kb.description,
        docCount=kb.doc_count,
        chunkCount=kb.chunk_count,
        status=kb.status,
        createdAt=kb.created_at,
        updatedAt=kb.updated_at,
        documents=[_doc_to_vo(d) for d in documents] if documents is not None else None,
    )


def _doc_to_vo(doc: KnowledgeDocument) -> KnowledgeDocumentVO:
    return KnowledgeDocumentVO(
        id=str(doc.id),
        kbId=str(doc.kb_id),
        userId=str(doc.user_id),
        fileName=doc.file_name,
        fileType=doc.file_type,
        fileSize=doc.file_size,
        chunkCount=doc.chunk_count,
        status=doc.status,
        errorMsg=doc.error_msg,
        createdAt=doc.created_at,
        updatedAt=doc.updated_at,
    )


async def _get_owned_kb(db: AsyncSession, kb_id: int, user_id: int) -> KnowledgeBase:
    kb = await knowledge_repo.get_owned_kb(db, kb_id, user_id)
    if kb is None:
        raise BizException(ResultCode.NOT_FOUND, "知识库不存在或无权限")
    return kb


async def list_by_user(db: AsyncSession, user_id: int) -> list[KnowledgeBaseVO]:
    """列出当前用户的知识库"""
    kbs = await knowledge_repo.list_kbs(db, user_id)
    return [_kb_to_vo(kb) for kb in kbs]


async def get_detail(db: AsyncSession, kb_id: int, user_id: int) -> KnowledgeBaseVO:
    """获取知识库详情 (含文档列表)"""
    kb = await _get_owned_kb(db, kb_id, user_id)
    docs = await knowledge_repo.list_docs(db, kb_id)
    return _kb_to_vo(kb, docs)


async def create(db: AsyncSession, req: KnowledgeBaseCreateDTO, user_id: int) -> KnowledgeBaseVO:
    """创建知识库"""
    kb = KnowledgeBase(
        user_id=user_id,
        name=req.name,
        description=req.description,
        doc_count=0,
        chunk_count=0,
        status="ACTIVE",
    )
    await knowledge_repo.insert_kb(db, kb)
    await db.commit()
    logger.info("知识库创建: id=%s, userId=%s, name=%s", kb.id, user_id, kb.name)
    return _kb_to_vo(kb)


async def delete(db: AsyncSession, kb_id: int, user_id: int) -> None:
    """删除知识库 (Qdrant 向量 + 所有文档元数据 + 知识库)"""
    await _get_owned_kb(db, kb_id, user_id)
    # 1. 删 Qdrant 向量
    await get_rag_service().delete_kb(str(kb_id))
    # 2. 删文档元数据 (物理删除, 与原 Java 行为一致)
    await knowledge_repo.hard_delete_docs_by_kb(db, kb_id)
    # 3. 删知识库
    await knowledge_repo.soft_delete_kb(db, kb_id)
    await db.commit()
    logger.info("知识库删除: id=%s, userId=%s", kb_id, user_id)


async def upload_document(
    db: AsyncSession,
    kb_id: int,
    user_id: int,
    file_name: str,
    file_size: int,
    file_content: bytes,
) -> KnowledgeDocumentVO:
    """上传文档到知识库: 解析 → 分块 → 向量化 → 入 Qdrant

    多阶段独立提交, 失败路径的 ERROR 文档记录也能落库展示原因。
    """
    kb = await _get_owned_kb(db, kb_id, user_id)
    if not file_content:
        raise BizException(ResultCode.PARAM_ERROR, "文件为空")

    ext = file_name.rsplit(".", 1)[-1].upper() if "." in file_name else "UNKNOWN"
    # 仅允许解析器支持的格式入库, 避免未知文件当乱码文本进库 (前端 accept 不可靠)
    if ext not in ALLOWED_DOC_EXTENSIONS:
        raise BizException(ResultCode.PARAM_ERROR, "仅支持 PDF / Markdown / TXT 格式文件")

    # 1. 创建文档元数据 (PROCESSING) 并立即提交
    doc = KnowledgeDocument(
        kb_id=kb_id,
        user_id=user_id,
        file_name=file_name,
        file_type=ext,
        file_size=file_size,
        chunk_count=0,
        status="PROCESSING",
    )
    await knowledge_repo.insert_doc(db, doc)
    await db.commit()

    try:
        # 2. 解析模型配置 (用于 Embedding; 复用用户默认模型的 API Key)
        model_config = await _resolve_embedding_model_config(db, user_id)

        # 3. 进程内处理文档 (解析 → 分块 → 向量化 → 入 Qdrant)
        #    传入知识库已锁定的向量化配置, 保证同库内向量维度一致
        result = await get_rag_service().process_document(
            kb_id=str(kb_id),
            doc_id=str(doc.id),
            file_name=file_name,
            file_content=file_content,
            model_config=model_config,
            kb_embedding_model=kb.embedding_model,
            kb_embedding_dim=kb.embedding_dim,
        )

        if result.status == "READY" and result.chunk_count > 0:
            # 竞态守卫: 向量化期间文档被并发删除时, 清理刚写入的向量防孤儿数据
            if await knowledge_repo.get_doc_by_id(db, doc.id) is None:
                await get_rag_service().delete_doc(str(kb_id), str(doc.id))
                raise BizException(ResultCode.PARAM_ERROR, "文档已在处理期间被删除")
            doc.chunk_count = result.chunk_count
            doc.status = "READY"
            await knowledge_repo.update_doc(db, doc)

            # 计数原子自增 (读-改-写会在并发上传时丢更新)
            await knowledge_repo.update_kb_counts_on_upload(db, kb_id, result.chunk_count)
            # 首次上传锁定向量化配置 (条件更新: 并发时先成功者为准)
            if result.embedding_model:
                await knowledge_repo.try_lock_embedding(db, kb_id, result.embedding_model, result.embedding_dim)
            await db.commit()

            logger.info("文档上传成功: docId=%s, kbId=%s, 块数=%s", doc.id, kb_id, result.chunk_count)
        else:
            doc.status = "ERROR"
            doc.error_msg = (result.error_msg or "处理失败")[:500]
            await knowledge_repo.update_doc(db, doc)
            await db.commit()
            raise BizException(ResultCode.SYSTEM_ERROR, f"文档处理失败: {result.error_msg or '未知错误'}")
        return _doc_to_vo(doc)
    except BizException:
        raise
    except Exception as e:
        doc.status = "ERROR"
        doc.error_msg = (str(e) or "未知错误")[:500]
        await knowledge_repo.update_doc(db, doc)
        await db.commit()
        logger.exception("文档上传失败: docId=%s, kbId=%s", doc.id, kb_id)
        raise BizException(ResultCode.SYSTEM_ERROR, f"文档处理失败: {e}") from e


async def delete_document(db: AsyncSession, kb_id: int, doc_id: int, user_id: int) -> None:
    """删除文档 (Qdrant 向量 + 元数据 + 知识库计数递减)"""
    await _get_owned_kb(db, kb_id, user_id)
    doc = await knowledge_repo.get_doc_by_id(db, doc_id)
    if doc is None or doc.kb_id != kb_id:
        raise BizException(ResultCode.NOT_FOUND, "文档不存在")
    # 1. 删 Qdrant 向量
    await get_rag_service().delete_doc(str(kb_id), str(doc_id))
    # 2. 原子更新知识库计数 (GREATEST 防负数)
    await knowledge_repo.update_kb_counts_on_delete(db, kb_id, doc.chunk_count or 0)
    # 3. 删文档元数据
    await knowledge_repo.hard_delete_doc(db, doc_id)
    await db.commit()
    logger.info("文档删除: docId=%s, kbId=%s", doc_id, kb_id)


async def get_embedding_model(db: AsyncSession, kb_id: int) -> str | None:
    """知识库锁定的 Embedding 模型 (聊天时 RAG 检索用; 未上传过文档返回 None)"""
    kb = await knowledge_repo.get_kb_by_id(db, kb_id)
    return kb.embedding_model if kb is not None else None


async def _resolve_embedding_model_config(db: AsyncSession, user_id: int) -> ModelConfig:
    """解析 Embedding 用的模型配置 (用户默认模型的 API Key + Base URL)"""
    from app.core.config import settings

    config = await model_service.resolve_default_config(db, user_id)
    if config is not None:
        return config
    if settings.dev_mode:
        return ModelConfig(modelCode="mock", baseUrl="mock", apiKey="mock")
    raise BizException(
        ResultCode.PARAM_ERROR, "尚未配置 AI 模型, 请先在「模型管理」添加一个模型 (Embedding 复用该模型的 API Key)"
    )
