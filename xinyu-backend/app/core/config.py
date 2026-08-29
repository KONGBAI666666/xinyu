"""应用配置中心

所有配置从环境变量读取 (前缀 XINYU_), 与原 Java application.yml 及
原 Python AI 服务的环境变量保持命名兼容, 方便存量部署平滑迁移。

对应 Java 侧的 application.yml + xinyu-ai 的 config.py 合并。
"""

from functools import lru_cache

from pydantic import AliasChoices, Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="XINYU_", extra="ignore")

    # ---------- 运行环境 / 服务 ----------
    # dev: 无默认模型时降级 mock; prod: 抛错提示先配置模型
    env: str = "dev"  # dev / prod
    host: str = "0.0.0.0"
    port: int = 9200

    # ---------- MySQL ----------
    db_host: str = "localhost"
    db_port: int = 3306
    db_user: str = "root"
    db_password: str = ""
    db_name: str = "xinyu"

    # ---------- JWT ----------
    jwt_secret: str = "xinyu-dev-jwt-secret-change-me-please!!"  # HS256 至少 32 字符
    jwt_expire_ms: int = 7_200_000  # 2 小时, 与 Java 版一致

    # ---------- AES (用户 API Key 入库加密) ----------
    # 留空回退 jwt_secret, 兼容存量密文
    crypto_secret: str = ""

    # ---------- Qdrant ----------
    qdrant_url: str = "http://localhost:7333"
    qdrant_collection: str = "xinyu_kb"

    # ---------- Embedding (可选覆盖, 兼容旧变量名 EMBEDDING_MODEL/DIM) ----------
    embedding_model: str = Field(
        default="",
        validation_alias=AliasChoices("XINYU_EMBEDDING_MODEL", "EMBEDDING_MODEL"),
    )
    embedding_dim: int = Field(
        default=0,
        validation_alias=AliasChoices("XINYU_EMBEDDING_DIM", "EMBEDDING_DIM"),
    )

    # ---------- RAG 参数 ----------
    chunk_size: int = 1000
    chunk_overlap: int = 50
    retrieve_top_k: int = 3
    retrieve_score_threshold: float = 0.5

    # ---------- LLM 计费单价 (元 / 1K tokens) ----------
    llm_input_price_per_1k: float = 0.0008
    llm_output_price_per_1k: float = 0.002

    # ---------- 雪花 ID ----------
    worker_id: int = 1

    # ---------- Mock 模式 (无 LLM 调用, 返回假数据) ----------
    mock_mode: bool = False

    # ============ 派生属性 ============

    @property
    def dev_mode(self) -> bool:
        return self.env.lower() == "dev"

    @property
    def database_url(self) -> str:
        return (
            f"mysql+aiomysql://{self.db_user}:{self.db_password}"
            f"@{self.db_host}:{self.db_port}/{self.db_name}?charset=utf8mb4"
        )

    @property
    def effective_crypto_secret(self) -> str:
        """AES 密钥: 优先独立密钥, 未配置回退 JWT 密钥 (兼容存量部署)"""
        return self.crypto_secret or self.jwt_secret


@lru_cache
def get_settings() -> Settings:
    return Settings()


settings = get_settings()
