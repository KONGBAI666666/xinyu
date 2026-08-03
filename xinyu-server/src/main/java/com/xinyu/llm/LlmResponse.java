package com.xinyu.llm;

/**
 * LLM 同步调用响应
 *
 * <p>用于记忆提取等不需要流式的内部场景: 一次调用拿到完整文本 + 用量。
 *
 * @param content 完整回复文本
 * @param usage   本次调用用量; 供应商未返回时为 {@link LlmUsage#empty()}
 */
public record LlmResponse(String content, LlmUsage usage) {
}
