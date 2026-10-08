"""知识库服务 — 对应 Java KnowledgeBaseService

重构后 AI 能力 (解析/分块/向量化/Qdrant) 直接进程内调用 RagService,
不再经过 HTTP 边界; 本模块只负责 MySQL 元数据管理和权限校验。

上传链路为异步: upload_document 只落 PROCESSING 元数据即返回,
向量化由后台任务 (_process_upload) 用独立 DB 会话执行, 失败落 ERROR 展示原因。
"""

import asyncio
import logging

from sqlalchemy.ext.asyncio import AsyncSession

from app.ai.rag.rag_service import get_rag_service
from app.ai.types import ModelConfig
from app.core.database import SessionFactory
from app.core.exceptions import BizException, ResultCode
from app.models import KnowledgeBase, KnowledgeDocument
from app.repositories import knowledge_repo
from app.schemas.knowledge import KnowledgeBaseCreateDTO, KnowledgeBaseVO, KnowledgeDocumentVO
from app.services import model_service

logger = logging.getLogger("xinyu.rag")

# 允许入库的文档扩展名 (与 DocumentParser 支持的格式一致)
ALLOWED_DOC_EXTENSIONS = {"PDF", "MD", "MARKDOWN", "TXT", "TEXT"}

# 后台向量化任务强引用: 事件循环对 task 仅持弱引用, 不持有可能被 GC 静默丢弃
_background_tasks: set[asyncio.Task] = set()


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
    """删除知识库: 先删 MySQL 元数据 (权威), 提交后再删 Qdrant 向量

    双写补偿约定: MySQL 为准。向量删除失败只告警不回滚 —— 元数据已不可见,
    遗留向量不影响检索, 待人工对账清理。旧顺序 (先向量后库) 在 MySQL 提交
    失败时会造成"计数>0 但检索永远为空"的更差状态。
    """
    await _get_owned_kb(db, kb_id, user_id)
    await knowledge_repo.hard_delete_docs_by_kb(db, kb_id)
    await knowledge_repo.soft_delete_kb(db, kb_id)
    await db.commit()
    logger.info("知识库删除: id=%s, userId=%s", kb_id, user_id)
    try:
        await get_rag_service().delete_kb(str(kb_id))
    except Exception:
        logger.error("知识库向量清理失败 (遗留向量待对账): kbId=%s", kb_id, exc_info=True)


async def upload_document(
    db: AsyncSession,
    kb_id: int,
    user_id: int,
    file_name: str,
    file_size: int,
    file_content: bytes,
) -> KnowledgeDocumentVO:
    """上传文档: 元数据 PROCESSING 落库后立即返回, 向量化在后台任务执行

    解析 + 分块 + Embedding + 入 Qdrant 可能耗时数分钟, 同步执行会打爆前端超时;
    前端通过知识库详情轮询文档状态 (PROCESSING → READY / ERROR)。
    """
    await _get_owned_kb(db, kb_id, user_id)
    if not file_content:
        raise BizException(ResultCode.PARAM_ERROR, "文件为空")

    ext = file_name.rsplit(".", 1)[-1].upper() if "." in file_name else "UNKNOWN"
    # 仅允许解析器支持的格式入库, 避免未知文件当乱码文本进库 (前端 accept 不可靠)
    if ext not in ALLOWED_DOC_EXTENSIONS:
        raise BizException(ResultCode.PARAM_ERROR, "仅支持 PDF / Markdown / TXT 格式文件")

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
    logger.info("文档已受理: docId=%s, kbId=%s (后台向量化)", doc.id, kb_id)

    task = asyncio.create_task(_process_upload(doc.id, kb_id, user_id, file_name, file_content))
    _background_tasks.add(task)
    task.add_done_callback(_background_tasks.discard)
    return _doc_to_vo(doc)


async def _process_upload(doc_id: int, kb_id: int, user_id: int, file_name: str, file_content: bytes) -> None:
    """后台向量化: 独立 DB 会话执行; 任何失败都把文档置 ERROR (不向外抛出)"""
    try:
        async with SessionFactory() as session:
            doc = await knowledge_repo.get_doc_by_id(session, doc_id)
            kb = await knowledge_repo.get_kb_by_id(session, kb_id)
            # 受理后、处理开始前文档/知识库已被删除: 直接放弃, 无向量需要清理
            if doc is None or kb is None:
                logger.info("文档处理开始前已被删除, 跳过: docId=%s, kbId=%s", doc_id, kb_id)
                return
            await _process_into_db(session, doc, kb, file_name, file_content)
    except asyncio.CancelledError:
        raise
    except Exception:
        logger.exception("后台向量化失败: docId=%s, kbId=%s", doc_id, kb_id)
        # 兜底: 处理中途崩溃也要把 PROCESSING 收敛到终态, 否则文档永远显示处理中
        try:
            async with SessionFactory() as session:
                doc = await knowledge_repo.get_doc_by_id(session, doc_id)
                if doc is not None and doc.status == "PROCESSING":
                    doc.status = "ERROR"
                    doc.error_msg = "文档处理失败, 请稍后重试或联系管理员"
                    await knowledge_repo.update_doc(session, doc)
                    await session.commit()
        except Exception:
            logger.exception("ERROR 状态落库失败: docId=%s", doc_id)


