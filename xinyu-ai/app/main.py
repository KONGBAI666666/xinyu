"""xinyu-ai FastAPI 主应用"""

from contextlib import asynccontextmanager
from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from app.core.exceptions import AiError
from app.routers import chat, memory, rag
from app.services.rag_service import get_rag_service
from app.config import settings


@asynccontextmanager
async def lifespan(app: FastAPI):
    # 启动时确保 Qdrant 集合存在 (复用全局 RagService 的长连接)
    try:
        await get_rag_service().ensure_collection()
        print(f"[xinyu-ai] Qdrant 集合就绪: {settings.qdrant_collection}")
    except Exception as e:
        print(f"[xinyu-ai] Qdrant 初始化失败 (非致命): {e}")

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


# 路由注册
app.include_router(chat.router)
app.include_router(memory.router)
app.include_router(rag.router)


@app.get("/health")
async def health():
    return {"status": "ok", "service": "xinyu-ai", "mock": settings.mock_mode}
