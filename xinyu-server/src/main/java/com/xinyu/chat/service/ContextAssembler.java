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
 * 上下文组装器: 角色 System Prompt + [M3 记忆] + 最近 N 条历史 + 当前输入
 *
 * <p>上下文完全服务端组装, 前端只传当前输入。
 * M3 在 system 与历史之间追加长期记忆注入。
 */
@Component
public class ContextAssembler {

    /** 上下文窗口: 最近 20 条消息（约 10 轮对话）, M3 换 token 预算制 */
    public static final int MAX_CONTEXT_MESSAGES = 20;

    /**
     * 组装 LLM 上下文
     *
     * @param character 角色（提供 system prompt）
     * @param history   最近历史（升序, 已含刚落库的当前 USER 消息与 GENERATING 占位）
     */
    public List<LlmMessage> assemble(AiCharacter character, List<Message> history) {
        List<LlmMessage> messages = new ArrayList<>();
        messages.add(LlmMessage.system(character.getSystemPrompt()));

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
