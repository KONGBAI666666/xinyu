package com.xinyu.llm;

/**
 * LLM 单次调用用量（来自供应商响应的 usage 字段）
 *
 * <p>用于消息表 token 落库与成本估算。totalTokens = promptTokens + completionTokens。
 *
 * @param promptTokens     输入 token 数（含 system + 历史 + 当前输入）
 * @param completionTokens 输出 token 数
 */
public record LlmUsage(int promptTokens, int completionTokens) {

    /** 总 token 数 */
    public int totalTokens() {
        return promptTokens + completionTokens;
    }

    /** 全零占位（异常路径无法获取真实用量时使用） */
    public static LlmUsage empty() {
        return new LlmUsage(0, 0);
    }
}
