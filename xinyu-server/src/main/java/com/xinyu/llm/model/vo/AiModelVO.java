package com.xinyu.llm.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 模型配置 VO（返回前端用, 不含 API Key 明文）
 */
@Data
public class AiModelVO {

    private Long id;
    private String provider;
    private String modelCode;
    private String displayName;
    private String baseUrl;

    /**
     * API Key 掩码: 已配置返回 sk-****1234, 未配置返回 null。
     * 前端据此判断是否已设置, 编辑时若不传新值则保留原值。
     */
    private String apiKeyMasked;

    private Integer isDefault;
    private Integer enabled;
    private LocalDateTime createdAt;
}
