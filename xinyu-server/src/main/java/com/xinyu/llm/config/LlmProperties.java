package com.xinyu.llm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LLM 接入配置（前缀 xinyu.llm）
 *
 * <p>api-key 通过环境变量 DASHSCOPE_API_KEY 注入, 绝不写死在配置文件;
 * base-url 采用 OpenAI 兼容模式, 换 DeepSeek/OpenAI 只改 url + model。
 *
 * <p>计费单价用于 stats 模块估算成本, 默认值取通义千问 qwen-plus 公开价
 * （输入 ¥0.0008/1K tokens, 输出 ¥0.002/1K tokens）; 切换模型时同步调整。
 *
 * @param apiKey            API Key（来自环境变量, dev 环境可为空）
 * @param baseUrl           OpenAI 兼容 API 根地址（不含 /chat/completions）
 * @param model             模型标识, 兼作 message.model_code 溯源值
 * @param connectTimeoutMs  建连超时毫秒
 * @param readTimeoutMs     整次流式响应的总超时毫秒（需小于 SSE 的 180s）
 * @param inputPricePer1k   输入 token 单价（元 / 1K tokens）
 * @param outputPricePer1k  输出 token 单价（元 / 1K tokens）
 */
@ConfigurationProperties(prefix = "xinyu.llm")
public record LlmProperties(
        String apiKey,
        String baseUrl,
        String model,
        long connectTimeoutMs,
        long readTimeoutMs,
        double inputPricePer1k,
        double outputPricePer1k
) {

    /** 通义千问 qwen-plus 默认单价（元 / 1K tokens） */
    public static final double DEFAULT_INPUT_PRICE = 0.0008;
    public static final double DEFAULT_OUTPUT_PRICE = 0.002;
}
