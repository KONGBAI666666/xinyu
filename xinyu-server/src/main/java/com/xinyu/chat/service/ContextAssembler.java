package com.xinyu.chat.service;

import com.xinyu.character.entity.AiCharacter;
import com.xinyu.llm.dto.LlmMessage;
import com.xinyu.message.entity.Message;
import com.xinyu.message.enums.MessageStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 上下文组装器: 角色 System Prompt + [M2.1 记忆] + [M3 RAG 知识库] + 最近 N 条历史 + 当前输入
 *
 * <p>上下文完全服务端组装, 前端只传当前输入。
 * 记忆块由 {@link com.xinyu.memory.service.MemoryInjector} 拼好透传进来,
 * 知识库块由 Python AI 服务在 /ai/chat/stream 内部检索并注入 (Java 侧 ragBlock 传空串),
 * 两者均追加到 system prompt 末尾, 本类不依赖 memory/rag 模块（避免横向依赖）。
 */
@Component
public class ContextAssembler {

    /** 上下文窗口: 最近 20 条消息（约 10 轮对话）, M3 换 token 预算制 */
    public static final int MAX_CONTEXT_MESSAGES = 20;

    /**
     * 组装 LLM 上下文（含记忆 + RAG 知识库注入）
     *
     * @param character   角色（提供 system prompt）
     * @param memoryBlock 拼好的记忆文本块, 空字符串表示无记忆; 追加到 system prompt 末尾
     * @param ragBlock    拼好的知识库文本块, 空字符串表示无知识库; 追加到 system prompt 末尾 (在记忆块后)
     * @param history     最近历史（升序, 已含刚落库的当前 USER 消息与 GENERATING 占位）
     */
    public List<LlmMessage> assemble(AiCharacter character, String memoryBlock, String ragBlock, List<Message> history) {
        List<LlmMessage> messages = new ArrayList<>();
        String systemPrompt = character.getSystemPrompt();
        if (StringUtils.hasText(memoryBlock)) {
            systemPrompt = systemPrompt + memoryBlock;
        }
        if (StringUtils.hasText(ragBlock)) {
            systemPrompt = systemPrompt + ragBlock;
        }
        messages.add(LlmMessage.system(systemPrompt));

        for (Message msg : history) {
            switch (msg.getMessageType()) {
                case USER -> messages.add(LlmMessage.user(msg.getContent()));
                case ASSISTANT -> {
                    // GENERATING 占位/FAILED 无有效文本, 不进上下文
                    if (msg.getStatus() == MessageStatus.COMPLETED || msg.getStatus() == MessageStatus.STOPPED) {
                        if (StringUtils.hasText(msg.getContent())) {
                            messages.add(LlmMessage.assistant(msg.getContent()));
                        }
                    }
                }
                // SYSTEM 类消息是站内提示, 不参与模型对话
                case SYSTEM -> { }
            }
        }
        return messages;
    }
}
