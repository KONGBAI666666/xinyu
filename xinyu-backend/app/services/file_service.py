"""文件服务 — 头像等上传 (M5)

安全设计:
- 文件名服务端生成 (雪花 ID + 校验过的扩展名), 客户端文件名只存 original_name,
  杜绝路径穿越;
- 类型白名单: 魔数嗅探 (JPEG/PNG/WEBP) 而非信任 Content-Type/扩展名;
- 大小上限 2MB; 超限/未知类型一律 42200;
- file 表落库留痕 (biz_type 区分业务), 文件本体存 uploads/ 本地目录。
"""

import asyncio
import uuid
from pathlib import Path

from app.core.exceptions import BizException, ResultCode
from app.models import File

MAX_AVATAR_BYTES = 2 * 1024 * 1024  # 2MB

# 项目根/uploads (与后端 venv 位置无关, 固定相对仓库根)
UPLOAD_ROOT = Path(__file__).resolve().parents[3] / "uploads"

# 魔数 → 扩展名 (嗅探结果即白名单)
_MAGIC_SIGNATURES: list[tuple[bytes, str, str]] = [
    (b"\xff\xd8\xff", "jpg", "image/jpeg"),
    (b"\x89PNG\r\n\x1a\n", "png", "image/png"),
    (b"RIFF", "webp", "image/webp"),  # WEBP 需再校验 RIFF 容器内 'WEBP' 标记
]


def _sniff_image(data: bytes) -> tuple[str, str]:
    """魔数嗅探图片类型, 返回 (扩展名, MIME); 未识别抛 42200"""
    for magic, ext, mime in _MAGIC_SIGNATURES:
        if data.startswith(magic):
            if magic == b"RIFF" and data[8:12] != b"WEBP":
                continue
            return ext, mime
    raise BizException(ResultCode.PARAM_ERROR, "仅支持 JPG/PNG/WEBP 图片")


def save_image_sync(user_id: int, biz_type: str, original_name: str, data: bytes) -> File:
    """同步落盘 + file 表建行 (由调用方在事务内提交; CPU/IO 轻量, 无需线程池)"""
    if not data:
        raise BizException(ResultCode.PARAM_ERROR, "文件内容为空")
    if len(data) > MAX_AVATAR_BYTES:
        raise BizException(ResultCode.PARAM_ERROR, "图片不能超过 2MB")

    ext, mime = _sniff_image(data)

    biz_dir = UPLOAD_ROOT / biz_type.lower()
    biz_dir.mkdir(parents=True, exist_ok=True)
    filename = f"u{user_id}_{uuid.uuid4().hex[:12]}.{ext}"
    (biz_dir / filename).write_bytes(data)

    url = f"/api/static/uploads/{biz_type.lower()}/{filename}"
    storage_path = str((biz_dir / filename).relative_to(UPLOAD_ROOT))
    return File(
        user_id=user_id,
        original_name=original_name[:255],
        storage_path=storage_path,
        url=url,
        file_type=mime,
        file_size=len(data),
        biz_type=biz_type,
    )


async def save_image(user_id: int, biz_type: str, original_name: str, data: bytes) -> File:
    """落盘 + file 表建行 (不提交, 由调用方决定事务边界); 落盘放线程池不阻塞事件循环"""
    return await asyncio.to_thread(save_image_sync, user_id, biz_type, original_name, data)


def public_url_to_path(url: str) -> Path | None:
    """访问地址 → 本地路径 (删除旧头像时用); 非本地上传路径返回 None"""
    prefix = "/api/static/uploads/"
    if not url.startswith(prefix):
        return None
    rel = url[len(prefix) :]
    if ".." in rel or "/" not in rel:
        return None
    return UPLOAD_ROOT / rel
