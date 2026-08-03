package com.xinyu.memory.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xinyu.llm.LlmClient;
import com.xinyu.llm.LlmException;
import com.xinyu.llm.LlmResponse;
import com.xinyu.llm.dto.LlmMessage;
import com.xinyu.memory.enums.MemoryImportance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * 记忆提取器: 每轮 ASSISTANT 完成后异步提取长期记忆
 *
 * <p>提取策略（M2.1 决策）:
 * <ul>
 *   <li>触发: ASSISTANT 消息 COMPLETED 后, 由 ChatServiceImpl 投递到 memoryExecutor</li>
 *   <li>模型: 复用当前会话的 LlmClient（用户当前用什么模型就用什么模型提取）</li>
 *   <li>输入: 最近 N 轮 USER+ASSISTANT 对话（过滤 SYSTEM）</li>
 *   <li>输出: JSON 数组 [{memory_key, content, importance}]</li>
 *   <li>失败: 静默, 只 log warn, 不影响聊天主链路</li>
 * </ul>
 *
 * <p>独立线程池 memoryExecutor 与 chatExecutor 隔离, 避免提取任务挤占 SSE 推送线程。
 */
@Slf4j
@Component
public class MemoryExtractor {

    /** 提取时最多回看的消息条数（控制 prompt 长度 + 成本） */
    private static final int RECENT_WINDOW = 10;

    /** 提取 prompt 模板 */
    private static final String EXTRACT_SYSTEM_PROMPT = """
            你是一个记忆提取助手。从下面的对话中提取关于用户的长期记忆（偏好、习惯、背景、身份、重要事实）。

            要求:
            1. 只提取有长期价值的记忆, 忽略临时话题、寒暄、本次任务细节
            2. 每条记忆用简短陈述句, 如 "用户喜欢 Java 编程"
            3. memory_key 从以下选一个: name / job / location / hobby / preference / personality / relationship / goal / fact
            4. importance: HIGH（核心身份: 姓名/职业/重要关系）/ MEDIUM（偏好习惯）/ LOW（次要事实）
            5. 没有可提取的记忆时返回 []

            只返回 JSON 数组, 不要任何解释:
            [{"memory_key": "hobby", "content": "用户喜欢 Java 编程", "importance": "MEDIUM"}]
            """;

    private final MemoryService memoryService;
    private final ObjectMapper objectMapper;
    private final Executor memoryExecutor;

    public MemoryExtractor(MemoryService memoryService,
                           ObjectMapper objectMapper,
                           @Qualifier("memoryExecutor") Executor memoryExecutor) {
        this.memoryService = memoryService;
        this.objectMapper = objectMapper;
        this.memoryExecutor = memoryExecutor;
    }

    /**
     * 异步提取记忆（不阻塞聊天主链路）
     *
     * @param userId        用户 ID
     * @param characterId   角色 ID
     * @param conversationId 来源会话 ID（可溯源）
     * @param context       本次对话的完整上下文（含 system + 历史）, 提取器内部过滤 system
     * @param llmClient     复用当前会话的 LLM client
     */
    public void extractAsync(Long userId, Long characterId, Long conversationId,
                             List<LlmMessage> context, LlmClient llmClient) {
        // 过滤 system, 只保留 user/assistant, 最多 RECENT_WINDOW 条
        List<LlmMessage> dialog = context.stream()
                .filter(m -> !"system".equals(m.role()))
                .toList();
        if (dialog.size() > RECENT_WINDOW) {
            dialog = dialog.subList(dialog.size() - RECENT_WINDOW, dialog.size());
        }
        // 至少要有 2 条（一问一答）才值得提取
        if (dialog.size() < 2) {
            return;
        }

        final List<LlmMessage> finalDialog = dialog;
        memoryExecutor.execute(() -> {
            try {
                doExtract(userId, characterId, conversationId, finalDialog, llmClient);
            } catch (Exception e) {
                log.warn("记忆提取失败（静默忽略）: userId={}, characterId={}, convId={}, cause={}",
                        userId, characterId, conversationId, e.getMessage());
            }
        });
    }

    private void doExtract(Long userId, Long characterId, Long conversationId,
                           List<LlmMessage> dialog, LlmClient llmClient) {
        List<LlmMessage> prompt = new ArrayList<>();
        prompt.add(LlmMessage.system(EXTRACT_SYSTEM_PROMPT));
        prompt.add(LlmMessage.user(formatDialog(dialog)));

        LlmResponse response;
        try {
            response = llmClient.chat(prompt);
        } catch (LlmException e) {
            log.warn("记忆提取 LLM 调用失败: code={}, msg={}", e.getResultCode().getCode(), e.getMessage());
            return;
        }
        if (response == null || response.content() == null || response.content().isBlank()) {
            return;
        }

        List<MemoryService.ExtractedMemory> extracted = parseResponse(response.content());
        if (extracted.isEmpty()) {
            return;
        }
        memoryService.saveExtracted(userId, characterId, conversationId, extracted);
        log.info("记忆提取成功: userId={}, characterId={}, 条数={}", userId, characterId, extracted.size());
    }

    /** 把对话拼成纯文本给提取模型 */
    private String formatDialog(List<LlmMessage> dialog) {
        StringBuilder sb = new StringBuilder();
        for (LlmMessage m : dialog) {
            String role = "user".equals(m.role()) ? "用户" : "AI";
            sb.append(role).append(": ").append(m.content()).append("\n");
        }
        return sb.toString();
    }

    /**
     * 解析模型返回的 JSON 数组, 容错处理:
     * - 去掉 ```json / ``` 包裹
     * - 截取第一个 [ 到最后一个 ]
     */
    List<MemoryService.ExtractedMemory> parseResponse(String content) {
        String json = extractJsonArray(content);
        if (json == null) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            if (!root.isArray()) {
                return List.of();
            }
            List<MemoryService.ExtractedMemory> result = new ArrayList<>();
            for (JsonNode item : root) {
                String key = item.path("memory_key").asText(null);
                String text = item.path("content").asText("");
                String importanceStr = item.path("importance").asText("LOW").toUpperCase();
                if (text.isBlank()) {
                    continue;
                }
                MemoryImportance importance;
                try {
                    importance = MemoryImportance.valueOf(importanceStr);
                } catch (IllegalArgumentException e) {
                    importance = MemoryImportance.LOW;
                }
                result.add(new MemoryService.ExtractedMemory(key, text.trim(), importance));
            }
            return result;
        } catch (Exception e) {
            log.warn("记忆 JSON 解析失败: {}", e.getMessage());
            return List.of();
        }
    }

    /** 从模型输出中截取 JSON 数组片段 */
    private String extractJsonArray(String content) {
        String s = content.trim();
        // 去掉 markdown 代码块包裹
        if (s.startsWith("```")) {
            s = s.replaceAll("^```\\w*\\s*", "").replaceAll("\\s*```$", "").trim();
        }
        int start = s.indexOf('[');
        int end = s.lastIndexOf(']');
        if (start < 0 || end < 0 || end <= start) {
            return null;
        }
        return s.substring(start, end + 1);
    }
}
