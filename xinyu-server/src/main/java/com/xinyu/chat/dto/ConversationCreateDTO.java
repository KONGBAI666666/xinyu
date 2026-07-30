package com.xinyu.chat.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建会话请求
 */
@Data
public class ConversationCreateDTO {

    /** 绑定角色ID（前端传字符串, Jackson 自动转 Long） */
    @NotNull(message = "角色ID不能为空")
    private Long characterId;

    /** 标题可选, 缺省取角色名, 首条用户消息后自动改为其前20字 */
    @Size(max = 50, message = "标题最长50字")
    private String title;
}
