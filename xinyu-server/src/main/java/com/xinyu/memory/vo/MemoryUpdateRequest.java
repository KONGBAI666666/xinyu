package com.xinyu.memory.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 记忆编辑请求（用户可改 content / importance / status, 不可改 memoryKey 与归属）
 */
@Data
public class MemoryUpdateRequest {

    /** 记忆正文 */
    @NotBlank(message = "记忆内容不能为空")
    private String content;

    /** 重要度: HIGH/MEDIUM/LOW */
    @NotBlank(message = "重要度不能为空")
    private String importance;

    /** 状态: ACTIVE/DISABLED */
    @NotNull(message = "状态不能为空")
    private String status;
}
