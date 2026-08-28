package com.xinyu.rag.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class KnowledgeBaseVO {
    private Long id;
    private Long userId;
    private String name;
    private String description;
    private Integer docCount;
    private Integer chunkCount;
    private String status;
    private String embeddingModel;
    private Integer embeddingDim;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 详情接口才填充, 列表接口为 null */
    private List<KnowledgeDocumentVO> documents;
}
