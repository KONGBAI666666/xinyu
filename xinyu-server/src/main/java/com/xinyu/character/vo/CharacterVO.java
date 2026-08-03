package com.xinyu.character.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 角色展示对象
 */
@Data
public class CharacterVO {

    private Long id;
    private String name;
    private String avatarUrl;
    private String intro;
    private String systemPrompt;
    private String greeting;
    private BigDecimal temperature;
    private Integer maxTokens;
    private Long modelId;
    private Long creatorId;
    /** OFFICIAL / USER */
    private String creatorType;
    /** DRAFT / PUBLISHED / OFFLINE */
    private String status;
    private Integer chatCount;
    private Integer favoriteCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 是否当前用户创建（前端判断可否编辑/删除） */
    private Boolean mine;

    /** 是否已被当前用户收藏（广场/详情页用, 游客恒为 false） */
    private Boolean favorited;
}
