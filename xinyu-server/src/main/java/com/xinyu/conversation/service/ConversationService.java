package com.xinyu.conversation.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xinyu.conversation.entity.Conversation;

import java.time.LocalDateTime;
import java.util.List;

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

    /**
     * 查询用户的全部会话, 按最新消息时间倒序（会话列表页）
     *
     * <p>M1 阶段单用户会话量小, 不分页; 会话数大后再演进为分页。
     */
    List<Conversation> listByUser(Long userId);

    /**
     * 删除会话（逻辑删除会话 + 关联消息）
     *
     * @return true=删除成功; false=会话不存在或不属于该用户
     */
    boolean delete(Long conversationId, Long userId);

    /**
     * 重命名会话
     *
     * @param conversationId 会话 ID
     * @param userId         用户 ID（鉴权）
     * @param newTitle       新标题
     * @return 更新后的会话; 不存在或越权返回 null
     */
    Conversation rename(Long conversationId, Long userId, String newTitle);
}
