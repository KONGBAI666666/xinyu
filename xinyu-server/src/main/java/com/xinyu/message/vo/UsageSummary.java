package com.xinyu.message.vo;

/**
 * 用户用量聚合结果（来自 message 表 ASSISTANT 行的 SUM/COUNT）
 *
 * <p>统计口径: ASSISTANT 消息且 token 字段非空（即真实发生 LLM 调用的消息）,
 * 不含 greeting 占位与 GENERATING 中断行。
 *
 * @param callCount         调用次数（行数）
 * @param promptTokens      输入 token 累计
 * @param completionTokens  输出 token 累计
 */
public record UsageSummary(long callCount, long promptTokens, long completionTokens) {

    /** 全零占位（用户从未发起过聊天） */
    public static UsageSummary zero() {
        return new UsageSummary(0, 0, 0);
    }
}
