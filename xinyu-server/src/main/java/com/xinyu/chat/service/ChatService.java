package com.xinyu.chat.service;

import com.xinyu.chat.dto.ChatRequestDTO;
import com.xinyu.chat.dto.ConversationCreateDTO;
import com.xinyu.conversation.vo.ConversationVO;
import com.xinyu.message.vo.MessageVO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 聊天编排服务（无表模块）: 会话创建、历史查询、SSE 流式聊天
 *
 * <p>依赖方向: chat → conversation/message/character/llm 的 Service, 单向不回环。
 */
public interface ChatService {

    /**
     * 创建会话, 同时写入角色 greeting 作为首条 ASSISTANT 消息
     *
     * @throws com.xinyu.common.exception.BizException 角色不存在时 40400
     */
    ConversationVO createConversation(Long userId, ConversationCreateDTO dto);

    /**
     * 游标分页查消息历史（升序返回）
     *
     * @param before 游标消息ID, 可空=最新一页
     * @param size   每页条数, 可空=20, 上限100
     * @throws com.xinyu.common.exception.BizException 会话不存在或非本人时 40400
     */
    List<MessageVO> listMessages(Long userId, Long conversationId, Long before, Integer size);

    /**
     * SSE 流式聊天: 落库 USER 消息与 ASSISTANT 占位 → 异步调 LLM 逐段推送
     *
     * <p>事件协议: meta(双消息ID) → delta*(增量) → done | error。
     * 前端 AbortController 断连时服务端将消息置 STOPPED 并保留已生成文本。
     */
    SseEmitter chat(Long userId, Long conversationId, ChatRequestDTO dto);
}
