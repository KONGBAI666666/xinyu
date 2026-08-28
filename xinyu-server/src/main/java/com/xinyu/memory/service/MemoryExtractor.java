package com.xinyu.memory.service;

import com.xinyu.llm.AiServiceClient;
import com.xinyu.llm.LlmModelConfig;
import com.xinyu.llm.dto.LlmMessage;
import com.xinyu.memory.enums.MemoryImportance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * 记忆提取器 (重构: LLM 调用委托给 Python xinyu-ai 服务)
 *
 * <p>流程:
 * <ol>
 *   <li>Java 格式化对话文本</li>
 *   <li>调用 Python /ai/memory/extract (Python 负责 LLM 调用 + JSON 解析)</li>
 *   <li>Java 将结构化记忆存入 MySQL</li>
 * </ol>
 *
 * <p>独立线程池 memoryExecutor 与 chatExecutor 隔离, 避免提取任务挤占 SSE 推送线程。
 */
@Slf4j
@Component
public class MemoryExtractor {

    /** 提取时最多回看的消息条数（控制 prompt 长度 + 成本） */
    private static final int RECENT_WINDOW = 10;

    private final MemoryService memoryService;
    private final AiServiceClient aiServiceClient;
    private final Executor memoryExecutor;

    public MemoryExtractor(MemoryService memoryService,
                           AiServiceClient aiServiceClient,
                           @Qualifier("memoryExecutor") Executor memoryExecutor) {
        this.memoryService = memoryService;
        this.aiServiceClient = aiServiceClient;
        this.memoryExecutor = memoryExecutor;
    }

    /**
     * 异步提取记忆（不阻塞聊天主链路）
     *
     * @param userId        用户 ID
     * @param characterId   角色 ID
     * @param conversationId 来源会话 ID
     * @param context       本次对话的完整上下文（含 system + 历史）
     * @param modelConfig   解密后的模型配置 (传给 Python)
     */
    public void extractAsync(Long userId, Long characterId, Long conversationId,
                             List<LlmMessage> context, LlmModelConfig modelConfig) {
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
                doExtract(userId, characterId, conversationId, finalDialog, modelConfig);
            } catch (Exception e) {
                log.warn("记忆提取失败（静默忽略）: userId={}, characterId={}, convId={}, cause={}",
                        userId, characterId, conversationId, e.getMessage());
            }
        });
    }

    private void doExtract(Long userId, Long characterId, Long conversationId,
                           List<LlmMessage> dialog, LlmModelConfig modelConfig) {
        // 1. 格式化对话文本
        String dialogText = formatDialog(dialog);

        // 2. 调用 Python 提取记忆 (Python 负责 LLM 调用 + JSON 解析)
        List<Map<String, Object>> memories = aiServiceClient.extractMemory(
                modelConfig, dialogText, userId, characterId, conversationId);
        if (memories == null || memories.isEmpty()) {
            return;
        }

        // 3. 转换为 ExtractedMemory 并存入 MySQL
        List<MemoryService.ExtractedMemory> extracted = memories.stream()
                .map(this::toExtractedMemory)
                .filter(m -> !m.content().isBlank())
                .toList();
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

    @SuppressWarnings("unchecked")
    private MemoryService.ExtractedMemory toExtractedMemory(Map<String, Object> map) {
        String key = (String) map.get("memoryKey");
        if (key == null) {
            key = (String) map.get("memory_key");
        }
        String content = (String) map.get("content");
        if (content == null) {
            content = "";
        }
        String importanceStr = (String) map.getOrDefault("importance", "LOW");
        MemoryImportance importance;
        try {
            importance = MemoryImportance.valueOf(importanceStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            importance = MemoryImportance.LOW;
        }
        return new MemoryService.ExtractedMemory(key, content.trim(), importance);
    }
}
