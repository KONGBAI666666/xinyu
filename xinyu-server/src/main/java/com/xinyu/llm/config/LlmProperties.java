package com.xinyu.llm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LLM 接入配置（前缀 xinyu.llm）
 *
 * <p>api-key 通过环境变量 DASHSCOPE_API_KEY 注入, 绝不写死在配置文件;
 * base-url 采用 OpenAI 兼容模式, 换 DeepSeek/OpenAI 只改 url + model。
 *
 * @param apiKey           API Key（来自环境变量, dev 环境可为空）
 * @param baseUrl          OpenAI 兼容 API 根地址（不含 /chat/completions）
 * @param model            模型标识, 兼作 message.model_code 溯源值
 * @param connectTimeoutMs 建连超时毫秒
 * @param readTimeoutMs    整次流式响应的总超时毫秒（需小于 SSE 的 180s）
 */
@ConfigurationProperties(prefix = "xinyu.llm")
public record LlmProperties(
        String apiKey,
        String baseUrl,
        String model,
        long connectTimeoutMs,
        long readTimeoutMs
) {
}
