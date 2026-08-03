package com.xinyu.chat.vo;

/**
 * SSE done 事件: 生成正常结束
 *
 * @param messageId         ASSISTANT 消息 ID
 * @param promptTokens      输入 token 数（含 system + 上下文）
 * @param completionTokens  输出 token 数
 * @param status            终态状态名（COMPLETED）
 */
public record SseDoneVO(String messageId, Integer promptTokens, Integer completionTokens, String status) {
}
