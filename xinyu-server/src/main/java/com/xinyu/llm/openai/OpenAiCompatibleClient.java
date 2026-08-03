package com.xinyu.llm.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xinyu.common.result.ResultCode;
import com.xinyu.llm.LlmClient;
import com.xinyu.llm.LlmException;
import com.xinyu.llm.LlmModelConfig;
import com.xinyu.llm.LlmStreamCallback;
import com.xinyu.llm.LlmUsage;
import com.xinyu.llm.dto.LlmMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * OpenAI 兼容协议客户端（动态配置, 一个实例对应一个用户模型配置）
 *
 * <p>支持所有 OpenAI 兼容接口: 通义千问 / DeepSeek / GPT / Kimi / GLM / Ollama 等。
 * 不依赖 @Profile, 由 {@link com.xinyu.llm.LlmClientFactory} 按需创建并缓存。
 *
 * <p>两类 IO 异常语义必须分离（状态机正确性的关键）:
 * <ul>
 *   <li>上游断流（模型侧读失败）→ 转 {@link LlmException} 回调 onError → FAILED</li>
 *   <li>下游断连（onDelta 内 SSE 写失败抛 UncheckedIOException）→ 原样传播 → STOPPED</li>
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
public class OpenAiCompatibleClient implements LlmClient {

    /** OpenAI 兼容流式行前缀 */
    private static final String DATA_PREFIX = "data:";

    /** 流结束标记 */
    private static final String DONE_MARK = "[DONE]";

    private final LlmModelConfig config;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    /** 连接超时 */
    private static final long CONNECT_TIMEOUT_MS = 10_000;

    /** 读超时（响应头到达） */
    private static final long READ_TIMEOUT_MS = 60_000;

    public OpenAiCompatibleClient(LlmModelConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(CONNECT_TIMEOUT_MS))
                .build();
        log.debug("OpenAiCompatibleClient 创建: model={}, baseUrl={}",
                config.modelCode(), config.baseUrl());
    }

    @Override
    public String modelCode() {
        return config.modelCode();
    }

    @Override
    public void streamChat(List<LlmMessage> messages, LlmStreamCallback callback) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.baseUrl() + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + config.apiKey())
                .timeout(Duration.ofMillis(READ_TIMEOUT_MS))
                .POST(HttpRequest.BodyPublishers.ofString(buildRequestBody(messages)))
                .build();

        try {
            HttpResponse<Stream<String>> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofLines());
            try (Stream<String> lines = response.body()) {
                if (response.statusCode() != 200) {
                    throw mapHttpError(response.statusCode(),
                            lines.collect(Collectors.joining()));
                }
                LlmUsage usage = consumeStream(lines, callback);
                callback.onComplete(usage);
            }
        } catch (LlmException e) {
            callback.onError(e);
        } catch (HttpConnectTimeoutException e) {
            callback.onError(new LlmException(ResultCode.LLM_CONNECT_ERROR, e));
        } catch (HttpTimeoutException e) {
            callback.onError(new LlmException(ResultCode.LLM_TIMEOUT, e));
        } catch (IOException e) {
            callback.onError(new LlmException(ResultCode.LLM_CONNECT_ERROR, e));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            callback.onError(new LlmException(ResultCode.LLM_CONNECT_ERROR, e));
        }
    }

    /**
     * 消费流式行, 逐段回调 onDelta
     *
     * @return 本次调用用量（末块 usage 提供; 缺失时 completion 按字符数粗估）
     */
    private LlmUsage consumeStream(Stream<String> lines, LlmStreamCallback callback) {
        Iterator<String> iterator = lines.iterator();
        int promptTokens = 0;
        int completionTokens = -1;
        int totalChars = 0;

        while (true) {
            String line;
            try {
                if (!iterator.hasNext()) {
                    break;
                }
                line = iterator.next();
            } catch (UncheckedIOException e) {
                throw new LlmException(ResultCode.LLM_CONNECT_ERROR, e.getCause());
            }

            if (!line.startsWith(DATA_PREFIX)) {
                continue;
            }
            String payload = line.substring(DATA_PREFIX.length()).trim();
            if (payload.isEmpty() || DONE_MARK.equals(payload)) {
                continue;
            }

            JsonNode chunk = parseChunk(payload);
            String delta = chunk.path("choices").path(0).path("delta").path("content").asText("");
            if (!delta.isEmpty()) {
                totalChars += delta.length();
                callback.onDelta(delta);
            }
            JsonNode usage = chunk.path("usage");
            if (usage.hasNonNull("completion_tokens")) {
                completionTokens = usage.path("completion_tokens").asInt();
            }
            if (usage.hasNonNull("prompt_tokens")) {
                promptTokens = usage.path("prompt_tokens").asInt();
            }
        }
        int finalCompletion = completionTokens >= 0 ? completionTokens : totalChars;
        return new LlmUsage(promptTokens, finalCompletion);
    }

    private String buildRequestBody(List<LlmMessage> messages) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", config.modelCode());
        body.put("stream", true);
        body.putObject("stream_options").put("include_usage", true);
        ArrayNode array = body.putArray("messages");
        for (LlmMessage message : messages) {
            array.addObject()
                    .put("role", message.role())
                    .put("content", message.content());
        }
        return body.toString();
    }

    private JsonNode parseChunk(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (IOException e) {
            throw new LlmException(ResultCode.LLM_CONNECT_ERROR, e);
        }
    }

    private LlmException mapHttpError(int status, String body) {
        log.error("LLM 接口异常: model={}, status={}, body={}",
                config.modelCode(), status, body);
        if (status == 400) {
            String lower = body.toLowerCase();
            if (lower.contains("data_inspection") || lower.contains("inappropriate")) {
                return new LlmException(ResultCode.LLM_CONTENT_BLOCKED, body);
            }
            if (lower.contains("length") || lower.contains("token")) {
                return new LlmException(ResultCode.LLM_TOKEN_LIMIT, body);
            }
        }
        return new LlmException(ResultCode.LLM_CONNECT_ERROR, "HTTP " + status + ": " + body);
    }
}
