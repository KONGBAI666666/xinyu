package com.xinyu.rag.qdrant;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Collections;
import io.qdrant.client.grpc.JsonWithInt.Value;
import io.qdrant.client.grpc.Points;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static io.qdrant.client.ConditionFactory.matchKeyword;
import static io.qdrant.client.PointIdFactory.id;
import static io.qdrant.client.ValueFactory.value;
import static io.qdrant.client.VectorsFactory.vectors;

/**
 * Qdrant 向量库封装: 建集合 / 存点 / 检索 / 删除
 *
 * <p>集合设计: 一个项目一个 collection ({@code xinyu_kb}), 用 payload 字段区分不同知识库:
 * <ul>
 *   <li>{@code kb_id}: 知识库 ID (string, 检索时按此过滤)</li>
 *   <li>{@code doc_id}: 文档 ID (string, 删除文档时按此批量删点)</li>
 *   <li>{@code chunk_index}: 块序号 (int)</li>
 *   <li>{@code text}: 块正文 (string, 检索后注入 prompt)</li>
 * </ul>
 *
 * <p>point id 用 UUID (雪花 ID 转 long 可能溢出, UUID 字符串更安全)。
 * 应用启动时自动建集合 (幂等)。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QdrantService {

    private final QdrantClient qdrantClient;
    private final com.xinyu.rag.config.RagProperties ragProperties;

    /** payload 中知识库 ID 的字段名 */
    public static final String FIELD_KB_ID = "kb_id";
    /** payload 中文档 ID 的字段名 */
    public static final String FIELD_DOC_ID = "doc_id";
    /** payload 中块序号的字段名 */
    public static final String FIELD_CHUNK_INDEX = "chunk_index";
    /** payload 中块正文的字段名 */
    public static final String FIELD_TEXT = "text";

    /**
     * 应用启动后自动建集合 (幂等: 已存在则跳过)
     */
    @EventListener(ApplicationReadyEvent.class)
    public void ensureCollection() {
        try {
            boolean exists = qdrantClient
                    .collectionExistsAsync(ragProperties.getQdrantCollection())
                    .get(10, TimeUnit.SECONDS);
            if (exists) {
                log.info("Qdrant 集合已存在: {}", ragProperties.getQdrantCollection());
                return;
            }
            qdrantClient.createCollectionAsync(
                            ragProperties.getQdrantCollection(),
                            Collections.VectorParams.newBuilder()
                                    .setSize(ragProperties.getEmbeddingDimension())
                                    .setDistance(Collections.Distance.Cosine)
                                    .build())
                    .get(10, TimeUnit.SECONDS);
            log.info("Qdrant 集合创建成功: {}, dim={}, distance=Cosine",
                    ragProperties.getQdrantCollection(), ragProperties.getEmbeddingDimension());
        } catch (Exception e) {
            // 启动时 Qdrant 不可用不阻断应用 (知识库功能不可用, 但聊天仍可用)
            log.error("Qdrant 集合初始化失败 (RAG 功能将不可用): {}", e.getMessage(), e);
        }
    }

    /**
     * 批量存入文档分块 (每个 chunk 一个 point)
     *
     * @param kbId       知识库 ID
     * @param docId      文档 ID
     * @param chunks     块正文列表
     * @param embeddings 对应的向量列表 (与 chunks 等长)
     */
    public void upsertChunks(Long kbId, Long docId, List<String> chunks, List<List<Float>> embeddings) {
        if (chunks.size() != embeddings.size()) {
            throw new IllegalArgumentException("chunks 与 embeddings 长度不一致: "
                    + chunks.size() + " vs " + embeddings.size());
        }
        List<Points.PointStruct> points = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            UUID pointId = UUID.randomUUID();
            points.add(Points.PointStruct.newBuilder()
                    .setId(id(pointId))
                    .setVectors(vectors(embeddings.get(i)))
                    .putAllPayload(Map.of(
                            FIELD_KB_ID, value(String.valueOf(kbId)),
                            FIELD_DOC_ID, value(String.valueOf(docId)),
                            FIELD_CHUNK_INDEX, value(i),
                            FIELD_TEXT, value(chunks.get(i))
                    ))
                    .build());
        }
        try {
            qdrantClient.upsertAsync(ragProperties.getQdrantCollection(), points)
                    .get(60, TimeUnit.SECONDS);
            log.info("Qdrant upsert 成功: kbId={}, docId={}, 点数={}", kbId, docId, points.size());
        } catch (Exception e) {
            throw new RuntimeException("Qdrant upsert 失败: kbId=" + kbId + ", docId=" + docId, e);
        }
    }

    /**
     * 按相似度检索 Top-K 块 (按 kb_id 过滤)
     *
     * @param kbId     知识库 ID
     * @param queryVec 查询向量
     * @param topK     返回条数
     * @return 检索结果列表 (分数 + 正文), 按分数降序
     */
    public List<RetrievedChunk> search(Long kbId, List<Float> queryVec, int topK) {
        Points.SearchPoints.Builder reqBuilder = Points.SearchPoints.newBuilder()
                .setCollectionName(ragProperties.getQdrantCollection())
                .addAllVector(queryVec)
                .setLimit(topK)
                .setWithPayload(Points.WithPayloadSelector.newBuilder().setEnable(true).build())
                .setFilter(Points.Filter.newBuilder()
                        .addMust(matchKeyword(FIELD_KB_ID, String.valueOf(kbId)))
                        .build());
        try {
            List<Points.ScoredPoint> points = qdrantClient.searchAsync(reqBuilder.build())
                    .get(30, TimeUnit.SECONDS);
            List<RetrievedChunk> result = new ArrayList<>(points.size());
            for (Points.ScoredPoint p : points) {
                String text = p.getPayloadMap().getOrDefault(FIELD_TEXT, Value.newBuilder().setStringValue("").build()).getStringValue();
                result.add(new RetrievedChunk(text, p.getScore()));
            }
            return result;
        } catch (Exception e) {
            log.error("Qdrant 检索失败: kbId={}, cause={}", kbId, e.getMessage());
            return List.of();
        }
    }

    /**
     * 删除某文档的所有点 (文档删除时调用)
     */
    public void deleteByDoc(Long kbId, Long docId) {
        Points.Filter filter = Points.Filter.newBuilder()
                .addMust(matchKeyword(FIELD_KB_ID, String.valueOf(kbId)))
                .addMust(matchKeyword(FIELD_DOC_ID, String.valueOf(docId)))
                .build();
        try {
            qdrantClient.deleteAsync(ragProperties.getQdrantCollection(), filter)
                    .get(30, TimeUnit.SECONDS);
            log.info("Qdrant 删除文档点成功: kbId={}, docId={}", kbId, docId);
        } catch (Exception e) {
            log.error("Qdrant 删除文档点失败: kbId={}, docId={}, cause={}", kbId, docId, e.getMessage());
        }
    }

    /**
     * 删除某知识库的所有点 (知识库删除时调用)
     */
    public void deleteByKb(Long kbId) {
        Points.Filter filter = Points.Filter.newBuilder()
                .addMust(matchKeyword(FIELD_KB_ID, String.valueOf(kbId)))
                .build();
        try {
            qdrantClient.deleteAsync(ragProperties.getQdrantCollection(), filter)
                    .get(60, TimeUnit.SECONDS);
            log.info("Qdrant 删除知识库点成功: kbId={}", kbId);
        } catch (Exception e) {
            log.error("Qdrant 删除知识库点失败: kbId={}, cause={}", kbId, e.getMessage());
        }
    }

    /** 检索结果 */
    public record RetrievedChunk(String text, float score) {}
}
