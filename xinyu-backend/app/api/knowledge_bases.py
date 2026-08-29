"""知识库接口 — 对应 Java KnowledgeBaseController"""

from fastapi import APIRouter, Depends, File, UploadFile
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.deps import get_current_user_id, parse_id
from app.core.database import get_db
from app.core.exceptions import BizException, ResultCode
from app.schemas.common import Result
from app.schemas.knowledge import KnowledgeBaseCreateDTO, KnowledgeBaseVO, KnowledgeDocumentVO
from app.services import knowledge_service

router = APIRouter(prefix="/api/knowledge-bases", tags=["knowledge"])

# 与 Java 侧 multipart max-file-size 一致
MAX_UPLOAD_SIZE = 20 * 1024 * 1024


@router.get("")
async def list_kbs(
    user_id: int = Depends(get_current_user_id), db: AsyncSession = Depends(get_db)
) -> Result[list[KnowledgeBaseVO]]:
    """列出当前用户的知识库"""
    return Result.ok(await knowledge_service.list_by_user(db, user_id))


@router.post("")
async def create_kb(
    req: KnowledgeBaseCreateDTO,
    user_id: int = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> Result[KnowledgeBaseVO]:
    """创建知识库"""
    return Result.ok(await knowledge_service.create(db, req, user_id))


@router.get("/{kb_id}")
async def kb_detail(
    kb_id: str,
    user_id: int = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> Result[KnowledgeBaseVO]:
    """知识库详情 (含文档列表)"""
    return Result.ok(await knowledge_service.get_detail(db, parse_id(kb_id, "kbId"), user_id))


@router.delete("/{kb_id}")
async def delete_kb(
    kb_id: str,
    user_id: int = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> Result:
    """删除知识库 (含所有文档 + Qdrant 向量)"""
    await knowledge_service.delete(db, parse_id(kb_id, "kbId"), user_id)
    return Result.ok()


@router.post("/{kb_id}/documents")
async def upload_document(
    kb_id: str,
    file: UploadFile = File(...),
    user_id: int = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> Result[KnowledgeDocumentVO]:
    """上传文档到知识库 (同步处理: 解析 → 分块 → 向量化 → 入库)"""
    content = await file.read()
    if len(content) > MAX_UPLOAD_SIZE:
        raise BizException(ResultCode.PARAM_ERROR, "文件大小超过 20MB 限制")
    return Result.ok(
        await knowledge_service.upload_document(
            db, parse_id(kb_id, "kbId"), user_id, file.filename or "unknown", len(content), content
        )
    )


@router.delete("/{kb_id}/documents/{doc_id}")
async def delete_document(
    kb_id: str,
    doc_id: str,
    user_id: int = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> Result:
    """删除文档 (元数据 + Qdrant 向量)"""
    await knowledge_service.delete_document(db, parse_id(kb_id, "kbId"), parse_id(doc_id, "docId"), user_id)
    return Result.ok()
