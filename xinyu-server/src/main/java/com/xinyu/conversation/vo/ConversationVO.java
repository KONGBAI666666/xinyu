package com.xinyu.conversation.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.xinyu.conversation.entity.Conversation;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话视图对象
 *
 * <p>Long 型 ID 序列化为字符串, 防止前端 JS Number 精度丢失。
 */
@Data
public class ConversationVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long characterId;

    /** 会话级模型覆盖, NULL=用用户默认模型 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long modelId;

    /** 会话绑定的知识库ID (M3 RAG), NULL=普通聊天 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long kbId;

    private String title;

    /** 冗余: 最新消息时间（列表排序） */
    private LocalDateTime lastMessageAt;

    /** 冗余: 最新消息摘要（列表副标题） */
    private String lastMessagePreview;

    private LocalDateTime createdAt;

    public static ConversationVO from(Conversation conversation) {
        ConversationVO vo = new ConversationVO();
        vo.setId(conversation.getId());
        vo.setCharacterId(conversation.getCharacterId());
        vo.setModelId(conversation.getModelId());
        vo.setKbId(conversation.getKbId());
        vo.setTitle(conversation.getTitle());
        vo.setLastMessageAt(conversation.getLastMessageAt());
        vo.setLastMessagePreview(conversation.getLastMessagePreview());
        vo.setCreatedAt(conversation.getCreatedAt());
        return vo;
    }
}
