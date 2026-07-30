package com.xinyu.chat.vo;

/**
 * SSE meta 事件: 双消息ID回执, 前端立即渲染占位（AI 侧"思考中"）
 *
 * <p>ID 用字符串, 防前端 JS Number 精度丢失。
 */
public record SseMetaVO(String userMessageId, String assistantMessageId) {
}
