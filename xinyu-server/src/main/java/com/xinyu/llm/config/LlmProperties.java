package com.xinyu.llm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LLM 计费配置（前缀 xinyu.llm）
 *
 * <p>重构后 LLM 调用由 Python xinyu-ai 服务负责, Java 不再直接调用 LLM。
 * 本配置仅保留计费单价, 用于 stats 模块估算成本。
 *
 * @param inputPricePer1k   输入 token 单价（元 / 1K tokens）
 * @param outputPricePer1k  输出 token 单价（元 / 1K tokens）
 */
@ConfigurationProperties(prefix = "xinyu.llm")
public record LlmProperties(
        double inputPricePer1k,
        double outputPricePer1k
) {

    /** 通义千问 qwen-plus 默认单价（元 / 1K tokens） */
    public static final double DEFAULT_INPUT_PRICE = 0.0008;
    public static final double DEFAULT_OUTPUT_PRICE = 0.002;
}
