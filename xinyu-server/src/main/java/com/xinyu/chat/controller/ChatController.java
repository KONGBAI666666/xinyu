package com.xinyu.chat.controller;

import com.xinyu.chat.dto.ChatRequestDTO;
import com.xinyu.chat.dto.ConversationCreateDTO;
import com.xinyu.chat.service.ChatService;
import com.xinyu.common.result.Result;
import com.xinyu.common.security.UserContext;
import com.xinyu.conversation.vo.ConversationVO;
import com.xinyu.message.vo.MessageVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 聊天接口（需登录; M1-3 范围: 创建会话 / 消息历史 / SSE 聊天）
 *
 * <p>SSE 端点返回 SseEmitter, Spring 自动置 Content-Type: text/event-stream;
 * 不在 @PostMapping 上声明 produces, 保证 BizException 时
 * 全局异常处理器仍能正常返回 JSON。
 */
@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /** 创建会话（同时写入角色开场白为首条 ASSISTANT 消息） */
    @PostMapping
    public Result<ConversationVO> createConversation(@Valid @RequestBody ConversationCreateDTO dto) {
        return Result.success(chatService.createConversation(UserContext.getUserId(), dto));
    }

    /** 消息历史（游标分页: before=游标消息ID, 缺省取最新一页） */
    @GetMapping("/{id}/messages")
    public Result<List<MessageVO>> listMessages(@PathVariable Long id,
                                                @RequestParam(required = false) Long before,
                                                @RequestParam(required = false) Integer size) {
        return Result.success(chatService.listMessages(UserContext.getUserId(), id, before, size));
    }

    /** SSE 流式聊天: meta → delta* → done | error */
    @PostMapping("/{id}/chat")
    public SseEmitter chat(@PathVariable Long id, @Valid @RequestBody ChatRequestDTO dto) {
        return chatService.chat(UserContext.getUserId(), id, dto);
    }
}
