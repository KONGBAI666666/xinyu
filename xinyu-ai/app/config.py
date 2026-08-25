"""xinyu-ai 服务配置

所有配置从环境变量读取, 默认值仅用于本地开发。
"""

import os
from dataclasses import dataclass, field


@dataclass
class Settings:
    # --- 服务 ---
    host: str = os.getenv("AI_HOST", "0.0.0.0")
    port: int = int(os.getenv("AI_PORT", "9100"))

    # --- Qdrant ---
    qdrant_url: str = os.getenv("XINYU_QDRANT_URL", "http://localhost:6333")
    qdrant_collection: str = os.getenv("QDRANT_COLLECTION", "xinyu_kb")
    embedding_dim: int = int(os.getenv("EMBEDDING_DIM", "1536"))

    # --- Embedding ---
    embedding_model: str = os.getenv("EMBEDDING_MODEL", "text-embedding-v2")

    # --- RAG 参数 ---
    chunk_size: int = int(os.getenv("CHUNK_SIZE", "1000"))
    chunk_overlap: int = int(os.getenv("CHUNK_OVERLAP", "50"))
    retrieve_top_k: int = int(os.getenv("RETRIEVE_TOP_K", "3"))
    retrieve_score_threshold: float = float(os.getenv("RETRIEVE_SCORE_THRESHOLD", "0.5"))

    # --- LLM 默认值 (兜底, 实际由 Java 传入) ---
    default_connect_timeout: int = int(os.getenv("LLM_CONNECT_TIMEOUT", "10"))
    default_read_timeout: int = int(os.getenv("LLM_READ_TIMEOUT", "120"))

    # --- Mock 模式 (无 LLM 调用, 返回假数据) ---
    mock_mode: bool = os.getenv("AI_MOCK_MODE", "false").lower() == "true"


settings = Settings()
