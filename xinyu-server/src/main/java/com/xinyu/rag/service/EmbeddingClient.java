package com.xinyu.rag.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xinyu.llm.config.LlmProperties;
import com.xinyu.rag.config.RagProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 千问 Embedding 客户端: 把文本转向量
 *
 * <p>调用千问 OpenAI 兼容接口 {@code /v1/embeddings}, 模型 {@code text-embedding-v2} (1536 维)。
 * 复用 {@link LlmProperties#getApiKey()} (同一 Key 既用于 chat 也用于 embedding)。
 *
 * <p>批量调用: 一次请求最多 25 条文本 (千问限制), 超过自动分批。
 */
@Slf4j
@Component
public class EmbeddingClient {

    /** 千问单次 embedding 请求上限 */
    private static final int BATCH_SIZE = 25;

    private final LlmProperties llmProperties;
    private final RagProperties ragProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public EmbeddingClient(LlmProperties llmProperties, RagProperties ragProperties,
                           ObjectMapper objectMapper) {
        this.llmProperties = llmProperties;
        this.ragProperties = ragProperties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(llmProperties.connectTimeoutMs()))
                .build();
    }

    /**
     * 批量文本转向量
     *
     * @param texts 文本列表
     * @return 向量列表 (与输入等长, 顺序一致)
     */
    public List<List<Float>> embed(List<String> texts) {
        if (texts.isEmpty()) {
            return List.of();
        }
        List<List<Float>> all = new ArrayList<>(texts.size());
        // 分批 (千问单次最多 25 条)
        for (int i = 0; i < texts.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, texts.size());
            List<String> batch = texts.subList(i, end);
            all.addAll(embedBatch(batch));
        }
        return all;
    }

    /**
     * 单条文本转向量
     */
    public List<Float> embed(String text) {
        return embed(List.of(text)).get(0);
    }

    private List<List<Float>> embedBatch(List<String> texts) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", ragProperties.getEmbeddingModel());
        ArrayNode inputArray = body.putArray("input");
        for (String t : texts) {
            inputArray.add(t);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(llmProperties.baseUrl() + "/embeddings"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + llmProperties.apiKey())
                .timeout(Duration.ofMillis(llmProperties.readTimeoutMs()))
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException("Embedding 接口异常: status=" + response.statusCode()
                        + ", body=" + response.body());
            }
            return parseEmbeddings(response.body());
        } catch (IOException e) {
            throw new RuntimeException("Embedding 调用失败: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Embedding 调用被中断", e);
        }
    }

    /** 解析 embedding 响应: data[i].embedding 是向量数组 */
    private List<List<Float>> parseEmbeddings(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode data = root.path("data");
            List<List<Float>> result = new ArrayList<>(data.size());
            for (JsonNode item : data) {
                JsonNode embedding = item.path("embedding");
                List<Float> vec = new ArrayList<>(embedding.size());
                for (JsonNode v : embedding) {
                    vec.add((float) v.asDouble());
                }
                result.add(vec);
            }
            return result;
        } catch (IOException e) {
            throw new RuntimeException("Embedding 响应解析失败: " + e.getMessage(), e);
        }
    }
}
