package com.xinyu.conversation.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xinyu.conversation.entity.Conversation;

import java.time.LocalDateTime;

/**
 * 会话服务: conversation 模块对外唯一入口
 *
 * <p>其他模块（chat 等）只依赖本接口, 禁止直接引用 ConversationMapper。
 */
public interface ConversationService extends IService<Conversation> {

    /**
     * 查询归属指定用户的会话, 不存在或不属于该用户返回 null
     *
     * <p>越权访问与不存在同样返回 null, 由调用方统一按 40400 处理,
     * 不向外暴露"会话存在但不属于你"的信息。
     */
    Conversation getOwned(Long conversationId, Long userId);

    /**
     * 刷新会话冗余字段: 最新消息时间 + 摘要（会话列表排序/副标题用）
     */
    void refreshLastMessage(Long conversationId, String preview, LocalDateTime messageAt);
}
