# ============================================================
# 心屿 XinYu · Python 单体后端 (FastAPI)
# 职责: 用户/认证/角色/会话/消息/统计/Memory/RAG/LLM/Qdrant
# ============================================================

FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    TZ=Asia/Shanghai

WORKDIR /app

# 先装依赖 (利用镜像层缓存)
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app ./app

# 非 root 运行 (最小权限)
RUN useradd --create-home --uid 10001 appuser \
 && chown -R appuser:appuser /app
USER appuser

EXPOSE 9000

HEALTHCHECK --interval=10s --timeout=5s --retries=5 --start-period=15s \
  CMD python -c "import urllib.request;urllib.request.urlopen('http://127.0.0.1:9000/health', timeout=3)"

# 单 worker 是有意为之: _active_streams 等进程内状态在多 worker 下会失效, 扩容前须先改造
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "9000"]
