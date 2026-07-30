package com.xinyu.message.enums;

/**
 * 消息角色（对应 message.message_type 列）
 *
 * <p>命名与 OpenAI 兼容协议的 role 对齐, 便于 ContextAssembler 直接映射。
 */
public enum MessageRole {

    /** 用户消息 */
    USER,

    /** AI 回复 */
    ASSISTANT,

    /** 系统消息（预留, 如"记忆已更新"类提示） */
    SYSTEM
}
