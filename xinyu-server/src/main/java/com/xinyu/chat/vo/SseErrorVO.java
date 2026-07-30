package com.xinyu.chat.vo;

/**
 * SSE error 事件: 生成失败, 消息已置 FAILED, 前端显示重试
 */
public record SseErrorVO(int code, String message) {
}
