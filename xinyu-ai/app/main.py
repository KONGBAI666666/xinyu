"""xinyu-ai FastAPI 主应用"""

from contextlib import asynccontextmanager
from fastapi import Depends, FastAPI, Header, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from app.core.exceptions import AiError
from app.routers import chat, memory, rag
from app.services.rag_service import get_rag_service
from app.config import settings


async def require_internal_token(
    x_internal_token: str = Header(default="", alias="X-Internal-Token"),
) -> None:
    """内部接口令牌校验 (全部业务路由); 未配置 AI_INTERNAL_TOKEN 时不校验 (本地开发)"""
    if settings.internal_token and x_internal_token != settings.internal_token:
        raise HTTPException(status_code=403, detail="invalid internal token")


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Qdrant 集合按知识库在文档入库时创建, 启动阶段不再需要全局集合
    print(f"[xinyu-ai] 服务启动: {settings.host}:{settings.port}, mock={settings.mock_mode}")
    yield
    get_rag_service().close()
    print("[xinyu-ai] 服务关闭")


app = FastAPI(
    title="xinyu-ai",
    description="心屿 AI 服务 — LLM / RAG / Memory / Prompt 编排",
    version="1.0.0",
    lifespan=lifespan,
)

# CORS (允许 Java 后端调用)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.exception_handler(AiError)
async def ai_error_handler(request: Request, exc: AiError):
    """业务异常统一返回 code+message (HTTP 500); Java 侧对非 200 已有降级分支"""
    return JSONResponse(status_code=500, content={"code": exc.code, "message": exc.message})


# 路由注册: 业务路由统一挂令牌校验 (/health 保持开放供健康检查)
_internal_auth = [Depends(require_internal_token)]
app.include_router(chat.router, dependencies=_internal_auth)
app.include_router(memory.router, dependencies=_internal_auth)
app.include_router(rag.router, dependencies=_internal_auth)


@app.get("/health")
async def health():
    return {"status": "ok", "service": "xinyu-ai", "mock": settings.mock_mode}
