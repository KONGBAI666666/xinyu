package com.xinyu.message.enums;

/**
 * ASSISTANT 消息状态机（对应 message.status 列）
 *
 * <pre>
 * GENERATING ──正常结束──→ COMPLETED
 *     │
 *     ├──LLM异常────────→ FAILED
 *     └──前端断连───────→ STOPPED（保留已生成文本）
 * </pre>
 *
 * <p>USER 消息落库即 COMPLETED, 不参与流转。
 */
public enum MessageStatus {

    /** 生成中（SSE 建立时先落占位, 中断容错） */
    GENERATING,

    /** 已完成 */
    COMPLETED,

    /** 生成失败（LLM 异常） */
    FAILED,

    /** 用户主动停止（AbortController 断连） */
    STOPPED
}
