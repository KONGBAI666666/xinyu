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
import java.io.IOException;
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
     * 停止生成句柄: 编排层持有, 可随时取消进行中的 AI 服务调用。
     *
     * <p>取消 = 断开到 Python 的 HTTP 连接; 读线程随即抛 IOException 退出,
     * Python 侧检测到客户端断连后中止 LLM 流 (不再消耗后续 token)。
     */
    public static class StreamCancellation {
        private volatile HttpURLConnection connection;
        private volatile boolean cancelled;

        void register(HttpURLConnection conn) {
            this.connection = conn;
            // 取消先于连接建立到达时, 注册即断开
            if (cancelled) {
                conn.disconnect();
            }
        }

        public void cancel() {
            cancelled = true;
            HttpURLConnection conn = this.connection;
            if (conn != null) {
                conn.disconnect();
            }
        }

        public boolean isCancelled() {
            return cancelled;
        }
    }

    /**
     * SSE 流式聊天: 调用 Python /ai/chat/stream, 逐事件回调
     *
     * @param conversationId 会话 ID (Python 侧日志/追踪用)
     * @param userId         用户 ID
     * @param characterId    角色 ID
     * @param modelConfig  解密后的模型配置
     * @param messages     已组装的上下文消息 (system 中已含角色人设+记忆)
     * @param temperature  采样温度
     * @param maxTokens    最大输出 token
     * @param ragKbId      知识库 ID (可空, Python 侧负责检索并注入)
     * @param userQuery    用户原始输入 (用于 RAG 检索, 可空)
     * @param ragEmbeddingModel 知识库锁定的 Embedding 模型 (可空, 检索必须与入库一致)
     * @param cancellation 停止生成句柄 (可空); 取消时断开连接, 读循环退出且不触发 onError
     * @param callback     SSE 回调
     */
    public void streamChat(
            String conversationId,
            String userId,
            String characterId,
            LlmModelConfig modelConfig,
            List<LlmMessage> messages,
            double temperature,
            int maxTokens,
            String ragKbId,
            String userQuery,
            String ragEmbeddingModel,
            StreamCancellation cancellation,
            SseCallback callback
    ) {
        try {
            Map<String, Object> body = Map.of(
                    "conversationId", conversationId != null ? conversationId : "",
                    "userId", userId != null ? userId : "",
                    "characterId", characterId != null ? characterId : "",
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
                    "userQuery", userQuery != null ? userQuery : "",
                    "ragEmbeddingModel", ragEmbeddingModel != null ? ragEmbeddingModel : ""
            );

            String json = objectMapper.writeValueAsString(body);
            HttpURLConnection conn = openConnection("/ai/chat/stream", "POST");
            if (cancellation != null) {
                cancellation.register(conn);
            }
            writeBody(conn, json);

            int code = conn.getResponseCode();
            if (code != 200) {
                String err = readBody(conn);
                callback.onError(51001, "AI 服务错误: " + extractAiError(err, code));
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
            if (cancellation != null && cancellation.isCancelled()) {
                log.info("AI 流式调用已被停止 (主动取消)");
                return;
            }
            log.error("调用 AI 服务失败", e);
            callback.onError(51001, "AI 服务连接失败: " + e.getMessage());
        }
    }

    /**
     * 记忆提取: 调用 Python /ai/memory/extract
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> extractMemory(
            LlmModelConfig modelConfig, String dialog,
            Long userId, Long characterId, Long conversationId
    ) {
        try {
            Map<String, Object> body = Map.of(
                    "modelConfig", Map.of(
                            "modelCode", modelConfig.modelCode(),
                            "baseUrl", modelConfig.baseUrl(),
                            "apiKey", modelConfig.apiKey()
                    ),
                    "dialog", dialog,
                    "userId", userId != null ? String.valueOf(userId) : "",
                    "characterId", characterId != null ? String.valueOf(characterId) : "",
                    "conversationId", conversationId != null ? String.valueOf(conversationId) : ""
            );
            String json = objectMapper.writeValueAsString(body);
            HttpURLConnection conn = openConnection("/ai/memory/extract", "POST");
            writeBody(conn, json);
            int code = conn.getResponseCode();
            String resp = readBody(conn);
            if (code != 200) {
                throw new IOException("AI 记忆提取失败: " + extractAiError(resp, code));
            }
            Map<String, Object> result = objectMapper.readValue(resp, Map.class);
            return (List<Map<String, Object>>) result.get("memories");
        } catch (Exception e) {
            log.warn("记忆提取失败 (静默): {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 文档向量化: 调用 Python /ai/rag/process
     *
     * @param kbEmbeddingModel 知识库已锁定的 Embedding 模型 (首次上传为 null)
     * @param kbEmbeddingDim   知识库已锁定的向量维度 (首次上传为 null)
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> processDocument(
            String kbId, String docId, String fileName, byte[] fileContent,
            LlmModelConfig modelConfig, String kbEmbeddingModel, Integer kbEmbeddingDim
    ) {
        try {
            String base64 = java.util.Base64.getEncoder().encodeToString(fileContent);
            Map<String, Object> body = new java.util.HashMap<>();
            body.put("kbId", kbId);
            body.put("docId", docId);
            body.put("fileName", fileName);
            body.put("fileContentBase64", base64);
            body.put("modelConfig", Map.of(
                    "modelCode", modelConfig.modelCode(),
                    "baseUrl", modelConfig.baseUrl(),
                    "apiKey", modelConfig.apiKey()
            ));
            if (kbEmbeddingModel != null) {
                body.put("kbEmbeddingModel", kbEmbeddingModel);
            }
            if (kbEmbeddingDim != null) {
                body.put("kbEmbeddingDim", kbEmbeddingDim);
            }
            String json = objectMapper.writeValueAsString(body);
            HttpURLConnection conn = openConnection("/ai/rag/process", "POST");
            writeBody(conn, json);
            int code = conn.getResponseCode();
            String resp = readBody(conn);
            if (code != 200) {
                return Map.of("chunkCount", 0, "status", "ERROR",
                        "errorMsg", "AI 服务错误: " + extractAiError(resp, code));
            }
            return objectMapper.readValue(resp, Map.class);
        } catch (Exception e) {
            log.error("文档向量化失败: {}", e.getMessage());
            return Map.of("chunkCount", 0, "status", "ERROR", "errorMsg", e.getMessage());
        }
    }

    /**
     * 删除向量数据: 调用 Python /ai/rag/vectors
     *
     * <p>有限重试后仍失败则抛出: 调用方处于事务中, 抛错会连同 MySQL 元数据
     * 删除一起回滚, 避免"库里记录删了、Qdrant 残留孤儿向量"。
     */
    public void deleteRagVectors(String kbId, String docId) {
        String urlPath = "/ai/rag/vectors?kbId=" + kbId;
        if (docId != null) {
            urlPath += "&docId=" + docId;
        }
        Exception lastError = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                HttpURLConnection conn = openConnection(urlPath, "DELETE");
                int code = conn.getResponseCode();
                conn.disconnect();
                if (code >= 200 && code < 300) {
                    return;
                }
                lastError = new IOException("AI 服务返回错误码 " + code);
            } catch (Exception e) {
                lastError = e;
            }
            log.warn("删除向量数据失败 (第 {}/3 次): {}", attempt, lastError.getMessage());
        }
        throw new BizException(ResultCode.SYSTEM_ERROR,
                "向量清理失败, 删除操作已中止, 请稍后重试: " + lastError.getMessage());
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

    /**
     * 读取响应体: 非 2xx 时读错误流 (getInputStream 对错误码直接抛异常, 会丢失真实错误信息)
     */
    private String readBody(HttpURLConnection conn) throws Exception {
        int code = conn.getResponseCode();
        java.io.InputStream stream = code >= 200 && code < 300
                ? conn.getInputStream() : conn.getErrorStream();
        if (stream == null) {
            return "";
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    /**
     * 从 Python 错误响应体提取业务信息: 优先 JSON {"code","message"} (AiError 统一格式),
     * 非 JSON (如 422 校验错误) 时截断原文, 兜底返回 HTTP 状态码
     */
    private String extractAiError(String body, int httpCode) {
        if (body == null || body.isBlank()) {
            return "HTTP " + httpCode;
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(body, Map.class);
            Object msg = parsed.get("message");
            if (msg != null) {
                Object c = parsed.get("code");
                return c != null ? "[" + c + "] " + msg : String.valueOf(msg);
            }
        } catch (Exception ignored) {
            // 非 JSON 响应, 使用原文
        }
        String raw = body.strip();
        return raw.length() > 200 ? raw.substring(0, 200) : raw;
    }
}
