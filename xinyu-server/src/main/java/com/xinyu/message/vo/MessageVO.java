package com.xinyu.message.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.xinyu.message.entity.Message;
import com.xinyu.message.enums.MessageRole;
import com.xinyu.message.enums.MessageStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息视图对象（聊天记录渲染用）
 *
 * <p>Long 型 ID 序列化为字符串, 防止前端 JS Number 精度丢失。
 */
@Data
public class MessageVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long conversationId;

    /** 会话内业务序号 */
    private Integer sequenceNo;

    /** USER / ASSISTANT / SYSTEM */
    private MessageRole messageType;

    /** Markdown 原文 */
    private String content;

    /** GENERATING / COMPLETED / FAILED / STOPPED */
    private MessageStatus status;

    /** 输出 token（仅 ASSISTANT） */
    private Integer completionTokens;

    private LocalDateTime createdAt;

    public static MessageVO from(Message message) {
        MessageVO vo = new MessageVO();
        vo.setId(message.getId());
        vo.setConversationId(message.getConversationId());
        vo.setSequenceNo(message.getSequenceNo());
        vo.setMessageType(message.getMessageType());
        vo.setContent(message.getContent());
        vo.setStatus(message.getStatus());
        vo.setCompletionTokens(message.getCompletionTokens());
        vo.setCreatedAt(message.getCreatedAt());
        return vo;
    }
}
