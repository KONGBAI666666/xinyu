package com.xinyu.conversation.controller;

import com.xinyu.common.result.Result;
import com.xinyu.common.result.ResultCode;
import com.xinyu.common.security.UserContext;
import com.xinyu.conversation.service.ConversationService;
import com.xinyu.conversation.vo.ConversationVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 会话管理接口（需登录; M1-4 范围: 仅会话列表）
 *
 * <p>与 ChatController 共用 /api/conversations 前缀但按职责分开:
 * 本类负责会话本身的管理（列表, 后续重命名/删除）,
 * ChatController 负责聊天链路（创建会话+开场白 / 消息历史 / SSE）。
 */
@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    /** 会话列表: 仅当前用户的会话, 按最新消息时间倒序 */
    @GetMapping
    public Result<List<ConversationVO>> list() {
        List<ConversationVO> list = conversationService.listByUser(UserContext.getUserId()).stream()
                .map(ConversationVO::from)
                .toList();
        return Result.success(list);
    }

    /** 删除会话: 逻辑删除会话及关联消息 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        boolean ok = conversationService.delete(id, UserContext.getUserId());
        if (!ok) {
            return Result.fail(ResultCode.NOT_FOUND.getCode(), "会话不存在或无权操作");
        }
        return Result.success();
    }

    /** 重命名会话 */
    @PutMapping("/{id}/title")
    public Result<ConversationVO> rename(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String title = body.get("title");
        com.xinyu.conversation.entity.Conversation updated =
                conversationService.rename(id, UserContext.getUserId(), title);
        if (updated == null) {
            return Result.fail(ResultCode.NOT_FOUND.getCode(), "会话不存在或无权操作");
        }
        return Result.success(ConversationVO.from(updated));
    }
}
