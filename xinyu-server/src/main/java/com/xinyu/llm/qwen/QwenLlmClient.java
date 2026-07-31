package com.xinyu.llm.qwen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xinyu.common.result.ResultCode;
import com.xinyu.llm.LlmClient;
import com.xinyu.llm.LlmException;
import com.xinyu.llm.LlmStreamCallback;
import com.xinyu.llm.config.LlmProperties;
import com.xinyu.llm.dto.LlmMessage;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

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
 * 通义千问客户端（OpenAI 兼容模式, prod 环境注册）
 *
 * <p>不引官方 SDK, 直接以 JDK HttpClient 调 DashScope 的
 * /chat/completions 流式接口: 换 DeepSeek/OpenAI 只需改 base-url + model,
 * LlmClient 接口与聊天编排层零改动。
 *
 * <p>两类 IO 异常语义必须分离（状态机正确性的关键）:
 * <ul>
 *   <li>上游断流（千问侧读失败）→ 转 {@link LlmException} 回调 onError → FAILED</li>
 *   <li>下游断连（onDelta 内 SSE 写失败抛 UncheckedIOException）→ 按接口约定
 *       原样向上传播, 由编排层置 STOPPED</li>
 * </ul>
 */
@Slf4j
@Profile("prod")
@Component
@RequiredArgsConstructor
public class QwenLlmClient implements LlmClient {

    /** OpenAI 兼容流式行前缀 */
    private static final String DATA_PREFIX = "data:";

    /** 流结束标记 */
    private static final String DONE_MARK = "[DONE]";

    private final LlmProperties properties;

    private final ObjectMapper objectMapper;

    private HttpClient httpClient;

    /**
     * 启动即校验配置: Key 缺失直接启动失败, 不等到第一次聊天才暴露
     */
    @PostConstruct
    void init() {
        if (!StringUtils.hasText(properties.apiKey())) {
            throw new IllegalStateException(
                    "xinyu.llm.api-key 未配置: 请设置环境变量 DASHSCOPE_API_KEY 后重启");
        }
        if (!StringUtils.hasText(properties.baseUrl()) || !StringUtils.hasText(properties.model())) {
            throw new IllegalStateException("xinyu.llm.base-url / model 未配置");
        }
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.connectTimeoutMs()))
                .build();
        log.info("QwenLlmClient 就绪: model={}, baseUrl={}", properties.model(), properties.baseUrl());
    }

    @Override
    public String modelCode() {
        return properties.model();
    }

    @Override
    public void streamChat(List<LlmMessage> messages, LlmStreamCallback callback) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(properties.baseUrl() + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + properties.apiKey())
                // 该超时覆盖建连到响应头到达; 流式 body 的总时长由 SSE 的 180s 兜底
                .timeout(Duration.ofMillis(properties.readTimeoutMs()))
                .POST(HttpRequest.BodyPublishers.ofString(buildRequestBody(messages)))
                .build();

        try {
            HttpResponse<Stream<String>> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofLines());
            // try-with-resources: onDelta 异常向上传播时同步关闭上游连接, 千问侧停止生成
            try (Stream<String> lines = response.body()) {
                if (response.statusCode() != 200) {
                    throw mapHttpError(response.statusCode(),
                            lines.collect(Collectors.joining()));
                }
                int completionTokens = consumeStream(lines, callback);
                callback.onComplete(completionTokens);
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
     * <p>迭代器的 hasNext/next 单独 try: 上游断流抛出的 UncheckedIOException
     * 在此转 LlmException（FAILED）; onDelta 抛出的异常不在 try 内, 原样传播（STOPPED）。
     *
     * @return completionTokens（末块 usage 提供; 缺失时按字符数粗估）
     */
    private int consumeStream(Stream<String> lines, LlmStreamCallback callback) {
        Iterator<String> iterator = lines.iterator();
        int usageTokens = -1;
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
            // stream_options.include_usage: 最后一个数据块携带 usage
            JsonNode usage = chunk.path("usage");
            if (usage.hasNonNull("completion_tokens")) {
                usageTokens = usage.path("completion_tokens").asInt();
            }
        }
        return usageTokens >= 0 ? usageTokens : totalChars;
    }

    /** 组装 OpenAI 兼容请求体（stream + include_usage 取真实 token 数） */
    private String buildRequestBody(List<LlmMessage> messages) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", properties.model());
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

    /**
     * HTTP 错误 → 510xx 错误码（错误码集在 M1-3 已冻结, 不新增）
     *
     * <p>401/403 Key 无效、429 限流按连接失败降级; 400 依据 DashScope
     * 错误体区分内容拦截（data_inspection）与上下文超长（length/token）。
     */
    private LlmException mapHttpError(int status, String body) {
        log.error("千问接口异常: status={}, body={}", status, body);
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
