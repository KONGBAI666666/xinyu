package com.xinyu.memory.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 记忆展示对象
 *
 * <p>characterName 为冗余字段（JOIN character 表）, 便于前端列表展示无需再单独拉角色名。
 */
@Data
public class MemoryVO {

    private Long id;

    private Long userId;

    private Long characterId;

    /** 角色名（冗余, 便于展示） */
    private String characterName;

    private String memoryKey;

    private String content;

    /** HIGH/MEDIUM/LOW */
    private String importance;

    /** ACTIVE/DISABLED */
    private String status;

    private Long sourceConversationId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
