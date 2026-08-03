package com.xinyu.rag.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KnowledgeDocumentVO {
    private Long id;
    private Long kbId;
    private Long userId;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private Integer chunkCount;
    private String status;
    private String errorMsg;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
