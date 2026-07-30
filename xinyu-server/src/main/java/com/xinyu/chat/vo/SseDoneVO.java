package com.xinyu.chat.vo;

/**
 * SSE done 事件: 生成正常结束
 */
public record SseDoneVO(String messageId, Integer completionTokens, String status) {
}
