package com.xinyu.llm;

import com.xinyu.common.security.AesCryptoUtil;
import com.xinyu.llm.model.entity.AiModel;
import com.xinyu.llm.model.service.AiModelService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * LLM 模型配置解析器 (重构: 不再创建 LLM 客户端, 只解析解密后的配置传给 Python)
 *
 * <p>从 MySQL 读取用户配置的 AiModel, 解密 API Key, 构造 {@link LlmModelConfig}
 * 供 Java 侧通过 {@link AiServiceClient} 调用 Python AI 服务时使用。
 *
 * <p>选择优先级（由 {@link com.xinyu.chat.service.impl.ChatServiceImpl} 决策）:
 * <ol>
 *   <li>会话级模型（conversation.model_id）</li>
 *   <li>用户默认模型（ai_model.is_default=1）</li>
 *   <li>dev 环境: 传 dummy 配置, Python mock 模式兜底; prod: 抛异常提示用户先配置模型</li>
 * </ol>
 */
@Slf4j
@Component
public class LlmClientFactory {

    private final AiModelService modelService;
    private final AesCryptoUtil crypto;
    private final boolean devProfile;

    public LlmClientFactory(AiModelService modelService,
                            AesCryptoUtil crypto,
                            @Value("${spring.profiles.active:prod}") String activeProfile) {
        this.modelService = modelService;
        this.crypto = crypto;
        this.devProfile = "dev".equalsIgnoreCase(activeProfile);
    }

    /**
     * 按 modelId 解析模型配置 (解密 API Key), 供 Python AI 服务使用
     */
    public LlmModelConfig resolveConfig(Long modelId, Long userId) {
        AiModel model = modelService.getByIdAndUser(modelId, userId);
        return createConfig(model);
    }

    /**
     * 解析用户默认模型配置, 供 Python AI 服务使用
     *
     * @return 默认模型配置; 无默认模型返回 null
     */
    public LlmModelConfig resolveDefaultConfig(Long userId) {
        AiModel model = modelService.getDefaultModel(userId);
        if (model == null) {
            return null;
        }
        return createConfig(model);
    }

    /** 是否 dev 环境（决定无模型时是否降级到 Mock） */
    public boolean isDevProfile() {
        return devProfile;
    }

    private LlmModelConfig createConfig(AiModel model) {
        String apiKey = crypto.decrypt(model.getApiKeyEncrypted());
        return new LlmModelConfig(
                model.getModelCode(),
                model.getBaseUrl(),
                apiKey
        );
    }
}
