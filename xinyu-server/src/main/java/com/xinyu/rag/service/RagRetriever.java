package com.xinyu.rag.service;

import com.xinyu.rag.config.RagProperties;
import com.xinyu.rag.qdrant.QdrantService;
import com.xinyu.rag.qdrant.QdrantService.RetrievedChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * RAG 检索器: 把用户问题 → 向量 → Qdrant Top-K 检索 → 过滤相似度下限
 *
 * <p>调用链 (同步, 在请求线程):
 * <pre>
 *   用户输入
 *     ↓ EmbeddingClient.embed
 *   查询向量
 *     ↓ QdrantService.search (按 kb_id 过滤)
 *   Top-K 候选块
 *     ↓ 按 scoreThreshold 过滤
 *   最终检索结果
 * </pre>
 *
 * <p>同步执行 (非异步): 用户输入已知, embedding 单次 ~100ms, Qdrant 检索 ~10ms,
 * 总延迟可控; 不像记忆提取需要后台异步。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RagRetriever {

    private final EmbeddingClient embeddingClient;
    private final QdrantService qdrantService;
    private final RagProperties ragProperties;

    /**
     * 检索与用户问题相关的知识片段
     *
     * @param kbId       知识库 ID (会话绑定)
     * @param userInput  用户当前问题
     * @return 命中的块列表 (已过相似度阈值); 无知识库或检索失败返回空
     */
    public List<RetrievedChunk> retrieve(Long kbId, String userInput) {
        if (kbId == null) {
            return List.of();
        }
        try {
            List<Float> queryVec = embeddingClient.embed(userInput);
            List<RetrievedChunk> raw = qdrantService.search(kbId, queryVec, ragProperties.getRetrieveTopK());
            // 过滤低于相似度阈值的块
            List<RetrievedChunk> filtered = raw.stream()
                    .filter(c -> c.score() >= ragProperties.getRetrieveScoreThreshold())
                    .collect(Collectors.toList());
            log.info("RAG 检索: kbId={}, 候选={}, 命中={}", kbId, raw.size(), filtered.size());
            return filtered;
        } catch (Exception e) {
            // RAG 失败不阻断聊天, 降级为无知识库
            log.error("RAG 检索失败, 降级为无知识库: kbId={}, cause={}", kbId, e.getMessage());
            return List.of();
        }
    }
}
