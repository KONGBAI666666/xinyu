package com.xinyu.memory.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 长期记忆实体（对应 memory 表）
 *
 * <p>记忆归属 = 用户 × 角色, 角色间记忆隔离。
 * 由 {@link com.xinyu.memory.service.MemoryExtractor} 在每轮 ASSISTANT 完成后异步提取,
 * 注入时由 {@link com.xinyu.memory.service.MemoryInjector} 按 importance 取 Top-K。
 */
@Data
@TableName("memory")
public class Memory {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 记忆归属用户 */
    private Long userId;

    /** 记忆所属角色（角色间记忆隔离） */
    private Long characterId;

    /** 结构化键: hobby/job/name/location/preference/fact 等, 可空 */
    private String memoryKey;

    /** 记忆正文, 如 "用户喜欢 Java 编程" */
    private String content;

    /** 重要度: HIGH/MEDIUM/LOW */
    private String importance;

    /** 状态: ACTIVE/DISABLED */
    private String status;

    /** 提取来源会话 ID（可溯源） */
    private Long sourceConversationId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
