package com.xinyu.conversation.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xinyu.conversation.entity.Conversation;
import com.xinyu.conversation.mapper.ConversationMapper;
import com.xinyu.conversation.service.ConversationService;
import com.xinyu.message.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 会话服务实现
 */
@Service
@RequiredArgsConstructor
public class ConversationServiceImpl extends ServiceImpl<ConversationMapper, Conversation>
        implements ConversationService {

    private final MessageService messageService;

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
        String truncated = preview != null && preview.length() > 100 ? preview.substring(0, 100) : preview;
        lambdaUpdate()
                .eq(Conversation::getId, conversationId)
                .set(Conversation::getLastMessageAt, messageAt)
                .set(Conversation::getLastMessagePreview, truncated)
                .update();
    }

    @Override
    public List<Conversation> listByUser(Long userId) {
        // 上限 100 条: 侧边栏只展示最近会话, 防止会话量大时全表返回
        return lambdaQuery()
                .eq(Conversation::getUserId, userId)
                .orderByDesc(Conversation::getLastMessageAt)
                .last("LIMIT 100")
                .list();
    }

    @Override
    @Transactional
    public boolean delete(Long conversationId, Long userId) {
        Conversation conversation = getOwned(conversationId, userId);
        if (conversation == null) {
            return false;
        }
        messageService.deleteByConversation(conversationId);
        removeById(conversationId);
        return true;
    }

    @Override
    public Conversation rename(Long conversationId, Long userId, String newTitle) {
        Conversation conversation = getOwned(conversationId, userId);
        if (conversation == null) {
            return null;
        }
        String trimmed = newTitle != null ? newTitle.trim() : "";
        if (trimmed.isEmpty()) {
            return conversation;
        }
        // 标题长度限制 50 字符
        if (trimmed.length() > 50) {
            trimmed = trimmed.substring(0, 50);
        }
        lambdaUpdate()
                .eq(Conversation::getId, conversationId)
                .set(Conversation::getTitle, trimmed)
                .update();
        conversation.setTitle(trimmed);
        return conversation;
    }
}