async def _process_into_db(
    session: AsyncSession,
    doc: KnowledgeDocument,
    kb: KnowledgeBase,
    file_name: str,
    file_content: bytes,
) -> None:
    """解析 → 分块 → 向量化 → 入 Qdrant → 计数/锁定向量化配置 (多阶段独立提交)

    失败路径的 ERROR 文档记录落库展示原因; 所有异常在本函数内消化 (后台任务无调用方)。
    """
    kb_id = kb.id
    try:
        # 解析模型配置 (用于 Embedding; 复用用户默认模型的 API Key)
        model_config = await _resolve_embedding_model_config(session, doc.user_id)

        # 进程内处理文档 (解析 → 分块 → 向量化 → 入 Qdrant)
        # 传入知识库已锁定的向量化配置, 保证同库内向量维度一致
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
            if await knowledge_repo.get_doc_by_id(session, doc.id) is None:
                await get_rag_service().delete_doc(str(kb_id), str(doc.id))
                logger.info("文档在处理期间被删除, 已清理向量: docId=%s, kbId=%s", doc.id, kb_id)
                return
            doc.chunk_count = result.chunk_count
            doc.status = "READY"
            await knowledge_repo.update_doc(session, doc)

            # 计数原子自增 (读-改-写会在并发上传时丢更新)
            await knowledge_repo.update_kb_counts_on_upload(session, kb_id, result.chunk_count)
            # 首次上传锁定向量化配置 (条件更新: 并发时先成功者为准)
            if result.embedding_model:
                await knowledge_repo.try_lock_embedding(session, kb_id, result.embedding_model, result.embedding_dim)
            await session.commit()

            logger.info("文档上传成功: docId=%s, kbId=%s, 块数=%s", doc.id, kb_id, result.chunk_count)
        else:
            doc.status = "ERROR"
            doc.error_msg = (result.error_msg or "处理失败")[:500]
            await knowledge_repo.update_doc(session, doc)
            await session.commit()
            logger.warning("文档处理失败: docId=%s, kbId=%s, 原因=%s", doc.id, kb_id, result.error_msg)
    except BizException as e:
        # 常见为"尚未配置 AI 模型"等用户可自助解决的错误, 落到文档 ERROR 展示
        doc.status = "ERROR"
        doc.error_msg = e.message[:500]
        await knowledge_repo.update_doc(session, doc)
        await session.commit()
        logger.warning("文档处理业务失败: docId=%s, kbId=%s, 原因=%s", doc.id, kb_id, e.message)
    except Exception as e:
        doc.status = "ERROR"
        # 固定文案: 异常原文可能携带内网服务响应, 详情在日志里
        doc.error_msg = "文档处理失败, 请稍后重试或联系管理员"
        await knowledge_repo.update_doc(session, doc)
        await session.commit()
        logger.exception("文档处理异常: docId=%s, kbId=%s, error=%r", doc.id, kb_id, e)


async def delete_document(db: AsyncSession, kb_id: int, doc_id: int, user_id: int) -> None:
    """删除文档: 先删 MySQL 元数据 (权威), 提交后再删 Qdrant 向量 (失败只告警, 见 delete)"""
    await _get_owned_kb(db, kb_id, user_id)
    doc = await knowledge_repo.get_doc_by_id(db, doc_id)
    if doc is None or doc.kb_id != kb_id:
        raise BizException(ResultCode.NOT_FOUND, "文档不存在")
    # 1. 原子更新知识库计数 (GREATEST 防负数) + 删文档元数据
    await knowledge_repo.update_kb_counts_on_delete(db, kb_id, doc.chunk_count or 0)
    await knowledge_repo.hard_delete_doc(db, doc_id)
    await db.commit()
    logger.info("文档删除: docId=%s, kbId=%s", doc_id, kb_id)
    # 2. 删 Qdrant 向量 (失败留孤儿向量, 不影响任何可见功能)
    try:
        await get_rag_service().delete_doc(str(kb_id), str(doc_id))
    except Exception:
        logger.error("文档向量清理失败 (遗留向量待对账): docId=%s, kbId=%s", doc_id, kb_id, exc_info=True)


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
