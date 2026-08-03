package com.xinyu.llm.model.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xinyu.common.exception.BizException;
import com.xinyu.common.result.ResultCode;
import com.xinyu.common.security.AesCryptoUtil;
import com.xinyu.llm.LlmClientFactory;
import com.xinyu.llm.model.entity.AiModel;
import com.xinyu.llm.model.mapper.AiModelMapper;
import com.xinyu.llm.model.service.AiModelService;
import com.xinyu.llm.model.vo.AiModelSaveRequest;
import com.xinyu.llm.model.vo.AiModelVO;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

/**
 * AI 模型配置服务实现
 *
 * <p>关键设计:
 * <ul>
 *   <li>API Key 加密入库: 明文 → AesCryptoUtil.encrypt → ai_model.api_key_encrypted</li>
 *   <li>默认模型唯一性: setDefault 时先把同用户其他模型 is_default=0, 事务保证</li>
 *   <li>删除默认模型: 自动把最近创建的一个置为默认, 避免用户无默认模型</li>
 * </ul>
 *
 * <p>注: {@link LlmClientFactory} 用 {@link Lazy} 注入, 打断 Factory↔Service 循环依赖
 * (Factory 构造需要 Service 查模型, Service 变更需要 Factory 清缓存)。
 */
@Service
public class AiModelServiceImpl implements AiModelService {

    private final AiModelMapper modelMapper;
    private final AesCryptoUtil crypto;
    private final LlmClientFactory clientFactory;

    public AiModelServiceImpl(AiModelMapper modelMapper, AesCryptoUtil crypto, @Lazy LlmClientFactory clientFactory) {
        this.modelMapper = modelMapper;
        this.crypto = crypto;
        this.clientFactory = clientFactory;
    }

    @Override
    public List<AiModelVO> listByUser(Long userId) {
        LambdaQueryWrapper<AiModel> qw = Wrappers.<AiModel>lambdaQuery()
                .eq(AiModel::getUserId, userId)
                .orderByDesc(AiModel::getIsDefault)
                .orderByDesc(AiModel::getCreatedAt);
        return modelMapper.selectList(qw).stream().map(this::toVO).toList();
    }

    @Override
    public AiModel getDefaultModel(Long userId) {
        return modelMapper.selectOne(Wrappers.<AiModel>lambdaQuery()
                .eq(AiModel::getUserId, userId)
                .eq(AiModel::getIsDefault, 1)
                .last("LIMIT 1"));
    }

    @Override
    public AiModel getByIdAndUser(Long id, Long userId) {
        AiModel model = modelMapper.selectById(id);
        if (model == null || !Objects.equals(model.getUserId(), userId)) {
            throw new BizException(ResultCode.NOT_FOUND, "模型不存在或无权访问");
        }
        return model;
    }

    @Override
    @Transactional
    public AiModelVO create(Long userId, AiModelSaveRequest req) {
        AiModel model = new AiModel();
        BeanUtils.copyProperties(req, model);
        model.setUserId(userId);
        model.setApiKeyEncrypted(crypto.encrypt(req.getApiKey()));
        model.setIsDefault(Boolean.TRUE.equals(req.getIsDefault()) ? 1 : 0);
        model.setEnabled(1);

        // 若设为默认, 先清掉旧的默认
        if (model.getIsDefault() == 1) {
            clearDefaultForUser(userId);
        }
        modelMapper.insert(model);
        return toVO(model);
    }

    @Override
    @Transactional
    public AiModelVO update(Long id, Long userId, AiModelSaveRequest req) {
        AiModel model = getByIdAndUser(id, userId);
        model.setProvider(req.getProvider());
        model.setModelCode(req.getModelCode());
        model.setDisplayName(req.getDisplayName());
        model.setBaseUrl(req.getBaseUrl());

        // apiKey 非空才覆盖, 空保留原值
        if (StringUtils.hasText(req.getApiKey())) {
            model.setApiKeyEncrypted(crypto.encrypt(req.getApiKey()));
        }

        boolean wantDefault = Boolean.TRUE.equals(req.getIsDefault());
        if (wantDefault && (model.getIsDefault() == null || model.getIsDefault() != 1)) {
            clearDefaultForUser(userId);
            model.setIsDefault(1);
        } else if (!wantDefault) {
            model.setIsDefault(0);
        }
        modelMapper.updateById(model);
        clientFactory.evict(model.getId());
        return toVO(model);
    }

    @Override
    @Transactional
    public void delete(Long id, Long userId) {
        AiModel model = getByIdAndUser(id, userId);
        boolean wasDefault = model.getIsDefault() != null && model.getIsDefault() == 1;
        modelMapper.deleteById(id);
        clientFactory.evict(id);

        // 删除的是默认模型 → 自动选最近创建的一个为默认
        if (wasDefault) {
            AiModel next = modelMapper.selectOne(Wrappers.<AiModel>lambdaQuery()
                    .eq(AiModel::getUserId, userId)
                    .orderByDesc(AiModel::getCreatedAt)
                    .last("LIMIT 1"));
            if (next != null) {
                next.setIsDefault(1);
                modelMapper.updateById(next);
                clientFactory.evict(next.getId());
            }
        }
    }

    @Override
    @Transactional
    public void setDefault(Long id, Long userId) {
        AiModel model = getByIdAndUser(id, userId);
        clearDefaultForUser(userId);
        model.setIsDefault(1);
        modelMapper.updateById(model);
        clientFactory.evict(id);
    }

    /** 把该用户所有模型的 is_default 置 0 */
    private void clearDefaultForUser(Long userId) {
        AiModel update = new AiModel();
        update.setIsDefault(0);
        modelMapper.update(update, Wrappers.<AiModel>lambdaUpdate()
                .eq(AiModel::getUserId, userId)
                .eq(AiModel::getIsDefault, 1));
    }

    /** 实体 → VO, API Key 转掩码 */
    private AiModelVO toVO(AiModel model) {
        AiModelVO vo = new AiModelVO();
        BeanUtils.copyProperties(model, vo);
        vo.setApiKeyMasked(maskApiKey(model.getApiKeyEncrypted()));
        return vo;
    }

    /**
     * 掩码策略: 已配置 → "已配置 ****", 未配置 → null
     * 不泄露 key 片段, 避免通过掩码推测
     */
    private String maskApiKey(String encrypted) {
        if (!StringUtils.hasText(encrypted)) {
            return null;
        }
        return "已配置 ****";
    }
}
