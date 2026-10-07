"""Embedding 模型按服务商映射

聊天模型与 Embedding 模型是两个概念。根据用户默认模型所属服务商
(由 baseUrl / modelCode 推断), 自动选择其提供的 Embedding 模型与向量维度:
- 支持 Embedding 的服务商 (通义 / OpenAI / 智谱): 走内置映射表
- 不提供 Embedding 的服务商 (DeepSeek / Moonshot): 抛出明确错误提示切换模型
- Ollama 等本地/自建服务: 需通过 XINYU_EMBEDDING_MODEL / DIM 环境变量指定
"""

from dataclasses import dataclass

from app.ai.types import ModelConfig
from app.core.config import settings
from app.core.exceptions import AiError

DEFAULT_FALLBACK_DIM = 1536

# 错误码: Embedding 配置/服务商不支持 (区别于 51xxx LLM 错误)
EMBEDDING_CONFIG_ERROR = 52001


@dataclass(frozen=True)
class EmbeddingProfile:
    model: str
    dim: int


# 服务商 -> (Embedding 模型, 维度)
PROVIDER_PROFILES = {
    "dashscope": EmbeddingProfile("text-embedding-v2", 1536),
    "openai": EmbeddingProfile("text-embedding-3-small", 1536),
    "zhipu": EmbeddingProfile("embedding-3", 2048),
}

# 完全不提供 Embedding 接口的服务商
NO_EMBEDDING_PROVIDERS = {
    "deepseek": "DeepSeek",
    "moonshot": "Moonshot (Kimi)",
}


def detect_provider(base_url: str, model_code: str) -> str:
    """从 baseUrl 优先推断服务商, modelCode 前缀兜底"""
    bu = (base_url or "").lower()
    mc = (model_code or "").lower()
    # Ollama 优先判断: 本地常部署 qwen/glm 等同名模型, baseUrl 才是权威信号
    if "ollama" in bu or ":11434" in bu:
        return "ollama"
    if "dashscope" in bu or mc.startswith("qwen"):
        return "dashscope"
    if "deepseek" in bu or mc.startswith("deepseek"):
        return "deepseek"
    if "bigmodel" in bu or mc.startswith("glm"):
        return "zhipu"
    if "moonshot" in bu or mc.startswith(("moonshot", "kimi")):
        return "moonshot"
    if "openai.com" in bu or mc.startswith(("gpt-", "o1", "o3", "o4")):
        return "openai"
    return "unknown"


def resolve_embedding_profile(model_config: ModelConfig) -> EmbeddingProfile:
    """解析本次向量化使用的模型与维度; 无法解析时抛出带用户指引的 AiError"""
    if settings.embedding_model:
        dim = settings.embedding_dim or DEFAULT_FALLBACK_DIM
        return EmbeddingProfile(settings.embedding_model, dim)

    provider = detect_provider(model_config.baseUrl, model_config.modelCode)

    if provider in NO_EMBEDDING_PROVIDERS:
        raise AiError(
            f"当前默认模型所属服务商 ({NO_EMBEDDING_PROVIDERS[provider]}) 不提供 Embedding 接口, "
            "无法构建知识库。请将默认模型切换为通义千问等支持向量化的模型后重试。",
            code=EMBEDDING_CONFIG_ERROR,
        )

    if provider == "ollama":
        raise AiError(
            "Ollama 本地服务需要指定嵌入模型: 请先拉取一个嵌入模型 (如 bge-m3), "
            "然后在 .env 配置 XINYU_EMBEDDING_MODEL=bge-m3 与 XINYU_EMBEDDING_DIM=1024 后重试。",
            code=EMBEDDING_CONFIG_ERROR,
        )

    profile = PROVIDER_PROFILES.get(provider)
    if profile is None:
        raise AiError(
            f"无法识别模型服务商 (baseUrl: {model_config.baseUrl}), 未找到对应的 Embedding 模型。"
            "请在 .env 配置 XINYU_EMBEDDING_MODEL 与 XINYU_EMBEDDING_DIM 后重试。",
            code=EMBEDDING_CONFIG_ERROR,
        )
    return profile
