package com.xinyu.llm.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 模型配置实体（用户级, 每个用户管理自己的模型库）
 *
 * <p>支持 OpenAI 兼容协议: 通义千问/DeepSeek/GPT/Kimi/GLM/Ollama 等。
 * API Key 经 AES-GCM 加密后存 api_key_encrypted, 不存明文。
 */
@Data
@TableName("ai_model")
public class AiModel {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 归属用户 */
    private Long userId;

    /** 供应商: QWEN/OPENAI/DEEPSEEK/MOONSHOT/GLM/OLLAMA... */
    private String provider;

    /** 模型标识: qwen-plus / gpt-4o / deepseek-chat */
    private String modelCode;

    /** 展示名（用户自定义） */
    private String displayName;

    /** OpenAI 兼容接口地址 */
    private String baseUrl;

    /** AES-GCM 加密后的 API Key（解密后用） */
    private String apiKeyEncrypted;

    /** 是否用户默认模型（每用户至多 1 个） */
    private Integer isDefault;

    /** 是否启用 */
    private Integer enabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
