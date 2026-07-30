package com.xinyu.conversation.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xinyu.conversation.entity.Conversation;
import com.xinyu.conversation.mapper.ConversationMapper;
import com.xinyu.conversation.service.ConversationService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 会话服务实现
 */
@Service
public class ConversationServiceImpl extends ServiceImpl<ConversationMapper, Conversation>
        implements ConversationService {

    @Override
    public Conversation getOwned(Long conversationId, Long userId) {
        Conversation conversation = getById(conversationId);
        if (conversation == null || !conversation.getUserId().equals(userId)) {
            return null;
        }
        return conversation;
    }

    @Override
    public void refreshLastMessage(Long conversationId, String preview, LocalDateTime messageAt) {
        // 摘要列上限100字符, 超长截断
        String truncated = preview != null && preview.length() > 100 ? preview.substring(0, 100) : preview;
        lambdaUpdate()
                .eq(Conversation::getId, conversationId)
                .set(Conversation::getLastMessageAt, messageAt)
                .set(Conversation::getLastMessagePreview, truncated)
                .update();
    }
}
