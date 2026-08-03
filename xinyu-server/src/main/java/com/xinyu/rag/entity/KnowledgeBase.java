package com.xinyu.rag.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库实体（对应 knowledge_base 表, M3 RAG）
 *
 * <p>用户级知识库: 每个用户可创建多个知识库, 会话通过 conversation.kb_id 绑定。
 * 向量数据存 Qdrant, MySQL 只存元数据 + 冗余计数。
 */
@Data
@TableName("knowledge_base")
public class KnowledgeBase {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 归属用户 */
    private Long userId;

    /** 知识库名称 */
    private String name;

    /** 简介 */
    private String description;

    /** 冗余: 文档数 */
    private Integer docCount;

    /** 冗余: 切片数（= Qdrant 点数） */
    private Integer chunkCount;

    /** 状态: ACTIVE/PROCESSING/ERROR */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
