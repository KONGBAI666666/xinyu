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
 * 知识库文档实体（对应 knowledge_document 表, M3 RAG）
 *
 * <p>记录用户上传的每个文档及其处理状态。文档被切片后向量化存入 Qdrant,
 * 此表只存元数据, 不存向量（向量在 Qdrant 的 point payload 里）。
 *
 * <p>状态机: PROCESSING（上传后处理中）→ READY（成功）/ ERROR（解析或向量化失败）
 */
@Data
@TableName("knowledge_document")
public class KnowledgeDocument {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属知识库 */
    private Long kbId;

    /** 冗余: 上传者（权限校验免 join） */
    private Long userId;

    /** 原始文件名 */
    private String fileName;

    /** 文件类型: PDF/MARKDOWN/TXT */
    private String fileType;

    /** 字节数 */
    private Long fileSize;

    /** 切成的块数 */
    private Integer chunkCount;

    /** 状态: PROCESSING/READY/ERROR */
    private String status;

    /** 处理失败原因 */
    private String errorMsg;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
