FROM python:3.12-slim

WORKDIR /app

# 先装依赖 (利用 Docker 层缓存; PyMuPDF 自带二进制轮子, 无需额外系统包)
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# 复制应用代码
COPY app/ ./app/

EXPOSE 9100

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "9100"]
