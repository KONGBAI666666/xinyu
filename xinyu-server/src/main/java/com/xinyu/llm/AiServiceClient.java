package com.xinyu.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xinyu.common.config.AiServiceProperties;
import com.xinyu.common.exception.BizException;
import com.xinyu.common.result.ResultCode;
import com.xinyu.llm.dto.LlmMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Java → Python AI 服务 HTTP 客户端
 *
 * <p>封装所有对 xinyu-ai 的 HTTP 调用:
 * <ul>
 *   <li>POST /ai/chat/stream — SSE 流式聊天</li>
 *   <li>POST /ai/memory/extract — 记忆提取</li>
 *   <li>POST /ai/rag/process — 文档向量化</li>
 *   <li>POST /ai/rag/search — 向量检索</li>
 *   <li>DELETE /ai/rag/vectors — 删除向量</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiServiceClient {

    private final AiServiceProperties props;
    private final ObjectMapper objectMapper;

    /** SSE 事件回调接口 */
    public interface SseCallback {
        void onMeta(String userMessageId, String assistantMessageId);
        void onDelta(String delta);
        void onDone(int promptTokens, int completionTokens, String status);
        void onError(int code, String message);
    }

    /**
     * SSE 流式聊天: 调用 Python /ai/chat/stream, 逐事件回调
     *
     * @param modelConfig  解密后的模型配置
     * @param messages     已组装的上下文消息 (system 中已含角色人设+记忆)
     * @param temperature  采样温度
     * @param maxTokens    最大输出 token
     * @param ragKbId      知识库 ID (可空, Python 侧负责检索并注入)
     * @param userQuery    用户原始输入 (用于 RAG 检索, 可空)
     * @param callback     SSE 回调
     */
    public void streamChat(
            LlmModelConfig modelConfig,
            List<LlmMessage> messages,
            double temperature,
            int maxTokens,
            String ragKbId,
            String userQuery,
            SseCallback callback
    ) {
        try {
            Map<String, Object> body = Map.of(
                    "conversationId", "",
                    "userId", "",
                    "characterId", "",
                    "modelConfig", Map.of(
                            "modelCode", modelConfig.modelCode(),
                            "baseUrl", modelConfig.baseUrl(),
                            "apiKey", modelConfig.apiKey()
                    ),
                    "messages", messages.stream().map(m -> Map.of(
                            "role", m.role(),
                            "content", m.content()
                    )).toList(),
                    "temperature", temperature,
                    "maxTokens", maxTokens,
                    "ragKbId", ragKbId != null ? ragKbId : "",
                    "userQuery", userQuery != null ? userQuery : ""
            );

            String json = objectMapper.writeValueAsString(body);
            HttpURLConnection conn = openConnection("/ai/chat/stream", "POST");
            writeBody(conn, json);

            int code = conn.getResponseCode();
            if (code != 200) {
                String err = readAll(conn);
                callback.onError(51001, "AI 服务返回错误: " + code);
                return;
            }

            // 逐行读取 SSE 流
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String event = null;
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("event:")) {
                        event = line.substring(6).trim();
                    } else if (line.startsWith("data:")) {
                        String data = line.substring(5).trim();
                        handleSseEvent(event, data, callback);
                    } else if (line.isEmpty()) {
                        event = null;
                    }
                }
            }
        } catch (Exception e) {
            log.error("调用 AI 服务失败", e);
            callback.onError(51001, "AI 服务连接失败: " + e.getMessage());
        }
    }

    /**
     * 记忆提取: 调用 Python /ai/memory/extract
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> extractMemory(
            LlmModelConfig modelConfig, String dialog
    ) {
        try {
            Map<String, Object> body = Map.of(
                    "modelConfig", Map.of(
                            "modelCode", modelConfig.modelCode(),
                            "baseUrl", modelConfig.baseUrl(),
                            "apiKey", modelConfig.apiKey()
                    ),
                    "dialog", dialog,
                    "userId", "",
                    "characterId", "",
                    "conversationId", ""
            );
            String json = objectMapper.writeValueAsString(body);
            HttpURLConnection conn = openConnection("/ai/memory/extract", "POST");
            writeBody(conn, json);
            String resp = readAll(conn);
            Map<String, Object> result = objectMapper.readValue(resp, Map.class);
            return (List<Map<String, Object>>) result.get("memories");
        } catch (Exception e) {
            log.warn("记忆提取失败 (静默): {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 文档向量化: 调用 Python /ai/rag/process
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> processDocument(
            String kbId, String docId, String fileName, byte[] fileContent,
            LlmModelConfig modelConfig
    ) {
        try {
            String base64 = java.util.Base64.getEncoder().encodeToString(fileContent);
            Map<String, Object> body = Map.of(
                    "kbId", kbId,
                    "docId", docId,
                    "fileName", fileName,
                    "fileContentBase64", base64,
                    "modelConfig", Map.of(
                            "modelCode", modelConfig.modelCode(),
                            "baseUrl", modelConfig.baseUrl(),
                            "apiKey", modelConfig.apiKey()
                    )
            );
            String json = objectMapper.writeValueAsString(body);
            HttpURLConnection conn = openConnection("/ai/rag/process", "POST");
            writeBody(conn, json);
            String resp = readAll(conn);
            return objectMapper.readValue(resp, Map.class);
        } catch (Exception e) {
            log.error("文档向量化失败: {}", e.getMessage());
            return Map.of("chunkCount", 0, "status", "ERROR", "errorMsg", e.getMessage());
        }
    }

    /**
     * 删除向量数据: 调用 Python /ai/rag/vectors
     */
    public void deleteRagVectors(String kbId, String docId) {
        try {
            String urlPath = "/ai/rag/vectors?kbId=" + kbId;
            if (docId != null) {
                urlPath += "&docId=" + docId;
            }
            HttpURLConnection conn = openConnection(urlPath, "DELETE");
            conn.getResponseCode();
            conn.disconnect();
        } catch (Exception e) {
            log.warn("删除向量数据失败 (静默): {}", e.getMessage());
        }
    }

    // ---------- 内部方法 ----------

    @SuppressWarnings("unchecked")
    private void handleSseEvent(String event, String data, SseCallback callback) {
        if (event == null || data.isEmpty()) return;
        try {
            Map<String, Object> payload = objectMapper.readValue(data, Map.class);
            switch (event) {
                case "meta" -> callback.onMeta(
                        (String) payload.get("userMessageId"),
                        (String) payload.get("assistantMessageId"));
                case "delta" -> callback.onDelta((String) payload.get("content"));
                case "done" -> callback.onDone(
                        ((Number) payload.get("promptTokens")).intValue(),
                        ((Number) payload.get("completionTokens")).intValue(),
                        (String) payload.get("status"));
                case "error" -> callback.onError(
                        ((Number) payload.get("code")).intValue(),
                        (String) payload.get("message"));
            }
        } catch (Exception e) {
            log.warn("解析 SSE 事件失败: event={}, data={}", event, data, e);
        }
    }

    private HttpURLConnection openConnection(String path, String method) throws Exception {
        URL url = URI.create(props.getBaseUrl() + path).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "text/event-stream");
        if (props.getInternalToken() != null && !props.getInternalToken().isEmpty()) {
            conn.setRequestProperty("X-Internal-Token", props.getInternalToken());
        }
        conn.setConnectTimeout(props.getConnectTimeoutSec() * 1000);
        conn.setReadTimeout(props.getReadTimeoutSec() * 1000);
        if (!method.equals("GET") && !method.equals("DELETE")) {
            conn.setDoOutput(true);
        }
        return conn;
    }

    private void writeBody(HttpURLConnection conn, String json) throws Exception {
        try (OutputStream os = conn.getOutputStream()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }
    }

    private String readAll(HttpURLConnection conn) throws Exception {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }
}
