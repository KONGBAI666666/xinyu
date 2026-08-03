package com.xinyu.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xinyu.common.security.AesCryptoUtil;
import com.xinyu.llm.model.entity.AiModel;
import com.xinyu.llm.model.service.AiModelService;
import com.xinyu.llm.openai.OpenAiCompatibleClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * LLM 客户端工厂（核心架构改造: 从单例 Bean → 动态工厂）
 *
 * <p>按模型配置创建 {@link OpenAiCompatibleClient}, 按 modelId 缓存复用。
 * 模型配置变更（API Key 修改/删除）时需调用 {@link #evict} 清除缓存。
 *
 * <p>选择优先级（由 {@link com.xinyu.chat.service.impl.ChatServiceImpl} 决策）:
 * <ol>
 *   <li>会话级模型（conversation.model_id）</li>
 *   <li>用户默认模型（ai_model.is_default=1）</li>
 *   <li>dev 环境: MockLlmClient 兜底; prod: 抛异常提示用户先配置模型</li>
 * </ol>
 */
@Slf4j
@Component
public class LlmClientFactory {

    private final AiModelService modelService;
    private final AesCryptoUtil crypto;
    private final ObjectMapper objectMapper;
    private final boolean devProfile;

    /** modelId → client 缓存（模型配置不变时复用, 避免重复创建 HttpClient） */
    private final ConcurrentHashMap<Long, OpenAiCompatibleClient> cache = new ConcurrentHashMap<>();

    public LlmClientFactory(AiModelService modelService,
                            AesCryptoUtil crypto,
                            ObjectMapper objectMapper,
                            @Value("${spring.profiles.active:prod}") String activeProfile) {
        this.modelService = modelService;
        this.crypto = crypto;
        this.objectMapper = objectMapper;
        this.devProfile = "dev".equalsIgnoreCase(activeProfile);
    }

    /**
     * 按 modelId 获取 client（带缓存）
     *
     * @param modelId 模型配置 ID
     * @param userId  当前用户（权限校验 + 解密 Key）
     */
    public LlmClient get(Long modelId, Long userId) {
        return cache.computeIfAbsent(modelId, id -> {
            AiModel model = modelService.getByIdAndUser(id, userId);
            return createClient(model);
        });
    }

    /**
     * 获取用户默认模型对应的 client（无缓存, 因为要按用户查）
     *
     * @return 默认模型 client; 无默认模型返回 null（由调用方决定降级策略）
     */
    public LlmClient getDefaultClient(Long userId) {
        AiModel model = modelService.getDefaultModel(userId);
        if (model == null) {
            return null;
        }
        return cache.computeIfAbsent(model.getId(), id -> createClient(model));
    }

    /**
     * 模型配置变更时清除缓存（编辑/删除模型后调用）
     */
    public void evict(Long modelId) {
        cache.remove(modelId);
        log.debug("LLM client 缓存已清除: modelId={}", modelId);
    }

    /** 清除全部缓存（用户切换/登出时可选调用） */
    public void evictAll() {
        cache.clear();
    }

    /** 是否 dev 环境（决定无模型时是否降级到 Mock） */
    public boolean isDevProfile() {
        return devProfile;
    }

    private OpenAiCompatibleClient createClient(AiModel model) {
        String apiKey = crypto.decrypt(model.getApiKeyEncrypted());
        LlmModelConfig config = new LlmModelConfig(
                model.getModelCode(),
                model.getBaseUrl(),
                apiKey
        );
        log.info("创建 LLM client: modelId={}, model={}, baseUrl={}",
                model.getId(), model.getModelCode(), model.getBaseUrl());
        return new OpenAiCompatibleClient(config, objectMapper);
    }
}
