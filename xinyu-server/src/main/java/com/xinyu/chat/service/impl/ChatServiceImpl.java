package com.xinyu.chat.service.impl;

import com.xinyu.character.entity.AiCharacter;
import com.xinyu.character.service.CharacterService;
import com.xinyu.chat.dto.ChatRequestDTO;
import com.xinyu.chat.dto.ConversationCreateDTO;
import com.xinyu.chat.service.ChatService;
import com.xinyu.chat.service.ContextAssembler;
import com.xinyu.chat.vo.SseDeltaVO;
import com.xinyu.chat.vo.SseDoneVO;
import com.xinyu.chat.vo.SseErrorVO;
import com.xinyu.chat.vo.SseMetaVO;
import com.xinyu.common.exception.BizException;
import com.xinyu.common.result.ResultCode;
import com.xinyu.conversation.entity.Conversation;
import com.xinyu.conversation.service.ConversationService;
import com.xinyu.conversation.vo.ConversationVO;
import com.xinyu.llm.AiServiceClient;
import com.xinyu.llm.LlmClientFactory;
import com.xinyu.llm.LlmModelConfig;
import com.xinyu.llm.dto.LlmMessage;
import com.xinyu.memory.service.MemoryExtractor;
import com.xinyu.memory.service.MemoryInjector;
import com.xinyu.message.entity.Message;
import com.xinyu.message.enums.MessageRole;
import com.xinyu.message.enums.MessageStatus;
import com.xinyu.message.service.MessageService;
import com.xinyu.message.vo.MessageVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * 聊天编排实现 (重构: AI 能力委托给 Python xinyu-ai 服务)
 *
 * <p>SSE 主链路:
 * <pre>
 * 请求线程: 鉴权 → USER消息落库 → ASSISTANT占位(GENERATING) → 组装上下文 → 返回 SseEmitter
 * 异步线程: meta → 调用 Python /ai/chat/stream → delta* → done(COMPLETED) | error(FAILED)
 *          前端断连 → STOPPED
 * </pre>
 *
 * <p>Java 负责业务 (消息持久化/归属校验/模型配置解密), Python 负责AI (LLM/RAG/Memory)。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private static final long SSE_TIMEOUT_MS = 180_000L;
    private static final int TITLE_MAX_LEN = 20;

    private final ConversationService conversationService;
    private final MessageService messageService;
    private final CharacterService characterService;
    private final LlmClientFactory llmClientFactory;
    private final ContextAssembler contextAssembler;
    private final MemoryInjector memoryInjector;
    private final MemoryExtractor memoryExtractor;
    private final AiServiceClient aiServiceClient;

    @Qualifier("chatExecutor")
    private final Executor chatExecutor;

    @Override
    @Transactional
    public ConversationVO createConversation(Long userId, ConversationCreateDTO dto) {
        AiCharacter character = characterService.getChattable(dto.getCharacterId(), userId);
        if (character == null) {
            throw new BizException(ResultCode.NOT_FOUND, "角色不存在或不可用");
        }

        Conversation conversation = new Conversation();
        conversation.setUserId(userId);
        conversation.setCharacterId(character.getId());
        conversation.setKbId(dto.getKbId());
        conversation.setTitle(StringUtils.hasText(dto.getTitle())
                ? dto.getTitle() : character.getName());
        conversation.setLastMessageAt(LocalDateTime.now());
        conversation.setLastMessagePreview(character.getGreeting());
        conversationService.save(conversation);

        Message greeting = new Message();
        greeting.setConversationId(conversation.getId());
        greeting.setUserId(userId);
        greeting.setSequenceNo(1);
        greeting.setMessageType(MessageRole.ASSISTANT);
        greeting.setContent(character.getGreeting());
        greeting.setStatus(MessageStatus.COMPLETED);
        messageService.save(greeting);

        return ConversationVO.from(conversation);
    }

    @Override
    public List<MessageVO> listMessages(Long userId, Long conversationId, Long before, Integer size) {
        requireOwned(conversationId, userId);
        int pageSize = size == null ? 20 : Math.min(Math.max(size, 1), 100);
        return messageService.listHistory(conversationId, before, pageSize).stream()
                .map(MessageVO::from)
                .toList();
    }

    @Override
    public SseEmitter chat(Long userId, Long conversationId, ChatRequestDTO dto) {
        Conversation conversation = requireOwned(conversationId, userId);
        AiCharacter character = characterService.getById(conversation.getCharacterId());
        if (character == null) {
            throw new BizException(ResultCode.NOT_FOUND, "角色不存在");
        }

        // 解析模型配置 (解密 API Key, 传给 Python)
        LlmModelConfig modelConfig = resolveModelConfig(conversation, userId);

        // 1. USER 消息落库
        int seq = messageService.nextSequenceNo(conversationId);
        Message userMsg = new Message();
        userMsg.setConversationId(conversationId);
        userMsg.setUserId(userId);
        userMsg.setSequenceNo(seq);
        userMsg.setClientMessageId(dto.getClientMessageId());
        userMsg.setMessageType(MessageRole.USER);
        userMsg.setContent(dto.getContent());
        userMsg.setStatus(MessageStatus.COMPLETED);
        try {
            messageService.save(userMsg);
        } catch (DuplicateKeyException e) {
            throw new BizException(ResultCode.PARAM_ERROR, "重复的消息请求");
        }

        // 首条用户消息自动改标题
        if (seq == 2 && conversation.getTitle().equals(character.getName())) {
            Conversation rename = new Conversation();
            rename.setId(conversationId);
            rename.setTitle(dto.getContent().length() > TITLE_MAX_LEN
                    ? dto.getContent().substring(0, TITLE_MAX_LEN) : dto.getContent());
            conversationService.updateById(rename);
        }
        conversationService.refreshLastMessage(conversationId, dto.getContent(), LocalDateTime.now());

        // 2. ASSISTANT 占位 (GENERATING)
        Message assistantMsg = new Message();
        assistantMsg.setConversationId(conversationId);
        assistantMsg.setUserId(userId);
        assistantMsg.setSequenceNo(seq + 1);
        assistantMsg.setParentMessageId(userMsg.getId());
        assistantMsg.setMessageType(MessageRole.ASSISTANT);
        assistantMsg.setContent("");
        assistantMsg.setStatus(MessageStatus.GENERATING);
        assistantMsg.setModelCode(modelConfig.modelCode());
        messageService.save(assistantMsg);

        // 3. 组装上下文 (Java 侧: system prompt + memory + history, Python 侧: RAG 检索 + LLM 调用)
        String memoryBlock = memoryInjector.inject(userId, conversation.getCharacterId());
        List<LlmMessage> context = contextAssembler.assemble(character, memoryBlock, "",
                messageService.listRecent(conversationId, ContextAssembler.MAX_CONTEXT_MESSAGES));

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emitter.onTimeout(() -> markStopped(assistantMsg.getId(), conversationId, null));

        // RAG 参数: 会话绑定了知识库时, 由 Python 负责检索 + 注入
        String ragKbId = conversation.getKbId() != null ? String.valueOf(conversation.getKbId()) : null;

        chatExecutor.execute(() -> streamAndPersist(emitter, conversationId, userId,
                conversation.getCharacterId(), userMsg, assistantMsg, context, modelConfig,
                ragKbId, dto.getContent(), memoryBlock,
                character.getTemperature() != null ? character.getTemperature().doubleValue() : 0.8,
                character.getMaxTokens() != null ? character.getMaxTokens() : 1024));
        return emitter;
    }

    /**
     * 解析模型配置 (解密后传给 Python)
     */
    private LlmModelConfig resolveModelConfig(Conversation conversation, Long userId) {
        if (conversation.getModelId() != null) {
            return llmClientFactory.resolveConfig(conversation.getModelId(), userId);
        }
        LlmModelConfig defaultConfig = llmClientFactory.resolveDefaultConfig(userId);
        if (defaultConfig != null) {
            return defaultConfig;
        }
        if (llmClientFactory.isDevProfile()) {
            // dev 环境: 传 dummy 配置, Python 的 mock 模式会兜底
            return new LlmModelConfig("mock", "mock", "mock");
        }
        throw new BizException(ResultCode.PARAM_ERROR,
                "尚未配置 AI 模型, 请先在「模型管理」添加一个模型");
    }

    /**
     * 异步流式推送: 调用 Python AI 服务, 转发 SSE 给前端
     */
    private void streamAndPersist(SseEmitter emitter, Long conversationId, Long userId,
                                  Long characterId, Message userMsg, Message assistantMsg,
                                  List<LlmMessage> context, LlmModelConfig modelConfig,
                                  String ragKbId, String userQuery, String memoryBlock,
                                  double temperature, int maxTokens) {
        StringBuilder generated = new StringBuilder();
        try {
            sendEvent(emitter, "meta",
                    new SseMetaVO(String.valueOf(userMsg.getId()), String.valueOf(assistantMsg.getId())));

            aiServiceClient.streamChat(modelConfig, context,
                    temperature, maxTokens,
                    ragKbId, userQuery, memoryBlock,
                    new AiServiceClient.SseCallback() {
                        @Override
                        public void onMeta(String um, String am) { /* meta 已发送 */ }

                        @Override
                        public void onDelta(String delta) {
                            generated.append(delta);
                            sendEvent(emitter, "delta", new SseDeltaVO(delta));
                        }

                        @Override
                        public void onDone(int promptTokens, int completionTokens, String status) {
                            String content = generated.toString();
                            messageService.lambdaUpdate()
                                    .eq(Message::getId, assistantMsg.getId())
                                    .eq(Message::getStatus, MessageStatus.GENERATING)
                                    .set(Message::getContent, content)
                                    .set(Message::getStatus, MessageStatus.COMPLETED)
                                    .set(Message::getPromptTokens, promptTokens)
                                    .set(Message::getCompletionTokens, completionTokens)
                                    .update();
                            conversationService.refreshLastMessage(conversationId, content, LocalDateTime.now());
                            sendEvent(emitter, "done",
                                    new SseDoneVO(String.valueOf(assistantMsg.getId()),
                                            promptTokens, completionTokens,
                                            MessageStatus.COMPLETED.name()));
                            emitter.complete();
                            // 异步触发记忆提取 (委托 MemoryExtractor → Python)
                            memoryExtractor.extractAsync(
                                    userId, characterId, conversationId, context, modelConfig);
                        }

                        @Override
                        public void onError(int code, String message) {
                            log.error("AI 生成失败: assistantMessageId={}, code={}, msg={}",
                                    assistantMsg.getId(), code, message);
                            messageService.lambdaUpdate()
                                    .eq(Message::getId, assistantMsg.getId())
                                    .eq(Message::getStatus, MessageStatus.GENERATING)
                                    .set(Message::getContent, generated.toString())
                                    .set(Message::getStatus, MessageStatus.FAILED)
                                    .update();
                            sendEvent(emitter, "error", new SseErrorVO(code, message));
                            emitter.complete();
                        }
                    });
        } catch (UncheckedIOException e) {
            log.info("SSE 客户端断连, 消息置 STOPPED: assistantMessageId={}", assistantMsg.getId());
            markStopped(assistantMsg.getId(), conversationId, generated.toString());
            emitter.complete();
        } catch (Exception e) {
            log.error("SSE 编排异常: assistantMessageId={}", assistantMsg.getId(), e);
            messageService.lambdaUpdate()
                    .eq(Message::getId, assistantMsg.getId())
                    .eq(Message::getStatus, MessageStatus.GENERATING)
                    .set(Message::getStatus, MessageStatus.FAILED)
                    .update();
            emitter.completeWithError(e);
        }
    }

    private void markStopped(Long messageId, Long conversationId, String content) {
        boolean updated = messageService.lambdaUpdate()
                .eq(Message::getId, messageId)
                .eq(Message::getStatus, MessageStatus.GENERATING)
                .set(content != null, Message::getContent, content)
                .set(Message::getStatus, MessageStatus.STOPPED)
                .update();
        if (updated && content != null && !content.isEmpty()) {
            conversationService.refreshLastMessage(conversationId, content, LocalDateTime.now());
        }
    }

    private void sendEvent(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Conversation requireOwned(Long conversationId, Long userId) {
        Conversation conversation = conversationService.getOwned(conversationId, userId);
        if (conversation == null) {
            throw new BizException(ResultCode.NOT_FOUND, "会话不存在");
        }
        return conversation;
    }

    @Override
    public void switchModel(Long userId, Long conversationId, Long modelId) {
        requireOwned(conversationId, userId);
        if (modelId != null) {
            llmClientFactory.resolveConfig(modelId, userId);
        }
        Conversation update = new Conversation();
        update.setId(conversationId);
        update.setModelId(modelId);
        conversationService.updateById(update);
    }
}
