package com.xinyu.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RAG 配置 (对应 application.yml 的 xinyu.rag)
 *
 * <p>Qdrant 地址、集合名、Embedding 模型、分块参数、检索参数集中管理。
 */
@Data
@Component
@ConfigurationProperties(prefix = "xinyu.rag")
public class RagProperties {

    /** Qdrant gRPC 地址 (Java SDK 走 6334 端口) */
    private String qdrantUrl;

    /** Qdrant 集合名 (一个项目一个集合, 用 payload.kb_id 区分知识库) */
    private String qdrantCollection;

    /** 千问 Embedding 模型名 */
    private String embeddingModel;

    /** Embedding 维度 (text-embedding-v2 = 1536) */
    private int embeddingDimension;

    /** 文档分块大小 (字符数) */
    private int chunkSize;

    /** 分块重叠 (字符数, 避免切断语义) */
    private int chunkOverlap;

    /** 检索 Top-K */
    private int retrieveTopK;

    /** 相似度下限 (cosine 分数 0~1, 越大越相关) */
    private double retrieveScoreThreshold;
}
