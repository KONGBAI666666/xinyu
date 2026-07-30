package com.xinyu.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发送消息请求（SSE 聊天）
 *
 * <p>上下文完全服务端组装, 前端只传当前输入（防篡改/省流量/职责归位）。
 */
@Data
public class ChatRequestDTO {

    /** 用户输入 */
    @NotBlank(message = "消息内容不能为空")
    @Size(max = 2000, message = "单条消息最长2000字")
    private String content;

    /** 前端 UUID, 幂等防重复提交, 可空 */
    @Size(max = 64, message = "clientMessageId 最长64字符")
    private String clientMessageId;
}
