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
import com.xinyu.llm.LlmClient;
import com.xinyu.llm.LlmStreamCallback;
import com.xinyu.llm.dto.LlmMessage;
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
 * 聊天编排实现
 *
 * <p>SSE 主链路（chat 方法）:
 * <pre>
 * 请求线程: 鉴权校验 → USER消息落库 → ASSISTANT占位(GENERATING)落库
 *          → 组装上下文 → 返回 SseEmitter
 * 异步线程: meta → LLM流式回调 delta* → done(COMPLETED) | error(FAILED)
 *          前端断连(IOException) → STOPPED, 保留已生成文本
 * </pre>
 *
 * <p>状态流转全部用条件更新（仅 GENERATING 可迁出）, 保证
 * 完成/失败/停止三种终态互斥, 不会互相覆盖。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    /** SSE 超时: 3 分钟（覆盖长回复; Mock 秒级, 为真实 LLM 预留） */
    private static final long SSE_TIMEOUT_MS = 180_000L;

    /** 会话标题自动截取长度（取首条用户消息前20字） */
    private static final int TITLE_MAX_LEN = 20;

    private final ConversationService conversationService;

    private final MessageService messageService;

    private final CharacterService characterService;

    private final LlmClient llmClient;

    private final ContextAssembler contextAssembler;

    @Qualifier("chatExecutor")
    private final Executor chatExecutor;

    @Override
    @Transactional
    public ConversationVO createConversation(Long userId, ConversationCreateDTO dto) {
        AiCharacter character = characterService.getById(dto.getCharacterId());
        if (character == null) {
            throw new BizException(ResultCode.NOT_FOUND, "角色不存在");
        }

        Conversation conversation = new Conversation();
        conversation.setUserId(userId);
        conversation.setCharacterId(character.getId());
        conversation.setTitle(StringUtils.hasText(dto.getTitle())
                ? dto.getTitle() : character.getName());
        conversation.setLastMessageAt(LocalDateTime.now());
        conversation.setLastMessagePreview(character.getGreeting());
        conversationService.save(conversation);

        // greeting 作为首条 ASSISTANT 消息, 让新会话一进来就有开场白
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

        // 1. USER 消息落库（幂等: uk(user_id, client_message_id)）
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

        // 首条用户消息且标题仍是默认角色名 → 自动改为消息前20字
        if (seq == 2 && conversation.getTitle().equals(character.getName())) {
            Conversation rename = new Conversation();
            rename.setId(conversationId);
            rename.setTitle(dto.getContent().length() > TITLE_MAX_LEN
                    ? dto.getContent().substring(0, TITLE_MAX_LEN) : dto.getContent());
            conversationService.updateById(rename);
        }
        conversationService.refreshLastMessage(conversationId, dto.getContent(), LocalDateTime.now());

        // 2. ASSISTANT 占位先落 GENERATING（SSE 中断容错: 任何时刻库中都有这条消息可追溯）
        Message assistantMsg = new Message();
        assistantMsg.setConversationId(conversationId);
        assistantMsg.setUserId(userId);
        assistantMsg.setSequenceNo(seq + 1);
        assistantMsg.setParentMessageId(userMsg.getId());
        assistantMsg.setMessageType(MessageRole.ASSISTANT);
        assistantMsg.setContent("");
        assistantMsg.setStatus(MessageStatus.GENERATING);
        assistantMsg.setModelCode("mock");
        messageService.save(assistantMsg);

        // 3. 上下文在请求线程组装（含刚落库的 USER 消息）, 异步线程不依赖 ThreadLocal
        List<LlmMessage> context = contextAssembler.assemble(character,
                messageService.listRecent(conversationId, ContextAssembler.MAX_CONTEXT_MESSAGES));

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        // 超时兜底: 仍在 GENERATING 则按 STOPPED 收尾（条件更新, 不覆盖终态）
        emitter.onTimeout(() -> markStopped(assistantMsg.getId(), conversationId, null));

        chatExecutor.execute(() -> streamAndPersist(emitter, conversationId, userMsg, assistantMsg, context));
        return emitter;
    }

    /**
     * 异步流式推送并持久化（SSE 生命周期全在此方法内闭环）
     */
    private void streamAndPersist(SseEmitter emitter, Long conversationId,
                                  Message userMsg, Message assistantMsg, List<LlmMessage> context) {
        StringBuilder generated = new StringBuilder();
        try {
            sendEvent(emitter, "meta",
                    new SseMetaVO(String.valueOf(userMsg.getId()), String.valueOf(assistantMsg.getId())));

            llmClient.streamChat(context, new LlmStreamCallback() {
                @Override
                public void onDelta(String delta) {
                    generated.append(delta);
                    sendEvent(emitter, "delta", new SseDeltaVO(delta));
                }

                @Override
                public void onComplete(int completionTokens) {
                    // 先落库再推 done: 即使 done 推送失败, 消息也已是 COMPLETED 终态
                    String content = generated.toString();
                    messageService.lambdaUpdate()
                            .eq(Message::getId, assistantMsg.getId())
                            .eq(Message::getStatus, MessageStatus.GENERATING)
                            .set(Message::getContent, content)
                            .set(Message::getStatus, MessageStatus.COMPLETED)
                            .set(Message::getCompletionTokens, completionTokens)
                            .update();
                    conversationService.refreshLastMessage(conversationId, content, LocalDateTime.now());
                    sendEvent(emitter, "done",
                            new SseDoneVO(String.valueOf(assistantMsg.getId()), completionTokens,
                                    MessageStatus.COMPLETED.name()));
                    emitter.complete();
                }

                @Override
                public void onError(Throwable cause) {
                    log.error("LLM 生成失败: assistantMessageId={}", assistantMsg.getId(), cause);
                    messageService.lambdaUpdate()
                            .eq(Message::getId, assistantMsg.getId())
                            .eq(Message::getStatus, MessageStatus.GENERATING)
                            .set(Message::getContent, generated.toString())
                            .set(Message::getStatus, MessageStatus.FAILED)
                            .update();
                    sendEvent(emitter, "error",
                            new SseErrorVO(ResultCode.LLM_CONNECT_ERROR.getCode(),
                                    ResultCode.LLM_CONNECT_ERROR.getMessage()));
                    emitter.complete();
                }
            });
        } catch (UncheckedIOException e) {
            // 前端 AbortController 断连 → STOPPED, 保留已生成文本（设计约定: 不做独立 stop 接口）
            log.info("SSE 客户端断连, 消息置 STOPPED: assistantMessageId={}", assistantMsg.getId());
            markStopped(assistantMsg.getId(), conversationId, generated.toString());
            emitter.complete();
        } catch (Exception e) {
            // 兜底: 编排自身异常按 FAILED 收尾（条件更新, 若已是终态则不动）
            log.error("SSE 编排异常: assistantMessageId={}", assistantMsg.getId(), e);
            messageService.lambdaUpdate()
                    .eq(Message::getId, assistantMsg.getId())
                    .eq(Message::getStatus, MessageStatus.GENERATING)
                    .set(Message::getStatus, MessageStatus.FAILED)
                    .update();
            emitter.completeWithError(e);
        }
    }

    /** GENERATING → STOPPED（条件更新, content 为 null 时不覆盖已有文本） */
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

    /** SSE 发送, IOException 统一转 UncheckedIOException 由主流程按断连处理 */
    private void sendEvent(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 会话归属校验: 不存在与越权统一 40400, 不暴露资源存在性 */
    private Conversation requireOwned(Long conversationId, Long userId) {
        Conversation conversation = conversationService.getOwned(conversationId, userId);
        if (conversation == null) {
            throw new BizException(ResultCode.NOT_FOUND, "会话不存在");
        }
        return conversation;
    }
}
