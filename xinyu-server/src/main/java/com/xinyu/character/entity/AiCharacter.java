package com.xinyu.character.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 角色实体（对应 scripts/schema.sql 的 character 表）
 *
 * <p>类名不取 Character 以避免与 java.lang.Character 冲突（java.lang 隐式导入,
 * 同名类在业务代码中极易引发误引用）。
 *
 * <p>M1-3 仅作只读切片供聊天链路取 greeting/system_prompt,
 * 角色 CRUD、广场、状态机在 M2 实现。
 */
@Data
@TableName("`character`")
public class AiCharacter {

    /** 雪花ID */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 角色名 */
    private String name;

    /** 角色头像 */
    private String avatarUrl;

    /** 一句话介绍（广场卡片） */
    private String intro;

    /** 人设核心 Prompt（组装上下文时作为 system 消息） */
    private String systemPrompt;

    /** 开场白（新会话首条 ASSISTANT 消息） */
    private String greeting;

    /** 采样温度 0~2 */
    private BigDecimal temperature;

    /** 单次回复 token 上限 */
    private Integer maxTokens;

    /** 指定模型, 空=系统默认（多模型预留） */
    private Long modelId;

    /** 创建者 user.id */
    private Long creatorId;

    /** OFFICIAL / USER */
    private String creatorType;

    /** DRAFT / PENDING / PUBLISHED / OFFLINE */
    private String status;

    /** 冗余计数: 累计会话数（热度排序） */
    private Integer chatCount;

    /** 冗余计数: 收藏数 */
    private Integer favoriteCount;

    /** 创建时间（插入时自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间（插入/更新时自动填充） */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 逻辑删除：0-正常 1-已删除 */
    @TableLogic
    private Integer deleted;
}
