package com.xinyu.conversation.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话实体（对应 scripts/schema.sql 的 conversation 表）
 *
 * <p>会话 = 用户 × 角色的一次对话线。
 */
@Data
@TableName("conversation")
public class Conversation {

    /** 雪花ID */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 归属用户 */
    private Long userId;

    /** 绑定角色 */
    private Long characterId;

    /** 会话级模型覆盖, NULL=用用户默认模型 */
    private Long modelId;

    /** 标题: 创建时默认角色名, 首条用户消息后取其前20字, 可重命名 */
    private String title;

    /** 冗余: 最新消息时间（会话列表排序） */
    private LocalDateTime lastMessageAt;

    /** 冗余: 最新消息摘要（会话列表副标题） */
    private String lastMessagePreview;

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
