package com.xinyu.message.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xinyu.message.entity.Message;
import com.xinyu.message.mapper.MessageMapper;
import com.xinyu.message.service.MessageService;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * 消息服务实现
 */
@Service
public class MessageServiceImpl extends ServiceImpl<MessageMapper, Message>
        implements MessageService {

    @Override
    public int nextSequenceNo(Long conversationId) {
        Message last = lambdaQuery()
                .eq(Message::getConversationId, conversationId)
                .orderByDesc(Message::getSequenceNo)
                .last("LIMIT 1")
                .one();
        return last == null ? 1 : last.getSequenceNo() + 1;
    }

    @Override
    public List<Message> listHistory(Long conversationId, Long before, int size) {
        // 倒序取一页再翻转为升序, 前端按时间正序渲染
        List<Message> page = lambdaQuery()
                .eq(Message::getConversationId, conversationId)
                .lt(before != null, Message::getId, before)
                .orderByDesc(Message::getId)
                .last("LIMIT " + size)
                .list();
        return page.stream()
                .sorted(Comparator.comparing(Message::getSequenceNo))
                .toList();
    }

    @Override
    public List<Message> listRecent(Long conversationId, int limit) {
        return listHistory(conversationId, null, limit);
    }
}
