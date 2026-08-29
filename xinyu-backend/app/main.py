"""心屿单体后端入口 — FastAPI 应用组装

对应 Java XinyuApplication + 各 Config + GlobalExceptionHandler 的合并:
- 挂载全部业务路由 (路径与 Java 版完全一致, 前端零改动)
- 全局异常处理器: 所有异常统一翻译为 HTTP 200 + Result{code,message,data}
- 生命周期: 启动/关闭时管理 Qdrant 长连接
"""

import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException as StarletteHTTPException

from app.api import auth, characters, conversations, knowledge_bases, memories, messages, models, stats, users
from app.core.exceptions import BizException, ResultCode
from app.schemas.common import Result

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s [%(name)s] %(message)s",
)


@asynccontextmanager
async def lifespan(app: FastAPI):
    # QdrantClient 惰性创建 (首次使用时建立长连接), 无需启动钩子
    yield
    # 关闭时释放 Qdrant 长连接
    from app.ai.rag.rag_service import get_rag_service

    try:
        get_rag_service().close()
    except Exception:
        pass


app = FastAPI(title="Xinyu Backend", version="1.0.0", lifespan=lifespan, docs_url=None, redoc_url=None)

app.include_router(auth.router)
app.include_router(users.router)
app.include_router(characters.router)
app.include_router(conversations.router)
app.include_router(messages.router)
app.include_router(memories.router)
app.include_router(models.router)
app.include_router(knowledge_bases.router)
app.include_router(stats.router)


@app.get("/health")
async def health() -> Result:
    """存活探针 (docker healthcheck / nginx 探测用)"""
    return Result.ok()


# ==================== 全局异常处理器 (对应 Java GlobalExceptionHandler) ====================


def _json(result: Result) -> JSONResponse:
    return JSONResponse(status_code=200, content=result.model_dump())


@app.exception_handler(BizException)
async def biz_exception_handler(request: Request, exc: BizException) -> JSONResponse:
    """业务异常: code 与 message 由抛出方决定 (warn, 不打堆栈)"""
    logging.getLogger("xinyu.api").warning("业务异常: code=%s, message=%s", exc.code, exc.message)
    return _json(Result.fail(exc.code, exc.message))


@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request: Request, exc: RequestValidationError) -> JSONResponse:
    """DTO 参数校验失败: 取第一条错误提示给前端 (对齐 Java "field message" 格式)"""
    errors = exc.errors()
    if not errors:
        return _json(Result.fail(ResultCode.PARAM_ERROR, "请求参数校验失败"))
    first = errors[0]
    # 请求体 JSON 格式错误 → 统一文案 (对齐 Java HttpMessageNotReadableException)
    if first.get("type") == "json_invalid":
        return _json(Result.fail(ResultCode.PARAM_ERROR, "请求体缺失或格式错误"))
    field = ".".join(str(loc) for loc in first.get("loc", []) if loc != "body")
    msg = first.get("msg", "请求参数校验失败")
    # 去掉 Pydantic 前缀, 保留自定义校验文案
    msg = msg.removeprefix("Value error, ")
    message = f"{field} {msg}" if field else msg
    logging.getLogger("xinyu.api").warning("参数校验失败: %s", message)
    return _json(Result.fail(ResultCode.PARAM_ERROR, message))


@app.exception_handler(StarletteHTTPException)
async def http_exception_handler(request: Request, exc: StarletteHTTPException) -> JSONResponse:
    """访问不存在的路径 → 40400; 其余 HTTP 异常按系统错误兜底"""
    if exc.status_code == 404:
        logging.getLogger("xinyu.api").warning("路径不存在: %s", request.url.path)
        return _json(Result.fail(ResultCode.NOT_FOUND, "资源不存在"))
    return _json(Result.fail(ResultCode.SYSTEM_ERROR, "系统异常，请稍后重试"))


@app.exception_handler(Exception)
async def generic_exception_handler(request: Request, exc: Exception) -> JSONResponse:
    """兜底: 未知异常打全堆栈, 返回统一文案避免泄露内部细节"""
    logging.getLogger("xinyu.api").exception("系统异常: path=%s", request.url.path)
    return _json(Result.fail(ResultCode.SYSTEM_ERROR, "系统异常，请稍后重试"))
