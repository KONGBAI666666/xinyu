package com.xinyu.llm;

/**
 * LLM 模型运行时配置（轻量 record, 由 LlmClientFactory 从 AiModel 解密后构造）
 *
 * <p>与持久化实体 {@link com.xinyu.llm.model.entity.AiModel} 区分:
 * 这个对象持有解密后的 apiKey 明文, 仅存在于内存, 不入库不进日志。
 *
 * @param modelCode  模型标识（如 qwen-plus / gpt-4o）
 * @param baseUrl    OpenAI 兼容接口地址（不含 /chat/completions 后缀）
 * @param apiKey     解密后的 API Key 明文
 */
public record LlmModelConfig(String modelCode, String baseUrl, String apiKey) {
}
